package cz.healthwidgy.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.SleepSessionEntity
import cz.healthwidgy.data.history.stageList
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.BarChart
import cz.healthwidgy.ui.components.Hypnogram
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Sleep: one night at a time (hypnogram and stage split) plus the last 30 nights. */
@Composable
fun SleepScreen(onBack: () -> Unit, onOpenHistory: () -> Unit, contentPadding: PaddingValues) {
  val container = LocalContext.current.appContainer
  val colors = LocalMetricColors.current
  val today = rememberToday()
  val sessionsOrNull by remember(today) { container.history.sleep(today.minusDays(120)) }.collectAsStateWithLifecycle(initialValue = null)
  val nightly by remember(today) { container.history.daily(Metrics.SLEEP, today.minusDays(29)) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
  val sleepGoal = settings.sleepGoalMinutes
  // One night per wake-up day: the longest session (naps are ignored here).
  val nights: List<SleepSessionEntity> =
    sessionsOrNull.orEmpty().groupBy { it.wakeDate }.values.map { s -> s.maxBy { it.end - it.start } }.sortedByDescending { it.end }
  var index by rememberSaveable { mutableIntStateOf(0) }
  val night = nights.getOrNull(index.coerceIn(0, (nights.size - 1).coerceAtLeast(0)))

  DetailScreen("Spánek", onBack, contentPadding) {
    // Still loading from the database: show nothing rather than a false "no nights".
    if (sessionsOrNull == null) return@DetailScreen
    if (night == null) {
      item { Text("Zatím tu nejsou žádné noci. Data se načtou při příští synchronizaci.", style = MaterialTheme.typography.bodyMedium) }
      return@DetailScreen
    }
    val start = Instant.ofEpochMilli(night.start)
    val end = Instant.ofEpochMilli(night.end)
    val wake = LocalDate.parse(night.wakeDate)
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { index++ }, enabled = index < nights.size - 1) { AppIcon(R.drawable.ic_chevron_left, size = 22.dp, contentDescription = "Předchozí noc") }
        Text(
          "Noc ${Format.weekRange(wake.minusDays(1), wake)}",
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
        )
        IconButton(onClick = { index-- }, enabled = index > 0) { AppIcon(R.drawable.ic_chevron_right, size = 22.dp, contentDescription = "Další noc") }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        TileHeader(R.drawable.ic_moon, "Spánek", colors.sleep)
        Text(Format.duration(night.asleepMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = colors.sleep)
        Text("V posteli ${Format.time(start)} – ${Format.time(end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val stages = night.stageList()
        if (stages.isNotEmpty()) {
          Hypnogram(
            stages = stages,
            start = start,
            end = end,
            laneColors = listOf(colors.stageAwake, colors.stageRem, colors.stageLight, colors.stageDeep),
            laneLabels = listOf("Bdění", "REM", "Lehký", "Hluboký"),
            laneOf = ::laneOf,
            modifier = Modifier.padding(top = 12.dp),
          )
          Row(Modifier.fillMaxWidth().padding(start = 54.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.time(start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.time(end), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          Text("Tato noc nemá fáze spánku.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        val inBed = ((night.end - night.start) / 60_000).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          StageRow("Hluboký", night.deepMinutes, inBed, colors.stageDeep)
          StageRow("Lehký", night.lightMinutes + night.unspecifiedMinutes, inBed, colors.stageLight)
          StageRow("REM", night.remMinutes, inBed, colors.stageRem)
          StageRow("Bdění", night.awakeMinutes, inBed, colors.stageAwake)
        }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        val slots = buildSlots(nightly, HistoryPeriod.MONTH, today)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          TileHeader(R.drawable.ic_moon, "Posledních 30 nocí", colors.sleep)
          slots.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.let {
            Text("Ø ${Format.duration(it.average().roundToInt().toLong())}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        BarChart(slots.map { it.value }, slots.map { it.label }, colors.sleep, goal = sleepGoal.toDouble(), height = 120.dp, modifier = Modifier.padding(top = 8.dp))
        Text("Čárkovaná čára: cíl ${Format.hours(sleepGoal)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenHistory, contentPadding = PaddingValues(0.dp)) { Text("Celá historie spánku") }
      }
    }
    item {
      Text(
        "Skóre spánku Garmin do Health Connect neposílá, proto ho tu nenajdeš.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun laneOf(type: Int): Int? =
  when (type) {
    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> 0
    SleepSessionRecord.STAGE_TYPE_REM -> 1
    SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> 2
    SleepSessionRecord.STAGE_TYPE_DEEP -> 3
    else -> null
  }

@Composable
private fun StageRow(name: String, minutes: Long, inBed: Long, color: Color) {
  val share = minutes.toFloat() / inBed
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Text(name, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodyMedium)
    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
      Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
    Text("${Format.duration(minutes)} · ${(share * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
