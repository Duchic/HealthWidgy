package cz.healthwidgy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Circular progress ring starting at 12 o'clock. [progress] is clamped to 0..1. */
@Composable
fun ProgressRing(
  progress: Float,
  color: Color,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = 10.dp,
  trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
  content: @Composable () -> Unit = {},
) {
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val stroke = strokeWidth.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
      val sweep = 360f * progress.coerceIn(0f, 1f)
      if (sweep > 0f) {
        drawArc(color, -90f, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }
    }
    content()
  }
}

/** A thin line through [values] with the last point emphasised; nulls break nothing, they are skipped. */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
  val points = values.withIndex().filter { it.value != null }.map { it.index to it.value!! }
  Canvas(modifier) {
    if (points.isEmpty()) return@Canvas
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val pad = 3.dp.toPx()
    val span = (values.size - 1).coerceAtLeast(1)
    fun x(i: Int) = pad + i * (size.width - 2 * pad) / span
    fun y(v: Double) = if (max == min) size.height / 2 else (pad + (max - v) * (size.height - 2 * pad) / (max - min)).toFloat()
    if (points.size > 1) {
      val path = Path()
      points.forEachIndexed { k, (i, v) -> if (k == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
      drawPath(path, color.copy(alpha = 0.9f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val (li, lv) = points.last()
    drawCircle(color, radius = 2.8.dp.toPx(), center = Offset(x(li), y(lv)))
  }
}

/** Horizontal bar split proportionally into colored parts (sleep stages, active vs. total calories). */
@Composable
fun SegmentBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
  val total = parts.sumOf { it.first.toDouble() }.toFloat()
  Row(
    modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    if (total <= 0f) {
      Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
      parts.filter { it.first > 0f }.forEach { (value, color) -> Box(Modifier.weight(value).fillMaxHeight().background(color)) }
    }
  }
}

/** Daily bars with labels; the last bar is emphasised and an optional dashed goal line is drawn. */
@Composable
fun DayBars(
  values: List<Double?>,
  labels: List<String>,
  color: Color,
  modifier: Modifier = Modifier,
  goal: Double? = null,
  barHeight: Dp = 64.dp,
) {
  val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
  val track = MaterialTheme.colorScheme.surfaceContainerHighest
  val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
  Column(modifier) {
    Canvas(Modifier.fillMaxWidth().height(barHeight)) {
      val slot = size.width / values.size
      val barWidth = (slot * 0.56f).coerceAtMost(22.dp.toPx())
      values.forEachIndexed { i, v ->
        val left = i * slot + (slot - barWidth) / 2
        val h = ((v ?: 0.0) / max * size.height).toFloat()
        val last = i == values.lastIndex
        if (v == null) {
          drawRoundRect(track, topLeft = Offset(left, size.height - 3.dp.toPx()), size = Size(barWidth, 3.dp.toPx()))
        } else {
          drawRoundRect(
            color.copy(alpha = if (last) 1f else 0.45f),
            topLeft = Offset(left, size.height - h),
            size = Size(barWidth, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
          )
        }
      }
      if (goal != null) {
        val gy = (size.height - goal / max * size.height).toFloat()
        drawLine(goalColor.copy(alpha = 0.6f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
      }
    }
    Row(Modifier.fillMaxWidth()) {
      labels.forEachIndexed { i, label ->
        Text(
          label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          fontWeight = if (i == labels.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
          color = if (i == labels.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
  }
}
