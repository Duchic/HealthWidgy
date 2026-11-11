package cz.healthwidgy.data.sync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.db.DailyMetricEntity
import cz.healthwidgy.data.db.ExerciseSessionEntity
import cz.healthwidgy.data.db.HealthDao
import cz.healthwidgy.data.db.HourlyMetricEntity
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.db.SyncStateEntity
import cz.healthwidgy.data.db.TransactionRunner
import cz.healthwidgy.data.hc.FailureKind
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.data.hc.HcResult
import cz.healthwidgy.data.hc.HcType
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.data.settings.SourceMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Metric ids stored in daily_metric / hourly_metric. */
object Metrics {
  const val STEPS = "steps"
  const val DISTANCE = "distance"
  const val FLOORS = "floors"
  const val TOTAL_KCAL = "total_kcal"
  const val ACTIVE_KCAL = "active_kcal"
  const val HEART_RATE = "heart_rate"
  const val RESTING_HR = "resting_hr"
  /** Minutes asleep in the main sleep that ended on that day. */
  const val SLEEP = "sleep"
}

data class SyncStatus(
  val running: Boolean = false,
  val backfilling: Boolean = false,
  val lastSyncAt: Instant? = null,
  val failures: Map<String, FailureKind> = emptyMap(),
)

/**
 * Copies Health Connect into the local database. The first run (per source mode) backfills up to a year
 * (30 days without the history permission); later runs recompute the last [RECENT_DAYS] days, which covers
 * late Garmin syncs and edits. Days are local calendar days: Garmin writes daily distance and total calories
 * as one record per day, and Health Connect prorates records that only partly overlap a range.
 */
class SyncEngine(
  private val source: HealthConnectSource,
  private val dao: HealthDao,
  private val tx: TransactionRunner,
  private val settingsRepository: SettingsRepository,
) {
  private val mutex = Mutex()
  private val _status = MutableStateFlow(SyncStatus())
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  /** Syncs unless one ran less than [MIN_INTERVAL] ago (a backfill or [force] always runs). */
  suspend fun sync(force: Boolean = false) {
    mutex.withLock {
      val settings = settingsRepository.settings.first()
      val access = source.access()
      if (!access.isAvailable || !access.hasAnyDataPermission) return
      val key = settings.sourceMode.name
      val state = dao.syncState(key)
      val needBackfill = state == null || (access.hasHistory && !state.backfilledWithHistory)
      val last = state?.lastSyncAt?.let(Instant::ofEpochMilli)
      if (!force && !needBackfill && last != null && Duration.between(last, Instant.now()) < MIN_INTERVAL) return

      _status.update { it.copy(running = true, backfilling = needBackfill) }
      val failures = mutableMapOf<String, FailureKind>()
      try {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val from = if (needBackfill) today.minusDays(if (access.hasHistory) BACKFILL_DAYS_HISTORY else BACKFILL_DAYS) else today.minusDays(RECENT_DAYS)
        val run = Run(access, settings.sourceMode, from, today, now, zone, failures)
        run.execute(state)
        _status.update { SyncStatus(running = false, lastSyncAt = now, failures = failures) }
      } catch (e: Exception) {
        if (e is kotlin.coroutines.cancellation.CancellationException) {
          _status.update { it.copy(running = false, backfilling = false) }
          throw e
        }
        _status.update { it.copy(running = false, backfilling = false, failures = failures + ("sync" to FailureKind.Unknown)) }
      }
    }
  }

  /** Computes distance, calories and heart rate of a workout that was cached without them. */
  suspend fun ensureExerciseStats(id: String) {
    val mode = settingsRepository.settings.first().sourceMode
    val entity = dao.exerciseById(mode.name, id) ?: return
    if (entity.statsComputed) return
    val access = source.access()
    val run = Run(access, mode, LocalDate.now(), LocalDate.now(), Instant.now(), ZoneId.systemDefault(), mutableMapOf())
    dao.upsertExercises(listOf(run.withStats(entity)))
  }

  /** One sync pass over [from]..[today] (local dates, inclusive). */
  private inner class Run(
    private val access: HcAccess,
    private val mode: SourceMode,
    private val from: LocalDate,
    private val today: LocalDate,
    private val now: Instant,
    private val zone: ZoneId,
    private val failures: MutableMap<String, FailureKind>,
  ) {
    private val key = mode.name
    private val origins = if (mode == SourceMode.GARMIN_ONLY) setOf(DataOrigin(GARMIN_CONNECT_PACKAGE)) else emptySet()
    private val fromInstant = from.atStartOfDay(zone).toInstant()
    private val dayRange = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay())
    private val stamp = now.toEpochMilli()

    private fun allowed(type: HcType) = access.isGranted(type)

    private suspend fun <T> call(type: HcType, block: suspend HealthConnectClient.() -> T): T? =
      when (val r = source.call(block)) {
        is HcResult.Ok -> r.value
        is HcResult.Failed -> {
          failures[type.id] = r.kind
          null
        }
      }

    private suspend fun daily(type: HcType, metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>): List<AggregationResultGroupedByPeriod>? =
      if (!allowed(type)) null
      else call(type) { aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics, dayRange, Period.ofDays(1), origins)) }

    private suspend fun <T : Record> readAll(type: HcType, recordType: KClass<T>, range: TimeRangeFilter, filter: Set<DataOrigin> = origins): List<T>? {
      if (!allowed(type)) return null
      return call(type) {
        val out = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
          val response = readRecords(ReadRecordsRequest(recordType, range, filter, ascendingOrder = true, pageSize = 5000, pageToken = token))
          out += response.records
          token = response.pageToken
          pages++
        } while (!token.isNullOrEmpty() && pages < 50)
        out
      }
    }

    suspend fun execute(previous: SyncStateEntity?) {
      val fromDate = from.toString()
      val toDate = today.toString()
      fun rows(groups: List<AggregationResultGroupedByPeriod>, metric: String, map: (AggregationResultGroupedByPeriod) -> DailyMetricEntity?) =
        metric to groups.mapNotNull(map)

      // 1. Daily totals per local day.
      val dailyBatches = mutableListOf<Pair<String, List<DailyMetricEntity>>>()
      fun day(g: AggregationResultGroupedByPeriod) = g.startTime.toLocalDate().toString()
      daily(HcTypes.Steps, setOf(StepsRecord.COUNT_TOTAL))?.let { gs ->
        dailyBatches += rows(gs, Metrics.STEPS) { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { DailyMetricEntity(day(g), Metrics.STEPS, key, it.toDouble(), updatedAt = stamp) } }
      }
      daily(HcTypes.Distance, setOf(DistanceRecord.DISTANCE_TOTAL))?.let { gs ->
        dailyBatches += rows(gs, Metrics.DISTANCE) { g -> g.result[DistanceRecord.DISTANCE_TOTAL]?.let { DailyMetricEntity(day(g), Metrics.DISTANCE, key, it.inMeters, updatedAt = stamp) } }
      }
      daily(HcTypes.Floors, setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL))?.let { gs ->
        dailyBatches += rows(gs, Metrics.FLOORS) { g -> g.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { DailyMetricEntity(day(g), Metrics.FLOORS, key, it, updatedAt = stamp) } }
      }
      daily(HcTypes.ActiveCalories, setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL))?.let { gs ->
        dailyBatches += rows(gs, Metrics.ACTIVE_KCAL) { g -> g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { DailyMetricEntity(day(g), Metrics.ACTIVE_KCAL, key, it.inKilocalories, updatedAt = stamp) } }
      }
      daily(HcTypes.TotalCalories, setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL))?.let { gs ->
        // Days without any real record would contain only Health Connect's derived BMR estimate: skip them.
        dailyBatches += rows(gs, Metrics.TOTAL_KCAL) { g ->
          if (g.result.dataOrigins.isEmpty()) null
          else g.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { DailyMetricEntity(day(g), Metrics.TOTAL_KCAL, key, it.inKilocalories, updatedAt = stamp) }
        }
      }
      daily(HcTypes.HeartRate, setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_AVG))?.let { gs ->
        dailyBatches += rows(gs, Metrics.HEART_RATE) { g ->
          val avg = g.result[HeartRateRecord.BPM_AVG] ?: return@rows null
          DailyMetricEntity(day(g), Metrics.HEART_RATE, key, avg.toDouble(), g.result[HeartRateRecord.BPM_MIN]?.toDouble(), g.result[HeartRateRecord.BPM_MAX]?.toDouble(), avg.toDouble(), stamp)
        }
      }
      daily(HcTypes.RestingHeartRate, setOf(RestingHeartRateRecord.BPM_AVG))?.let { gs ->
        dailyBatches += rows(gs, Metrics.RESTING_HR) { g -> g.result[RestingHeartRateRecord.BPM_AVG]?.let { DailyMetricEntity(day(g), Metrics.RESTING_HR, key, it.toDouble(), updatedAt = stamp) } }
      }

      // 2. Hourly steps and heart rate for the last week (day detail charts).
      val hourlyFrom = maxOf(from, today.minusDays(HOURLY_DAYS)).atStartOfDay(zone).toInstant()
      val hourlyRange = TimeRangeFilter.between(hourlyFrom, now)
      val hourlyBatches = mutableListOf<Pair<String, List<HourlyMetricEntity>>>()
      if (allowed(HcTypes.Steps)) {
        call(HcTypes.Steps) { aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), hourlyRange, Duration.ofHours(1), origins)) }?.let { gs ->
          hourlyBatches += Metrics.STEPS to gs.mapNotNull { g ->
            g.result[StepsRecord.COUNT_TOTAL]?.let { HourlyMetricEntity(g.startTime.toEpochMilli(), g.startTime.atZone(zone).toLocalDate().toString(), Metrics.STEPS, key, it.toDouble()) }
          }
        }
      }
      if (allowed(HcTypes.HeartRate)) {
        call(HcTypes.HeartRate) {
          aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_AVG), hourlyRange, Duration.ofHours(1), origins))
        }?.let { gs ->
          hourlyBatches += Metrics.HEART_RATE to gs.mapNotNull { g ->
            val avg = g.result[HeartRateRecord.BPM_AVG] ?: return@mapNotNull null
            HourlyMetricEntity(
              g.startTime.toEpochMilli(), g.startTime.atZone(zone).toLocalDate().toString(), Metrics.HEART_RATE, key,
              avg.toDouble(), g.result[HeartRateRecord.BPM_MIN]?.toDouble(), g.result[HeartRateRecord.BPM_MAX]?.toDouble(), avg.toDouble(),
            )
          }
        }
      }

      // 3. Sleep sessions (a night belongs to the day it ended) and minutes asleep per day.
      val sleeps = readAll(HcTypes.Sleep, SleepSessionRecord::class, TimeRangeFilter.between(fromInstant.minus(Duration.ofDays(1)), now))
      val sleepEntities = sleeps?.filter { !it.endTime.isBefore(fromInstant) }?.map { sleepEntity(it) }
      val sleepDaily =
        sleepEntities
          ?.groupBy { it.wakeDate }
          ?.mapNotNull { (date, sessions) ->
            val main = sessions.filter { it.end - it.start >= MAIN_SLEEP.toMillis() }.maxByOrNull { it.asleepMinutes } ?: sessions.maxBy { it.asleepMinutes }
            DailyMetricEntity(date, Metrics.SLEEP, key, main.asleepMinutes.toDouble(), updatedAt = stamp)
          }

      // 4. Workouts; stats are computed now for the last 30 days, older ones when first opened.
      val exercises = readAll(HcTypes.Exercise, ExerciseSessionRecord::class, TimeRangeFilter.between(fromInstant, now))
      val cached = dao.exercisesFrom(key, fromInstant.toEpochMilli()).associateBy { it.id }
      val eagerFrom = now.minus(Duration.ofDays(EAGER_STATS_DAYS)).toEpochMilli()
      val exerciseEntities =
        exercises?.map { record ->
          val base =
            ExerciseSessionEntity(
              id = record.metadata.id, source = key, exerciseType = record.exerciseType, title = record.title,
              start = record.startTime.toEpochMilli(), end = record.endTime.toEpochMilli(),
              date = record.startTime.atZone(zone).toLocalDate().toString(),
              statsComputed = false, distanceMeters = null, activeKcal = null, avgHeartRate = null, maxHeartRate = null, elevationGainMeters = null,
            )
          val old = cached[base.id]
          when {
            old != null && old.statsComputed && old.start == base.start && old.end == base.end && record.endTime.isBefore(now.minus(Duration.ofHours(6))) ->
              old.copy(exerciseType = base.exerciseType, title = base.title)
            base.start >= eagerFrom -> withStats(base)
            else -> base
          }
        }

      // 5. Body measurements from any app.
      val bodyRange = TimeRangeFilter.between(fromInstant, now)
      val weights =
        readAll(HcTypes.Weight, WeightRecord::class, bodyRange, filter = emptySet())?.map {
          BodyMeasurementEntity(it.metadata.id, BodyKinds.WEIGHT, it.time.toEpochMilli(), it.time.atZone(zone).toLocalDate().toString(), it.weight.inKilograms, it.metadata.dataOrigin.packageName)
        }
      val fats =
        readAll(HcTypes.BodyFat, BodyFatRecord::class, bodyRange, filter = emptySet())?.map {
          BodyMeasurementEntity(it.metadata.id, BodyKinds.BODY_FAT, it.time.toEpochMilli(), it.time.atZone(zone).toLocalDate().toString(), it.percentage.value, it.metadata.dataOrigin.packageName)
        }

      // Replace the window atomically; a type that failed to read keeps its old cache.
      tx.run {
        dailyBatches.forEach { (metric, list) ->
          dao.deleteDaily(key, metric, fromDate, toDate)
          dao.upsertDaily(list)
        }
        hourlyBatches.forEach { (metric, list) ->
          dao.deleteHourly(key, metric, hourlyFrom.toEpochMilli())
          dao.upsertHourly(list)
        }
        dao.pruneHourly(today.minusDays(HOURLY_DAYS + 1).atStartOfDay(zone).toInstant().toEpochMilli())
        if (sleepEntities != null && sleepDaily != null) {
          dao.deleteSleepFrom(key, fromInstant.toEpochMilli())
          dao.upsertSleep(sleepEntities)
          dao.deleteDaily(key, Metrics.SLEEP, fromDate, toDate)
          dao.upsertDaily(sleepDaily)
        }
        if (exerciseEntities != null) {
          dao.deleteExercisesFrom(key, fromInstant.toEpochMilli())
          dao.upsertExercises(exerciseEntities)
        }
        weights?.let {
          dao.deleteBodyFrom(BodyKinds.WEIGHT, fromInstant.toEpochMilli())
          dao.upsertBody(it)
        }
        fats?.let {
          dao.deleteBodyFrom(BodyKinds.BODY_FAT, fromInstant.toEpochMilli())
          dao.upsertBody(it)
        }
        dao.upsertSyncState(
          SyncStateEntity(
            source = key,
            coveredFrom = if (previous?.coveredFrom != null && previous.coveredFrom < fromDate) previous.coveredFrom else fromDate,
            lastSyncAt = stamp,
            backfilledWithHistory = (previous?.backfilledWithHistory == true) || (from <= today.minusDays(BACKFILL_DAYS_HISTORY) && access.hasHistory),
          )
        )
      }
    }

    private fun sleepEntity(record: SleepSessionRecord): SleepSessionEntity {
      val minutes = record.stages.groupBy { it.stage }.mapValues { (_, s) -> s.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
      fun m(stage: Int) = minutes[stage] ?: 0L
      val hasStages = record.stages.isNotEmpty()
      val deep = m(SleepSessionRecord.STAGE_TYPE_DEEP)
      val light = m(SleepSessionRecord.STAGE_TYPE_LIGHT)
      val rem = m(SleepSessionRecord.STAGE_TYPE_REM)
      val unspecified = m(SleepSessionRecord.STAGE_TYPE_SLEEPING)
      val awake = m(SleepSessionRecord.STAGE_TYPE_AWAKE) + m(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED) + m(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
      return SleepSessionEntity(
        id = record.metadata.id,
        source = key,
        start = record.startTime.toEpochMilli(),
        end = record.endTime.toEpochMilli(),
        wakeDate = record.endTime.atZone(zone).toLocalDate().toString(),
        asleepMinutes = if (hasStages) deep + light + rem + unspecified else Duration.between(record.startTime, record.endTime).toMinutes(),
        deepMinutes = deep,
        lightMinutes = light,
        remMinutes = rem,
        awakeMinutes = awake,
        unspecifiedMinutes = unspecified,
        stages = record.stages.sortedBy { it.startTime }.joinToString(";") { "${it.stage},${it.startTime.toEpochMilli()},${it.endTime.toEpochMilli()}" },
      )
    }

    suspend fun withStats(e: ExerciseSessionEntity): ExerciseSessionEntity {
      val window = TimeRangeFilter.between(Instant.ofEpochMilli(e.start), Instant.ofEpochMilli(e.end))
      suspend fun <T : Any> agg(type: HcType, metric: androidx.health.connect.client.aggregate.AggregateMetric<T>): T? =
        if (!allowed(type)) null else call(type) { aggregate(AggregateRequest(setOf(metric), window, origins))[metric] }
      // Heart rate records from Garmin are (near) single samples, so window aggregates are exact enough here.
      return e.copy(
        statsComputed = true,
        distanceMeters = if (e.exerciseType in DISTANCE_EXERCISES) agg(HcTypes.Distance, DistanceRecord.DISTANCE_TOTAL)?.inMeters else null,
        activeKcal = agg(HcTypes.ActiveCalories, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
        avgHeartRate = agg(HcTypes.HeartRate, HeartRateRecord.BPM_AVG),
        maxHeartRate = agg(HcTypes.HeartRate, HeartRateRecord.BPM_MAX),
        elevationGainMeters = agg(HcTypes.Elevation, ElevationGainedRecord.ELEVATION_GAINED_TOTAL)?.inMeters,
      )
    }
  }

  companion object {
    val MIN_INTERVAL: Duration = Duration.ofMinutes(2)
    const val RECENT_DAYS = 3L
    const val BACKFILL_DAYS = 29L
    const val BACKFILL_DAYS_HISTORY = 365L
    const val HOURLY_DAYS = 6L
    const val EAGER_STATS_DAYS = 30L
    val MAIN_SLEEP: Duration = Duration.ofHours(3)
  }
}

object BodyKinds {
  const val WEIGHT = "weight"
  const val BODY_FAT = "body_fat"
}

/** Exercise types where a distance is meaningful (distance during e.g. strength training is just walking around). */
val DISTANCE_EXERCISES: Set<Int> =
  setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR,
  )
