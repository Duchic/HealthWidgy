package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.DailyMetricEntity
import cz.healthwidgy.data.sync.BodyKinds
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.BarChart
import cz.healthwidgy.ui.components.LineChart
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** "Trendy" tab: the last 30 days of every metric at a glance; each card opens its detail. */
@Composable
fun TrendsScreen(
  contentPadding: PaddingValues,
  onOpenMetric: (String) -> Unit,
  onOpenHeart: () -> Unit,
  onOpenSleep: () -> Unit,
  onOpenBody: () -> Unit,
) {
  val container = LocalContext.current.appContainer
  val today = rememberToday()
  val from = today.minusDays(29)
  val status by container.syncEngine.status.collectAsStateWithLifecycle()
  val syncState by remember { container.history.syncState() }.collectAsStateWithLifecycle(initialValue = null)
  val colors = LocalMetricColors.current

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trendy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (status.running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
          Text(
            when {
              status.running && status.backfilling -> "Načítám historii z Health Connect. První načtení může chvíli trvat."
              syncState?.coveredFrom != null ->
                "Posledních 30 dní · historie od ${Format.dayMonth(LocalDate.parse(syncState!!.coveredFrom))}${LocalDate.parse(syncState!!.coveredFrom).year.let { if (it != today.year) " $it" else "" }}" +
                  (syncState?.lastSyncAt?.let { " · aktualizováno ${Format.dayAndTime(Instant.ofEpochMilli(it)).replaceFirstChar { c -> c.lowercase() }}" } ?: "")
              else -> "Posledních 30 dní"
            } + if (!status.running && status.failures.isNotEmpty()) " · některá data se nepodařilo načíst" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    item { MetricTrendCard(MetricDefs.Steps, from, today) { onOpenMetric(MetricDefs.Steps.id) } }
    item { MetricTrendCard(MetricDefs.Distance, from, today) { onOpenMetric(MetricDefs.Distance.id) } }
    item { MetricTrendCard(MetricDefs.TotalKcal, from, today) { onOpenMetric(MetricDefs.TotalKcal.id) } }
    item { MetricTrendCard(MetricDefs.Floors, from, today) { onOpenMetric(MetricDefs.Floors.id) } }
    item { MetricTrendCard(MetricDefs.RestingHr, from, today, titleOverride = "Srdce · klidový tep", onClick = onOpenHeart) }
    item { MetricTrendCard(MetricDefs.Sleep, from, today, onClick = onOpenSleep) }
    item {
      val weights by remember(today) { container.history.body(BodyKinds.WEIGHT, today.minusDays(89).atStartOfDay(ZoneId.systemDefault()).toInstant()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
      TrendCardFrame("Váha · 90 dní", R.drawable.ic_scale, colors.body, weights.lastOrNull()?.let { "${Format.decimal(it.value)} kg" }, null, onOpenBody) {
        val perDay = weights.groupBy { it.date }.mapValues { (_, v) -> v.maxBy { it.time }.value }
        if (perDay.size > 1) WeightTimeChart(perDay.toList().sortedBy { it.first }, colors.body, 56.dp)
        else Text("Málo měření pro graf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun MetricTrendCard(def: MetricDef, from: LocalDate, today: LocalDate, titleOverride: String? = null, onClick: () -> Unit) {
  val container = LocalContext.current.appContainer
  val rows: List<DailyMetricEntity> by remember(def.id, from) { container.history.daily(def.id, from) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val color = def.color(LocalMetricColors.current)
  val slots = buildSlots(rows, HistoryPeriod.MONTH, today)
  val known = slots.mapNotNull { it.value }
  val latest = slots.lastOrNull { it.value != null }
  val average = finishedAverage(slots, today)
  fun fmt(v: Double) = def.format(v) + if (def.unit.isNotEmpty()) " ${def.unit}" else ""
  TrendCardFrame(
    title = titleOverride ?: def.label,
    icon = def.icon,
    color = color,
    value = latest?.value?.let(::fmt),
    caption = average?.let { "Ø ${fmt(it)}" },
    onClick = onClick,
  ) {
    if (known.isEmpty()) {
      Text("Zatím bez dat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      val values = slots.map { it.value }
      when (def.style) {
        ChartStyle.BARS -> BarChart(values, List(values.size) { null }, color, height = 56.dp)
        ChartStyle.LINE -> LineChart(values, List(values.size) { null }, color, height = 56.dp, showPoints = false)
      }
    }
  }
}

@Composable
private fun TrendCardFrame(
  title: String,
  icon: Int,
  color: Color,
  value: String?,
  caption: String?,
  onClick: () -> Unit,
  chart: @Composable () -> Unit,
) {
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      TileHeader(icon, title, color)
      caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    value?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp)) }
    chart()
  }
}
