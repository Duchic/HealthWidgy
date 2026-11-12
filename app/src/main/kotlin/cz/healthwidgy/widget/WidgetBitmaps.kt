package cz.healthwidgy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Bitmaps for widgets (Glance has no Canvas). Every bitmap is a white mask: the widget tints it with a
 * color provider, so charts follow light/dark mode like text does (a bitmap with baked-in colors could
 * not). Several masks are stacked in a Box when a chart needs more than one color. Opacity is part of
 * the mask, since the tint replaces the color but keeps the mask's alpha.
 */
object WidgetBitmaps {
  /** Mask opacity of ring tracks (tinted with onSurface). */
  const val TRACK_ALPHA = 0.14f

  /** Mask opacity of past bars and heart rate ranges (tinted with the metric accent). */
  const val SOFT_ALPHA = 0.45f

  private fun white(alpha: Float = 1f): Int = android.graphics.Color.argb((alpha * 255).roundToInt(), 255, 255, 255)

  private class Px(context: Context, widthDp: Number, heightDp: Number) {
    val d = context.resources.displayMetrics.density
    val w = (widthDp.toFloat() * d).toInt().coerceAtLeast(1)
    val h = (heightDp.toFloat() * d).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
  }

  private fun stroke(widthPx: Float, alpha: Float = 1f) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = widthPx
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
      color = white(alpha)
    }

  private fun fill(alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(alpha) }

  /** Bounds of concentric ring [index] (0 = outermost) in a square of [size] px. */
  private fun ringRect(size: Int, strokePx: Float, index: Int): RectF {
    val inset = strokePx / 2 + index * strokePx * 1.3f
    return RectF(inset, inset, size - inset, size - inset)
  }

  /** Full circles of [count] concentric rings, faint (tint: onSurface). */
  fun ringTracks(context: Context, sizeDp: Int, strokeDp: Float, count: Int = 1): Bitmap {
    val p = Px(context, sizeDp, sizeDp)
    val paint = stroke(strokeDp * p.d, TRACK_ALPHA)
    repeat(count) { p.canvas.drawArc(ringRect(p.w, paint.strokeWidth, it), 0f, 360f, false, paint) }
    return p.bitmap
  }

  /** Progress arc of concentric ring [index] from 12 o'clock clockwise (tint: the metric accent). */
  fun ringArc(context: Context, sizeDp: Int, strokeDp: Float, progress: Float, index: Int = 0): Bitmap {
    val p = Px(context, sizeDp, sizeDp)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    if (sweep > 0f) {
      val paint = stroke(strokeDp * p.d)
      p.canvas.drawArc(ringRect(p.w, paint.strokeWidth, index), -90f, sweep, false, paint)
    }
    return p.bitmap
  }

  /** One night in time order as a rounded strip: one mask per stage group, each tinted with its color. */
  fun hypnogram(context: Context, startMs: Long, endMs: Long, stages: List<Pair<Accent, Pair<Long, Long>>>, widthDp: Int, heightDp: Int): Map<Accent, Bitmap> {
    val span = (endMs - startMs).coerceAtLeast(1).toFloat()
    return stages.groupBy { it.first }.mapValues { (_, segments) ->
      val p = Px(context, widthDp, heightDp)
      p.canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, p.w.toFloat(), p.h.toFloat()), 6 * p.d, 6 * p.d, Path.Direction.CW) })
      val paint = fill()
      segments.forEach { (_, range) ->
        val x0 = (range.first - startMs) / span * p.w
        val x1 = (range.second - startMs) / span * p.w
        p.canvas.drawRect(x0, 0f, maxOf(x1, x0 + 1f), p.h.toFloat(), paint)
      }
      p.bitmap
    }
  }

  /**
   * Line through [values] (nulls skipped, neighbours joined) with the last point, or every point, as a dot.
   * [slotCentered] puts point i in the middle of the i-th of n equal columns (to line up with labels).
   */
  fun line(context: Context, values: List<Double?>, widthDp: Int, heightDp: Int, dots: Boolean = false, slotCentered: Boolean = false): Bitmap {
    val p = Px(context, widthDp, heightDp)
    val points = values.withIndex().mapNotNull { (i, v) -> v?.let { i to it } }
    if (points.isEmpty()) return p.bitmap
    val pad = 4 * p.d
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val span = (values.size - 1).coerceAtLeast(1)
    val slot = p.w.toFloat() / values.size.coerceAtLeast(1)
    fun x(i: Int) = if (slotCentered) slot * i + slot / 2 else pad + i * (p.w - 2 * pad) / span
    fun y(v: Double) = if (max == min) p.h / 2f else (pad + (max - v) * (p.h - 2 * pad) / (max - min)).toFloat()
    if (points.size > 1) {
      val path = Path()
      points.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
      p.canvas.drawPath(path, stroke(2 * p.d))
    }
    val dot = fill()
    if (dots) points.forEach { (i, v) -> p.canvas.drawCircle(x(i), y(v), 2.5f * p.d, dot) }
    val (li, lv) = points.last()
    p.canvas.drawCircle(x(li), y(lv), 3.5f * p.d, dot)
    return p.bitmap
  }

  /**
   * Daily bars scaled to [max]; the bar at [highlight] goes to the second mask (full strength), all others
   * to the first one (soft). Days without data get no bar, zero gets a stub.
   */
  fun bars(context: Context, values: List<Double?>, max: Double, widthDp: Int, heightDp: Int, highlight: Int?): Pair<Bitmap, Bitmap> {
    val soft = Px(context, widthDp, heightDp)
    val strong = Px(context, widthDp, heightDp)
    if (values.isEmpty() || max <= 0) return soft.bitmap to strong.bitmap
    val d = soft.d
    val slot = soft.w.toFloat() / values.size
    val barW = minOf(slot * 0.62f, 22 * d)
    val radius = minOf(4 * d, barW / 2)
    val softPaint = fill(SOFT_ALPHA)
    val strongPaint = fill()
    values.forEachIndexed { i, v ->
      if (v == null) return@forEachIndexed
      val h = (v / max * soft.h).toFloat().coerceIn(2 * d, soft.h.toFloat())
      val cx = slot * i + slot / 2
      val rect = RectF(cx - barW / 2, soft.h - h, cx + barW / 2, soft.h.toFloat())
      if (i == highlight) strong.canvas.drawRoundRect(rect, radius, radius, strongPaint) else soft.canvas.drawRoundRect(rect, radius, radius, softPaint)
    }
    return soft.bitmap to strong.bitmap
  }

  /** Dashed horizontal line at [fraction] of the height from the top (tint: outline). */
  fun dashedLine(context: Context, fraction: Float, widthDp: Int, heightDp: Int): Bitmap {
    val p = Px(context, widthDp, heightDp)
    val paint = stroke(1.2f * p.d).apply {
      strokeCap = Paint.Cap.BUTT
      pathEffect = DashPathEffect(floatArrayOf(4 * p.d, 3 * p.d), 0f)
    }
    val y = (fraction * p.h).coerceIn(1f, p.h - 1f)
    p.canvas.drawLine(0f, y, p.w.toFloat(), y, paint)
    return p.bitmap
  }

  /** One hour of heart rate: [slot] 0–23, minimum, maximum and average bpm. */
  data class HourBpm(val slot: Int, val min: Double, val max: Double, val avg: Double)

  /**
   * Today's heart rate by hour between [low] and [high] bpm: soft min–max bars (first mask) and the hourly
   * average as a line broken at hours without data (second mask).
   */
  fun heartDay(context: Context, hours: List<HourBpm>, low: Double, high: Double, widthDp: Int, heightDp: Int): Pair<Bitmap, Bitmap> {
    val ranges = Px(context, widthDp, heightDp)
    val avg = Px(context, widthDp, heightDp)
    val d = ranges.d
    val slot = ranges.w / 24f
    val barW = slot * 0.56f
    val pad = 3 * d
    val span = (high - low).coerceAtLeast(1.0)
    fun y(v: Double) = (pad + (high - v) / span * (ranges.h - 2 * pad)).toFloat()
    val soft = fill(SOFT_ALPHA)
    hours.forEach { h ->
      val cx = slot * h.slot + slot / 2
      val top = y(h.max)
      val bottom = maxOf(y(h.min), top + 2 * d)
      ranges.canvas.drawRoundRect(RectF(cx - barW / 2, top, cx + barW / 2, bottom), barW / 2, barW / 2, soft)
    }
    val line = stroke(1.8f * d)
    val dot = fill()
    val path = Path()
    var previous: Int? = null
    hours.sortedBy { it.slot }.forEach { h ->
      val x = slot * h.slot + slot / 2
      if (previous == h.slot - 1) path.lineTo(x, y(h.avg)) else path.moveTo(x, y(h.avg))
      avg.canvas.drawCircle(x, y(h.avg), 1.8f * d, dot)
      previous = h.slot
    }
    avg.canvas.drawPath(path, line)
    return ranges.bitmap to avg.bitmap
  }
}
