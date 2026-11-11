package cz.healthwidgy.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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

/** Metric accents in widgets: same hues as in the app, lighter variants at night. */
object WidgetColors {
  val steps = ColorProvider(day = Color(0xFF239163), night = Color(0xFF4CC48D))
  val heart = ColorProvider(day = Color(0xFFD93A49), night = Color(0xFFFF6B78))
  val sleep = ColorProvider(day = Color(0xFF6152D9), night = Color(0xFF9A8CFF))
  val calories = ColorProvider(day = Color(0xFFE07421), night = Color(0xFFFF9C4F))
  val body = ColorProvider(day = Color(0xFF64748B), night = Color(0xFF9AA8BC))
  val workout = ColorProvider(day = Color(0xFF2D5BE3), night = Color(0xFFA8BCFF))
  val stageDeep = ColorProvider(day = Color(0xFF2C3E9E), night = Color(0xFF4257C9))
  val stageLight = ColorProvider(day = Color(0xFF5B8DEF), night = Color(0xFF6E9CFF))
  val stageRem = ColorProvider(day = Color(0xFF8E7CF0), night = Color(0xFFA796FF))
  val stageAwake = ColorProvider(day = Color(0xFFE59A2F), night = Color(0xFFF2B45A))

  // Bitmaps cannot follow day/night; these mid tones read on both backgrounds.
  const val STEPS_ARGB = 0xFF2FA872.toInt()
  const val HEART_ARGB = 0xFFE5505D.toInt()
  const val BODY_ARGB = 0xFF7D8BA0.toInt()
}

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
      Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(14.dp), colorFilter = iconColor?.let { androidx.glance.ColorFilter.tint(it) })
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
fun WidgetSmall(text: String) {
  Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
}

/** "↻ 9:49" in the corner: tap refreshes from Health Connect. */
@Composable
fun RefreshStamp(lastSyncAt: Instant?) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>())) {
    Image(ImageProvider(R.drawable.ic_sync), contentDescription = "Obnovit", modifier = GlanceModifier.size(12.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
    Spacer(GlanceModifier.width(3.dp))
    WidgetSmall(lastSyncAt?.let { Format.time(it) } ?: "–")
  }
}

/** Empty state when the cache has nothing for today yet. */
@Composable
fun NoDataYet() {
  Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Zatím bez dat", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    WidgetSmall("Otevři aplikaci HealthWidgy")
  }
}
