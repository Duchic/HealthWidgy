package cz.healthwidgy.data.overview

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
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
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.data.hc.FailureKind
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcResult
import cz.healthwidgy.data.hc.HcType
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.settings.SourceMode
import cz.healthwidgy.data.sync.DISTANCE_EXERCISES
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.reflect.KClass

data class DayValue(val date: LocalDate, val value: Double?)

data class HeartRateStats(
  val latestBpm: Long?,
  val latestTime: Instant?,
  val minBpm: Long?,
  val maxBpm: Long?,
  val avgBpm: Long?,
)

data class RestingHeartRate(
  val bpm: Long,
  val time: Instant,
  /** True when Garmin wrote no resting heart rate and the value is the lowest heart rate during sleep. */
  val isEstimate: Boolean,
  /** One value per day for the last 7 days (oldest first); null where no value exists. */
  val last7Days: List<DayValue>,
)

data class SleepStageTotals(
  val deepMinutes: Long,
  val lightMinutes: Long,
  val remMinutes: Long,
  val awakeMinutes: Long,
  /** Stage "sleeping" without a phase. */
  val unspecifiedMinutes: Long,
)

data class SleepSummary(
  val start: Instant,
  val end: Instant,
  val asleepMinutes: Long,
  /** Null when the session carries no stages; the UI then shows duration only. */
  val stages: SleepStageTotals?,
)

data class WorkoutSummary(
  /** Health Connect record id (opens the workout detail). */
  val id: String,
  val exerciseType: Int,
  val title: String?,
  val start: Instant,
  val end: Instant,
  /** Only for activities that move you somewhere (running, walking, cycling, swimming…). */
  val distanceMeters: Double?,
  val activeKcal: Double?,
  val avgHeartRate: Long?,
  val maxHeartRate: Long?,
)

data class WeightSummary(
  val kilograms: Double,
  val time: Instant,
  /** Change against a measurement 6–10 days older; null without such a measurement. */
  val deltaKilograms: Double?,
  /** Up to 7 latest measurements, oldest first, for the sparkline. */
  val recent: List<Double>,
  /** Body fat measured on the same day as the weight, if any. */
  val bodyFatPercent: Double?,
)

data class Overview(
  val date: LocalDate,
  val stepGoal: Int,
  val sourceMode: SourceMode,
  val steps: Long?,
  val distanceMeters: Double?,
  val floors: Double?,
  val activeKcal: Double?,
  val totalKcal: Double?,
  val heart: HeartRateStats?,
  val restingHeartRate: RestingHeartRate?,
  val sleep: SleepSummary?,
  val workout: WorkoutSummary?,
  val weight: WeightSummary?,
  val steps7Days: List<DayValue>,
  /** Newest Health Connect write by the data source, i.e. roughly the last Garmin sync. */
  val latestSourceWrite: Instant?,
  val refreshedAt: Instant,
  /** Types whose read failed (rate limit, busy, lost permission…), keyed by [HcType.id]. */
  val failures: Map<String, FailureKind>,
  /** Types the user has not granted; they are skipped, not treated as errors. */
  val notGranted: Set<String>,
) {
  val hasAnyData: Boolean
    get() =
      listOf(steps, distanceMeters, floors, activeKcal, totalKcal, heart, restingHeartRate, sleep, workout, weight)
        .any { it != null } || steps7Days.any { it.value != null }

  /** Steps are unknown (not just zero) when they could not be read. */
  val stepsUnknown: Boolean
    get() = steps == null && (HcTypes.Steps.id in failures || HcTypes.Steps.id in notGranted)
}

/** Reads everything the overview screen shows, straight from Health Connect (foreground only). */
class OverviewRepository(private val source: HealthConnectSource) {

  suspend fun load(
    settings: Settings,
    granted: Set<String>,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
  ): Overview {
    val failures = mutableMapOf<String, FailureKind>()
    val notGranted = HcTypes.all.filter { it.readPermission !in granted }.map { it.id }.toSet()
    val origins = if (settings.sourceMode == SourceMode.GARMIN_ONLY) setOf(DataOrigin(GARMIN_CONNECT_PACKAGE)) else emptySet()
    val today = now.atZone(zone).toLocalDate()
    val startOfDay = today.atStartOfDay(zone).toInstant()
    val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant()
    // For reading records: everything up to now.
    val todayRange = TimeRangeFilter.between(startOfDay, now)
    // For totals: the whole calendar day. Garmin writes today's distance and total calories as ONE record
    // spanning 00:00–23:59:59 (updated on every sync); Health Connect prorates records that only partly
    // overlap the range, so a range ending "now" would report only a fraction (verified on the device).
    val dayRange = TimeRangeFilter.between(startOfDay, endOfDay)
    // Body measurements are single readings, so mixing sources cannot double count: take them from any app
    // (e.g. a smart scale app writes body fat, Garmin does not).
    val bodyOrigins = emptySet<DataOrigin>()

    fun allowed(type: HcType) = type.id !in notGranted

    suspend fun <T : Any> aggregate(type: HcType, metric: AggregateMetric<T>, range: TimeRangeFilter = dayRange): T? {
      if (!allowed(type)) return null
      return source
        .call { aggregate(AggregateRequest(metrics = setOf(metric), timeRangeFilter = range, dataOriginFilter = origins)) }
        .also { if (it is HcResult.Failed) failures[type.id] = it.kind }
        .let { (it as? HcResult.Ok)?.value?.get(metric) }
    }

    suspend fun <T : Record> read(
      type: HcType,
      recordType: KClass<T>,
      range: TimeRangeFilter,
      ascending: Boolean,
      pageSize: Int,
      originFilter: Set<DataOrigin> = origins,
    ): List<T> {
      if (!allowed(type)) return emptyList()
      return source
        .call {
          readRecords(
            ReadRecordsRequest(
              recordType = recordType,
              timeRangeFilter = range,
              dataOriginFilter = originFilter,
              ascendingOrder = ascending,
              pageSize = pageSize,
            )
          ).records
        }
        .also { if (it is HcResult.Failed) failures[type.id] = it.kind }
        .let { (it as? HcResult.Ok)?.value.orEmpty() }
    }

    /**
     * Heart rate samples inside [from, to). Aggregates (BPM_MIN/MAX/AVG) would include every sample of any
     * record that merely overlaps the window, so samples are read and filtered here. Records may start
     * before the window, hence the extra look-back.
     */
    suspend fun heartSamples(from: Instant, to: Instant): Pair<List<HeartRateRecord.Sample>, Instant?> {
      val records = read(HcTypes.HeartRate, HeartRateRecord::class, TimeRangeFilter.between(from.minus(Duration.ofHours(12)), to), ascending = true, pageSize = 5000)
      val samples = records.flatMap { it.samples }.filter { it.time >= from && it.time < to }
      return samples to records.maxOfOrNull { it.metadata.lastModifiedTime }
    }

    fun statsOf(samples: List<HeartRateRecord.Sample>): HeartRateStats? {
      if (samples.isEmpty()) return null
      val latest = samples.maxBy { it.time }
      return HeartRateStats(
        latestBpm = latest.beatsPerMinute,
        latestTime = latest.time,
        minBpm = samples.minOf { it.beatsPerMinute },
        maxBpm = samples.maxOf { it.beatsPerMinute },
        avgBpm = samples.map { it.beatsPerMinute }.average().roundToLong(),
      )
    }

    // Activity totals for today.
    val steps = aggregate(HcTypes.Steps, StepsRecord.COUNT_TOTAL)
    val distance = aggregate(HcTypes.Distance, DistanceRecord.DISTANCE_TOTAL)?.inMeters
    val floors = aggregate(HcTypes.Floors, FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
    val activeKcal = aggregate(HcTypes.ActiveCalories, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories

    // Total calories: Health Connect fills every gap without a record with derived BMR (from a default
    // profile if needed) plus active calories of other apps, so the aggregate is never empty. Count only up to
    // the end of the newest real record of today and only if a real record contributed.
    val todayTotals = read(HcTypes.TotalCalories, TotalCaloriesBurnedRecord::class, dayRange, ascending = false, pageSize = 50)
    val totalKcal =
      todayTotals.maxOfOrNull { it.endTime }?.let { lastEnd ->
        source
          .call {
            aggregate(
              AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, minOf(lastEnd, endOfDay)),
                dataOriginFilter = origins,
              )
            )
          }
          .also { if (it is HcResult.Failed) failures[HcTypes.TotalCalories.id] = it.kind }
          .let { (it as? HcResult.Ok)?.value }
          ?.takeIf { it.dataOrigins.isNotEmpty() }
          ?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
          ?.inKilocalories
      }

    // Steps per day for the last 7 days. LocalDateTime ranges make Health Connect slice by local days.
    val weekStart = today.minusDays(6)
    val stepsByDay: Map<LocalDate, Long> =
      if (!allowed(HcTypes.Steps)) emptyMap()
      else
        source
          .call {
            aggregateGroupByPeriod(
              AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
                dataOriginFilter = origins,
              )
            )
          }
          .also { if (it is HcResult.Failed) failures[HcTypes.Steps.id] = it.kind }
          .let { result ->
            (result as? HcResult.Ok)?.value.orEmpty().mapNotNull { group ->
              group.result[StepsRecord.COUNT_TOTAL]?.let { group.startTime.toLocalDate() to it }
            }.toMap()
          }
    val steps7Days = (0L..6L).map { offset -> weekStart.plusDays(offset).let { DayValue(it, stepsByDay[it]?.toDouble()) } }

    // Heart rate today.
    val (todaySamples, latestHeartWrite) = heartSamples(startOfDay, now)
    val heart = statsOf(todaySamples)

    // Last night's sleep: the newest main sleep (3 h+) that ended within the last 36 hours; a nap only if
    // there is nothing longer.
    val sleepCandidates =
      read(HcTypes.Sleep, SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(36)), now), ascending = false, pageSize = 20)
        .filter { it.endTime <= now }
    val sleepRecord =
      sleepCandidates.filter { Duration.between(it.startTime, it.endTime) >= MAIN_SLEEP_MIN }.maxByOrNull { it.endTime }
        ?: sleepCandidates.maxByOrNull { it.endTime }
    val sleep = sleepRecord?.let(::summarizeSleep)

    // Resting heart rate: Garmin's value if present, otherwise the lowest heart rate during the last sleep.
    val restingRecords =
      read(HcTypes.RestingHeartRate, RestingHeartRateRecord::class, TimeRangeFilter.between(weekStart.atStartOfDay(zone).toInstant(), now), ascending = true, pageSize = 200)
    val restingByDay = restingRecords.groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { (_, v) -> v.maxBy { it.time }.beatsPerMinute }
    val restingLast7 = (0L..6L).map { offset -> weekStart.plusDays(offset).let { DayValue(it, restingByDay[it]?.toDouble()) } }
    val restingHeartRate =
      restingRecords.maxByOrNull { it.time }?.let { RestingHeartRate(it.beatsPerMinute, it.time, isEstimate = false, last7Days = restingLast7) }
        ?: sleepRecord?.let { s ->
          heartSamples(s.startTime, s.endTime).first.minOfOrNull { it.beatsPerMinute }?.let {
            RestingHeartRate(it, s.endTime, isEstimate = true, last7Days = restingLast7)
          }
        }

    // Latest workout within 30 days, with distance, calories and heart rate from its own time window.
    val workoutRecord =
      read(HcTypes.Exercise, ExerciseSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now), ascending = false, pageSize = 1)
        .firstOrNull()
    val workout =
      workoutRecord?.let { w ->
        val window = TimeRangeFilter.between(w.startTime, w.endTime)
        val hr = statsOf(heartSamples(w.startTime, w.endTime).first)
        WorkoutSummary(
          id = w.metadata.id,
          exerciseType = w.exerciseType,
          title = w.title,
          start = w.startTime,
          end = w.endTime,
          distanceMeters = if (w.exerciseType in DISTANCE_EXERCISES) aggregate(HcTypes.Distance, DistanceRecord.DISTANCE_TOTAL, window)?.inMeters else null,
          activeKcal = aggregate(HcTypes.ActiveCalories, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, window)?.inKilocalories,
          avgHeartRate = hr?.avgBpm,
          maxHeartRate = hr?.maxBpm,
        )
      }

    // Body: newest weight within 90 days (any app), compared with a measurement about a week older.
    val bodyRange = TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now)
    val weights = read(HcTypes.Weight, WeightRecord::class, bodyRange, ascending = false, pageSize = 200, originFilter = bodyOrigins)
    val bodyFat = read(HcTypes.BodyFat, BodyFatRecord::class, bodyRange, ascending = false, pageSize = 1, originFilter = bodyOrigins).firstOrNull()
    val weight =
      weights.firstOrNull()?.let { latest ->
        val reference =
          weights.firstOrNull { it.time <= latest.time.minus(Duration.ofDays(6)) }?.takeIf { it.time >= latest.time.minus(Duration.ofDays(10)) }
        val latestDay = latest.time.atZone(zone).toLocalDate()
        // One value per day: a scale app and Garmin often store the same weighing twice.
        val perDay = weights.distinctBy { it.time.atZone(zone).toLocalDate() }
        WeightSummary(
          kilograms = latest.weight.inKilograms,
          time = latest.time,
          deltaKilograms = reference?.let { latest.weight.inKilograms - it.weight.inKilograms },
          recent = perDay.take(7).map { it.weight.inKilograms }.reversed(),
          bodyFatPercent = bodyFat?.takeIf { it.time.atZone(zone).toLocalDate() == latestDay }?.percentage?.value,
        )
      }

    // When did the source last write? Newest modification among today's steps, heart rate and last sleep.
    val latestStepsRecord = read(HcTypes.Steps, StepsRecord::class, todayRange, ascending = false, pageSize = 1).firstOrNull()
    val latestSourceWrite =
      listOfNotNull(latestStepsRecord?.metadata?.lastModifiedTime, latestHeartWrite, sleepRecord?.metadata?.lastModifiedTime).maxOrNull()

    return Overview(
      date = today,
      stepGoal = settings.stepGoal,
      sourceMode = settings.sourceMode,
      steps = steps,
      distanceMeters = distance,
      floors = floors,
      activeKcal = activeKcal,
      totalKcal = totalKcal,
      heart = heart,
      restingHeartRate = restingHeartRate,
      sleep = sleep,
      workout = workout,
      weight = weight,
      steps7Days = steps7Days,
      latestSourceWrite = latestSourceWrite,
      refreshedAt = now,
      failures = failures,
      notGranted = notGranted,
    )
  }

  private fun summarizeSleep(record: SleepSessionRecord): SleepSummary {
    if (record.stages.isEmpty()) {
      return SleepSummary(record.startTime, record.endTime, Duration.between(record.startTime, record.endTime).toMinutes(), null)
    }
    val minutesByStage =
      record.stages.groupBy { it.stage }.mapValues { (_, stages) -> stages.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
    fun m(stage: Int) = minutesByStage[stage] ?: 0L
    val totals =
      SleepStageTotals(
        deepMinutes = m(SleepSessionRecord.STAGE_TYPE_DEEP),
        lightMinutes = m(SleepSessionRecord.STAGE_TYPE_LIGHT),
        remMinutes = m(SleepSessionRecord.STAGE_TYPE_REM),
        awakeMinutes = m(SleepSessionRecord.STAGE_TYPE_AWAKE) + m(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED) + m(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
        unspecifiedMinutes = m(SleepSessionRecord.STAGE_TYPE_SLEEPING),
      )
    val asleep = totals.deepMinutes + totals.lightMinutes + totals.remMinutes + totals.unspecifiedMinutes
    return SleepSummary(record.startTime, record.endTime, asleep, totals)
  }

  private companion object {
    val MAIN_SLEEP_MIN: Duration = Duration.ofHours(3)
  }
}
