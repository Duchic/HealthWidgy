package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.appContainer
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.sync.BodyKinds
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.LineChart
import cz.healthwidgy.ui.components.TimeLineChart
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Body: weight and body fat from any app (Garmin, smart scales), one value per day. */
@Composable
fun BodyScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
  val container = LocalContext.current.appContainer
  val color = LocalMetricColors.current.body
  val today = rememberToday()
  var year by rememberSaveable { mutableStateOf(false) }
  val from = today.minusDays(if (year) 364 else 89).atStartOfDay(ZoneId.systemDefault()).toInstant()
  val weights by remember(from) { container.history.body(BodyKinds.WEIGHT, from) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val fats by remember(from) { container.history.body(BodyKinds.BODY_FAT, from) }.collectAsStateWithLifecycle(initialValue = emptyList())

  DetailScreen(tr("Tělo", "Body"), onBack, contentPadding) {
    item {
      ChoiceSelector(listOf(tr("3 měsíce", "3 months"), tr("Rok", "Year")), if (year) 1 else 0) { year = it == 1 }
    }
    item { MeasurementCard(tr("Váha", "Weight"), "kg", R.drawable.ic_scale, weights) }
    if (fats.isNotEmpty()) item { MeasurementCard(tr("Tělesný tuk", "Body fat"), "%", R.drawable.ic_scale, fats) }
    val latest = (weights + fats).sortedByDescending { it.time }.distinctBy { it.kind + it.date }.take(20)
    if (latest.isNotEmpty()) {
      item { Text(tr("Měření", "Measurements"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
      items(latest, key = { it.kind + it.id }) { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("${Format.weekdayDate(LocalDate.parse(m.date))} · ${sourceName(m.origin)}", style = MaterialTheme.typography.bodyMedium)
          Text(
            if (m.kind == BodyKinds.WEIGHT) "${Format.decimal(m.value)} kg" else Format.percent(Format.decimal(m.value)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
      }
    } else {
      item { Text(tr("V tomto období nejsou žádná měření.", "No measurements in this period."), style = MaterialTheme.typography.bodyMedium) }
    }
  }
}

@Composable
private fun MeasurementCard(title: String, unit: String, icon: Int, rows: List<BodyMeasurementEntity>) {
  val color = LocalMetricColors.current.body
  // One value per day: a scale app and Garmin often store the same weighing twice.
  val perDay = rows.groupBy { it.date }.map { (date, v) -> date to v.maxBy { it.time }.value }.sortedBy { it.first }
  SurfaceCard(Modifier.fillMaxWidth()) {
    TileHeader(icon, title, color)
    val last = perDay.lastOrNull()
    if (last == null) {
      Text(tr("Bez měření.", "No measurements."), style = MaterialTheme.typography.bodySmall)
      return@SurfaceCard
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(if (unit == "%") Format.percent(Format.decimal(last.second)) else "${Format.decimal(last.second)} $unit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
      val first = perDay.first()
      if (perDay.size > 1) {
        val delta = last.second - first.second
        val shown = Format.decimal(kotlin.math.abs(delta))
        val since = Format.dayMonth(LocalDate.parse(first.first))
        val arrow = if (delta < 0) "▼" else "▲"
        val amount = if (unit == "%") Format.percent(shown) else "$shown $unit"
        Text(
          if (shown == Format.decimal(0.0)) tr("beze změny od $since", "no change since $since")
          else tr("$arrow $amount od $since", "$arrow $amount since $since"),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
    }
    if (perDay.size > 1) {
      // Placed by date, so sparse and dense weighing periods keep their real proportions.
      WeightTimeChart(perDay, color, 120.dp)
    }
    // "dnes", "po 3. 11." / "today", "Mon Nov 3": the day without the time.
    val lastDay = Format.dayAndTime(Instant.ofEpochMilli(rows.maxOf { it.time }), inSentence = true).substringBeforeLast(' ')
    Text(
      tr("Poslední měření $lastDay", "Last measured $lastDay"),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Daily values ([date ISO] to value) on a time axis, with the first and last date below. */
@Composable
fun WeightTimeChart(perDay: List<Pair<String, Double>>, color: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
  val zone = ZoneId.systemDefault()
  val points = perDay.map { (date, v) -> LocalDate.parse(date).atStartOfDay(zone).toInstant() to v }
  androidx.compose.foundation.layout.Column(Modifier.padding(top = 8.dp)) {
    TimeLineChart(points, points.first().first, points.last().first, color, height = height, showLastPoint = true)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(Format.dayMonth(LocalDate.parse(perDay.first().first)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(Format.dayMonth(LocalDate.parse(perDay.last().first)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun sourceName(pkg: String): String =
  when (pkg) {
    GARMIN_CONNECT_PACKAGE -> "Garmin"
    "com.moving.movinglife" -> "MovingLife"
    "com.sec.android.app.shealth" -> "Samsung Health"
    "com.fitbit.FitbitMobile" -> "Fitbit"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
  }
