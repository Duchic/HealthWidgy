package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.history.WorkoutSeries
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.components.TimeLineChart
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Instant
import kotlinx.coroutines.flow.map

/** Wraps a loaded value so "loaded but empty" differs from "still loading" (null). */
private data class Loaded<T>(val value: T)

private val PACE_TYPES =
  setOf(
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
  )

/** One workout: summary numbers plus heart rate and speed over time (read live from Health Connect). */
@Composable
fun WorkoutDetailScreen(id: String, onBack: () -> Unit, contentPadding: PaddingValues) {
  val container = LocalContext.current.appContainer
  val colors = LocalMetricColors.current
  // Outer null = still loading, Loaded(null) = not in the cache.
  val workout by remember(id) { container.history.exercise(id).map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
  LaunchedEffect(id) { container.syncEngine.ensureExerciseStats(id) }
  val w = workout?.value
  val series by
    produceState<WorkoutSeries?>(null, w?.start, w?.end) {
      value = if (w == null) null else container.history.workoutSeries(Instant.ofEpochMilli(w.start), Instant.ofEpochMilli(w.end))
    }

  DetailScreen(w?.let(::workoutName) ?: "Trénink", onBack, contentPadding) {
    if (workout == null) return@DetailScreen
    if (w == null) {
      item { Text("Trénink se nenašel. Možná ještě neproběhla synchronizace.", style = MaterialTheme.typography.bodyMedium) }
      return@DetailScreen
    }
    val start = Instant.ofEpochMilli(w.start)
    val end = Instant.ofEpochMilli(w.end)
    val seconds = (w.end - w.start) / 1000
    item {
      Text(
        "${Format.dayAndTime(start)} – ${Format.time(end)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          val distance = w.distanceMeters?.takeIf { it >= 100 }
          StatRow(
            listOf(
              "Čas" to Format.clock(seconds),
              "Vzdálenost" to (distance?.let { "${Format.km(it)} km" } ?: "–"),
              if (w.exerciseType in PACE_TYPES) "Tempo" to (distance?.let { Format.pace(seconds, it) }?.let { "$it /km" } ?: "–")
              else "Rychlost" to (distance?.let { "${Format.decimal(it / 1000.0 / (seconds / 3600.0))} km/h" } ?: "–"),
            )
          )
          StatRow(
            listOf(
              "Ø tep" to (w.avgHeartRate?.let { "$it bpm" } ?: "–"),
              "Max tep" to (w.maxHeartRate?.let { "$it bpm" } ?: "–"),
              "Kalorie" to (w.activeKcal?.let { "${Format.int(it)} kcal" } ?: "–"),
            )
          )
          w.elevationGainMeters?.takeIf { it > 0.5 }?.let {
            Text("Převýšení ${Format.int(it)} m", style = MaterialTheme.typography.bodyMedium)
          }
          if (!w.statsComputed) Text("Počítám souhrn…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    val s = series
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        TileHeader(R.drawable.ic_heart, "Tep v průběhu tréninku", colors.heart)
        when {
          s == null -> Text("Načítám…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
          s.heartRate.size < 2 -> Text("Žádná data tepu.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
          else -> {
            TimeLineChart(s.heartRate.map { it.first to it.second.toDouble() }, start, end, colors.heart, Modifier.padding(top = 8.dp))
            Text(
              "${s.heartRate.minOf { it.second }}–${s.heartRate.maxOf { it.second }} bpm · ${s.heartRate.size} měření",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    if (s != null && s.speed.size >= 2) {
      item {
        SurfaceCard(Modifier.fillMaxWidth()) {
          TileHeader(R.drawable.ic_route, "Rychlost (km/h)", colors.workout)
          TimeLineChart(s.speed.map { it.first to it.second * 3.6 }, start, end, colors.workout, Modifier.padding(top = 8.dp))
          Text(
            "max ${Format.decimal(s.speed.maxOf { it.second } * 3.6)} km/h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    item {
      Text(
        "Mapu trasy a mezičasy Garmin do Health Connect neposílá.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
      )
    }
  }
}
