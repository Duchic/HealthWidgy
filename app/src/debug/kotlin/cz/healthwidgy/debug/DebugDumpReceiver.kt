package cz.healthwidgy.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
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
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.hc.endTimeOrNull
import cz.healthwidgy.data.hc.startTimeOrNull
import cz.healthwidgy.data.settings.SourceMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only developer tool. From the PC:
 *   adb shell am broadcast -n cz.healthwidgy/.debug.DebugDumpReceiver -a cz.healthwidgy.DUMP_DAY --es date 2026-11-10
 *   adb shell am broadcast -n cz.healthwidgy/.debug.DebugDumpReceiver -a cz.healthwidgy.DUMP_OVERVIEW
 * Output goes to the app's private files/exports/ (read with `adb shell run-as cz.healthwidgy cat …`).
 * Reading while the app is closed needs READ_HEALTH_DATA_IN_BACKGROUND.
 */
class DebugDumpReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val out =
          when (intent.action) {
            ACTION_DAY -> dumpDay(app, intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now())
            ACTION_OVERVIEW -> dumpOverview(app)
            ACTION_SYNC -> dumpSync(app)
            ACTION_RENDER -> {
              // Same path as after a sync: render every placed widget in this process.
              cz.healthwidgy.widget.WidgetUpdater.updateAll(app)
              null
            }
            else -> null
          }
        Log.i(TAG, "dump written: ${out?.absolutePath}")
      } catch (e: Throwable) {
        Log.e(TAG, "dump failed", e)
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun dumpOverview(context: Context): File {
    val container = context.appContainer
    val settings = container.settings.settings.first()
    val granted = container.healthConnect.access().granted
    val text = buildString {
      for (mode in SourceMode.entries) {
        appendLine("=== $mode ===")
        val overview = container.overview.load(settings.copy(sourceMode = mode), granted)
        overview.toString().split(", ").forEach { appendLine(it) }
        appendLine()
      }
    }
    return write(context, "debug-overview.txt", text)
  }

  /** Runs a forced sync (a backfill if the cache is new) and summarises what landed in the database. */
  private suspend fun dumpSync(context: Context): File {
    val container = context.appContainer
    val started = Instant.now()
    container.syncEngine.sync(force = true)
    val took = java.time.Duration.between(started, Instant.now())
    val mode = container.settings.settings.first().sourceMode.name
    val dao = container.database.dao()
    val today = LocalDate.now()
    val text = buildString {
      appendLine("Sync took ${took.toMillis()} ms, status ${container.syncEngine.status.value}")
      appendLine("State: ${dao.syncState(mode)}")
      for (metric in listOf("steps", "distance", "floors", "total_kcal", "active_kcal", "heart_rate", "resting_hr", "sleep")) {
        val rows = dao.dailyFlow(metric, mode, "2000-01-01").first()
        appendLine("daily $metric: ${rows.size} days (${rows.firstOrNull()?.date} .. ${rows.lastOrNull()?.date}); last 4: " + rows.takeLast(4).joinToString { "${it.date}=${it.value?.let { v -> "%.1f".format(v) }}${it.min?.let { m -> " [${m.toInt()}-${it.max?.toInt()}]" } ?: ""}" })
      }
      appendLine("hourly steps today: ${dao.hourlyFlow("steps", mode, today.toString()).first().size} hours, heart today: ${dao.hourlyFlow("heart_rate", mode, today.toString()).first().size} hours")
      val sleeps = dao.sleepFlow(mode, "2000-01-01").first()
      appendLine("sleep sessions: ${sleeps.size}; latest: ${sleeps.firstOrNull()?.let { "${it.wakeDate} asleep=${it.asleepMinutes} deep=${it.deepMinutes} rem=${it.remMinutes} stages=${it.stages.count { c -> c == ';' } + 1}" }}")
      val ex = dao.exerciseFlow(mode).first()
      appendLine("exercises: ${ex.size}")
      ex.take(12).forEach { appendLine("  ${it.date} type=${it.exerciseType} ${(it.end - it.start) / 60000} min dist=${it.distanceMeters} kcal=${it.activeKcal} hr=${it.avgHeartRate}/${it.maxHeartRate} elev=${it.elevationGainMeters} stats=${it.statsComputed}") }
      val weights = dao.bodyFlow("weight", 0).first()
      val fats = dao.bodyFlow("body_fat", 0).first()
      appendLine("body: ${weights.size} weights, ${fats.size} body fat; latest weight ${weights.lastOrNull()?.let { "${it.date} ${it.value} ${it.origin}" }}")
    }
    return write(context, "debug-sync.txt", text)
  }

  private suspend fun dumpDay(context: Context, date: LocalDate): File {
    val zone = ZoneId.systemDefault()
    val client = HealthConnectClient.getOrCreate(context)
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val now = Instant.now()
    val garmin = setOf(DataOrigin(GARMIN_CONNECT_PACKAGE))
    val text = buildString {
      appendLine("Day $date ($zone), dumped at $now")
      appendLine()
      appendLine("--- Aggregates, Garmin only: full day vs. [start, min(now, end)) ---")
      val partialEnd = minOf(now, end)
      for ((label, range) in listOf("full day" to TimeRangeFilter.between(start, end), "until now" to TimeRangeFilter.between(start, partialEnd))) {
        val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range, garmin))[StepsRecord.COUNT_TOTAL]
        val dist = client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range, garmin))
        val total = client.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), range, garmin))
        val active = client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range, garmin))
        val floors = client.aggregate(AggregateRequest(setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), range, garmin))[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
        appendLine(
          "$label: steps=$steps distance=${dist[DistanceRecord.DISTANCE_TOTAL]?.inMeters}m (origins ${dist.dataOrigins.map { it.packageName }}) " +
            "totalKcal=${total[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories} (origins ${total.dataOrigins.map { it.packageName }}) " +
            "activeKcal=${active[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories} floors=$floors"
        )
      }
      appendLine()
      for (type in HcTypes.all) {
        if (type.recordType == StepsRecord::class || type.recordType == HeartRateRecord::class) continue
        val records = readAll(client, type.recordType, TimeRangeFilter.between(start, end))
        appendLine("--- ${type.label} (${records.size}) ---")
        records.forEach { appendLine("  " + describe(it)) }
      }
      val steps = readAll(client, StepsRecord::class, TimeRangeFilter.between(start, end)).filterIsInstance<StepsRecord>()
      appendLine("--- Kroky: ${steps.size} records, sum by origin ${steps.groupBy { it.metadata.dataOrigin.packageName }.mapValues { (_, v) -> v.sumOf { it.count } }}")
      val hr = readAll(client, HeartRateRecord::class, TimeRangeFilter.between(start, end)).filterIsInstance<HeartRateRecord>()
      val samples = hr.flatMap { it.samples }
      appendLine("--- Tep: ${hr.size} records, ${samples.size} samples, min ${samples.minOfOrNull { it.beatsPerMinute }}, max ${samples.maxOfOrNull { it.beatsPerMinute }}, first ${samples.minOfOrNull { it.time }}, last ${samples.maxOfOrNull { it.time }}")
    }
    return write(context, "debug-day-$date.txt", text)
  }

  private suspend fun readAll(client: HealthConnectClient, type: KClass<out Record>, range: TimeRangeFilter): List<Record> {
    @Suppress("UNCHECKED_CAST") val k = type as KClass<Record>
    val out = mutableListOf<Record>()
    var token: String? = null
    do {
      val r = client.readRecords(ReadRecordsRequest(recordType = k, timeRangeFilter = range, pageSize = 5000, pageToken = token))
      out += r.records
      token = r.pageToken
    } while (!token.isNullOrEmpty())
    return out
  }

  private fun describe(r: Record): String {
    val origin = r.metadata.dataOrigin.packageName.let { if (it == GARMIN_CONNECT_PACKAGE) "garmin" else it }
    val value =
      when (r) {
        is DistanceRecord -> "${r.distance.inMeters} m"
        is TotalCaloriesBurnedRecord -> "${r.energy.inKilocalories} kcal"
        is ActiveCaloriesBurnedRecord -> "${r.energy.inKilocalories} kcal"
        is FloorsClimbedRecord -> "${r.floors} floors"
        is ElevationGainedRecord -> "${r.elevation.inMeters} m"
        is RestingHeartRateRecord -> "${r.beatsPerMinute} bpm"
        is SleepSessionRecord -> "${r.stages.size} stages, title=${r.title}"
        is ExerciseSessionRecord -> "type=${r.exerciseType} title=${r.title} segments=${r.segments.size} laps=${r.laps.size}"
        is SpeedRecord -> "${r.samples.size} samples"
        is WeightRecord -> "${r.weight.inKilograms} kg"
        is BodyFatRecord -> "${r.percentage.value} %"
        else -> ""
      }
    return "$origin ${r.startTimeOrNull()} -> ${r.endTimeOrNull()} $value (method ${r.metadata.recordingMethod}, modified ${r.metadata.lastModifiedTime})"
  }

  private fun write(context: Context, name: String, text: String): File =
    File(context.filesDir, "exports/$name").apply {
      parentFile?.mkdirs()
      writeText(text)
    }

  private companion object {
    const val TAG = "HealthWidgyDebug"
    const val ACTION_DAY = "cz.healthwidgy.DUMP_DAY"
    const val ACTION_OVERVIEW = "cz.healthwidgy.DUMP_OVERVIEW"
    const val ACTION_SYNC = "cz.healthwidgy.SYNC"
    const val ACTION_RENDER = "cz.healthwidgy.RENDER_WIDGETS"
  }
}
