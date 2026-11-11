package cz.healthwidgy.data.history

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.db.DailyMetricEntity
import cz.healthwidgy.data.db.ExerciseSessionEntity
import cz.healthwidgy.data.db.HealthDao
import cz.healthwidgy.data.db.HourlyMetricEntity
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.db.SyncStateEntity
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcResult
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.data.settings.SourceMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** A stage of a night for the hypnogram. */
data class SleepStage(val type: Int, val start: Instant, val end: Instant)

fun SleepSessionEntity.stageList(): List<SleepStage> =
  if (stages.isBlank()) emptyList()
  else
    stages.split(';').mapNotNull { part ->
      val (type, start, end) = part.split(',').takeIf { it.size == 3 } ?: return@mapNotNull null
      SleepStage(type.toInt(), Instant.ofEpochMilli(start.toLong()), Instant.ofEpochMilli(end.toLong()))
    }

/** Time series of one workout, read live from Health Connect (foreground). */
data class WorkoutSeries(
  val heartRate: List<Pair<Instant, Long>>,
  /** Speed in metres per second. */
  val speed: List<Pair<Instant, Double>>,
)

/** Read side for history screens: Room flows for the current source mode, plus live workout series. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository(
  private val dao: HealthDao,
  private val settings: SettingsRepository,
  private val source: HealthConnectSource,
) {
  private val mode: Flow<SourceMode> = settings.settings.map { it.sourceMode }.distinctUntilChanged()

  fun daily(metric: String, from: LocalDate): Flow<List<DailyMetricEntity>> =
    mode.flatMapLatest { dao.dailyFlow(metric, it.name, from.toString()) }

  fun hourly(metric: String, date: LocalDate): Flow<List<HourlyMetricEntity>> =
    mode.flatMapLatest { dao.hourlyFlow(metric, it.name, date.toString()) }

  fun sleep(from: LocalDate): Flow<List<SleepSessionEntity>> = mode.flatMapLatest { dao.sleepFlow(it.name, from.toString()) }

  fun exercises(): Flow<List<ExerciseSessionEntity>> = mode.flatMapLatest { dao.exerciseFlow(it.name) }

  fun exercise(id: String): Flow<ExerciseSessionEntity?> = mode.flatMapLatest { dao.exerciseByIdFlow(it.name, id) }

  fun body(kind: String, from: Instant): Flow<List<BodyMeasurementEntity>> = dao.bodyFlow(kind, from.toEpochMilli())

  fun syncState(): Flow<SyncStateEntity?> = mode.flatMapLatest { dao.syncStateFlow(it.name) }

  /** Heart rate and speed samples inside a workout; empty lists when not readable. */
  suspend fun workoutSeries(start: Instant, end: Instant): WorkoutSeries {
    val origins = if (settings.settings.first().sourceMode == SourceMode.GARMIN_ONLY) setOf(DataOrigin(GARMIN_CONNECT_PACKAGE)) else emptySet()
    val hr =
      source.call {
        readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(start.minus(Duration.ofMinutes(1)), end), origins, pageSize = 5000)).records
      }
    val speed =
      source.call { readRecords(ReadRecordsRequest(SpeedRecord::class, TimeRangeFilter.between(start, end), origins, pageSize = 5000)).records }
    return WorkoutSeries(
      heartRate =
        (hr as? HcResult.Ok)?.value.orEmpty().flatMap { r -> r.samples }.filter { it.time >= start && it.time <= end }.sortedBy { it.time }.map { it.time to it.beatsPerMinute },
      speed =
        (speed as? HcResult.Ok)?.value.orEmpty().flatMap { r -> r.samples }.filter { it.time >= start && it.time <= end }.sortedBy { it.time }.map { it.time to it.speed.inMetersPerSecond },
    )
  }
}
