package cz.healthwidgy.data.settings

import android.content.Context
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

data class Settings(
  val stepGoal: Int = DEFAULT_STEP_GOAL,
  val sourceMode: SourceMode = SourceMode.GARMIN_ONLY,
) {
  companion object {
    const val DEFAULT_STEP_GOAL = 10_000
    val STEP_GOAL_OPTIONS = listOf(6_000, 8_000, 10_000, 12_000, 15_000)
  }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
  private val stepGoalKey = intPreferencesKey("step_goal")
  private val sourceModeKey = stringPreferencesKey("source_mode")

  val settings: Flow<Settings> =
    context.settingsStore.data.map { prefs ->
      Settings(
        stepGoal = prefs[stepGoalKey] ?: Settings.DEFAULT_STEP_GOAL,
        sourceMode = prefs[sourceModeKey]?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() } ?: SourceMode.GARMIN_ONLY,
      )
    }

  suspend fun setStepGoal(goal: Int) {
    context.settingsStore.edit { it[stepGoalKey] = goal }
  }

  suspend fun setSourceMode(mode: SourceMode) {
    context.settingsStore.edit { it[sourceModeKey] = mode.name }
  }
}
