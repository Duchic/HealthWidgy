package cz.healthwidgy.widget

import android.content.Context
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.db.ExerciseSessionEntity
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.settings.ThemeMode
import cz.healthwidgy.data.sync.BodyKinds
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.data.sync.SyncEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** One cached day of a metric. */
data class DayRow(val value: Double?, val min: Double?, val max: Double?)

/** One cached hour of heart rate. */
data class HourRow(val start: Instant, val min: Double?, val max: Double?, val avg: Double?)

data class Goals(val steps: Int, val floors: Int, val distanceMeters: Double, val sleepMinutes: Int)

/**
 * Everything the widgets show, read from the local database (never from Health Connect directly, so a
 * widget renders even when Health Connect is busy or the app has no background access).
 */
data class WidgetSnapshot(
  val today: LocalDate,
  val goals: Goals,
  val theme: ThemeMode,
  /** Daily rows per metric id (see [Metrics]) for the last [WidgetData.DAYS] days. */
  val daily: Map<String, Map<LocalDate, DayRow>>,
  /** Today's heart rate per hour, oldest first. */
  val heartHours: List<HourRow>,
  /** The main sleep that ended most recently (within 2 days). */
  val sleep: SleepSessionEntity?,
  val workout: ExerciseSessionEntity?,
  /** Weight and body fat measurements (any source) of the last 120 days, oldest first. */
  val weights: List<BodyMeasurementEntity>,
  val bodyFats: List<BodyMeasurementEntity>,
  val lastSyncAt: Instant?,
) {
  fun value(metric: String, date: LocalDate = today): Double? = daily[metric]?.get(date)?.value

  /** The last [count] days of a daily metric, oldest first (today last). */
  fun days(metric: String, count: Int): List<Pair<LocalDate, Double?>> =
    (count - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to value(metric, it) }

  val steps: Double? get() = value(Metrics.STEPS)
  val distanceMeters: Double? get() = value(Metrics.DISTANCE)
  val floors: Double? get() = value(Metrics.FLOORS)
  val totalKcal: Double? get() = value(Metrics.TOTAL_KCAL)

  /** Today's resting heart rate, or the newest one of the last week. */
  val restingHr: Pair<LocalDate, Double>?
    get() = days(Metrics.RESTING_HR, 7).lastOrNull { it.second != null }?.let { it.first to it.second!! }

  val hrMin: Double? get() = daily[Metrics.HEART_RATE]?.get(today)?.min
  val hrMax: Double? get() = daily[Metrics.HEART_RATE]?.get(today)?.max

  /** Average of the newest hour with heart rate data today. */
  val lastHourHr: Pair<Instant, Double>?
    get() = heartHours.lastOrNull { it.avg != null }?.let { it.start to it.avg!! }

  val weight: BodyMeasurementEntity? get() = weights.lastOrNull()
  val bodyFat: BodyMeasurementEntity? get() = bodyFats.lastOrNull()

  val sleepIsLastNight: Boolean
    get() = sleep?.wakeDate == today.toString()

  /** The newest measurement per day for the last [count] days, oldest first. */
  fun bodyDays(list: List<BodyMeasurementEntity>, count: Int): List<Pair<LocalDate, Double?>> {
    val byDay = list.groupBy { it.date }.mapValues { (_, v) -> v.maxBy { it.time }.value }
    return (count - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to byDay[it.toString()] }
  }
}

object WidgetData {
  /** Six weeks: the month calendar and 30-day trends. */
  const val DAYS = 42L

  private val dailyMetrics = listOf(Metrics.STEPS, Metrics.DISTANCE, Metrics.FLOORS, Metrics.TOTAL_KCAL, Metrics.HEART_RATE, Metrics.RESTING_HR, Metrics.SLEEP)

  suspend fun load(context: Context): WidgetSnapshot {
    val container = context.appContainer
    val settings = container.settings.settings.first()
    val dao = container.database.dao()
    val mode = settings.sourceMode.name
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from = today.minusDays(DAYS - 1).toString()

    val daily =
      dailyMetrics.associateWith { metric ->
        dao.dailyFlow(metric, mode, from).first().associate { LocalDate.parse(it.date) to DayRow(it.value, it.min, it.max) }
      }

    val sleeps = dao.sleepFlow(mode, today.minusDays(2).toString()).first()
    val mainSleep =
      sleeps
        .groupBy { it.wakeDate }
        .maxByOrNull { it.key }
        ?.value
        ?.let { s -> s.filter { it.end - it.start >= SyncEngine.MAIN_SLEEP.toMillis() }.maxByOrNull { it.asleepMinutes } ?: s.maxByOrNull { it.asleepMinutes } }
    val hours = dao.hourlyFlow(Metrics.HEART_RATE, mode, today.toString()).first().map { HourRow(Instant.ofEpochMilli(it.hourStart), it.min, it.max, it.avg) }
    val bodyFrom = today.minusDays(120).atStartOfDay(zone).toInstant().toEpochMilli()

    return WidgetSnapshot(
      today = today,
      goals = Goals(settings.stepGoal, settings.floorsGoal, settings.distanceGoalKm * 1000.0, settings.sleepGoalMinutes),
      theme = settings.widgetTheme,
      daily = daily,
      heartHours = hours,
      sleep = mainSleep,
      workout = dao.exerciseFlow(mode).first().firstOrNull(),
      weights = dao.bodyFlow(BodyKinds.WEIGHT, bodyFrom).first(),
      bodyFats = dao.bodyFlow(BodyKinds.BODY_FAT, bodyFrom).first(),
      lastSyncAt = dao.syncState(mode)?.lastSyncAt?.let(Instant::ofEpochMilli),
    )
  }
}
