package cz.healthwidgy.ui

import androidx.health.connect.client.records.ExerciseSessionRecord
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Czech formatting for numbers, durations and times. */
object Format {
  private val czech: Locale = Locale.forLanguageTag("cs-CZ")
  private val time = DateTimeFormatter.ofPattern("H:mm", czech)
  private val dayMonth = DateTimeFormatter.ofPattern("d. M.", czech)
  private val longDate = DateTimeFormatter.ofPattern("EEEE d. MMMM", czech)
  /** Abbreviated weekday ("út"); java.time allows at most five pattern letters. */
  private val weekdayShort = DateTimeFormatter.ofPattern("EEE", czech)

  fun int(value: Long): String = NumberFormat.getIntegerInstance(czech).format(value)

  fun int(value: Double): String = int(value.roundToLong())

  fun decimal(value: Double, digits: Int = 1): String =
    NumberFormat.getNumberInstance(czech).apply {
      minimumFractionDigits = digits
      maximumFractionDigits = digits
    }.format(value)

  fun km(meters: Double): String = decimal(meters / 1000.0, 1)

  /** Czech plural: 1 patro, 2–4 patra, 0 / 5+ pater. */
  fun plural(n: Long, one: String, few: String, many: String): String =
    when (abs(n)) {
      1L -> one
      in 2L..4L -> few
      else -> many
    }

  /** "7 h 24 min", "42 min". */
  fun duration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
  }

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

  fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = time.format(instant.atZone(zone))

  /** "Dnes 7:05", "Včera 17:05", "po 3. 11. 17:05". */
  fun dayAndTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val date = instant.atZone(zone).toLocalDate()
    val prefix =
      when (date) {
        today -> "Dnes"
        today.minusDays(1) -> "Včera"
        else -> "${weekdayShort.format(date)} ${dayMonth.format(date)}"
      }
    return "$prefix ${time(instant, zone)}"
  }

  /** "Úterý 10. listopadu". */
  fun longDate(date: LocalDate): String = longDate.format(date).replaceFirstChar { it.titlecase(czech) }

  fun weekdayShort(date: LocalDate): String = weekdayShort.format(date).replaceFirstChar { it.titlecase(czech) }

  /** "10. 11." */
  fun dayMonth(date: LocalDate): String = dayMonth.format(date)

  /** "Út 10. 11." */
  fun weekdayDate(date: LocalDate): String = "${weekdayShort(date)} ${dayMonth(date)}"

  private val monthShort = DateTimeFormatter.ofPattern("LLL", czech)
  private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", czech)

  /** "lis" (standalone month abbreviation). */
  fun monthShort(date: LocalDate): String = monthShort.format(date).trimEnd('.')

  /** "Listopad 2026". */
  fun monthYear(date: LocalDate): String = monthYear.format(date).replaceFirstChar { it.titlecase(czech) }

  /** Range of a week, e.g. "3.–9. 11." or "27. 10.–2. 11.". */
  fun weekRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) "${start.dayOfMonth}.–${dayMonth(end)}" else "${dayMonth(start)}–${dayMonth(end)}"

  fun signedKg(delta: Double): String = (if (delta < 0) "▼ " else "▲ ") + decimal(abs(delta), 1) + " kg"

  /** Czech name of a Health Connect exercise type; unknown types fall back to "Trénink". */
  fun exerciseName(type: Int): String =
    when (type) {
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Běh"
      ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Běh na pásu"
      ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Chůze"
      ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Turistika"
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Kolo"
      ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Rotoped"
      ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Plavání v bazénu"
      ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Plavání"
      ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Posilování"
      ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Jóga"
      ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Eliptický trenažér"
      ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Veslařský trenažér"
      ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Lyžování"
      ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golf"
      ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
      else -> "Trénink"
    }
}
