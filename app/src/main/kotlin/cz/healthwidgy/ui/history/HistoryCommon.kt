package cz.healthwidgy.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.healthwidgy.R
import cz.healthwidgy.data.db.DailyMetricEntity
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import java.time.LocalDate
import java.time.YearMonth

enum class HistoryPeriod(val label: String, val days: Long) {
  WEEK("Týden", 7),
  MONTH("Měsíc", 30),
  YEAR("Rok", 365),
}

/** One chart slot: a day, or a month in the year view (value = average per day of that month). */
data class Slot(
  val label: String?,
  val fullLabel: String,
  val value: Double?,
  val min: Double? = null,
  val max: Double? = null,
  val date: LocalDate,
)

fun firstDay(period: HistoryPeriod, today: LocalDate): LocalDate =
  if (period == HistoryPeriod.YEAR) YearMonth.from(today).minusMonths(11).atDay(1) else today.minusDays(period.days - 1)

/** Builds evenly spaced slots for [period], with nulls for days without data. */
fun buildSlots(rows: List<DailyMetricEntity>, period: HistoryPeriod, today: LocalDate, value: (DailyMetricEntity) -> Double? = { it.value }): List<Slot> {
  val byDate = rows.associateBy { LocalDate.parse(it.date) }
  return when (period) {
    HistoryPeriod.WEEK, HistoryPeriod.MONTH -> {
      val n = period.days.toInt()
      (0 until n).map { i ->
        val date = today.minusDays((n - 1 - i).toLong())
        val row = byDate[date]
        val label =
          when {
            period == HistoryPeriod.WEEK -> Format.weekdayShort(date)
            (n - 1 - i) % 7 == 0 -> Format.dayMonth(date)
            else -> null
          }
        Slot(label, Format.weekdayDate(date), row?.let(value), row?.min, row?.max, date)
      }
    }
    HistoryPeriod.YEAR -> {
      (11 downTo 0).map { back ->
        val month = YearMonth.from(today).minusMonths(back.toLong())
        val days = byDate.filterKeys { YearMonth.from(it) == month }.values.mapNotNull(value)
        Slot(Format.monthShort(month.atDay(1)), Format.monthYear(month.atDay(1)) + " · Ø za den", days.takeIf { it.isNotEmpty() }?.average(), date = month.atDay(1))
      }
    }
  }
}

data class Summary(val average: Double?, val total: Double?, val best: Slot?, val goalHits: Int?, val daysWithData: Int)

fun summarize(rows: List<DailyMetricEntity>, from: LocalDate, isSum: Boolean, goal: Double?, value: (DailyMetricEntity) -> Double? = { it.value }): Summary {
  val days = rows.filter { LocalDate.parse(it.date) >= from }.mapNotNull { r -> value(r)?.let { LocalDate.parse(r.date) to it } }
  val best = days.maxByOrNull { it.second }
  return Summary(
    average = days.map { it.second }.takeIf { it.isNotEmpty() }?.average(),
    total = if (isSum) days.sumOf { it.second } else null,
    best = best?.let { Slot(null, Format.weekdayDate(it.first), it.second, date = it.first) },
    goalHits = goal?.let { g -> days.count { it.second >= g } },
    daysWithData = days.size,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit) {
  TopAppBar(
    title = { Text(title) },
    navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_back, contentDescription = "Zpět", size = 22.dp, tint = MaterialTheme.colorScheme.onSurface) } },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
  )
}

/** Detail screen frame: top bar with back plus a lazy list respecting the scaffold padding. */
@Composable
fun DetailScreen(title: String, onBack: () -> Unit, contentPadding: PaddingValues, content: LazyListScope.() -> Unit) {
  Column(Modifier.fillMaxSize().padding(contentPadding)) {
    DetailTopBar(title, onBack)
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
fun PeriodSelector(selected: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
  ChoiceSelector(HistoryPeriod.entries.map { it.label }, HistoryPeriod.entries.indexOf(selected)) { onSelect(HistoryPeriod.entries[it]) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSelector(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    options.forEachIndexed { i, label ->
      SegmentedButton(selected = i == selectedIndex, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
    }
  }
}

/** Label/value pairs in a row ("Průměr 9 400", "Maximum 15 320"…). */
@Composable
fun StatRow(stats: List<Pair<String, String>>) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    stats.forEach { (label, value) ->
      Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
      }
    }
  }
}
