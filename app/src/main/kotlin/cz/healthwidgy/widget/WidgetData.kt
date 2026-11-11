package cz.healthwidgy.widget

import android.content.Context
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.db.ExerciseSessionEntity
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.sync.BodyKinds
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.data.sync.SyncEngine
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Everything the widgets show, read from the local database (never from Health Connect directly, so a
 * widget renders even when Health Connect is busy or the app has no background access).
 */
data class WidgetSnapshot(
  val today: LocalDate,
  val stepGoal: Int,
  val steps: Double?,
  val distanceMeters: Double?,
  val floors: Double?,
  val totalKcal: Double?,
  val restingHr: Double?,
  val hrMin: Double?,
  val hrMax: Double?,
  /** Average heart rate of the newest hour with data today. */
  val lastHourHr: Pair<Instant, Double>?,
  /** Resting heart rate for the last 7 days, oldest first. */
  val restingLast7: List<Double?>,
  /** Steps for the last 7 days, oldest first (today last). */
  val steps7: List<Pair<LocalDate, Double?>>,
  /** Sleep for the last 7 nights in minutes, oldest first. */
  val sleep7: List<Pair<LocalDate, Double?>>,
  /** Total calories for the last 7 days. */
  val kcal7: List<Pair<LocalDate, Double?>>,
  /** The main sleep that ended most recently (within 2 days). */
  val sleep: SleepSessionEntity?,
  val workout: ExerciseSessionEntity?,
  val weight: BodyMeasurementEntity?,
  val weightLast7: List<Double>,
  val bodyFat: BodyMeasurementEntity?,
  val lastSyncAt: Instant?,
) {
  val sleepIsLastNight: Boolean
    get() = sleep?.wakeDate == today.toString()
}

object WidgetData {
  suspend fun load(context: Context): WidgetSnapshot {
    val container = context.appContainer
    val settings = container.settings.settings.first()
    val dao = container.database.dao()
    val mode = settings.sourceMode.name
    val today = LocalDate.now()
    val weekFrom = today.minusDays(6)

    suspend fun days(metric: String): Map<LocalDate, DayRow> =
      dao.dailyFlow(metric, mode, weekFrom.toString()).first().associate { LocalDate.parse(it.date) to DayRow(it.value, it.min, it.max) }

    val steps = days(Metrics.STEPS)
    val distance = days(Metrics.DISTANCE)
    val floors = days(Metrics.FLOORS)
    val kcal = days(Metrics.TOTAL_KCAL)
    val heart = days(Metrics.HEART_RATE)
    val resting = days(Metrics.RESTING_HR)
    val sleepDays = days(Metrics.SLEEP)
    val week = (0L..6L).map { weekFrom.plusDays(it) }

    val sleeps = dao.sleepFlow(mode, today.minusDays(2).toString()).first()
    val mainSleep =
      sleeps
        .groupBy { it.wakeDate }
        .maxByOrNull { it.key }
        ?.value
        ?.let { s -> s.filter { it.end - it.start >= SyncEngine.MAIN_SLEEP.toMillis() }.maxByOrNull { it.asleepMinutes } ?: s.maxByOrNull { it.asleepMinutes } }
    val lastHour = dao.hourlyFlow(Metrics.HEART_RATE, mode, today.toString()).first().lastOrNull()
    val weights = dao.bodyFlow(BodyKinds.WEIGHT, today.minusDays(120).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()).first()
    val fats = dao.bodyFlow(BodyKinds.BODY_FAT, today.minusDays(120).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()).first()

    return WidgetSnapshot(
      today = today,
      stepGoal = settings.stepGoal,
      steps = steps[today]?.value,
      distanceMeters = distance[today]?.value,
      floors = floors[today]?.value,
      totalKcal = kcal[today]?.value,
      restingHr = resting[today]?.value ?: resting.entries.maxByOrNull { it.key }?.value?.value,
      hrMin = heart[today]?.min,
      hrMax = heart[today]?.max,
      lastHourHr = lastHour?.avg?.let { Instant.ofEpochMilli(lastHour.hourStart) to it },
      restingLast7 = week.map { resting[it]?.value },
      steps7 = week.map { it to steps[it]?.value },
      sleep7 = week.map { it to sleepDays[it]?.value },
      kcal7 = week.map { it to kcal[it]?.value },
      sleep = mainSleep,
      workout = dao.exerciseFlow(mode).first().firstOrNull(),
      weight = weights.lastOrNull(),
      weightLast7 = weights.groupBy { it.date }.map { (_, v) -> v.maxBy { it.time }.value }.takeLast(7),
      bodyFat = fats.lastOrNull(),
      lastSyncAt = dao.syncState(mode)?.lastSyncAt?.let(Instant::ofEpochMilli),
    )
  }
}

/** One cached day of a metric. */
private data class DayRow(val value: Double?, val min: Double?, val max: Double?)
