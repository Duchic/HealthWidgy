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
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.LineChart
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
  val today = remember { LocalDate.now() }
  var year by rememberSaveable { mutableStateOf(false) }
  val from = today.minusDays(if (year) 364 else 89).atStartOfDay(ZoneId.systemDefault()).toInstant()
  val weights by remember(from) { container.history.body(BodyKinds.WEIGHT, from) }.collectAsStateWithLifecycle(initialValue = emptyList())
  val fats by remember(from) { container.history.body(BodyKinds.BODY_FAT, from) }.collectAsStateWithLifecycle(initialValue = emptyList())

  DetailScreen("Tělo", onBack, contentPadding) {
    item {
      ChoiceSelector(listOf("3 měsíce", "Rok"), if (year) 1 else 0) { year = it == 1 }
    }
    item { MeasurementCard("Váha", "kg", R.drawable.ic_scale, weights) }
    if (fats.isNotEmpty()) item { MeasurementCard("Tělesný tuk", "%", R.drawable.ic_scale, fats) }
    val latest = (weights + fats).sortedByDescending { it.time }.distinctBy { it.kind + it.date }.take(20)
    if (latest.isNotEmpty()) {
      item { Text("Měření", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
      items(latest, key = { it.kind + it.id }) { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("${Format.weekdayDate(LocalDate.parse(m.date))} · ${sourceName(m.origin)}", style = MaterialTheme.typography.bodyMedium)
          Text(
            if (m.kind == BodyKinds.WEIGHT) "${Format.decimal(m.value)} kg" else "${Format.decimal(m.value)} %",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
      }
    } else {
      item { Text("V tomto období nejsou žádná měření.", style = MaterialTheme.typography.bodyMedium) }
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
      Text("Bez měření.", style = MaterialTheme.typography.bodySmall)
      return@SurfaceCard
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("${Format.decimal(last.second)} $unit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
      val first = perDay.first()
      if (perDay.size > 1) {
        val delta = last.second - first.second
        Text(
          "${if (delta < 0) "▼" else "▲"} ${Format.decimal(kotlin.math.abs(delta))} $unit od ${Format.dayMonth(LocalDate.parse(first.first))}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
    }
    if (perDay.size > 1) {
      LineChart(
        values = perDay.map { it.second },
        labels = perDay.mapIndexed { i, p -> if (i == 0 || i == perDay.lastIndex) Format.dayMonth(LocalDate.parse(p.first)) else null },
        color = color,
        height = 120.dp,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    Text("Poslední měření ${Format.dayAndTime(Instant.ofEpochMilli(rows.maxOf { it.time })).substringBeforeLast(' ')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
