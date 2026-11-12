package cz.healthwidgy.ui.theme

import android.app.UiModeManager
import android.content.Context
import cz.healthwidgy.data.settings.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = primaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    tertiary = tertiaryDark,
    background = backgroundDark,
    surface = backgroundDark,
    surfaceContainer = surfaceContainerDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = primaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    tertiary = tertiaryLight,
    background = backgroundLight,
    surface = backgroundLight,
    surfaceContainer = surfaceContainerLight,
  )

/**
 * Light/dark choice for the app (Settings → Vzhled). The system keeps it per app and applies it to the
 * app's configuration, so isSystemInDarkTheme(), the window background and the status bar follow it.
 */
fun applyAppTheme(context: Context, mode: ThemeMode) {
  val night =
    when (mode) {
      ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
      ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
      ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
  context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(night)
}

/** Material 3 with colors taken from the wallpaper (minSdk 34 always supports dynamic color). */
@Composable
fun HealthWidgyTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  CompositionLocalProvider(LocalMetricColors provides if (darkTheme) DarkMetricColors else LightMetricColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
