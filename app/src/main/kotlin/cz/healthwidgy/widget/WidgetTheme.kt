package cz.healthwidgy.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.unit.ColorProvider
import cz.healthwidgy.data.settings.ThemeMode

/**
 * Metric accents in widgets: the same hues as the app's MetricColors, lighter variants on dark
 * backgrounds. Stored as ARGB so they can be turned into fixed or day/night color providers.
 */
enum class Accent(val day: Long, val night: Long) {
  STEPS(0xFF239163, 0xFF4CC48D),
  DISTANCE(0xFF0E8F87, 0xFF4FD1C5),
  HEART(0xFFD93A49, 0xFFFF6B78),
  SLEEP(0xFF6152D9, 0xFF9A8CFF),
  CALORIES(0xFFE07421, 0xFFFF9C4F),
  BODY(0xFF64748B, 0xFF9AA8BC),
  WORKOUT(0xFF2D5BE3, 0xFFA8BCFF),
  STAGE_DEEP(0xFF2C3E9E, 0xFF4257C9),
  STAGE_LIGHT(0xFF5B8DEF, 0xFF6E9CFF),
  STAGE_REM(0xFF8E7CF0, 0xFFA796FF),
  STAGE_AWAKE(0xFFE59A2F, 0xFFF2B45A),
}

/** null: widgets follow the system's light/dark mode; true/false: forced dark/light (Settings → Vzhled). */
val LocalWidgetDark = compositionLocalOf<Boolean?> { null }

/** The accent as a color provider that follows [LocalWidgetDark]; [alpha] for softer fills. */
@Composable
fun Accent.provider(alpha: Float = 1f): ColorProvider {
  val d = Color(day).copy(alpha = alpha)
  val n = Color(night).copy(alpha = alpha)
  return when (LocalWidgetDark.current) {
    null -> ColorProvider(day = d, night = n)
    true -> ColorProvider(n)
    false -> ColorProvider(d)
  }
}

/** Shorthands used across the widgets. */
object WidgetColors {
  val steps: ColorProvider @Composable get() = Accent.STEPS.provider()
  val distance: ColorProvider @Composable get() = Accent.DISTANCE.provider()
  val heart: ColorProvider @Composable get() = Accent.HEART.provider()
  val sleep: ColorProvider @Composable get() = Accent.SLEEP.provider()
  val calories: ColorProvider @Composable get() = Accent.CALORIES.provider()
  val body: ColorProvider @Composable get() = Accent.BODY.provider()
  val workout: ColorProvider @Composable get() = Accent.WORKOUT.provider()
}

/**
 * Material You colors for widgets. With [ThemeMode.SYSTEM] they are Glance's dynamic day/night resources,
 * so the launcher swaps them when dark mode toggles. A forced mode uses the same system palette tones
 * Glance picks for day or night, as framework color resources: they cannot flip with the night mode,
 * but the launcher still resolves them on every redraw, so they follow a new wallpaper right away.
 */
@Composable
fun WidgetTheme(mode: ThemeMode, content: @Composable () -> Unit) {
  val dark =
    when (mode) {
      ThemeMode.SYSTEM -> null
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
  // Outside any GlanceTheme this is the system's dynamic palette.
  val colors = if (dark == null) GlanceTheme.colors else if (dark) DarkWidgetColors else LightWidgetColors
  CompositionLocalProvider(LocalWidgetDark provides dark) { GlanceTheme(colors = colors, content = content) }
}

private fun res(id: Int): ColorProvider = ColorProvider(id)

private fun argb(value: Long): ColorProvider = ColorProvider(Color(value))

/** Glance's day mapping of the system palette (glance_color* in values-v31). */
private val LightWidgetColors: ColorProviders =
  colorProviders(
    primary = res(android.R.color.system_accent1_600),
    onPrimary = res(android.R.color.system_accent1_0),
    primaryContainer = res(android.R.color.system_accent1_100),
    onPrimaryContainer = res(android.R.color.system_accent1_900),
    secondary = res(android.R.color.system_accent2_600),
    onSecondary = res(android.R.color.system_accent2_0),
    secondaryContainer = res(android.R.color.system_accent2_100),
    onSecondaryContainer = res(android.R.color.system_accent2_900),
    tertiary = res(android.R.color.system_accent3_600),
    onTertiary = res(android.R.color.system_accent3_0),
    tertiaryContainer = res(android.R.color.system_accent3_100),
    onTertiaryContainer = res(android.R.color.system_accent3_900),
    error = argb(0xFFB3261E),
    errorContainer = argb(0xFFF9DEDC),
    onError = argb(0xFFFFFFFF),
    onErrorContainer = argb(0xFF410E0B),
    background = res(android.R.color.system_neutral1_10),
    onBackground = res(android.R.color.system_neutral1_900),
    surface = res(android.R.color.system_neutral1_10),
    onSurface = res(android.R.color.system_neutral1_900),
    surfaceVariant = res(android.R.color.system_neutral2_100),
    onSurfaceVariant = res(android.R.color.system_neutral2_700),
    outline = res(android.R.color.system_neutral2_500),
    inverseOnSurface = res(android.R.color.system_neutral1_50),
    inverseSurface = res(android.R.color.system_neutral1_800),
    inversePrimary = res(android.R.color.system_accent1_200),
    widgetBackground = res(android.R.color.system_accent2_50),
  )

/** Glance's night mapping of the system palette (glance_color* in values-night-v31). */
private val DarkWidgetColors: ColorProviders =
  colorProviders(
    primary = res(android.R.color.system_accent1_200),
    onPrimary = res(android.R.color.system_accent1_800),
    primaryContainer = res(android.R.color.system_accent1_700),
    onPrimaryContainer = res(android.R.color.system_accent1_100),
    secondary = res(android.R.color.system_accent2_200),
    onSecondary = res(android.R.color.system_accent2_800),
    secondaryContainer = res(android.R.color.system_accent2_700),
    onSecondaryContainer = res(android.R.color.system_accent2_100),
    tertiary = res(android.R.color.system_accent3_200),
    onTertiary = res(android.R.color.system_accent3_800),
    tertiaryContainer = res(android.R.color.system_accent3_700),
    onTertiaryContainer = res(android.R.color.system_accent3_100),
    error = argb(0xFFF2B8B5),
    errorContainer = argb(0xFF8C1D18),
    onError = argb(0xFF601410),
    onErrorContainer = argb(0xFFF2B8B5),
    background = res(android.R.color.system_neutral1_900),
    onBackground = res(android.R.color.system_neutral1_100),
    surface = res(android.R.color.system_neutral1_900),
    onSurface = res(android.R.color.system_neutral1_100),
    surfaceVariant = res(android.R.color.system_neutral2_700),
    onSurfaceVariant = res(android.R.color.system_neutral2_200),
    outline = res(android.R.color.system_neutral2_400),
    inverseOnSurface = res(android.R.color.system_neutral1_800),
    inverseSurface = res(android.R.color.system_neutral1_100),
    inversePrimary = res(android.R.color.system_accent1_600),
    widgetBackground = res(android.R.color.system_accent2_800),
  )
