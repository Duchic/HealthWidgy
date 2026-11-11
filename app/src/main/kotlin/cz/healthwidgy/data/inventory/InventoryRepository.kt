package cz.healthwidgy.data.inventory

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.BuildConfig
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.data.hc.HcResult
import cz.healthwidgy.data.hc.HcType
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.hc.endTimeOrNull
import cz.healthwidgy.data.hc.isInterval
import cz.healthwidgy.data.hc.sampleTimesOrNull
import cz.healthwidgy.data.hc.startTimeOrNull
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Stats(val min: Double, val median: Double, val max: Double, val count: Int)

/**
 * What Health Connect holds for one record type: volume, sources, time span and granularity. No individual
 * measurements; only summaries (record counts and lengths, sleep stage minutes, exercise types, devices).
 */
@Serializable
data class TypeInventory(
  val id: String,
  val label: String,
  val recordClass: String,
  val permissionGranted: Boolean,
  val error: String? = null,
  val recordsInWindow: Int = 0,
  val origins: Map<String, Int> = emptyMap(),
  val devices: List<String> = emptyList(),
  val recordingMethods: Map<String, Int> = emptyMap(),
  val earliestAccessibleRecord: String? = null,
  val newestRecord: String? = null,
  val newestLastModified: String? = null,
  val recordsPerDay: Double? = null,
  /** Interval records: length of one record in seconds. */
  val recordDurationSeconds: Stats? = null,
  /** Series records (heart rate, speed, cadence): samples stored in one record. */
  val samplesPerRecord: Stats? = null,
  /** Series records: gap between consecutive samples in seconds. */
  val sampleSpacingSeconds: Stats? = null,
  val sleepSessionsWithStages: Int? = null,
  val sleepStageMinutes: Map<String, Long>? = null,
  val exerciseTypes: Map<String, Int>? = null,
)

@Serializable
data class InventoryReport(
  val generatedAt: String,
  val timeZone: String,
  val device: String,
  val androidRelease: String,
  val sdkInt: Int,
  val appVersion: String,
  val windowDays: Int,
  val backgroundPermission: Boolean,
  val historyPermission: Boolean,
  val stepsInWindowGarminOnly: Long?,
  val stepsInWindowAllSources: Long?,
  val types: List<TypeInventory>,
)

/**
 * Builds a data inventory of Health Connect: which types Garmin really writes, since when, how often and
 * at what granularity. Reads all sources so that other writers (e.g. the phone's step counter) show up too.
 */
class InventoryRepository(private val context: Context, private val source: HealthConnectSource) {

  val exportFile: File
    get() = File(context.filesDir, "exports/inventory.json")

  private val json = Json {
    prettyPrint = true
    encodeDefaults = true
  }

  suspend fun build(
    access: HcAccess,
    windowDays: Int = WINDOW_DAYS,
    onProgress: (TypeInventory) -> Unit = {},
  ): InventoryReport {
    val now = Instant.now()
    val windowStart = now.minus(Duration.ofDays(windowDays.toLong()))
    val window = TimeRangeFilter.between(windowStart, now)

    val types =
      HcTypes.all.map { type ->
        val result =
          if (!access.isGranted(type)) TypeInventory(type.id, type.label, type.recordType.simpleName.orEmpty(), permissionGranted = false)
          else inventoryOf(type, window, windowDays)
        onProgress(result)
        result
      }

    suspend fun stepsTotal(origins: Set<DataOrigin>): Long? =
      if (!access.isGranted(HcTypes.Steps)) null
      else
        source
          .call { aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), window, origins)) }
          .let { (it as? HcResult.Ok)?.value?.get(StepsRecord.COUNT_TOTAL) }

    val report =
      InventoryReport(
        generatedAt = now.toString(),
        timeZone = ZoneId.systemDefault().id,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        windowDays = windowDays,
        backgroundPermission = access.hasBackground,
        historyPermission = access.hasHistory,
        stepsInWindowGarminOnly = stepsTotal(setOf(DataOrigin(GARMIN_CONNECT_PACKAGE))),
        stepsInWindowAllSources = stepsTotal(emptySet()),
        types = types,
      )
    exportFile.parentFile?.mkdirs()
    exportFile.writeText(json.encodeToString(InventoryReport.serializer(), report))
    return report
  }

  private suspend fun inventoryOf(type: HcType, window: TimeRangeFilter, windowDays: Int): TypeInventory {
    val base = TypeInventory(type.id, type.label, type.recordType.simpleName.orEmpty(), permissionGranted = true)
    val records =
      when (val r = source.call { readAll(type.recordType, window) }) {
        is HcResult.Ok -> r.value
        is HcResult.Failed -> return base.copy(error = "${r.kind}: ${r.error.message}")
      }
    val earliest =
      source
        .call {
          readRecords(
            ReadRecordsRequest(
              recordType = type.recordType.asRecordClass(),
              timeRangeFilter = TimeRangeFilter.between(Instant.now().minus(Duration.ofDays(3650)), Instant.now()),
              ascendingOrder = true,
              pageSize = 1,
            )
          ).records.firstOrNull()
        }
        .let { (it as? HcResult.Ok)?.value }

    val intervalDurations =
      records.filter { it.isInterval() }.mapNotNull { r ->
        val start = r.startTimeOrNull() ?: return@mapNotNull null
        val end = r.endTimeOrNull() ?: return@mapNotNull null
        Duration.between(start, end).seconds.toDouble()
      }
    val sampleTimes = records.mapNotNull { it.sampleTimesOrNull() }
    val sleeps = records.filterIsInstance<SleepSessionRecord>()
    val exercises = records.filterIsInstance<ExerciseSessionRecord>()

    return base.copy(
      recordsInWindow = records.size,
      origins = records.groupingBy { it.metadata.dataOrigin.packageName }.eachCount(),
      devices = records.mapNotNull { it.metadata.device?.let { d -> "${d.manufacturer ?: "?"} ${d.model ?: "?"} (type ${d.type})" } }.distinct(),
      recordingMethods = records.groupingBy { recordingMethodName(it.metadata.recordingMethod) }.eachCount(),
      earliestAccessibleRecord = earliest?.startTimeOrNull()?.toString(),
      newestRecord = records.mapNotNull { it.endTimeOrNull() }.maxOrNull()?.toString(),
      newestLastModified = records.maxOfOrNull { it.metadata.lastModifiedTime }?.toString(),
      recordsPerDay = if (records.isEmpty()) null else records.size.toDouble() / windowDays,
      recordDurationSeconds = statsOf(intervalDurations),
      samplesPerRecord = statsOf(sampleTimes.map { it.size.toDouble() }),
      sampleSpacingSeconds = statsOf(sampleTimes.flatMap { times -> times.sorted().zipWithNext { a, b -> Duration.between(a, b).toMillis() / 1000.0 } }),
      sleepSessionsWithStages = if (type == HcTypes.Sleep) sleeps.count { it.stages.isNotEmpty() } else null,
      sleepStageMinutes =
        if (type == HcTypes.Sleep)
          sleeps
            .flatMap { it.stages }
            .groupBy { SleepSessionRecord.STAGE_TYPE_INT_TO_STRING_MAP[it.stage] ?: "stage_${it.stage}" }
            .mapValues { (_, stages) -> stages.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } }
        else null,
      exerciseTypes = if (type == HcTypes.Exercise) exercises.groupingBy { it.exerciseType.toString() }.eachCount() else null,
    )
  }

  private suspend fun HealthConnectClient.readAll(recordType: KClass<out Record>, range: TimeRangeFilter): List<Record> {
    val out = mutableListOf<Record>()
    var pageToken: String? = null
    var pages = 0
    do {
      val response =
        readRecords(
          ReadRecordsRequest(
            recordType = recordType.asRecordClass(),
            timeRangeFilter = range,
            ascendingOrder = true,
            pageSize = PAGE_SIZE,
            pageToken = pageToken,
          )
        )
      out += response.records
      pageToken = response.pageToken
      pages++
    } while (!pageToken.isNullOrEmpty() && pages < MAX_PAGES)
    return out
  }

  private fun recordingMethodName(method: Int): String =
    when (method) {
      Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> "actively_recorded"
      Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> "automatically_recorded"
      Metadata.RECORDING_METHOD_MANUAL_ENTRY -> "manual_entry"
      else -> "unknown"
    }

  private fun statsOf(values: List<Double>): Stats? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    return Stats(min = sorted.first(), median = median, max = sorted.last(), count = sorted.size)
  }

  private companion object {
    const val WINDOW_DAYS = 30
    const val PAGE_SIZE = 1000
    const val MAX_PAGES = 100
  }
}

/** Health Connect's request types are generic in the record class; the cast only widens the static type. */
@Suppress("UNCHECKED_CAST")
private fun KClass<out Record>.asRecordClass(): KClass<Record> = this as KClass<Record>
