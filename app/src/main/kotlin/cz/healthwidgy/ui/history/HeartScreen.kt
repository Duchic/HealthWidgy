package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.data.sync.SyncEngine
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.LineChart
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Heart: hourly range for one of the last 7 days, the day's min/avg/max and the resting heart rate trend. */
@Composable
fun HeartScreen(onBack: () -> Unit, onOpenResting: () -> Unit, contentPadding: PaddingValues) {
  val container = LocalContext.current.appContainer
  val color = LocalMetricColors.current.heart
  val today = rememberToday()
  var dayOffset by rememberSaveable { mutableIntStateOf(0) }
  val date = today.minusDays(dayOffset.toLong())
  val zone = ZoneId.systemDefault()

  val hourly by remember(date) { container.history.hourly(Metrics.HEART_RATE, date) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val daily by remember(today) { container.history.daily(Metrics.HEART_RATE, today.minusDays(SyncEngine.HOURLY_DAYS)) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val resting by remember(today) { container.history.daily(Metrics.RESTING_HR, today.minusDays(29)) }.collectAsStateWithLifecycle(initialValue = emptyList())

  val byHour = hourly.associateBy { Instant.ofEpochMilli(it.hourStart).atZone(zone).hour }
  val avg = (0..23).map { byHour[it]?.avg }
  val band = (0..23).map { h -> byHour[h]?.let { r -> if (r.min != null && r.max != null) r.min to r.max else null } }
  val labels = (0..23).map { if (it % 6 == 0) "$it:00" else null }
  val day = daily.firstOrNull { it.date == date.toString() }
  val restingToday = resting.firstOrNull { it.date == date.toString() }

  DetailScreen("Srdce", onBack, contentPadding) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { dayOffset++ }, enabled = dayOffset < SyncEngine.HOURLY_DAYS) { AppIcon(R.drawable.ic_chevron_left, size = 22.dp, contentDescription = "Předchozí den") }
        Text(
          if (dayOffset == 0) "Dnes" else if (dayOffset == 1) "Včera" else Format.weekdayDate(date),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { dayOffset-- }, enabled = dayOffset > 0) { AppIcon(R.drawable.ic_chevron_right, size = 22.dp, contentDescription = "Další den") }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        TileHeader(R.drawable.ic_heart, "Průběh dne · rozsah a průměr za hodinu", color)
        if (avg.all { it == null }) {
          Text("Pro tento den nejsou hodinová data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        } else {
          LineChart(avg, labels, color, band = band, height = 170.dp, showPoints = false, modifier = Modifier.padding(top = 8.dp))
        }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        StatRow(
          listOf(
            "Minimum" to (day?.min?.let { "${Format.int(it)} bpm" } ?: "–"),
            "Průměr" to (day?.avg?.let { "${Format.int(it)} bpm" } ?: "–"),
            "Maximum" to (day?.max?.let { "${Format.int(it)} bpm" } ?: "–"),
          )
        )
        restingToday?.value?.let {
          Text("Klidový tep: ${Format.int(it)} bpm", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        val slots = buildSlots(resting, HistoryPeriod.MONTH, today)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          TileHeader(R.drawable.ic_heart, "Klidový tep · 30 dní", color)
          slots.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.let {
            Text("Ø ${Format.int(it.average())} bpm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        LineChart(slots.map { it.value }, slots.map { it.label }, color, height = 120.dp, showPoints = false, modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = onOpenResting, contentPadding = PaddingValues(0.dp)) { Text("Celá historie klidového tepu") }
      }
    }
    item {
      Text(
        "Garmin posílá do Health Connect tep zhruba každé dvě minuty, při tréninku častěji. HRV a stres do Health Connect neposílá.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
      )
    }
  }
}
