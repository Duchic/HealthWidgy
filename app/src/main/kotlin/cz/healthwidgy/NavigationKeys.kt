package cz.healthwidgy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Tabs (roots of the back stack).
@Serializable data object OverviewKey : NavKey

@Serializable data object TrendsKey : NavKey

@Serializable data object ActivitiesKey : NavKey

@Serializable data object SettingsKey : NavKey

// Detail screens (pushed on top of a tab).
@Serializable data class MetricKey(val metric: String) : NavKey

@Serializable data object HeartKey : NavKey

@Serializable data object SleepKey : NavKey

@Serializable data object BodyKey : NavKey

@Serializable data class WorkoutKey(val id: String) : NavKey

@Serializable data object InventoryKey : NavKey
