package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.BarChart
import cz.healthwidgy.ui.components.LineChart
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.LocalDate

/** Week / month / year view of one daily metric with summary and a list of values. */
@Composable
fun MetricDetailScreen(metricId: String, onBack: () -> Unit, contentPadding: androidx.compose.foundation.layout.PaddingValues) {
  val def = MetricDefs.byId(metricId)
  val container = LocalContext.current.appContainer
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
  var period by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
  var selected by rememberSaveable(period) { mutableStateOf<Int?>(null) }
  val today = remember { LocalDate.now() }
  val from = firstDay(period, today)
  val rows by remember(metricId, from) { container.history.daily(metricId, from) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val color = def.color(LocalMetricColors.current)
  val goal = def.goal(settings)
  val slots = buildSlots(rows, period, today)
  val summary = summarize(rows, from, def.isSum, goal, today)
  fun fmt(v: Double?) = v?.let { def.format(it) + if (def.unit.isNotEmpty()) " ${def.unit}" else "" } ?: "–"

  DetailScreen(def.label, onBack, contentPadding) {
    item { PeriodSelector(period, { period = it }) }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        val shown = selected?.let { slots.getOrNull(it) } ?: slots.lastOrNull { it.value != null }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TileHeader(def.icon, shown?.fullLabel ?: def.label, color)
        }
        Text(fmt(shown?.value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        val values = slots.map { it.value }
        val labels = slots.map { it.label }
        when (def.style) {
          ChartStyle.BARS -> BarChart(values, labels, color, goal = goal, selected = selected, onSelect = { selected = it })
          ChartStyle.LINE -> LineChart(values, labels, color, selected = selected, onSelect = { selected = it }, showPoints = period != HistoryPeriod.MONTH)
        }
        if (rows.isEmpty()) {
          Text("Pro toto období zatím nejsou v databázi žádná data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    item {
      SurfaceCard(Modifier.fillMaxWidth()) {
        StatRow(
          buildList {
            add("Průměr / den" to fmt(summary.average))
            summary.total?.takeIf { def.id != MetricDefs.RestingHr.id }?.let { add("Celkem" to fmt(it)) }
            add("Maximum" to fmt(summary.best?.value))
          }
        )
        if (summary.goalHits != null && goal != null) {
          Text(
            "Cíl ${def.format(goal)} splněn ${summary.goalHits}× z ${summary.daysWithData} ukončených dní (bez dneška)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        summary.best?.let {
          Text("Nejlépe: ${it.fullLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    val listed = slots.filter { it.value != null }.reversed()
    items(listed, key = { it.date.toString() }) { slot ->
      Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(slot.fullLabel, style = MaterialTheme.typography.bodyMedium)
        Text(fmt(slot.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
  }
}
