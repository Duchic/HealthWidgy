package cz.healthwidgy.widget

import cz.healthwidgy.R
import cz.healthwidgy.data.db.BodyMeasurementEntity
import cz.healthwidgy.data.sync.Metrics
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.Format
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Values a widget slot can show. The enum names are stored in widget state, so keep them stable
 * (the first seven come from the original "Jedna hodnota" widget).
 */
enum class WidgetMetric(
  private val labelCs: String,
  private val labelEn: String,
  private val shortLabelCs: String,
  private val shortLabelEn: String,
  val icon: Int,
  val accent: Accent,
) {
  STEPS("Kroky", "Steps", "Kroky", "Steps", R.drawable.ic_walk, Accent.STEPS),
  CALORIES("Kalorie", "Calories", "Kalorie", "Calories", R.drawable.ic_flame, Accent.CALORIES),
  DISTANCE("Vzdálenost", "Distance", "Vzdálenost", "Distance", R.drawable.ic_route, Accent.DISTANCE),
  FLOORS("Patra", "Floors", "Patra", "Floors", R.drawable.ic_stairs, Accent.BODY),
  RESTING_HR("Klidový tep", "Resting heart rate", "Klidový tep", "Resting HR", R.drawable.ic_heart, Accent.HEART),
  SLEEP("Spánek", "Sleep", "Spánek", "Sleep", R.drawable.ic_moon, Accent.SLEEP),
  WEIGHT("Váha", "Weight", "Váha", "Weight", R.drawable.ic_scale, Accent.BODY),
  HEART_RATE("Tep (poslední hodina)", "Heart rate (last hour)", "Tep", "Heart rate", R.drawable.ic_heart, Accent.HEART),
  BODY_FAT("Tělesný tuk", "Body fat", "Tuk", "Body fat", R.drawable.ic_scale, Accent.BODY);

  /** Name in pickers, in the current language (read on every use: the language can change at runtime). */
  val label: String
    get() = tr(labelCs, labelEn)

  /** Short name on widgets, in the current language. */
  val shortLabel: String
    get() = tr(shortLabelCs, shortLabelEn)

  /** Day-by-day values are levels (line chart, average) rather than daily totals (bars, sum). */
  val isLevel: Boolean
    get() = this == RESTING_HR || this == HEART_RATE || this == WEIGHT || this == BODY_FAT

  companion object {
    /** Metrics with a daily goal (rings, calendar). */
    val withGoal = listOf(STEPS, DISTANCE, FLOORS, SLEEP)

    /** Metrics with a value per day (trend charts). */
    val withHistory = listOf(STEPS, DISTANCE, FLOORS, CALORIES, SLEEP, RESTING_HR, WEIGHT, BODY_FAT)

    /** Order in pickers. */
    val pickerOrder = listOf(STEPS, DISTANCE, FLOORS, CALORIES, RESTING_HR, HEART_RATE, SLEEP, WEIGHT, BODY_FAT)

    fun byName(name: String?): WidgetMetric? = entries.firstOrNull { it.name == name }
  }
}

/** What a widget shows for one metric right now. */
data class Reading(
  val value: String,
  val unit: String?,
  /** Short context: "62 % cíle", "včera", "Ø týden 54". */
  val note: String?,
  /** Share of the daily goal (0 = nothing yet), null for metrics without a goal. */
  val progress: Float?,
  val destination: String,
)

/** Shown instead of a value when there is no data. */
const val NO_VALUE = "–"
private const val NONE = NO_VALUE

fun WidgetSnapshot.goal(metric: WidgetMetric): Double? =
  when (metric) {
    WidgetMetric.STEPS -> goals.steps.toDouble()
    WidgetMetric.DISTANCE -> goals.distanceMeters
    WidgetMetric.FLOORS -> goals.floors.toDouble()
    WidgetMetric.SLEEP -> goals.sleepMinutes.toDouble()
    else -> null
  }

/** "10 000 kroků", "8 km", "10 pater", "8 h". */
fun WidgetSnapshot.goalText(metric: WidgetMetric): String =
  when (metric) {
    WidgetMetric.STEPS -> tr("${Format.int(goals.steps.toLong())} kroků", "${Format.int(goals.steps.toLong())} steps")
    WidgetMetric.DISTANCE -> "${Format.int(goals.distanceMeters / 1000)} km"
    WidgetMetric.FLOORS ->
      "${goals.floors} ${tr(Format.plural(goals.floors.toLong(), "patro", "patra", "pater"), Format.pluralEn(goals.floors.toLong(), "floor", "floors"))}"
    WidgetMetric.SLEEP -> Format.hours(goals.sleepMinutes)
    else -> ""
  }

/** Today's value of a daily-goal metric (last night for sleep; 0 until data arrives). */
fun WidgetSnapshot.todayValue(metric: WidgetMetric): Double =
  when (metric) {
    WidgetMetric.STEPS -> steps ?: 0.0
    WidgetMetric.DISTANCE -> distanceMeters ?: 0.0
    WidgetMetric.FLOORS -> floors ?: 0.0
    WidgetMetric.SLEEP -> if (sleepIsLastNight) sleep?.asleepMinutes?.toDouble() ?: 0.0 else 0.0
    WidgetMetric.CALORIES -> totalKcal ?: 0.0
    else -> 0.0
  }

fun WidgetSnapshot.progress(metric: WidgetMetric): Float? = goal(metric)?.let { g -> (todayValue(metric) / g.coerceAtLeast(1.0)).toFloat() }

/** "62 %" in Czech, "62%" in English. */
private fun Float.percent(): String = Format.percent((this * 100).roundToInt())

/** "dnes", "včera", "12. 11." */
fun relativeDay(date: LocalDate, today: LocalDate): String =
  when (date) {
    today -> tr("dnes", "today")
    today.minusDays(1) -> tr("včera", "yesterday")
    else -> Format.dayMonth(date)
  }

/** Formats a day value of [metric] (as stored per day) without unit. */
fun WidgetMetric.format(value: Double): String =
  when (this) {
    WidgetMetric.DISTANCE -> Format.km(value)
    WidgetMetric.SLEEP -> Format.hoursMinutes(value.roundToLong())
    WidgetMetric.WEIGHT, WidgetMetric.BODY_FAT -> Format.decimal(value)
    else -> Format.int(value)
  }

/** Unit after a formatted value, by amount for Czech plurals. */
fun WidgetMetric.unit(value: Double?): String? =
  when (this) {
    WidgetMetric.STEPS -> null
    WidgetMetric.DISTANCE -> "km"
    WidgetMetric.FLOORS -> {
      val n = (value ?: 0.0).roundToLong()
      tr(Format.plural(n, "patro", "patra", "pater"), Format.pluralEn(n, "floor", "floors"))
    }
    WidgetMetric.CALORIES -> "kcal"
    WidgetMetric.RESTING_HR, WidgetMetric.HEART_RATE -> "bpm"
    WidgetMetric.SLEEP -> "h"
    WidgetMetric.WEIGHT -> "kg"
    WidgetMetric.BODY_FAT -> "%"
  }

fun WidgetMetric.destination(): String =
  when (this) {
    WidgetMetric.STEPS -> Destinations.metric(Metrics.STEPS)
    WidgetMetric.DISTANCE -> Destinations.metric(Metrics.DISTANCE)
    WidgetMetric.FLOORS -> Destinations.metric(Metrics.FLOORS)
    WidgetMetric.CALORIES -> Destinations.metric(Metrics.TOTAL_KCAL)
    WidgetMetric.RESTING_HR, WidgetMetric.HEART_RATE -> Destinations.HEART
    WidgetMetric.SLEEP -> Destinations.SLEEP
    WidgetMetric.WEIGHT, WidgetMetric.BODY_FAT -> Destinations.BODY
  }

fun WidgetSnapshot.read(metric: WidgetMetric): Reading {
  fun withGoal(value: Double): Reading {
    val p = progress(metric) ?: 0f
    return Reading(metric.format(value), metric.unit(value), tr("${p.percent()} cíle", "${p.percent()} of goal"), p, metric.destination())
  }
  fun body(m: BodyMeasurementEntity?): Reading =
    Reading(m?.let { metric.format(it.value) } ?: NONE, metric.unit(m?.value), m?.let { relativeDay(LocalDate.parse(it.date), today) }, null, metric.destination())

  return when (metric) {
    // Steps start at 0 every day; distance and floors without a record are unknown, not zero (watches
    // without a barometer never write floors), like the overview in the app hides them.
    WidgetMetric.STEPS -> withGoal(todayValue(metric))
    WidgetMetric.DISTANCE -> distanceMeters?.let { withGoal(it) } ?: Reading(NONE, null, null, 0f, metric.destination())
    WidgetMetric.FLOORS -> floors?.let { withGoal(it) } ?: Reading(NONE, null, null, 0f, metric.destination())
    WidgetMetric.SLEEP -> {
      val night = sleep
      when {
        night == null -> Reading(NONE, null, null, 0f, metric.destination())
        sleepIsLastNight -> withGoal(night.asleepMinutes.toDouble())
        else ->
          Reading(metric.format(night.asleepMinutes.toDouble()), "h", relativeDay(LocalDate.parse(night.wakeDate), today), 0f, metric.destination())
      }
    }
    WidgetMetric.CALORIES -> Reading(totalKcal?.let { metric.format(it) } ?: NONE, "kcal", tr("dnes", "today"), null, metric.destination())
    WidgetMetric.RESTING_HR -> {
      val r = restingHr
      val week = days(Metrics.RESTING_HR, 7).mapNotNull { it.second }
      val note =
        when {
          r == null -> null
          r.first != today -> relativeDay(r.first, today)
          week.size > 1 -> tr("Ø týden ${Format.int(week.average())}", "week avg ${Format.int(week.average())}")
          else -> tr("dnes", "today")
        }
      Reading(r?.let { metric.format(it.second) } ?: NONE, "bpm", note, null, metric.destination())
    }
    WidgetMetric.HEART_RATE -> {
      val h = lastHourHr
      Reading(
        h?.let { metric.format(it.second) } ?: NONE,
        "bpm",
        h?.let { tr("Ø od ${Format.time(it.first)}", "avg since ${Format.time(it.first)}") },
        null,
        metric.destination(),
      )
    }
    WidgetMetric.WEIGHT -> body(weight)
    WidgetMetric.BODY_FAT -> body(bodyFat)
  }
}

/**
 * Reading for a goal ring ("today's progress"): a night that did not end today is not today's sleep,
 * so the ring shows no value; the note says when it was and how long ("naposledy včera 7:32 h").
 */
fun WidgetSnapshot.readForGoal(metric: WidgetMetric): Reading {
  val r = read(metric)
  val night = sleep
  if (metric != WidgetMetric.SLEEP || sleepIsLastNight || night == null) return r
  return Reading(NONE, null, tr("naposledy ${r.note} ${r.value} h", "last: ${r.note}, ${r.value} h"), 0f, r.destination)
}

/** Day the current value of [metric] comes from: today for daily totals, older for measurements and nights. */
fun WidgetSnapshot.valueDay(metric: WidgetMetric): LocalDate? =
  when (metric) {
    WidgetMetric.WEIGHT -> weight?.let { LocalDate.parse(it.date) }
    WidgetMetric.BODY_FAT -> bodyFat?.let { LocalDate.parse(it.date) }
    WidgetMetric.RESTING_HR -> restingHr?.first
    WidgetMetric.SLEEP -> sleep?.let { LocalDate.parse(it.wakeDate) }
    else -> today
  }

/**
 * The line under the value in the 1×1 "Malá hodnota": the unit with a hint of what it measures
 * ("bpm v klidu", "% tuku"), or with the day when the value is not from today ("kg · včera").
 */
fun WidgetSnapshot.compactCaption(metric: WidgetMetric, r: Reading): String {
  if (r.value == NO_VALUE) return metric.shortLabel
  val caption =
    when (metric) {
      WidgetMetric.STEPS -> {
        val n = (steps ?: 0.0).roundToLong()
        tr(Format.plural(n, "krok", "kroky", "kroků"), Format.pluralEn(n, "step", "steps"))
      }
      WidgetMetric.RESTING_HR -> tr("bpm v klidu", "bpm at rest")
      WidgetMetric.SLEEP -> tr("h spánku", "h of sleep")
      WidgetMetric.BODY_FAT -> tr("% tuku", "% body fat")
      else -> r.unit ?: metric.shortLabel
    }
  val day = valueDay(metric)?.takeIf { it != today } ?: return caption
  return "${r.unit ?: caption} · ${relativeDay(day, today)}"
}

/** Day values for trend charts, oldest first (today last). */
fun WidgetSnapshot.series(metric: WidgetMetric, count: Int): List<Pair<LocalDate, Double?>> =
  when (metric) {
    WidgetMetric.STEPS -> days(Metrics.STEPS, count)
    WidgetMetric.DISTANCE -> days(Metrics.DISTANCE, count)
    WidgetMetric.FLOORS -> days(Metrics.FLOORS, count)
    WidgetMetric.CALORIES -> days(Metrics.TOTAL_KCAL, count)
    WidgetMetric.SLEEP -> days(Metrics.SLEEP, count)
    WidgetMetric.RESTING_HR -> days(Metrics.RESTING_HR, count)
    WidgetMetric.HEART_RATE -> days(Metrics.HEART_RATE, count)
    WidgetMetric.WEIGHT -> bodyDays(weights, count)
    WidgetMetric.BODY_FAT -> bodyDays(bodyFats, count)
  }
