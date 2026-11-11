package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.ExerciseSessionEntity
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.SurfaceCard
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** "Aktivity" tab: all cached workouts grouped by week (Monday to Sunday). */
@Composable
fun ActivitiesScreen(contentPadding: PaddingValues, onOpenWorkout: (String) -> Unit) {
  val container = LocalContext.current.appContainer
  val workouts by remember { container.history.exercises() }.collectAsStateWithLifecycle(initialValue = emptyList())
  val weeks =
    workouts
      .groupBy { LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
      .toSortedMap(compareByDescending { it })

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item { Text("Aktivity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
    if (workouts.isEmpty()) {
      item {
        Text(
          "Zatím tu nejsou žádné tréninky. Garmin je do Health Connect zapíše po synchronizaci hodinek.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    weeks.forEach { (monday, list) ->
      item(key = "week-$monday") {
        val minutes = list.sumOf { (it.end - it.start) / 60_000 }
        val km = list.mapNotNull { it.distanceMeters }.sum()
        Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
          Text("Týden ${Format.weekRange(monday, monday.plusDays(6))}", style = MaterialTheme.typography.titleSmall)
          Text(
            buildString {
              append("${list.size} ${Format.plural(list.size.toLong(), "trénink", "tréninky", "tréninků")} · ${Format.duration(minutes)}")
              if (km >= 100) append(" · ${Format.km(km)} km")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(list, key = { it.id }) { workout -> WorkoutRow(workout) { onOpenWorkout(workout.id) } }
    }
  }
}

@Composable
private fun WorkoutRow(w: ExerciseSessionEntity, onClick: () -> Unit) {
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_run, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 22.dp) }
      }
      Column(Modifier.weight(1f)) {
        Text(workoutName(w), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          buildString {
            append(Format.dayAndTime(Instant.ofEpochMilli(w.start)))
            append(" · ").append(Format.clock((w.end - w.start) / 1000))
            w.distanceMeters?.takeIf { it >= 100 }?.let { append(" · ${Format.km(it)} km") }
            w.avgHeartRate?.let { append(" · Ø $it bpm") }
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      AppIcon(R.drawable.ic_chevron_right, size = 18.dp)
    }
  }
}

fun workoutName(w: ExerciseSessionEntity): String = w.title?.takeIf { it.isNotBlank() } ?: Format.exerciseName(w.exerciseType)
