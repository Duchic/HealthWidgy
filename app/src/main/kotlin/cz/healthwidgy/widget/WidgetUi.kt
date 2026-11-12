package cz.healthwidgy.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.healthwidgy.MainActivity
import cz.healthwidgy.R
import cz.healthwidgy.ui.Format
import java.time.Instant

/** Where a tap on a widget leads inside the app (handled by MainActivity). */
object Destinations {
  const val EXTRA = "cz.healthwidgy.OPEN"
  const val OVERVIEW = "overview"
  const val HEART = "heart"
  const val SLEEP = "sleep"
  const val BODY = "body"
  const val ACTIVITIES = "activities"
  fun metric(id: String) = "metric:$id"
  fun workout(id: String) = "workout:$id"
}

fun openApp(context: Context, destination: String): Action =
  actionStartActivity(
    Intent(context, MainActivity::class.java)
      .putExtra(Destinations.EXTRA, destination)
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      // Distinct data per destination so PendingIntents of different widgets do not overwrite each other.
      .setData(android.net.Uri.parse("healthwidgy://open/$destination"))
  )

/** Rounded widget surface following the wallpaper colors (Material You), tappable as a whole. */
@Composable
fun WidgetFrame(onClick: Action, padding: Dp = 12.dp, content: @Composable () -> Unit) {
  Box(
    GlanceModifier.fillMaxSize()
      .appWidgetBackground()
      .cornerRadius(22.dp)
      .background(GlanceTheme.colors.widgetBackground)
      .clickable(onClick)
      .padding(padding),
  ) { content() }
}

@Composable
fun WidgetLabel(text: String, icon: Int? = null, iconColor: ColorProvider? = null) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) {
      Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(14.dp), colorFilter = iconColor?.let { ColorFilter.tint(it) })
      Spacer(GlanceModifier.width(5.dp))
    }
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
  }
}

@Composable
fun WidgetValue(value: String, unit: String? = null, size: TextUnit = 22.sp, color: ColorProvider = GlanceTheme.colors.onSurface) {
  Row(verticalAlignment = Alignment.Bottom) {
    Text(value, style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold), maxLines = 1)
    if (unit != null) {
      Spacer(GlanceModifier.width(3.dp))
      Text(unit, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.padding(bottom = 3.dp))
    }
  }
}

@Composable
fun WidgetSmall(text: String, modifier: GlanceModifier = GlanceModifier) {
  Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1, modifier = modifier)
}

/** Metric icon in its accent color. */
@Composable
fun MetricIcon(metric: WidgetMetric, size: Dp = 14.dp) {
  Image(ImageProvider(metric.icon), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = ColorFilter.tint(metric.accent.provider()))
}

/** A white mask bitmap (see [WidgetBitmaps]) tinted with [color]. */
@Composable
fun Mask(bitmap: Bitmap, color: ColorProvider, width: Dp, height: Dp) {
  Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.width(width).height(height), colorFilter = ColorFilter.tint(color))
}

/**
 * Concentric progress rings (outermost first) with [content] in the middle. Each ring is its own mask
 * so it can carry its metric's color in light and dark mode.
 */
@Composable
fun Rings(context: Context, rings: List<Pair<Float, Accent>>, sizeDp: Int, strokeDp: Float, content: @Composable () -> Unit = {}) {
  Box(GlanceModifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
    Mask(WidgetBitmaps.ringTracks(context, sizeDp, strokeDp, rings.size), GlanceTheme.colors.onSurface, sizeDp.dp, sizeDp.dp)
    rings.forEachIndexed { i, (progress, accent) ->
      if (progress > 0f) Mask(WidgetBitmaps.ringArc(context, sizeDp, strokeDp, progress, i), accent.provider(), sizeDp.dp, sizeDp.dp)
    }
    content()
  }
}

/** "↻ 9:49" in the corner: tap refreshes from Health Connect. */
@Composable
fun RefreshStamp(lastSyncAt: Instant?) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>())) {
    Image(ImageProvider(R.drawable.ic_sync), contentDescription = "Obnovit", modifier = GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
    Spacer(GlanceModifier.width(3.dp))
    WidgetSmall(lastSyncAt?.let { Format.time(it) } ?: "–")
  }
}

/** Empty state when the cache has nothing for today yet. */
@Composable
fun NoDataYet(text: String = "Zatím bez dat") {
  Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    WidgetSmall("Otevři aplikaci HealthWidgy")
  }
}
