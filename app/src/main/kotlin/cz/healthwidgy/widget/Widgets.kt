package cz.healthwidgy.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.LocalState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.healthwidgy.R
import cz.healthwidgy.data.history.stageList
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.ui.Format
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Common plumbing: load the cached snapshot, reload it when [WidgetUpdater] signals, render with Material You colors. */
abstract class HealthWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val initial = WidgetData.load(context)
    provideContent {
      val tick by WidgetUpdater.tick.collectAsState()
      val snapshot by produceState(initial, tick) { value = WidgetData.load(context) }
      GlanceTheme { Content(context, snapshot) }
    }
  }

  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val snapshot = WidgetData.load(context)
    provideContent { GlanceTheme { Content(context, snapshot) } }
  }

  @Composable abstract fun Content(context: Context, s: WidgetSnapshot)
}

private val Dp.orZero: Dp
  get() = if (value < 0f) 0.dp else this

// ---------------------------------------------------------------- A: Kroky (2×2)

class StepsWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.metric(Metrics.STEPS))) {
      val steps = s.steps ?: 0.0
      val ring = (minOf(size.width, size.height) - 46.dp).value.coerceIn(48f, 130f).toInt()
      Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
          Image(ImageProvider(WidgetBitmaps.ring(context, (steps / s.stepGoal).toFloat(), WidgetColors.STEPS_ARGB, ring, 9f)), contentDescription = null, modifier = GlanceModifier.size(ring.dp))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.int(steps), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (ring > 80) 18.sp else 14.sp, fontWeight = FontWeight.Bold))
            WidgetSmall("kroků")
          }
        }
        Spacer(GlanceModifier.height(4.dp))
        WidgetSmall("${(steps * 100 / s.stepGoal).roundToInt()} % z cíle ${Format.int(s.stepGoal.toLong())}")
      }
    }
  }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

// ---------------------------------------------------------------- B: Dnešní přehled (4×2)

class TodayWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.OVERVIEW)) {
      Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          WidgetLabel("Dnes")
          Spacer(GlanceModifier.defaultWeight())
          RefreshStamp(s.lastSyncAt)
        }
        Spacer(GlanceModifier.height(6.dp))
        val ring = (size.height - 52.dp).value.coerceIn(56f, 110f).toInt()
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
          Box(contentAlignment = Alignment.Center) {
            val steps = s.steps ?: 0.0
            Image(ImageProvider(WidgetBitmaps.ring(context, (steps / s.stepGoal).toFloat(), WidgetColors.STEPS_ARGB, ring, 9f)), contentDescription = null, modifier = GlanceModifier.size(ring.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(Format.int(steps), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
              WidgetSmall("kroků")
            }
          }
          Spacer(GlanceModifier.width(12.dp))
          Column(GlanceModifier.defaultWeight()) {
            Row(GlanceModifier.fillMaxWidth()) {
              MiniStat(R.drawable.ic_flame, WidgetColors.calories, s.totalKcal?.let { Format.int(it) } ?: "–", "kcal", GlanceModifier.defaultWeight())
              MiniStat(R.drawable.ic_route, WidgetColors.steps, s.distanceMeters?.let { Format.km(it) } ?: "–", "km", GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
              MiniStat(R.drawable.ic_stairs, WidgetColors.body, s.floors?.let { Format.int(it) } ?: "–", "pater", GlanceModifier.defaultWeight())
              MiniStat(R.drawable.ic_heart, WidgetColors.heart, s.restingHr?.let { Format.int(it) } ?: "–", "klid. tep", GlanceModifier.defaultWeight())
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MiniStat(icon: Int, color: ColorProvider, value: String, label: String, modifier: GlanceModifier) {
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(13.dp), colorFilter = androidx.glance.ColorFilter.tint(color))
      Spacer(GlanceModifier.width(4.dp))
      Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
    WidgetSmall(label)
  }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---------------------------------------------------------------- C: Spánek (4×2)

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
          // Taller widget: the night in time order, drawn as one bitmap (a Glance Row renders at most
          // 10 children, and a night has dozens of stage segments).
          val night24 = WidgetBitmaps.hypnogram(
            context,
            night.start,
            night.end,
            stages.map { it.type to (it.start.toEpochMilli() to it.end.toEpochMilli()) },
            barWidth.value.toInt().coerceAtLeast(1),
            22,
          )
          Image(ImageProvider(night24), contentDescription = null, modifier = GlanceModifier.width(barWidth).height(22.dp))
        } else {
          StageBar(
            listOf(
              night.deepMinutes to WidgetColors.stageDeep,
              (night.lightMinutes + night.unspecifiedMinutes) to WidgetColors.stageLight,
              night.remMinutes to WidgetColors.stageRem,
              night.awakeMinutes to WidgetColors.stageAwake,
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

// ---------------------------------------------------------------- D: Srdce (2×2)

class HeartWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.HEART)) {
      Column(GlanceModifier.fillMaxSize()) {
        WidgetLabel("Srdce", R.drawable.ic_heart, WidgetColors.heart)
        Spacer(GlanceModifier.height(4.dp))
        WidgetValue(s.restingHr?.let { Format.int(it) } ?: "–", "bpm v klidu")
        val line = s.restingLast7.count { it != null } > 1
        if (line) {
          val w = (size.width - 24.dp).orZero.value.toInt().coerceAtLeast(40)
          val h = (size.height - 104.dp).value.coerceIn(18f, 96f).toInt()
          Spacer(GlanceModifier.height(4.dp))
          Image(ImageProvider(WidgetBitmaps.sparkline(context, s.restingLast7, WidgetColors.HEART_ARGB, w, h)), contentDescription = null, modifier = GlanceModifier.width(w.dp).height(h.dp))
        }
        Spacer(GlanceModifier.defaultWeight())
        if (s.hrMin != null && s.hrMax != null) WidgetSmall("dnes ${Format.int(s.hrMin)}–${Format.int(s.hrMax)} bpm")
        s.lastHourHr?.let { (hour, bpm) -> WidgetSmall("${Format.time(hour)} Ø ${Format.int(bpm)} bpm") }
      }
    }
  }
}

class HeartWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = HeartWidget()
}

// ---------------------------------------------------------------- E: Kroky 7 dní (4×2)

class StepsTrendWidget : HealthWidget() {
  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    val size = LocalSize.current
    WidgetFrame(openApp(context, Destinations.metric(Metrics.STEPS))) {
      Column(GlanceModifier.fillMaxSize()) {
        val finished = s.steps7.filter { it.first != s.today }.mapNotNull { it.second }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          WidgetLabel("Kroky · 7 dní", R.drawable.ic_walk, WidgetColors.steps)
          Spacer(GlanceModifier.defaultWeight())
          if (finished.isNotEmpty()) WidgetSmall("Ø ${Format.int(finished.average())} / den")
        }
        Spacer(GlanceModifier.height(6.dp))
        val chartHeight = (size.height - 64.dp).value.coerceIn(28f, 160f)
        val max = (s.steps7.mapNotNull { it.second } + s.stepGoal.toDouble()).max()
        Box(GlanceModifier.fillMaxWidth().height(chartHeight.dp)) {
          // Goal line behind the bars.
          val goalTop = (chartHeight * (1 - s.stepGoal / max)).toFloat().coerceIn(0f, chartHeight - 1f)
          Column(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.height(goalTop.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
          }
          Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            s.steps7.forEach { (date, value) ->
              Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                val h = ((value ?: 0.0) / max * chartHeight).toFloat().coerceAtLeast(2f)
                val today = date == s.today
                Box(
                  GlanceModifier.width(18.dp).height(h.dp).cornerRadius(4.dp)
                    .background(if (today) WidgetColors.steps else ColorProvider(day = Color(0x80239163), night = Color(0x804CC48D)))
                ) {}
              }
            }
          }
        }
        Spacer(GlanceModifier.height(3.dp))
        Row(GlanceModifier.fillMaxWidth()) {
          s.steps7.forEach { (date, _) ->
            Text(
              Format.weekdayShort(date),
              modifier = GlanceModifier.defaultWeight(),
              style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = if (date == s.today) FontWeight.Bold else FontWeight.Normal),
            )
          }
        }
      }
    }
  }
}

class StepsTrendWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = StepsTrendWidget()
}

// ---------------------------------------------------------------- G: Poslední aktivita (4×1)

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
          Image(ImageProvider(R.drawable.ic_run), contentDescription = null, modifier = GlanceModifier.size(22.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
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

// ---------------------------------------------------------------- F: Jedna hodnota (2×1, configurable)

/** Metrics the single-value widget can show (id stored per widget instance). */
enum class SingleMetric(val label: String) {
  STEPS("Kroky"),
  CALORIES("Kalorie"),
  DISTANCE("Vzdálenost"),
  FLOORS("Patra"),
  RESTING_HR("Klidový tep"),
  SLEEP("Spánek"),
  WEIGHT("Váha"),
}

class SingleValueWidget : HealthWidget() {
  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  @Composable
  override fun Content(context: Context, s: WidgetSnapshot) {
    // No state exists while the system renders the picker preview: fall back to steps then.
    val prefs = LocalState.current as? Preferences
    val metric = prefs?.get(METRIC_KEY)?.let { runCatching { SingleMetric.valueOf(it) }.getOrNull() } ?: SingleMetric.STEPS
    val v = describe(metric, s)
    WidgetFrame(openApp(context, v.destination), padding = 10.dp) {
      Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(v.icon), contentDescription = null, modifier = GlanceModifier.size(22.dp), colorFilter = androidx.glance.ColorFilter.tint(v.color))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
          WidgetValue(v.value, v.unit, size = 20.sp)
          WidgetSmall(v.caption)
        }
      }
    }
  }

  private data class Shown(val value: String, val unit: String?, val caption: String, val icon: Int, val color: ColorProvider, val destination: String)

  private fun describe(metric: SingleMetric, s: WidgetSnapshot): Shown {
    fun delta(values: List<Double?>, today: Double?): String {
      val past = values.dropLast(1).filterNotNull()
      if (today == null || past.isEmpty()) return ""
      val avg = past.average()
      return if (today >= avg) " ▲" else " ▼"
    }
    return when (metric) {
      SingleMetric.STEPS -> Shown(Format.int(s.steps ?: 0.0), null, "Kroky · ${((s.steps ?: 0.0) * 100 / s.stepGoal.coerceAtLeast(1)).roundToInt()} % cíle", R.drawable.ic_walk, WidgetColors.steps, Destinations.metric(Metrics.STEPS))
      SingleMetric.CALORIES -> Shown(s.totalKcal?.let { Format.int(it) } ?: "–", "kcal", "Kalorie dnes", R.drawable.ic_flame, WidgetColors.calories, Destinations.metric(Metrics.TOTAL_KCAL))
      SingleMetric.DISTANCE -> Shown(s.distanceMeters?.let { Format.km(it) } ?: "–", "km", "Vzdálenost dnes", R.drawable.ic_route, WidgetColors.steps, Destinations.metric(Metrics.DISTANCE))
      SingleMetric.FLOORS -> Shown(s.floors?.let { Format.int(it) } ?: "–", null, "Patra dnes", R.drawable.ic_stairs, WidgetColors.body, Destinations.metric(Metrics.FLOORS))
      SingleMetric.RESTING_HR -> Shown(s.restingHr?.let { Format.int(it) } ?: "–", "bpm", "Klidový tep" + delta(s.restingLast7, s.restingLast7.lastOrNull()), R.drawable.ic_heart, WidgetColors.heart, Destinations.HEART)
      SingleMetric.SLEEP -> Shown(
        s.sleep?.asleepMinutes?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "–",
        "h",
        if (s.sleepIsLastNight) "Spánek v noci" else "Poslední spánek",
        R.drawable.ic_moon,
        WidgetColors.sleep,
        Destinations.SLEEP,
      )
      SingleMetric.WEIGHT -> Shown(s.weight?.let { Format.decimal(it.value) } ?: "–", "kg", s.weight?.let { "Váha · ${Format.dayAndTime(Instant.ofEpochMilli(it.time)).substringBeforeLast(' ')}" } ?: "Váha", R.drawable.ic_scale, WidgetColors.body, Destinations.BODY)
    }
  }

  companion object {
    val METRIC_KEY = stringPreferencesKey("metric")
  }
}

class SingleValueWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = SingleValueWidget()
}

