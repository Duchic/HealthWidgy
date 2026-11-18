package cz.healthwidgy.demo

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.data.hc.ExtraPermissions
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.hc.endTimeOrNull
import cz.healthwidgy.data.hc.startTimeOrNull
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A read-only Health Connect that answers from [DemoData]: every permission granted, records filtered and
 * paged like the real service, and the aggregates the app uses computed from the records (interval records
 * prorated by overlap, heart rate from the samples inside the range). Demo build only.
 */
internal class DemoHealthConnectClient(private val data: DemoData, private val clock: () -> Instant = Instant::now) : HealthConnectClient {

  override val permissionController: PermissionController =
    object : PermissionController {
      override suspend fun getGrantedPermissions(): Set<String> = HcTypes.dataPermissions + ExtraPermissions.Background + ExtraPermissions.History

      override suspend fun revokeAllPermissions() = Unit
    }

  override val features: HealthConnectFeatures =
    object : HealthConnectFeatures {
      override fun getFeatureStatus(feature: Int): Int = HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

  override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> = logged("readRecords") {
    val (from, to) = bounds(request.timeRangeFilter)
    val found = if (originAllowed(request.dataOriginFilter)) data.records(request.recordType, from, to, clock()) else emptyList()
    val sorted = if (request.ascendingOrder) found.sortedBy { it.startTimeOrNull() } else found.sortedByDescending { it.startTimeOrNull() }
    val offset = request.pageToken?.toIntOrNull() ?: 0
    val page = sorted.drop(offset).take(request.pageSize)
    val next = (offset + page.size).takeIf { it < sorted.size }?.toString()
    @Suppress("UNCHECKED_CAST")
    HcInternals.readResponse(page as List<T>, next)
  }

  override suspend fun aggregate(request: AggregateRequest): AggregationResult = logged("aggregate") {
    val (from, to) = bounds(HcInternals.range(request))
    compute(HcInternals.metrics(request), from, to, HcInternals.origins(request))
  }

  override suspend fun aggregateGroupByDuration(request: AggregateGroupByDurationRequest): List<AggregationResultGroupedByDuration> = logged("aggregateGroupByDuration") {
    val (from, to) = bounds(HcInternals.range(request))
    val step = HcInternals.slicer(request)
    val out = mutableListOf<AggregationResultGroupedByDuration>()
    var start = from
    while (start < to) {
      val end = minOf(start.plus(step), to)
      out += HcInternals.byDuration(compute(HcInternals.metrics(request), start, end, HcInternals.origins(request)), start, end, data.zone.rules.getOffset(start))
      start = end
    }
    out
  }

  override suspend fun aggregateGroupByPeriod(request: AggregateGroupByPeriodRequest): List<AggregationResultGroupedByPeriod> = logged("aggregateGroupByPeriod") {
    val filter = HcInternals.range(request)
    val zone = data.zone
    val from = filter.localStartTime ?: filter.startTime?.atZone(zone)?.toLocalDateTime() ?: error("Period slices need a start")
    val to = filter.localEndTime ?: filter.endTime?.atZone(zone)?.toLocalDateTime() ?: clock().atZone(zone).toLocalDateTime()
    val out = mutableListOf<AggregationResultGroupedByPeriod>()
    var start = from
    while (start < to) {
      val end = minOf(start.plus(HcInternals.slicer(request)), to)
      val result = compute(HcInternals.metrics(request), start.atZone(zone).toInstant(), end.atZone(zone).toInstant(), HcInternals.origins(request))
      out += HcInternals.byPeriod(result, start, end)
      start = end
    }
    out
  }

  /**
   * Runs the generation off the main thread, like the real client's binder calls, and logs failures: the app
   * maps every exception to a failure kind and shows only "could not be loaded".
   */
  private suspend fun <T> logged(call: String, block: () -> T): T =
    withContext(Dispatchers.Default) {
      try {
        block()
      } catch (e: Exception) {
        Log.w("DemoHealthConnect", "$call failed", e)
        throw e
      }
    }

  /** An empty origin filter means every app; the demo data all comes from Garmin Connect. */
  private fun originAllowed(origins: Set<DataOrigin>) = origins.isEmpty() || DemoData.ORIGIN in origins

  private fun bounds(filter: TimeRangeFilter): Pair<Instant, Instant> {
    val zone = data.zone
    val start = filter.startTime ?: filter.localStartTime?.atZone(zone)?.toInstant() ?: Instant.EPOCH
    val end = filter.endTime ?: filter.localEndTime?.atZone(zone)?.toInstant() ?: clock().plus(Duration.ofDays(1))
    return start to end
  }

  private fun compute(metrics: Set<AggregateMetric<*>>, from: Instant, to: Instant, origins: Set<DataOrigin>): AggregationResult {
    val longs = HashMap<String, Long>()
    val doubles = HashMap<String, Double>()
    if (originAllowed(origins)) {
      for (metric in metrics) {
        val value = valueOf(metric, from, to) ?: continue
        // The result picks the map that matches the metric's type; filling both keeps this independent of it.
        longs[metric.metricKey] = value.roundToLong()
        doubles[metric.metricKey] = value
      }
    }
    return HcInternals.result(longs, doubles, if (longs.isEmpty()) emptySet() else setOf(DemoData.ORIGIN))
  }

  private fun valueOf(metric: AggregateMetric<*>, from: Instant, to: Instant): Double? {
    val now = clock()
    fun <R : Record> total(type: KClass<R>, value: (R) -> Double): Double? {
      val records = data.records(type, from, to, now)
      if (records.isEmpty()) return null
      return records.sumOf { record ->
        val start = record.startTimeOrNull()!!
        val end = record.endTimeOrNull()!!
        val length = Duration.between(start, end).toMillis()
        val overlap = Duration.between(maxOf(start, from), minOf(end, to)).toMillis()
        @Suppress("UNCHECKED_CAST")
        value(record as R) * if (length > 0) overlap.toDouble() / length else 1.0
      }
    }
    fun heart(pick: (List<Long>) -> Double): Double? =
      data.records(HeartRateRecord::class, from, to, now)
        .flatMap { (it as HeartRateRecord).samples }
        .filter { it.time >= from && it.time < to }
        .map { it.beatsPerMinute }
        .takeIf { it.isNotEmpty() }
        ?.let(pick)
    fun resting(pick: (List<Long>) -> Double): Double? =
      data.records(RestingHeartRateRecord::class, from, to, now).map { (it as RestingHeartRateRecord).beatsPerMinute }.takeIf { it.isNotEmpty() }?.let(pick)

    return when (metric) {
      StepsRecord.COUNT_TOTAL -> total(StepsRecord::class) { it.count.toDouble() }
      DistanceRecord.DISTANCE_TOTAL -> total(DistanceRecord::class) { it.distance.inMeters }
      FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL -> total(FloorsClimbedRecord::class) { it.floors }
      ElevationGainedRecord.ELEVATION_GAINED_TOTAL -> total(ElevationGainedRecord::class) { it.elevation.inMeters }
      ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL -> total(ActiveCaloriesBurnedRecord::class) { it.energy.inKilocalories }
      TotalCaloriesBurnedRecord.ENERGY_TOTAL -> total(TotalCaloriesBurnedRecord::class) { it.energy.inKilocalories }
      HeartRateRecord.BPM_MIN -> heart { it.min().toDouble() }
      HeartRateRecord.BPM_MAX -> heart { it.max().toDouble() }
      HeartRateRecord.BPM_AVG -> heart { it.average() }
      RestingHeartRateRecord.BPM_MIN -> resting { it.min().toDouble() }
      RestingHeartRateRecord.BPM_MAX -> resting { it.max().toDouble() }
      RestingHeartRateRecord.BPM_AVG -> resting { it.average() }
      else -> null
    }
  }

  private fun readOnly(): Nothing = throw UnsupportedOperationException("The demo data is read-only")

  override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse = readOnly()

  override suspend fun updateRecords(records: List<Record>) = readOnly()

  override suspend fun deleteRecords(recordType: KClass<out Record>, recordIdsList: List<String>, clientRecordIdsList: List<String>) = readOnly()

  override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) = readOnly()

  override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> = readOnly()

  override suspend fun getChangesToken(request: ChangesTokenRequest): String = readOnly()

  override suspend fun getChanges(changesToken: String): ChangesResponse = readOnly()
}
