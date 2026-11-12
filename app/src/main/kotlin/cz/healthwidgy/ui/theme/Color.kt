package cz.healthwidgy.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fallback scheme when dynamic color is off (Material Theme Builder, seed #2D5BE3).
val primaryLight = Color(0xFF2D5BE3)
val primaryContainerLight = Color(0xFFDCE4FF)
val onPrimaryContainerLight = Color(0xFF0E2A7A)
val secondaryLight = Color(0xFF566173)
val tertiaryLight = Color(0xFF0E9C8F)
val backgroundLight = Color(0xFFF6F8FB)
val surfaceContainerLight = Color(0xFFEBEFF5)

val primaryDark = Color(0xFFA8BCFF)
val primaryContainerDark = Color(0xFF26386F)
val onPrimaryContainerDark = Color(0xFFDCE4FF)
val secondaryDark = Color(0xFF9AA4B5)
val tertiaryDark = Color(0xFF3FD0C1)
val backgroundDark = Color(0xFF0F1319)
val surfaceContainerDark = Color(0xFF1A2029)

/** Fixed accents per metric (same hues in the app and in widgets); they do not follow the wallpaper. */
@Immutable
data class MetricColors(
  val steps: Color,
  val distance: Color,
  val heart: Color,
  val sleep: Color,
  val calories: Color,
  val body: Color,
  val workout: Color,
  val stageAwake: Color,
  val stageRem: Color,
  val stageLight: Color,
  val stageDeep: Color,
)

val LightMetricColors =
  MetricColors(
    steps = Color(0xFF239163),
    distance = Color(0xFF0E8F87),
    heart = Color(0xFFD93A49),
    sleep = Color(0xFF6152D9),
    calories = Color(0xFFE07421),
    body = Color(0xFF64748B),
    workout = Color(0xFF2D5BE3),
    stageAwake = Color(0xFFE59A2F),
    stageRem = Color(0xFF8E7CF0),
    stageLight = Color(0xFF5B8DEF),
    stageDeep = Color(0xFF2C3E9E),
  )

val DarkMetricColors =
  MetricColors(
    steps = Color(0xFF4CC48D),
    distance = Color(0xFF4FD1C5),
    heart = Color(0xFFFF6B78),
    sleep = Color(0xFF9A8CFF),
    calories = Color(0xFFFF9C4F),
    body = Color(0xFF9AA8BC),
    workout = Color(0xFFA8BCFF),
    stageAwake = Color(0xFFF2B45A),
    stageRem = Color(0xFFA796FF),
    stageLight = Color(0xFF6E9CFF),
    stageDeep = Color(0xFF4257C9),
  )

val LocalMetricColors = staticCompositionLocalOf { LightMetricColors }
