package cz.healthwidgy.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.health.connect.client.records.SleepSessionRecord
import cz.healthwidgy.R
import cz.healthwidgy.data.history.stageList
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.ui.Format
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Common plumbing: load the cached snapshot, reload it when [WidgetUpdater] signals, apply the widget theme. */
abstract class HealthWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val initial = WidgetData.load(context)
    provideContent {
      val tick by WidgetUpdater.tick.collectAsState()
      val snapshot by produceState(initial, tick) { value = WidgetData.load(context) }
      WidgetTheme(snapshot.theme) { Content(context, snapshot) }
    }
  }

  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val snapshot = WidgetData.load(context)
    provideContent { WidgetTheme(snapshot.theme) { Content(context, snapshot) } }
  }

  @Composable abstract fun Content(context: Context, s: WidgetSnapshot)
}

private val Dp.orZero: Dp
  get() = if (value < 0f) 0.dp else this

private fun Float.percent(): Int = (this * 100).roundToInt()

/** Word under the value inside a goal ring. */
private fun ringCaption(metric: WidgetMetric, s: WidgetSnapshot, r: Reading): String =
  when (metric) {
    WidgetMetric.STEPS -> Format.plural((s.steps ?: 0.0).roundToLong(), "krok", "kroky", "kroků")
    WidgetMetric.SLEEP -> "spánek"
    else -> r.unit ?: metric.unit(null) ?: metric.shortLabel.lowercase()
  }

/** Ring for one goal metric with the value in the middle. */
@Composable
private fun GoalRing(context: Context, s: WidgetSnapshot, metric: WidgetMetric, sizeDp: Int, strokeDp: Float, valueSize: Int) {
  val r = s.readForGoal(metric)
  Rings(context, listOf((r.progress ?: 0f) to metric.accent), sizeDp, strokeDp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(r.value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = valueSize.sp, fontWeight = FontWeight.Bold), maxLines = 1)
      WidgetSmall(ringCaption(metric, s, r))
    }
  }
}

/**
 * Title on the left, short summary on the right. The title takes the remaining width (and is shortened
 * with "…" when narrow), so the summary is never pushed out of the widget.
 */
@Composable
private fun HeaderRow(title: String, metric: WidgetMetric, summary: String?) {
  Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(GlanceModifier.defaultWeight()) { WidgetLabel(title, metric.icon, metric.accent.provider()) }
    if (summary != null) {
      Spacer(GlanceModifier.width(6.dp))
      WidgetSmall(summary)
    }
  }
}

/** "85 % z 10 000 kroků"; without today's value the note ("naposledy včera 7:32 h") or the goal. */
private fun goalLine(metric: WidgetMetric, s: WidgetSnapshot, r: Reading): String =
  if (r.value == NO_VALUE) r.note ?: "cíl ${s.goalText(metric)}"
  else "${(r.progress ?: 0f).percent()} % z ${s.goalText(metric)}"

// ---------------------------------------------------------------- Kroužek (2×2, configurable)

class StepsWidget : HealthWidget() {
  companion object {
    val METRIC = Slot("metric", "Cíl v kroužku", WidgetMetric.withGoal, WidgetMetric.STEPS)
    val config = WidgetConfig(listOf(METRIC))
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metric = chosen(METRIC) ?: WidgetMetric.STEPS
    val r = s.readForGoal(metric)
    WidgetFrame(openApp(context, r.destination)) {
      val ring = (minOf(size.width, size.height) - 46.dp).value.coerceIn(48f, 130f).toInt()
      Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        GoalRing(context, s, metric, ring, 9f, if (ring > 80) 18 else 14)
        Spacer(GlanceModifier.height(4.dp))
        WidgetSmall(goalLine(metric, s, r))
      }
    }
  }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

// ---------------------------------------------------------------- Dnešní přehled (4×2, configurable)

class TodayWidget : HealthWidget() {
  companion object {
    val RING = Slot("ring", "Kroužek", WidgetMetric.withGoal, WidgetMetric.STEPS)
    val STATS =
      listOf(
        Slot("slot1", "Hodnota 1", WidgetMetric.pickerOrder, WidgetMetric.CALORIES),
        Slot("slot2", "Hodnota 2", WidgetMetric.pickerOrder, WidgetMetric.DISTANCE),
        Slot("slot3", "Hodnota 3", WidgetMetric.pickerOrder, WidgetMetric.FLOORS),
        Slot("slot4", "Hodnota 4", WidgetMetric.pickerOrder, WidgetMetric.RESTING_HR),
      )
    val config = WidgetConfig(listOf(RING) + STATS)
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val ringMetric = chosen(RING) ?: WidgetMetric.STEPS
    val stats = STATS.map { chosen(it) ?: it.default }
    WidgetFrame(openApp(context, Destinations.OVERVIEW)) {
      Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          WidgetLabel("Dnes")
          Spacer(GlanceModifier.defaultWeight())
          RefreshStamp(s.lastSyncAt)
        }
        Spacer(GlanceModifier.height(6.dp))
        val ring = minOf(size.height - 52.dp, size.width * 0.36f).value.coerceIn(56f, 130f).toInt()
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
          Box(GlanceModifier.clickable(openApp(context, ringMetric.destination()))) { GoalRing(context, s, ringMetric, ring, 9f, if (ring >= 100) 18 else 16) }
          Spacer(GlanceModifier.width(12.dp))
          Column(GlanceModifier.defaultWeight()) {
            Row(GlanceModifier.fillMaxWidth()) {
              MiniStat(context, stats[0], s, GlanceModifier.defaultWeight())
              MiniStat(context, stats[1], s, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
              MiniStat(context, stats[2], s, GlanceModifier.defaultWeight())
              MiniStat(context, stats[3], s, GlanceModifier.defaultWeight())
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MiniStat(context: Context, metric: WidgetMetric, s: WidgetSnapshot, modifier: GlanceModifier) {
  val r = s.read(metric)
  Column(modifier.clickable(openApp(context, r.destination))) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      MetricIcon(metric, 13.dp)
      Spacer(GlanceModifier.width(4.dp))
      Text(r.value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
      if (r.unit != null) {
        Spacer(GlanceModifier.width(2.dp))
        Text(r.unit, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
      }
    }
    WidgetSmall(metric.shortLabel)
  }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---------------------------------------------------------------- Více hodnot (4×1, configurable)

class MultiValueWidget : HealthWidget() {
  companion object {
    val SLOTS =
      listOf(
        Slot("slot1", "Hodnota 1", WidgetMetric.pickerOrder, WidgetMetric.STEPS),
        Slot("slot2", "Hodnota 2", WidgetMetric.pickerOrder, WidgetMetric.DISTANCE),
        Slot("slot3", "Hodnota 3", WidgetMetric.pickerOrder, WidgetMetric.CALORIES, optional = true),
        Slot("slot4", "Hodnota 4", WidgetMetric.pickerOrder, WidgetMetric.RESTING_HR, optional = true),
      )
    val config = WidgetConfig(SLOTS)
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metrics = SLOTS.mapNotNull { chosen(it) }
    WidgetFrame(openApp(context, Destinations.OVERVIEW), padding = 10.dp) {
      val layout = multiValueLayout(metrics.size, (size.width - 20.dp).value, (size.height - 20.dp).value)
      val rows = metrics.chunked(layout.cols)
      Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        rows.forEachIndexed { i, row ->
          if (i > 0) Spacer(GlanceModifier.height(if (layout.compact) 6.dp else 8.dp))
          Row(GlanceModifier.fillMaxWidth()) {
            row.forEach { m ->
              if (layout.compact) CompactCell(context, m, s, GlanceModifier.defaultWeight())
              else ValueCell(context, m, s, layout.valueSize, layout.showNote, GlanceModifier.defaultWeight())
            }
            repeat(layout.cols - row.size) { Spacer(GlanceModifier.defaultWeight()) }
          }
        }
      }
    }
  }

  /** One line per value (icon, value, unit) for short widgets that need two rows. */
  @Composable
  private fun CompactCell(context: Context, metric: WidgetMetric, s: WidgetSnapshot, modifier: GlanceModifier) {
    val r = s.read(metric)
    Row(modifier.clickable(openApp(context, r.destination)), verticalAlignment = Alignment.CenterVertically) {
      MetricIcon(metric, 12.dp)
      Spacer(GlanceModifier.width(4.dp))
      Text(r.value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
      if (r.unit != null) {
        Spacer(GlanceModifier.width(2.dp))
        Text(r.unit, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
      }
    }
  }

  @Composable
  private fun ValueCell(context: Context, metric: WidgetMetric, s: WidgetSnapshot, valueSize: Int, showNote: Boolean, modifier: GlanceModifier) {
    val r = s.read(metric)
    Column(modifier.clickable(openApp(context, r.destination))) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        MetricIcon(metric, 12.dp)
        Spacer(GlanceModifier.width(4.dp))
        WidgetSmall(metric.shortLabel)
      }
      WidgetValue(r.value, r.unit, size = valueSize.sp)
      if (showNote && r.note != null) WidgetSmall(r.note)
    }
  }
}

class MultiValueWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = MultiValueWidget()
}

// ---------------------------------------------------------------- Kroužky cílů (2×2, configurable)

class GoalRingsWidget : HealthWidget() {
  companion object {
    val SLOTS =
      listOf(
        Slot("ring1", "Vnější kroužek", WidgetMetric.withGoal, WidgetMetric.STEPS),
        Slot("ring2", "Prostřední kroužek", WidgetMetric.withGoal, WidgetMetric.FLOORS),
        Slot("ring3", "Vnitřní kroužek", WidgetMetric.withGoal, WidgetMetric.SLEEP),
      )
    val config = WidgetConfig(SLOTS)
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metrics = SLOTS.map { chosen(it) ?: it.default }
    val readings = metrics.map { it to s.readForGoal(it) }
    val rings = readings.map { (m, r) -> (r.progress ?: 0f) to m.accent }
    fun share(r: Reading) = if (r.value == NO_VALUE) NO_VALUE else "${(r.progress ?: 0f).percent()} %"
    WidgetFrame(openApp(context, Destinations.OVERVIEW)) {
      if (size.width >= size.height * 1.35f) {
        val ring = (size.height - 24.dp).value.coerceIn(60f, 150f).toInt()
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
          Rings(context, rings, ring, ring / 10f)
          Spacer(GlanceModifier.width(12.dp))
          Column(GlanceModifier.defaultWeight()) {
            readings.forEachIndexed { i, (m, r) ->
              if (i > 0) Spacer(GlanceModifier.height(6.dp))
              Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, r.destination)), verticalAlignment = Alignment.CenterVertically) {
                MetricIcon(m, 13.dp)
                Spacer(GlanceModifier.width(5.dp))
                WidgetValue(r.value, r.unit, size = 14.sp)
                Spacer(GlanceModifier.defaultWeight())
                WidgetSmall(share(r))
              }
            }
          }
        }
      } else {
        val ring = minOf(size.width - 24.dp, size.height - 58.dp).value.coerceIn(56f, 150f).toInt()
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
          Rings(context, rings, ring, ring / 10f)
          Spacer(GlanceModifier.height(6.dp))
          Row(GlanceModifier.fillMaxWidth()) {
            readings.forEach { (m, r) ->
              Row(GlanceModifier.defaultWeight().clickable(openApp(context, r.destination)), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                MetricIcon(m, 12.dp)
                Spacer(GlanceModifier.width(3.dp))
                Text(share(r), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}

class GoalRingsWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = GoalRingsWidget()
}

// ---------------------------------------------------------------- Trend (4×2, configurable)

class StepsTrendWidget : HealthWidget() {
  companion object {
    val METRIC = Slot("metric", "Údaj", WidgetMetric.withHistory, WidgetMetric.STEPS)
    val config = WidgetConfig(listOf(METRIC), periods = listOf(7, 14, 30), defaultPeriod = 7)
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metric = chosen(METRIC) ?: WidgetMetric.STEPS
    val days = chosenPeriod(config)
    val series = s.series(metric, days)
    val values = series.map { it.second }
    WidgetFrame(openApp(context, metric.destination())) {
      Column(GlanceModifier.fillMaxSize()) {
        HeaderRow("${metric.shortLabel} · $days dní", metric, trendSummary(metric, series, s, withRange = size.width >= 250.dp))
        Spacer(GlanceModifier.height(6.dp))
        if (values.none { it != null }) {
          NoDataYet("Za $days dní bez dat")
          return@Column
        }
        val w = (size.width - 24.dp).orZero.value.toInt().coerceAtLeast(40)
        val h = (size.height - 64.dp).value.coerceIn(28f, 160f).toInt()
        Box(GlanceModifier.width(w.dp).height(h.dp)) {
          if (metric.isLevel) {
            Mask(WidgetBitmaps.line(context, values, w, h, dots = days <= 14, slotCentered = true), metric.accent.provider(), w.dp, h.dp)
          } else {
            val goal = s.goal(metric)
            val max = (values.filterNotNull() + listOfNotNull(goal)).max().coerceAtLeast(1.0)
            if (goal != null) Mask(WidgetBitmaps.dashedLine(context, (1 - goal / max).toFloat(), w, h), GlanceTheme.colors.outline, w.dp, h.dp)
            val (soft, strong) = WidgetBitmaps.bars(context, values, max, w, h, highlight = values.lastIndex)
            Mask(soft, metric.accent.provider(), w.dp, h.dp)
            Mask(strong, metric.accent.provider(), w.dp, h.dp)
          }
        }
        Spacer(GlanceModifier.height(3.dp))
        if (days <= 7) {
          Row(GlanceModifier.fillMaxWidth()) {
            series.forEach { (date, _) ->
              Text(
                Format.weekdayShort(date),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = if (date == s.today) FontWeight.Bold else FontWeight.Normal),
              )
            }
          }
        } else {
          Row(GlanceModifier.fillMaxWidth()) {
            WidgetSmall(Format.dayMonth(series.first().first))
            Spacer(GlanceModifier.defaultWeight())
            WidgetSmall("dnes")
          }
        }
      }
    }
  }

  /** Sums: average of finished days ("Ø 8 400 / den"); levels: average of the period. */
  private fun trendSummary(metric: WidgetMetric, series: List<Pair<java.time.LocalDate, Double?>>, s: WidgetSnapshot, withRange: Boolean): String? {
    // Today's sleep is complete (last night); other daily totals are still growing.
    val counted = if (metric.isLevel || metric == WidgetMetric.SLEEP) series else series.filter { it.first != s.today }
    val values = counted.mapNotNull { it.second }
    if (values.isEmpty()) return null
    val avg = values.average()
    val unit = metric.unit(avg)?.let { " $it" } ?: ""
    // Line charts have no axis: the range tells how big the swings really are.
    val range = if (withRange && values.size > 1) "${metric.format(values.min())}–${metric.format(values.max())}$unit · " else ""
    return if (metric.isLevel) (if (range.isEmpty()) "Ø ${metric.format(avg)}$unit" else "${range}Ø ${metric.format(avg)}") else "Ø ${metric.format(avg)}$unit / ${if (metric == WidgetMetric.SLEEP) "noc" else "den"}"
  }
}

class StepsTrendWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = StepsTrendWidget()
}

// ---------------------------------------------------------------- Kalendář cíle (4×2, configurable)

class CalendarWidget : HealthWidget() {
  companion object {
    val METRIC = Slot("metric", "Cíl", WidgetMetric.withGoal, WidgetMetric.STEPS)
    val config = WidgetConfig(listOf(METRIC))
    const val WEEKS = 5
    private val weekdays = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metric = chosen(METRIC) ?: WidgetMetric.STEPS
    val goal = s.goal(metric) ?: 1.0
    val weeks = calendarWeeks(s.today, WEEKS)
    val values = s.series(metric, WidgetData.DAYS.toInt()).toMap()
    val met = weeks.flatten().count { d -> d <= s.today && (values[d] ?: 0.0) >= goal }
    WidgetFrame(openApp(context, metric.destination())) {
      Column(GlanceModifier.fillMaxSize()) {
        val label = if (size.width >= 250.dp) "${metric.shortLabel} · cíl ${s.goalText(metric)}" else metric.shortLabel
        HeaderRow(label, metric, "splněno ${met}×")
        Spacer(GlanceModifier.height(6.dp))
        val gap = 3f
        val cellW = ((size.width - 24.dp).value - 6 * gap) / 7
        val cellH = ((size.height - 24.dp - 20.dp - 6.dp - 14.dp).value - WEEKS * gap) / WEEKS
        val cell = minOf(cellW, cellH).coerceIn(8f, 30f)
        val step = (cell + gap).dp
        Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Row {
            weekdays.forEach { day ->
              Text(day, modifier = GlanceModifier.width(step), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
          }
          Spacer(GlanceModifier.height(2.dp))
          weeks.forEach { week ->
            Row {
              week.forEach { date ->
                Box(GlanceModifier.width(step).height(step).padding(end = gap.dp, bottom = gap.dp), contentAlignment = Alignment.Center) {
                  if (date <= s.today) {
                    val ratio = values[date]?.let { it / goal } ?: 0.0
                    Box(GlanceModifier.fillMaxSize().cornerRadius((cell / 4).dp).background(metric.accent.provider(calendarAlpha(ratio))), contentAlignment = Alignment.Center) {
                      if (date == s.today) Box(GlanceModifier.size((cell * 0.3f).dp).cornerRadius((cell * 0.15f).dp).background(GlanceTheme.colors.onSurface)) {}
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

// ---------------------------------------------------------------- Spánek (4×2)

class SleepWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.SLEEP)) {
      val night = s.sleep
      if (night == null) {
        NoDataYet()
        return@WidgetFrame
      }
      val start = Instant.ofEpochMilli(night.start)
      val end = Instant.ofEpochMilli(night.end)
      Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          WidgetLabel("Spánek", R.drawable.ic_moon, WidgetColors.sleep)
          Spacer(GlanceModifier.defaultWeight())
          WidgetSmall(if (s.sleepIsLastNight) "${Format.time(start)} – ${Format.time(end)}" else "${Format.dayAndTime(start)} – ${Format.time(end)}")
        }
        Spacer(GlanceModifier.height(4.dp))
        WidgetValue(Format.duration(night.asleepMinutes), size = 22.sp, color = WidgetColors.sleep)
        Spacer(GlanceModifier.height(8.dp))
        val barWidth = (size.width - 24.dp).orZero
        val stages = night.stageList()
        if (stages.isNotEmpty() && size.height >= 150.dp) {
          // Taller widget: the night in time order (one mask per stage color; a Glance Row renders at most
          // 10 children, and a night has dozens of stage segments).
          val w = barWidth.value.toInt().coerceAtLeast(1)
          val masks = WidgetBitmaps.hypnogram(context, night.start, night.end, stages.map { stageAccent(it.type) to (it.start.toEpochMilli() to it.end.toEpochMilli()) }, w, 22)
          Box(GlanceModifier.width(barWidth).height(22.dp)) {
            masks.forEach { (accent, bitmap) -> Mask(bitmap, accent.provider(), barWidth, 22.dp) }
          }
        } else {
          StageBar(
            listOf(
              night.deepMinutes to Accent.STAGE_DEEP.provider(),
              (night.lightMinutes + night.unspecifiedMinutes) to Accent.STAGE_LIGHT.provider(),
              night.remMinutes to Accent.STAGE_REM.provider(),
              night.awakeMinutes to Accent.STAGE_AWAKE.provider(),
            ),
            barWidth,
          )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
          Legend("Hluboký", night.deepMinutes, GlanceModifier.defaultWeight())
          Legend("Lehký", night.lightMinutes + night.unspecifiedMinutes, GlanceModifier.defaultWeight())
          Legend("REM", night.remMinutes, GlanceModifier.defaultWeight())
          Legend("Bdění", night.awakeMinutes, GlanceModifier.defaultWeight())
        }
      }
    }
  }

  private fun stageAccent(type: Int): Accent =
    when (type) {
      SleepSessionRecord.STAGE_TYPE_DEEP -> Accent.STAGE_DEEP
      SleepSessionRecord.STAGE_TYPE_REM -> Accent.STAGE_REM
      SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> Accent.STAGE_LIGHT
      else -> Accent.STAGE_AWAKE
    }

  @Composable
  private fun Legend(name: String, minutes: Long, modifier: GlanceModifier) {
    Column(modifier) {
      Text(name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
      Text(Format.duration(minutes), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
  }
}

/** Proportional bar; Glance has no weighted children, so widths are computed from the widget size. */
@Composable
private fun StageBar(parts: List<Pair<Long, ColorProvider>>, width: Dp) {
  val total = parts.sumOf { it.first }.coerceAtLeast(1)
  Row(GlanceModifier.width(width).height(10.dp).cornerRadius(5.dp)) {
    parts.filter { it.first > 0 }.forEach { (minutes, color) ->
      val w = (width.value * minutes / total).dp
      if (w >= 1.dp) Box(GlanceModifier.width(w).fillMaxHeight().background(color)) {}
    }
  }
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}

// ---------------------------------------------------------------- Srdce (2×2)

class HeartWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.HEART)) {
      Column(GlanceModifier.fillMaxSize()) {
        WidgetLabel("Srdce", R.drawable.ic_heart, WidgetColors.heart)
        Spacer(GlanceModifier.height(4.dp))
        WidgetValue(s.restingHr?.let { Format.int(it.second) } ?: "–", "bpm v klidu")
        val week = s.days(Metrics.RESTING_HR, 7).map { it.second }
        if (week.count { it != null } > 1) {
          val w = (size.width - 24.dp).orZero.value.toInt().coerceAtLeast(40)
          val h = (size.height - 104.dp).value.coerceIn(18f, 96f).toInt()
          Spacer(GlanceModifier.height(4.dp))
          Mask(WidgetBitmaps.line(context, week, w, h), WidgetColors.heart, w.dp, h.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        val min = s.hrMin
        val max = s.hrMax
        if (min != null && max != null) WidgetSmall("dnes ${Format.int(min)}–${Format.int(max)} bpm")
        s.lastHourHr?.let { (hour, bpm) -> WidgetSmall("${Format.time(hour)} Ø ${Format.int(bpm)} bpm") }
      }
    }
  }
}

class HeartWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = HeartWidget()
}

// ---------------------------------------------------------------- Tep dnes (4×2)

class HeartDayWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val hours = heartHours(s.heartHours)
    WidgetFrame(openApp(context, Destinations.HEART)) {
      if (hours.isEmpty()) {
        NoDataYet("Dnes zatím bez tepu")
        return@WidgetFrame
      }
      Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          WidgetLabel("Tep · dnes", R.drawable.ic_heart, WidgetColors.heart)
          Spacer(GlanceModifier.defaultWeight())
          RefreshStamp(s.lastSyncAt)
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
          val last = s.lastHourHr
          WidgetValue(last?.let { Format.int(it.second) } ?: "–", "bpm", size = 20.sp)
          Spacer(GlanceModifier.width(6.dp))
          last?.let { WidgetSmall("Ø od ${Format.time(it.first)}", GlanceModifier.padding(bottom = 3.dp)) }
          Spacer(GlanceModifier.defaultWeight())
          val range = listOfNotNull(s.hrMin, s.hrMax)
          val rest = s.restingHr?.takeIf { it.first == s.today }?.second
          WidgetSmall(
            listOfNotNull(
              if (range.size == 2) "${Format.int(range[0])}–${Format.int(range[1])}" else null,
              rest?.let { "klid ${Format.int(it)}" },
            ).joinToString(" · "),
            GlanceModifier.padding(bottom = 3.dp),
          )
        }
        Spacer(GlanceModifier.height(6.dp))
        val w = (size.width - 24.dp).orZero.value.toInt().coerceAtLeast(48)
        val h = (size.height - 98.dp).value.coerceIn(30f, 150f).toInt()
        val (low, high) = heartScale(hours)
        val (ranges, avg) = WidgetBitmaps.heartDay(context, hours, low, high, w, h)
        Box(GlanceModifier.width(w.dp).height(h.dp)) {
          Mask(ranges, WidgetColors.heart, w.dp, h.dp)
          Mask(avg, WidgetColors.heart, w.dp, h.dp)
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(GlanceModifier.width(w.dp)) {
          listOf("0", "6", "12", "18").forEach { WidgetSmall(it, GlanceModifier.defaultWeight()) }
        }
      }
    }
  }
}

class HeartDayWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = HeartDayWidget()
}

// ---------------------------------------------------------------- Jedna hodnota (2×1, configurable)

class SingleValueWidget : HealthWidget() {
  companion object {
    val METRIC = Slot("metric", "Hodnota", WidgetMetric.pickerOrder, WidgetMetric.STEPS)
    val config = WidgetConfig(listOf(METRIC))
  }

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    val metric = chosen(METRIC) ?: WidgetMetric.STEPS
    val r = s.read(metric)
    val big = size.height >= 100.dp
    WidgetFrame(openApp(context, r.destination), padding = 10.dp) {
      Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        MetricIcon(metric, if (big) 28.dp else 22.dp)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
          WidgetValue(r.value, r.unit, size = if (big) 30.sp else 20.sp)
          WidgetSmall(listOfNotNull(metric.shortLabel, r.note).joinToString(" · "))
        }
      }
    }
  }
}

class SingleValueWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SingleValueWidget()
}

// ---------------------------------------------------------------- Poslední aktivita (4×1)

class LastWorkoutWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val w = s.workout
    WidgetFrame(if (w != null) openApp(context, Destinations.workout(w.id)) else openApp(context, Destinations.ACTIVITIES), padding = 10.dp) {
      if (w == null) {
        NoDataYet()
        return@WidgetFrame
      }
      Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(40.dp).cornerRadius(14.dp).background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
          Image(ImageProvider(R.drawable.ic_run), contentDescription = null, modifier = GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
          val seconds = (w.end - w.start) / 1000
          Text(
            buildString {
              append(w.title?.takeIf { it.isNotBlank() } ?: Format.exerciseName(w.exerciseType))
              w.distanceMeters?.takeIf { it >= 100 }?.let { append(" · ${Format.km(it)} km") }
              append(" · ${Format.clock(seconds)}")
            },
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
          )
          WidgetSmall(
            buildString {
              append(Format.dayAndTime(Instant.ofEpochMilli(w.start)))
              w.avgHeartRate?.let { append(" · Ø $it bpm") }
              w.activeKcal?.let { append(" · ${Format.int(it)} kcal") }
            }
          )
        }
      }
    }
  }
}

class LastWorkoutWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = LastWorkoutWidget()
}
