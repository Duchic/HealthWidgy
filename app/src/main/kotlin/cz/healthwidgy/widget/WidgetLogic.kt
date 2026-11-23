package cz.healthwidgy.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/** [weeks] calendar rows Monday–Sunday, the last one containing [today]; oldest first. */
fun calendarWeeks(today: LocalDate, weeks: Int): List<List<LocalDate>> {
  val firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
  return (0 until weeks).map { w -> (0 until 7).map { d -> firstMonday.plusDays(w * 7L + d) } }
}

/** Fill strength of a calendar day by share of the goal: faint (nothing), light, medium, full (goal met). */
fun calendarAlpha(ratio: Double): Float =
  when {
    ratio <= 0.0 -> 0.10f
    ratio < 0.5 -> 0.30f
    ratio < 1.0 -> 0.58f
    else -> 1f
  }

/**
 * Cached hours with complete heart rate data as chart slots (hour of the local day). On the autumn clock
 * change two hours start at 2:00; they share one slot (range of both, mean of the averages).
 */
fun heartHours(rows: List<HourRow>, zone: ZoneId = ZoneId.systemDefault()): List<WidgetBitmaps.HourBpm> =
  rows
    .mapNotNull { r ->
      val min = r.min ?: return@mapNotNull null
      val max = r.max ?: return@mapNotNull null
      val avg = r.avg ?: return@mapNotNull null
      WidgetBitmaps.HourBpm(r.start.atZone(zone).hour, min, max, avg)
    }
    .groupBy { it.slot }
    .map { (slot, same) -> WidgetBitmaps.HourBpm(slot, same.minOf { it.min }, same.maxOf { it.max }, same.map { it.avg }.average()) }
    .sortedBy { it.slot }

/** How the "Více hodnot" widget arranges [count] values in a content area of [width] × [height] dp. */
data class MultiLayout(val cols: Int, val compact: Boolean, val valueSize: Int, val showNote: Boolean)

fun multiValueLayout(count: Int, width: Float, height: Float): MultiLayout {
  val n = count.coerceAtLeast(1)
  // A label + value cell needs about 72 dp; more values than fit side by side go to a second row.
  val fit = (width / 72f).toInt().coerceIn(1, 4)
  val twoRows = n > 1 && (n > fit || (n > 2 && height >= 140f))
  val cols = if (twoRows) (n + 1) / 2 else n
  val rowHeight = if (twoRows) height / 2 else height
  // Label + value rows need about 92 dp for two rows; shorter widgets get one line per value.
  val compact = twoRows && height < 92f
  val valueSize = if (cols >= 4) 17 else if (rowHeight >= 70f) 24 else 20
  return MultiLayout(cols, compact, valueSize, showNote = !compact && rowHeight >= 58f)
}

/**
 * Font size (sp) at which [text] fits [width] dp on one line, at about 0.62 em per character, between
 * [min] and [max]. The 1×1 widgets size their value with it, so "52" is large and "12 345" still fits.
 */
fun fitFontSize(text: String, width: Float, max: Int, min: Int = 10): Int =
  (width / (text.length.coerceAtLeast(1) * 0.62f)).toInt().coerceIn(min, max.coerceAtLeast(min))

/** Chart range in whole tens around the day's minimum and maximum (at least 40 bpm tall). */
fun heartScale(hours: List<WidgetBitmaps.HourBpm>): Pair<Double, Double> {
  val low = floor(hours.minOf { it.min } / 10) * 10
  val high = maxOf(ceil(hours.maxOf { it.max } / 10) * 10, low + 40)
  return low to high
}
