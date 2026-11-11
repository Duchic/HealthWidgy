package cz.healthwidgy.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import cz.healthwidgy.R
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.theme.MetricColors

enum class ChartStyle { BARS, LINE }

/** How a daily metric is labelled, colored, charted and formatted. */
data class MetricDef(
  val id: String,
  val label: String,
  /** Unit shown after a formatted value ("" when the formatter includes it). */
  val unit: String,
  @DrawableRes val icon: Int,
  val color: (MetricColors) -> Color,
  val style: ChartStyle,
  /** True for daily sums (steps, distance…): a "Celkem" makes sense; false for averages and durations. */
  val isSum: Boolean,
  val format: (Double) -> String,
  val goal: (Settings) -> Double? = { null },
)

object MetricDefs {
  val Steps = MetricDef(Metrics.STEPS, "Kroky", "", R.drawable.ic_walk, { it.steps }, ChartStyle.BARS, true, { Format.int(it) }, { it.stepGoal.toDouble() })
  val Distance = MetricDef(Metrics.DISTANCE, "Vzdálenost", "km", R.drawable.ic_route, { it.steps }, ChartStyle.BARS, true, { Format.km(it) })
  val Floors = MetricDef(Metrics.FLOORS, "Patra", "", R.drawable.ic_stairs, { it.body }, ChartStyle.BARS, true, { Format.int(it) })
  val TotalKcal = MetricDef(Metrics.TOTAL_KCAL, "Kalorie", "kcal", R.drawable.ic_flame, { it.calories }, ChartStyle.BARS, true, { Format.int(it) })
  val RestingHr = MetricDef(Metrics.RESTING_HR, "Klidový tep", "bpm", R.drawable.ic_heart, { it.heart }, ChartStyle.LINE, false, { Format.int(it) })
  val Sleep = MetricDef(Metrics.SLEEP, "Spánek", "", R.drawable.ic_moon, { it.sleep }, ChartStyle.BARS, false, { Format.duration(Math.round(it)) }, { SLEEP_GOAL_MINUTES })

  val all = listOf(Steps, Distance, Floors, TotalKcal, RestingHr, Sleep)

  fun byId(id: String): MetricDef = all.firstOrNull { it.id == id } ?: Steps

  /** Fixed 8 h line on sleep charts (no sleep goal setting yet). */
  const val SLEEP_GOAL_MINUTES = 480.0
}
