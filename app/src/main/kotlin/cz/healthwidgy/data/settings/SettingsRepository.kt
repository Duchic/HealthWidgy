package cz.healthwidgy.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which Health Connect data the app shows. */
enum class SourceMode {
  /** Only records written by Garmin Connect (default: avoids mixing in the phone's own step counter). */
  GARMIN_ONLY,
  /** Everything in Health Connect, deduplicated by Health Connect's app priority where it supports that. */
  ALL_SOURCES,
}

/** Light or dark look, for the app and (separately) for the widgets. */
enum class ThemeMode(val label: String) {
  SYSTEM("Systém"),
  LIGHT("Světlý"),
  DARK("Tmavý"),
}

data class Settings(
  val stepGoal: Int = DEFAULT_STEP_GOAL,
  val floorsGoal: Int = DEFAULT_FLOORS_GOAL,
  /** Daily distance goal in kilometres. */
  val distanceGoalKm: Int = DEFAULT_DISTANCE_GOAL_KM,
  val sleepGoalMinutes: Int = DEFAULT_SLEEP_GOAL_MINUTES,
  val sourceMode: SourceMode = SourceMode.GARMIN_ONLY,
  val appTheme: ThemeMode = ThemeMode.SYSTEM,
  val widgetTheme: ThemeMode = ThemeMode.SYSTEM,
) {
  companion object {
    const val DEFAULT_STEP_GOAL = 10_000
    const val DEFAULT_FLOORS_GOAL = 10
    const val DEFAULT_DISTANCE_GOAL_KM = 8
    const val DEFAULT_SLEEP_GOAL_MINUTES = 480
    val STEP_GOAL_OPTIONS = listOf(6_000, 8_000, 10_000, 12_000, 15_000)
    val FLOORS_GOAL_OPTIONS = listOf(5, 10, 20, 30, 50)
    val DISTANCE_GOAL_OPTIONS = listOf(3, 5, 8, 10, 15)
    val SLEEP_GOAL_OPTIONS = listOf(420, 450, 480, 510, 540)
  }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
  private val stepGoalKey = intPreferencesKey("step_goal")
  private val floorsGoalKey = intPreferencesKey("floors_goal")
  private val distanceGoalKey = intPreferencesKey("distance_goal_km")
  private val sleepGoalKey = intPreferencesKey("sleep_goal_minutes")
  private val sourceModeKey = stringPreferencesKey("source_mode")
  private val appThemeKey = stringPreferencesKey("app_theme")
  private val widgetThemeKey = stringPreferencesKey("widget_theme")

  val settings: Flow<Settings> =
    context.settingsStore.data.map { prefs ->
      Settings(
        stepGoal = prefs[stepGoalKey] ?: Settings.DEFAULT_STEP_GOAL,
        floorsGoal = prefs[floorsGoalKey] ?: Settings.DEFAULT_FLOORS_GOAL,
        distanceGoalKm = prefs[distanceGoalKey] ?: Settings.DEFAULT_DISTANCE_GOAL_KM,
        sleepGoalMinutes = prefs[sleepGoalKey] ?: Settings.DEFAULT_SLEEP_GOAL_MINUTES,
        sourceMode = prefs.enum(sourceModeKey, SourceMode.GARMIN_ONLY),
        appTheme = prefs.enum(appThemeKey, ThemeMode.SYSTEM),
        widgetTheme = prefs.enum(widgetThemeKey, ThemeMode.SYSTEM),
      )
    }

  suspend fun setStepGoal(goal: Int) = set(stepGoalKey, goal)

  suspend fun setFloorsGoal(goal: Int) = set(floorsGoalKey, goal)

  suspend fun setDistanceGoalKm(goal: Int) = set(distanceGoalKey, goal)

  suspend fun setSleepGoalMinutes(goal: Int) = set(sleepGoalKey, goal)

  suspend fun setSourceMode(mode: SourceMode) = set(sourceModeKey, mode.name)

  suspend fun setAppTheme(mode: ThemeMode) = set(appThemeKey, mode.name)

  suspend fun setWidgetTheme(mode: ThemeMode) = set(widgetThemeKey, mode.name)

  private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
    context.settingsStore.edit { it[key] = value }
  }
}

private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
  this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
