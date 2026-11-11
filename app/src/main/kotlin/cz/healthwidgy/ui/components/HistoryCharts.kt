package cz.healthwidgy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.healthwidgy.data.history.SleepStage
import java.time.Instant

/** Labels under a chart; null entries leave their slot empty (used to thin out dense axes). */
@Composable
fun ChartLabels(labels: List<String?>, modifier: Modifier = Modifier) {
  if (labels.all { it == null }) return
  Row(modifier.fillMaxWidth()) {
    labels.forEach { label ->
      Text(
        label ?: "",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

private fun Modifier.tapIndex(count: Int, onSelect: ((Int) -> Unit)?): Modifier =
  if (onSelect == null || count == 0) this
  else
    this.pointerInput(count) {
      detectTapGestures { offset -> onSelect((offset.x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)) }
    }

/** Bars per slot (day, hour, month). The selected bar is drawn in full color, others lighter. */
@Composable
fun BarChart(
  values: List<Double?>,
  labels: List<String?>,
  color: Color,
  modifier: Modifier = Modifier,
  goal: Double? = null,
  selected: Int? = null,
  onSelect: ((Int) -> Unit)? = null,
  height: Dp = 160.dp,
) {
  val track = MaterialTheme.colorScheme.surfaceContainerHighest
  val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
  val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
  val highlight = selected ?: values.indexOfLast { it != null }
  Column(modifier) {
    Canvas(Modifier.fillMaxWidth().height(height).tapIndex(values.size, onSelect)) {
      if (values.isEmpty()) return@Canvas
      val slot = size.width / values.size
      val barWidth = (slot * 0.62f).coerceIn(1.dp.toPx(), 26.dp.toPx())
      val radius = CornerRadius((barWidth / 2).coerceAtMost(4.dp.toPx()))
      values.forEachIndexed { i, v ->
        val left = i * slot + (slot - barWidth) / 2
        if (v == null || v <= 0.0) {
          drawRoundRect(track, topLeft = Offset(left, size.height - 2.dp.toPx()), size = Size(barWidth, 2.dp.toPx()))
        } else {
          val h = (v / max * size.height).toFloat().coerceAtLeast(2.dp.toPx())
          drawRoundRect(color.copy(alpha = if (i == highlight) 1f else 0.42f), topLeft = Offset(left, size.height - h), size = Size(barWidth, h), cornerRadius = radius)
        }
      }
      goal?.let { drawGoal(it, max, goalColor) }
    }
    ChartLabels(labels, Modifier.padding(top = 4.dp))
  }
}

private fun DrawScope.drawGoal(goal: Double, max: Double, color: Color) {
  val y = (size.height - goal / max * size.height).toFloat()
  drawLine(color.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
}

/**
 * Line through [values] (gaps where null), optionally over a min–max [band] per slot. The y range is fitted to
 * the data with some padding, so small changes (weight, resting heart rate) stay visible.
 */
@Composable
fun LineChart(
  values: List<Double?>,
  labels: List<String?>,
  color: Color,
  modifier: Modifier = Modifier,
  band: List<Pair<Double, Double>?>? = null,
  selected: Int? = null,
  onSelect: ((Int) -> Unit)? = null,
  height: Dp = 160.dp,
  showPoints: Boolean = true,
) {
  val grid = MaterialTheme.colorScheme.surfaceContainerHighest
  val all = values.filterNotNull() + band.orEmpty().filterNotNull().flatMap { listOf(it.first, it.second) }
  Column(modifier) {
    Canvas(Modifier.fillMaxWidth().height(height).tapIndex(values.size, onSelect)) {
      if (all.isEmpty() || values.isEmpty()) return@Canvas
      val lo = all.min()
      val hi = all.max()
      val pad = ((hi - lo) * 0.15).coerceAtLeast(1.0)
      val yMin = lo - pad
      val yMax = hi + pad
      val slot = size.width / values.size
      fun x(i: Int) = i * slot + slot / 2
      fun y(v: Double) = (size.height - (v - yMin) / (yMax - yMin) * size.height).toFloat()
      // Faint grid at three levels.
      for (k in 1..3) drawLine(grid, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), strokeWidth = 1.dp.toPx())
      band?.forEachIndexed { i, b ->
        if (b != null) {
          val w = (slot * 0.5f).coerceIn(2.dp.toPx(), 10.dp.toPx())
          val top = y(b.second)
          val bottom = y(b.first)
          drawRoundRect(color.copy(alpha = 0.22f), topLeft = Offset(x(i) - w / 2, top), size = Size(w, (bottom - top).coerceAtLeast(2.dp.toPx())), cornerRadius = CornerRadius(w / 2))
        }
      }
      var path: Path? = null
      values.forEachIndexed { i, v ->
        if (v == null) {
          path?.let { drawPath(it, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
          path = null
        } else {
          val p = path
          if (p == null) path = Path().apply { moveTo(x(i), y(v)) } else p.lineTo(x(i), y(v))
        }
      }
      path?.let { drawPath(it, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
      if (showPoints) {
        values.forEachIndexed { i, v ->
          if (v != null) drawCircle(color, radius = if (i == selected) 4.5.dp.toPx() else 2.5.dp.toPx(), center = Offset(x(i), y(v)))
        }
      } else selected?.let { i -> values.getOrNull(i)?.let { drawCircle(color, 4.5.dp.toPx(), Offset(x(i), y(it))) } }
    }
    ChartLabels(labels, Modifier.padding(top = 4.dp))
  }
}

/** Time-based line (workout heart rate, speed): x is time between [start] and [end]. */
@Composable
fun TimeLineChart(points: List<Pair<Instant, Double>>, start: Instant, end: Instant, color: Color, modifier: Modifier = Modifier, height: Dp = 140.dp) {
  val grid = MaterialTheme.colorScheme.surfaceContainerHighest
  Canvas(modifier.fillMaxWidth().height(height)) {
    if (points.size < 2) return@Canvas
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }
    val pad = ((hi - lo) * 0.1).coerceAtLeast(1.0)
    val span = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1)
    fun x(t: Instant) = ((t.toEpochMilli() - start.toEpochMilli()).toFloat() / span * size.width)
    fun y(v: Double) = (size.height - (v - (lo - pad)) / ((hi + pad) - (lo - pad)) * size.height).toFloat()
    for (k in 1..3) drawLine(grid, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), strokeWidth = 1.dp.toPx())
    val path = Path()
    points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
    val fill = Path().apply {
      addPath(path)
      lineTo(x(points.last().first), size.height)
      lineTo(x(points.first().first), size.height)
      close()
    }
    drawPath(fill, color.copy(alpha = 0.14f))
    drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** Sleep stages as blocks on four lanes (awake, REM, light, deep) with lane labels. */
@Composable
fun Hypnogram(
  stages: List<SleepStage>,
  start: Instant,
  end: Instant,
  laneColors: List<Color>,
  laneLabels: List<String>,
  laneOf: (Int) -> Int?,
  modifier: Modifier = Modifier,
  height: Dp = 132.dp,
) {
  val grid = MaterialTheme.colorScheme.surfaceContainerHighest
  Row(modifier.fillMaxWidth()) {
    Column(Modifier.width(54.dp).height(height)) {
      laneLabels.forEach { label ->
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Canvas(Modifier.weight(1f).height(height)) {
      val lanes = laneLabels.size
      val laneH = size.height / lanes
      for (k in 1 until lanes) drawLine(grid, Offset(0f, laneH * k), Offset(size.width, laneH * k), strokeWidth = 1.dp.toPx())
      val span = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1)
      stages.forEach { s ->
        val lane = laneOf(s.type) ?: return@forEach
        val x0 = (s.start.toEpochMilli() - start.toEpochMilli()).toFloat() / span * size.width
        val x1 = (s.end.toEpochMilli() - start.toEpochMilli()).toFloat() / span * size.width
        drawRoundRect(
          laneColors[lane],
          topLeft = Offset(x0, lane * laneH + 3.dp.toPx()),
          size = Size((x1 - x0 - 0.5f).coerceAtLeast(1f), laneH - 6.dp.toPx()),
          cornerRadius = CornerRadius(2.dp.toPx()),
        )
      }
    }
  }
}
