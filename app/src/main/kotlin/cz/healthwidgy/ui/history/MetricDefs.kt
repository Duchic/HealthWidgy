package cz.healthwidgy.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import cz.healthwidgy.R
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.theme.MetricColors

enum class ChartStyle { BARS, LINE }

/** How a daily metric is labelled, colored, charted and formatted. */
data class MetricDef(
  val id: String,
  val labelCs: String,
  val labelEn: String,
  /** Unit shown after a formatted value ("" when the formatter includes it). */
  val unit: String,
  @DrawableRes val icon: Int,
  val color: (MetricColors) -> Color,
  val style: ChartStyle,
  /** True for daily sums (steps, distance…): a "Celkem" makes sense; false for averages and durations. */
  val isSum: Boolean,
  val format: (Double) -> String,
  val goal: (Settings) -> Double? = { null },
) {
  /** Label in the current language, read on every use: the language can change while the app runs. */
  val label: String
    get() = tr(labelCs, labelEn)
}

object MetricDefs {
  val Steps = MetricDef(Metrics.STEPS, "Kroky", "Steps", "", R.drawable.ic_walk, { it.steps }, ChartStyle.BARS, true, { Format.int(it) }, { it.stepGoal.toDouble() })
  val Distance = MetricDef(Metrics.DISTANCE, "Vzdálenost", "Distance", "km", R.drawable.ic_route, { it.distance }, ChartStyle.BARS, true, { Format.km(it) }, { it.distanceGoalKm * 1000.0 })
  val Floors = MetricDef(Metrics.FLOORS, "Patra", "Floors", "", R.drawable.ic_stairs, { it.body }, ChartStyle.BARS, true, { Format.int(it) }, { it.floorsGoal.toDouble() })
  val TotalKcal = MetricDef(Metrics.TOTAL_KCAL, "Kalorie", "Calories", "kcal", R.drawable.ic_flame, { it.calories }, ChartStyle.BARS, true, { Format.int(it) })
  val RestingHr = MetricDef(Metrics.RESTING_HR, "Klidový tep", "Resting heart rate", "bpm", R.drawable.ic_heart, { it.heart }, ChartStyle.LINE, false, { Format.int(it) })
  val Sleep = MetricDef(Metrics.SLEEP, "Spánek", "Sleep", "", R.drawable.ic_moon, { it.sleep }, ChartStyle.BARS, false, { Format.duration(Math.round(it)) }, { it.sleepGoalMinutes.toDouble() })

  val all = listOf(Steps, Distance, Floors, TotalKcal, RestingHr, Sleep)

  fun byId(id: String): MetricDef = all.firstOrNull { it.id == id } ?: Steps
}
