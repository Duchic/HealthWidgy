package cz.healthwidgy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * Small bitmaps for widgets: Glance has no Canvas, and its progress indicators cannot show a value.
 * Each bitmap is drawn at the slot size (density-scaled), well below the widget bitmap memory budget.
 */
object WidgetBitmaps {
  /** Neutral track that works on light and dark widget backgrounds. */
  private const val TRACK = 0x33808080

  fun ring(context: Context, progress: Float, color: Int, sizeDp: Int, strokeDp: Float): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (sizeDp * d).toInt().coerceAtLeast(1)
    val stroke = strokeDp * d
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = stroke
    }
    paint.color = TRACK
    canvas.drawArc(rect, 0f, 360f, false, paint)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    if (sweep > 0f) {
      paint.color = color
      paint.strokeCap = Paint.Cap.ROUND
      canvas.drawArc(rect, -90f, sweep, false, paint)
    }
    return bitmap
  }

  /** A night in time order: each stage segment as a colored block, rounded strip, day/night colors. */
  fun hypnogram(context: Context, startMs: Long, endMs: Long, stages: List<Pair<Int, Pair<Long, Long>>>, widthDp: Int, heightDp: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (widthDp * d).toInt().coerceAtLeast(1)
    val h = (heightDp * d).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    val night =
      (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 6 * d, 6 * d, Path.Direction.CW) })
    val span = (endMs - startMs).coerceAtLeast(1).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    stages.forEach { (type, range) ->
      paint.color = stageArgb(type, night)
      val x0 = (range.first - startMs) / span * w
      val x1 = (range.second - startMs) / span * w
      canvas.drawRect(x0, 0f, maxOf(x1, x0 + 1f), h.toFloat(), paint)
    }
    return bitmap
  }

  private fun stageArgb(type: Int, night: Boolean): Int =
    when (type) {
      androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP -> if (night) 0xFF4257C9.toInt() else 0xFF2C3E9E.toInt()
      androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_REM -> if (night) 0xFFA796FF.toInt() else 0xFF8E7CF0.toInt()
      androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT,
      androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_SLEEPING -> if (night) 0xFF6E9CFF.toInt() else 0xFF5B8DEF.toInt()
      else -> if (night) 0xFFF2B45A.toInt() else 0xFFE59A2F.toInt()
    }

  /** Line through [values] (nulls skipped) with the last point emphasised. */
  fun sparkline(context: Context, values: List<Double?>, color: Int, widthDp: Int, heightDp: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (widthDp * d).toInt().coerceAtLeast(1)
    val h = (heightDp * d).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(w, h)
    val points = values.withIndex().filter { it.value != null }.map { it.index to it.value!! }
    if (points.isEmpty()) return bitmap
    val canvas = Canvas(bitmap)
    val pad = 4 * d
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val span = (values.size - 1).coerceAtLeast(1)
    fun x(i: Int) = pad + i * (w - 2 * pad) / span
    fun y(v: Double) = if (max == min) h / 2f else (pad + (max - v) * (h - 2 * pad) / (max - min)).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      style = Paint.Style.STROKE
      strokeWidth = 2 * d
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    if (points.size > 1) {
      val path = Path()
      points.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
      canvas.drawPath(path, paint)
    }
    paint.style = Paint.Style.FILL
    val (li, lv) = points.last()
    canvas.drawCircle(x(li), y(lv), 3 * d, paint)
    return bitmap
  }
}
