package cz.healthwidgy.ui

import androidx.health.connect.client.records.ExerciseSessionRecord
import cz.healthwidgy.i18n.AppLang
import cz.healthwidgy.i18n.tr
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Numbers, durations and dates in the app's language ([AppLang]): Czech or English. */
object Format {
  private val czech: Locale = Locale.forLanguageTag("cs-CZ")
  private val english: Locale = Locale.forLanguageTag("en")

  /** Date patterns of one language. 24-hour time in both ("7:05" / "07:05"): the app is used in a 24-hour country. */
  private class Patterns(val locale: Locale, czech: Boolean) {
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern(if (czech) "H:mm" else "HH:mm", locale)
    /** "10. 11." / "Nov 10" */
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern(if (czech) "d. M." else "MMM d", locale)
    /** "úterý 10. listopadu" / "Tuesday, November 10" */
    val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern(if (czech) "EEEE d. MMMM" else "EEEE, MMMM d", locale)
    /** Abbreviated weekday ("út", "Tue"); java.time allows at most five pattern letters. */
    val weekdayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", locale)
    /** Standalone month ("lis", "Nov"). */
    val monthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL", locale)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
  }

  private val csPatterns by lazy { Patterns(czech, czech = true) }
  private val enPatterns by lazy { Patterns(english, czech = false) }
  private val p: Patterns
    get() = if (AppLang.isCzech) csPatterns else enPatterns

  /** "7 369" / "7,369". */
  fun int(value: Long): String = NumberFormat.getIntegerInstance(p.locale).format(value)

  fun int(value: Double): String = int(value.roundToLong())

  /** "7,5" / "7.5". */
  fun decimal(value: Double, digits: Int = 1): String =
    NumberFormat.getNumberInstance(p.locale).apply {
      minimumFractionDigits = digits
      maximumFractionDigits = digits
    }.format(value)

  fun km(meters: Double): String = decimal(meters / 1000.0, 1)

  /** "62 %" in Czech, "62%" in English; [number] is already formatted. */
  fun percent(number: Any): String = tr("$number %", "$number%")

  /** Czech plural: 1 patro, 2–4 patra, 0 / 5+ pater. Use inside `tr(...)` next to [pluralEn]. */
  fun plural(n: Long, one: String, few: String, many: String): String =
    when (abs(n)) {
      1L -> one
      in 2L..4L -> few
      else -> many
    }

  /** English plural: 1 floor, 0 / 2+ floors. */
  fun pluralEn(n: Long, one: String, other: String): String = if (abs(n) == 1L) one else other

  /** "7 h 24 min", "42 min" (the same in both languages). */
  fun duration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
  }

  /** Whole or half hours for goals: "8 h", "7,5 h" / "7.5 h". */
  fun hours(minutes: Int): String = decimal(minutes / 60.0, if (minutes % 60 == 0) 0 else 1) + " h"

  /** Compact sleep length for widgets: "7:05". */
  fun hoursMinutes(minutes: Long): String = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"

  /** "42:10" or "1:02:10" for workout lengths. */
  fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
  }

  /** Running pace in min/km, e.g. "5:08". */
  fun pace(seconds: Long, meters: Double): String? {
    if (meters < 100) return null
    val perKm = (seconds / (meters / 1000.0)).roundToLong()
    return "%d:%02d".format(perKm / 60, perKm % 60)
  }

  fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = p.time.format(instant.atZone(zone))

  /**
   * "Dnes 7:05", "Včera 17:05", "po 3. 11. 17:05" / "Today 7:05", "Yesterday 17:05", "Mon Nov 3 17:05".
   * [inSentence] lowercases "Dnes"/"Today" for the middle of a sentence; weekdays keep their case.
   */
  fun dayAndTime(
    instant: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
    inSentence: Boolean = false,
  ): String {
    val date = instant.atZone(zone).toLocalDate()
    val prefix =
      when (date) {
        today -> tr("Dnes", "Today")
        today.minusDays(1) -> tr("Včera", "Yesterday")
        else -> return "${p.weekdayShort.format(date)} ${p.dayMonth.format(date)} ${time(instant, zone)}"
      }
    return "${if (inSentence) prefix.lowercase(p.locale) else prefix} ${time(instant, zone)}"
  }

  /** "Úterý 10. listopadu" / "Tuesday, November 10". */
  fun longDate(date: LocalDate): String = p.longDate.format(date).replaceFirstChar { it.titlecase(p.locale) }

  /** "Út" / "Tue". */
  fun weekdayShort(date: LocalDate): String = p.weekdayShort.format(date).replaceFirstChar { it.titlecase(p.locale) }

  /** "10. 11." / "Nov 10". */
  fun dayMonth(date: LocalDate): String = p.dayMonth.format(date)

  /** "Út 10. 11." / "Tue Nov 10". */
  fun weekdayDate(date: LocalDate): String = "${weekdayShort(date)} ${dayMonth(date)}"

  /** "lis" / "Nov" (standalone month abbreviation). */
  fun monthShort(date: LocalDate): String = p.monthShort.format(date).trimEnd('.')

  /** "Listopad 2026" / "November 2026". */
  fun monthYear(date: LocalDate): String = p.monthYear.format(date).replaceFirstChar { it.titlecase(p.locale) }

  /** Range of a week: "3.–9. 11." or "27. 10.–2. 11." / "Nov 3–9" or "Oct 27 – Nov 2". */
  fun weekRange(start: LocalDate, end: LocalDate): String =
    when {
      AppLang.isCzech && start.month == end.month -> "${start.dayOfMonth}.–${dayMonth(end)}"
      AppLang.isCzech -> "${dayMonth(start)}–${dayMonth(end)}"
      start.month == end.month -> "${dayMonth(start)}–${end.dayOfMonth}"
      else -> "${dayMonth(start)} – ${dayMonth(end)}"
    }

  fun signedKg(delta: Double): String = (if (delta < 0) "▼ " else "▲ ") + decimal(abs(delta), 1) + " kg"

  /** Name of a Health Connect exercise type; unknown types fall back to "Trénink" / "Workout". */
  fun exerciseName(type: Int): String =
    when (type) {
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> tr("Běh", "Running")
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> tr("Běh na pásu", "Treadmill running")
      ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> tr("Chůze", "Walking")
      ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> tr("Turistika", "Hiking")
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> tr("Kolo", "Cycling")
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> tr("Rotoped", "Indoor cycling")
      ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> tr("Plavání v bazénu", "Pool swimming")
      ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> tr("Plavání", "Open water swimming")
      ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> tr("Posilování", "Strength training")
      ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> tr("Jóga", "Yoga")
      ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> tr("Eliptický trenažér", "Elliptical")
      ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> tr("Veslařský trenažér", "Rowing machine")
      ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> tr("Lyžování", "Skiing")
      ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> tr("Golf", "Golf")
      ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> tr("HIIT", "HIIT")
      else -> tr("Trénink", "Workout")
    }
}
