package cz.healthwidgy.ui

import androidx.health.connect.client.records.ExerciseSessionRecord
import cz.healthwidgy.i18n.AppLang
import cz.healthwidgy.i18n.Lang
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FormatTest {
  private val prague = ZoneId.of("Europe/Prague")

  // Czech unless a test switches; tests must not depend on the machine's locale.
  @Before fun czech() { AppLang.override = Lang.CS }

  @After fun reset() { AppLang.override = null }

  @Test
  fun durationUsesHoursOnlyWhenNeeded() {
    assertEquals("7 h 24 min", Format.duration(444))
    assertEquals("42 min", Format.duration(42))
  }

  @Test
  fun clockFormatsWorkoutLength() {
    assertEquals("42:10", Format.clock(42 * 60 + 10))
    assertEquals("1:02:05", Format.clock(3600 + 2 * 60 + 5))
  }

  @Test
  fun paceIsMinutesPerKilometre() {
    // 2530 s / 8.2 km = 308.5 s per km, rounded to the nearest second.
    assertEquals("5:09", Format.pace(seconds = 42 * 60 + 10, meters = 8200.0))
    assertNull(Format.pace(seconds = 600, meters = 50.0))
  }

  @Test
  fun dayAndTimeUsesTodayAndYesterday() {
    val today = LocalDate.of(2026, 11, 10)
    val morning = Instant.parse("2026-11-10T06:05:00Z") // 7:05 in Prague (CET)
    val yesterday = Instant.parse("2026-11-09T16:05:00Z") // 17:05
    assertEquals("Dnes 7:05", Format.dayAndTime(morning, prague, today))
    assertEquals("Včera 17:05", Format.dayAndTime(yesterday, prague, today))
  }

  @Test
  fun olderDatesShowWeekdayAndDate() {
    val today = LocalDate.of(2026, 11, 10)
    val monday = Instant.parse("2026-11-02T16:05:00Z")
    assertEquals("po 2. 11. 17:05", Format.dayAndTime(monday, prague, today))
    assertEquals("Út", Format.weekdayShort(today))
  }

  @Test
  fun czechPlurals() {
    assertEquals("patro", Format.plural(1, "patro", "patra", "pater"))
    assertEquals("patra", Format.plural(3, "patro", "patra", "pater"))
    assertEquals("pater", Format.plural(0, "patro", "patra", "pater"))
    assertEquals("pater", Format.plural(12, "patro", "patra", "pater"))
  }

  @Test
  fun kilometresUseCzechDecimalComma() {
    assertEquals("6,1", Format.km(6120.0))
  }

  @Test
  fun percentAndMidSentenceDatesFollowTheLanguage() {
    val today = LocalDate.of(2026, 11, 10)
    val morning = Instant.parse("2026-11-10T06:05:00Z")
    val monday = Instant.parse("2026-11-02T16:05:00Z")
    assertEquals("62 %", Format.percent(62))
    assertEquals("dnes 7:05", Format.dayAndTime(morning, prague, today, inSentence = true))
    assertEquals("po 2. 11. 17:05", Format.dayAndTime(monday, prague, today, inSentence = true))
    AppLang.override = Lang.EN
    assertEquals("62%", Format.percent(62))
    assertEquals("23.4%", Format.percent(Format.decimal(23.4)))
    assertEquals("today 07:05", Format.dayAndTime(morning, prague, today, inSentence = true))
    assertEquals("Mon Nov 2 17:05", Format.dayAndTime(monday, prague, today, inSentence = true))
  }

  @Test
  fun englishUsesEnglishNumbersDatesAndWords() {
    AppLang.override = Lang.EN
    val today = LocalDate.of(2026, 11, 10)
    assertEquals("6.1", Format.km(6120.0))
    assertEquals("7,369", Format.int(7369L))
    assertEquals("Today 07:05", Format.dayAndTime(Instant.parse("2026-11-10T06:05:00Z"), prague, today))
    assertEquals("Mon Nov 2 17:05", Format.dayAndTime(Instant.parse("2026-11-02T16:05:00Z"), prague, today))
    assertEquals("Tuesday, November 10", Format.longDate(today))
    assertEquals("Nov 3–9", Format.weekRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)))
    assertEquals("Oct 27 – Nov 2", Format.weekRange(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2)))
    assertEquals("floors", Format.pluralEn(0, "floor", "floors"))
    assertEquals("Running", Format.exerciseName(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
  }
}
