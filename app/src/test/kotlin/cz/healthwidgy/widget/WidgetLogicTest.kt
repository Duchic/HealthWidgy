package cz.healthwidgy.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.settings.ThemeMode
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.i18n.AppLang
import cz.healthwidgy.i18n.Lang
import cz.healthwidgy.ui.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WidgetLogicTest {
  private val today = LocalDate.of(2026, 11, 12) // Thursday
  private val prague = ZoneId.of("Europe/Prague")

  // The expected texts below are Czech; the English test switches the language itself.
  @Before
  fun czech() {
    AppLang.override = Lang.CS
  }

  @After
  fun resetLanguage() {
    AppLang.override = null
  }

  private fun snapshot(
    daily: Map<String, Map<LocalDate, DayRow>> = emptyMap(),
    sleep: SleepSessionEntity? = null,
    weights: List<BodyMeasurementEntity> = emptyList(),
  ) =
    WidgetSnapshot(
      today = today,
      goals = Goals(steps = 10_000, floors = 10, distanceMeters = 8000.0, sleepMinutes = 480),
      theme = ThemeMode.SYSTEM,
      daily = daily,
      heartHours = emptyList(),
      sleep = sleep,
      workout = null,
      weights = weights,
      bodyFats = emptyList(),
      lastSyncAt = null,
    )

  private fun day(vararg values: Pair<LocalDate, Double>) = values.associate { (d, v) -> d to DayRow(v, null, null) }

  private fun night(wake: LocalDate, asleep: Long) = SleepSessionEntity("n", "GARMIN_ONLY", 0, 1, wake.toString(), asleep, 0, 0, 0, 0, 0, "")

  @Test
  fun calendarHasWholeWeeksEndingWithTodaysWeek() {
    val weeks = calendarWeeks(today, 5)
    assertEquals(5, weeks.size)
    assertEquals(LocalDate.of(2026, 10, 12), weeks.first().first()) // Monday four weeks back
    assertEquals(LocalDate.of(2026, 11, 15), weeks.last().last()) // Sunday of this week
    assert(weeks.all { it.size == 7 })
  }

  @Test
  fun calendarShadesBySharesOfGoal() {
    assertEquals(0.10f, calendarAlpha(0.0))
    assertEquals(0.30f, calendarAlpha(0.49))
    assertEquals(0.58f, calendarAlpha(0.99))
    assertEquals(1f, calendarAlpha(1.0))
  }

  @Test
  fun heartScaleRoundsToTensWithMinimumSpan() {
    assertEquals(50.0 to 150.0, heartScale(listOf(WidgetBitmaps.HourBpm(0, 52.0, 148.0, 70.0))))
    assertEquals(60.0 to 100.0, heartScale(listOf(WidgetBitmaps.HourBpm(0, 61.0, 68.0, 64.0))))
  }

  @Test
  fun heartHoursSkipIncompleteRowsAndUseLocalHour() {
    val nine = today.atTime(9, 0).atZone(prague).toInstant()
    val rows = listOf(HourRow(nine, 60.0, 90.0, 72.0), HourRow(nine.plusSeconds(3600), null, 80.0, 70.0))
    val hours = heartHours(rows, prague)
    assertEquals(1, hours.size)
    assertEquals(9, hours.single().slot)
  }

  @Test
  fun stepsReadingShowsShareOfGoal() {
    val s = snapshot(daily = mapOf(Metrics.STEPS to day(today to 8432.0)))
    val r = s.read(WidgetMetric.STEPS)
    assertEquals("8 432", r.value)
    assertEquals("84 % cíle", r.note)
    assertEquals(0.8432f, r.progress!!, 0.0001f)
  }

  @Test
  fun heartHoursMergeTheRepeatedHourOfTheAutumnClockChange() {
    val day = LocalDate.of(2026, 10, 25) // CEST → CET at 3:00, 2:00–3:00 happens twice
    val first = day.atTime(2, 0).atZone(prague).withEarlierOffsetAtOverlap().toInstant()
    val rows = listOf(HourRow(first, 50.0, 60.0, 55.0), HourRow(first.plusSeconds(3600), 48.0, 70.0, 57.0))
    val hours = heartHours(rows, prague)
    assertEquals(1, hours.size)
    assertEquals(WidgetBitmaps.HourBpm(2, 48.0, 70.0, 56.0), hours.single())
  }

  @Test
  fun missingDistanceAndFloorsAreUnknownNotZero() {
    val s = snapshot(daily = mapOf(Metrics.STEPS to day(today to 500.0)))
    assertEquals(NO_VALUE, s.read(WidgetMetric.DISTANCE).value)
    assertNull(s.read(WidgetMetric.FLOORS).note)
    assertEquals("500", s.read(WidgetMetric.STEPS).value)
  }

  @Test
  fun goalRingDoesNotShowAnOlderNightAsToday() {
    val r = snapshot(sleep = night(today.minusDays(1), 452)).readForGoal(WidgetMetric.SLEEP)
    assertEquals(NO_VALUE, r.value)
    assertEquals("naposledy včera 7:32 h", r.note)
    assertEquals("7:32", snapshot(sleep = night(today, 452)).readForGoal(WidgetMetric.SLEEP).value)
  }

  @Test
  fun multiValueLayoutFollowsTheWidgetSize() {
    // One UI cells: 4×1 ≈ 381×76 dp of content, 2×1 ≈ 167×76, 2×2 ≈ 167×198, 4×2 ≈ 381×198.
    assertEquals(MultiLayout(cols = 4, compact = false, valueSize = 17, showNote = true), multiValueLayout(4, 381f, 76f))
    assertEquals(MultiLayout(cols = 2, compact = true, valueSize = 20, showNote = false), multiValueLayout(4, 167f, 76f))
    assertEquals(MultiLayout(cols = 2, compact = false, valueSize = 24, showNote = true), multiValueLayout(3, 167f, 198f))
    assertEquals(MultiLayout(cols = 2, compact = false, valueSize = 24, showNote = true), multiValueLayout(4, 381f, 198f))
    assertEquals(MultiLayout(cols = 2, compact = false, valueSize = 24, showNote = true), multiValueLayout(2, 167f, 76f))
  }

  @Test
  fun valueFontShrinksWithTheTextAndStaysWithinBounds() {
    // 1×1 on One UI: about 77 dp of content width.
    assertEquals(26, fitFontSize("52", 77f, max = 26))
    assertEquals(24, fitFontSize("7 369", 77f, max = 26))
    assertEquals(20, fitFontSize("12 345", 77f, max = 26))
    assertEquals(10, fitFontSize("1 234 567 890", 40f, max = 26))
    assertEquals(10, fitFontSize("", 3f, max = 8))
  }

  @Test
  fun compactCaptionNamesTheUnitAndAnOlderDay() {
    val steps = snapshot(daily = mapOf(Metrics.STEPS to day(today to 7369.0)))
    assertEquals("kroků", steps.compactCaption(WidgetMetric.STEPS, steps.read(WidgetMetric.STEPS)))
    val resting = snapshot(daily = mapOf(Metrics.RESTING_HR to day(today to 52.0)))
    assertEquals("bpm v klidu", resting.compactCaption(WidgetMetric.RESTING_HR, resting.read(WidgetMetric.RESTING_HR)))
    val weight = snapshot(weights = listOf(BodyMeasurementEntity("w", "weight", 0, today.minusDays(1).toString(), 79.6, "x")))
    assertEquals("kg · včera", weight.compactCaption(WidgetMetric.WEIGHT, weight.read(WidgetMetric.WEIGHT)))
    val oldNight = snapshot(sleep = night(today.minusDays(3), 430))
    assertEquals("h · ${Format.dayMonth(today.minusDays(3))}", oldNight.compactCaption(WidgetMetric.SLEEP, oldNight.read(WidgetMetric.SLEEP)))
    val empty = snapshot()
    assertEquals("Vzdálenost", empty.compactCaption(WidgetMetric.DISTANCE, empty.read(WidgetMetric.DISTANCE)))
  }

  @Test
  fun floorsUseCzechPlural() {
    assertEquals("patra", snapshot(daily = mapOf(Metrics.FLOORS to day(today to 3.0))).read(WidgetMetric.FLOORS).unit)
    assertEquals("pater", snapshot(daily = mapOf(Metrics.FLOORS to day(today to 12.0))).read(WidgetMetric.FLOORS).unit)
  }

  @Test
  fun sleepFromAnEarlierNightHasNoProgress() {
    val r = snapshot(sleep = night(today.minusDays(1), 452)).read(WidgetMetric.SLEEP)
    assertEquals("7:32", r.value)
    assertEquals("včera", r.note)
    assertEquals(0f, r.progress!!, 0f)
    val lastNight = snapshot(sleep = night(today, 480)).read(WidgetMetric.SLEEP)
    assertEquals(1f, lastNight.progress!!, 0f)
  }

  @Test
  fun restingHeartRateFallsBackToNewestDayOfTheWeek() {
    val s = snapshot(daily = mapOf(Metrics.RESTING_HR to day(today.minusDays(2) to 52.0)))
    val r = s.read(WidgetMetric.RESTING_HR)
    assertEquals("52", r.value)
    assertEquals(Format.dayMonth(today.minusDays(2)), r.note)
  }

  @Test
  fun weightSeriesTakesNewestMeasurementPerDay() {
    fun w(date: LocalDate, hour: Int, kg: Double) =
      BodyMeasurementEntity("$date$hour", "weight", date.atTime(hour, 0).atZone(prague).toInstant().toEpochMilli(), date.toString(), kg, "x")
    val s = snapshot(weights = listOf(w(today.minusDays(1), 7, 82.0), w(today.minusDays(1), 20, 82.6), w(today, 7, 81.9)))
    val series = s.series(WidgetMetric.WEIGHT, 3).map { it.second }
    assertEquals(listOf(null, 82.6, 81.9), series)
    assertEquals("dnes", s.read(WidgetMetric.WEIGHT).note)
  }

  @Test
  fun choicesFallBackToDefaultsAndKeepEmptyOptionalSlots() {
    val required = Slot("ring", "Kroužek", "Ring", WidgetMetric.withGoal, WidgetMetric.STEPS)
    val optional = Slot("slot3", "Hodnota 3", "Value 3", WidgetMetric.pickerOrder, WidgetMetric.CALORIES, optional = true)
    assertEquals(WidgetMetric.STEPS, WidgetChoices.metric(null, required))
    // Not allowed in this slot (no goal) or empty in a required slot: default.
    assertEquals(WidgetMetric.STEPS, WidgetChoices.metric(mutablePreferencesOf(stringPreferencesKey("ring") to "CALORIES"), required))
    assertEquals(WidgetMetric.STEPS, WidgetChoices.metric(mutablePreferencesOf(stringPreferencesKey("ring") to WidgetChoices.EMPTY), required))
    assertNull(WidgetChoices.metric(mutablePreferencesOf(stringPreferencesKey("slot3") to WidgetChoices.EMPTY), optional))

    val config = WidgetConfig(listOf(required, optional), periods = listOf(7, 30), defaultPeriod = 7)
    val prefs = mutablePreferencesOf()
    WidgetChoices.write(prefs, config, mapOf("ring" to WidgetMetric.SLEEP, "slot3" to null), 30, theme = null)
    assertEquals(WidgetMetric.SLEEP, WidgetChoices.metric(prefs, required))
    assertNull(WidgetChoices.metric(prefs, optional))
    assertEquals(30, WidgetChoices.period(prefs, config))
    assertEquals("Spánek · 30 dní", WidgetChoices.summary(prefs, config))
  }

  @Test
  fun widgetThemeIsStoredPerWidgetAndCanFollowTheAppAgain() {
    val config = WidgetConfig(emptyList())
    val prefs = mutablePreferencesOf()
    // Nothing chosen yet: follow Settings → Vzhled → Widgety.
    assertNull(WidgetChoices.theme(prefs))
    assertEquals("Vzhled podle nastavení", WidgetChoices.summary(prefs, config))

    WidgetChoices.write(prefs, config, emptyMap(), period = null, theme = ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, WidgetChoices.theme(prefs))
    assertEquals("tmavý vzhled", WidgetChoices.summary(prefs, config))

    val single = WidgetConfig(listOf(Slot("metric", "Hodnota", "Value", WidgetMetric.pickerOrder, WidgetMetric.STEPS)))
    WidgetChoices.write(prefs, single, mapOf("metric" to WidgetMetric.WEIGHT), period = null, theme = ThemeMode.LIGHT)
    assertEquals("Váha · světlý vzhled", WidgetChoices.summary(prefs, single))

    WidgetChoices.write(prefs, single, mapOf("metric" to WidgetMetric.WEIGHT), period = null, theme = null)
    assertNull(WidgetChoices.theme(prefs))
    // An unknown stored value (e.g. from a newer version) falls back to the app setting.
    assertNull(WidgetChoices.theme(mutablePreferencesOf(WidgetChoices.THEME_KEY to "SEPIA")))
  }

  @Test
  fun englishTextsFollowTheLanguage() {
    AppLang.override = Lang.EN
    val steps = snapshot(daily = mapOf(Metrics.STEPS to day(today to 7369.0)))
    val r = steps.read(WidgetMetric.STEPS)
    assertEquals("7,369", r.value)
    assertEquals("74% of goal", r.note)
    assertEquals("steps", steps.compactCaption(WidgetMetric.STEPS, r))
    val resting = snapshot(daily = mapOf(Metrics.RESTING_HR to day(today to 52.0)))
    assertEquals("bpm at rest", resting.compactCaption(WidgetMetric.RESTING_HR, resting.read(WidgetMetric.RESTING_HR)))
    val weight = snapshot(weights = listOf(BodyMeasurementEntity("w", "weight", 0, today.minusDays(1).toString(), 79.6, "x")))
    assertEquals("kg · yesterday", weight.compactCaption(WidgetMetric.WEIGHT, weight.read(WidgetMetric.WEIGHT)))
    assertEquals("floor", snapshot(daily = mapOf(Metrics.FLOORS to day(today to 1.0))).read(WidgetMetric.FLOORS).unit)
    assertEquals("floors", snapshot(daily = mapOf(Metrics.FLOORS to day(today to 3.0))).read(WidgetMetric.FLOORS).unit)
    // Labels kept in enums and objects are read in the language of the moment, not the one at startup.
    assertEquals("Resting HR", WidgetMetric.RESTING_HR.shortLabel)
    val slot = Slot("metric", "Údaj", "Metric", WidgetMetric.withHistory, WidgetMetric.STEPS)
    assertEquals("Metric", slot.title)
    val config = WidgetConfig(listOf(slot), periods = listOf(7, 30), defaultPeriod = 7)
    val prefs = mutablePreferencesOf()
    WidgetChoices.write(prefs, config, mapOf("metric" to WidgetMetric.SLEEP), 30, theme = ThemeMode.DARK)
    assertEquals("Sleep · 30 days · dark theme", WidgetChoices.summary(prefs, config))
  }
}
