package cz.healthwidgy.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.data.hc.FailureKind
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.overview.Overview
import cz.healthwidgy.data.settings.OverviewCard
import cz.healthwidgy.data.settings.OverviewCardType
import cz.healthwidgy.data.settings.OverviewLayout
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.settings.SourceMode
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.DayBars
import cz.healthwidgy.ui.components.ProgressRing
import cz.healthwidgy.ui.components.SegmentBar
import cz.healthwidgy.ui.components.Sparkline
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.components.TileHeader
import cz.healthwidgy.ui.history.MetricDefs
import cz.healthwidgy.ui.theme.LocalMetricColors
import java.time.Duration
import java.time.ZoneId
import kotlin.math.roundToLong

/** Where the dashboard cards lead. */
data class OverviewActions(
  val openMetric: (String) -> Unit,
  val openHeart: () -> Unit,
  val openSleep: () -> Unit,
  val openBody: () -> Unit,
  val openWorkout: (String) -> Unit,
  /** Also refreshes the local history cache. */
  val syncHistory: () -> Unit,
  /** "Upravit přehled": which cards, in which order and size. */
  val editLayout: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(viewModel: OverviewViewModel, contentPadding: PaddingValues, actions: OverviewActions, settings: Settings?) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }

  PullToRefreshBox(
    isRefreshing = state.loading && state.overview != null,
    onRefresh = {
      viewModel.refresh(force = true)
      actions.syncHistory()
    },
    modifier = Modifier.fillMaxSize().padding(contentPadding),
  ) {
    val overview = state.overview
    when {
      overview == null && state.loading ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      overview == null ->
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Data se nepodařilo načíst: ${state.error}", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { viewModel.refresh(force = true) }) { Text("Zkusit znovu") }
          }
        }
      else -> OverviewContent(overview, state.error, actions, settings, onRefresh = { viewModel.refresh(force = true) })
    }
  }
}

@Composable
private fun OverviewContent(overview: Overview, error: String?, actions: OverviewActions, settings: Settings?, onRefresh: () -> Unit) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item { Header(overview, onRefresh, actions.editLayout) }
    error?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }

    if (!overview.hasAnyData) {
      item { NoDataCard(overview, onRefresh) }
      item { Footer(overview) }
      return@LazyColumn
    }

    // Settings are read a moment after the screen is (re)created, e.g. after rotating the phone: until
    // then no cards rather than a layout the user does not have.
    val cards = settings?.overviewCards
    when {
      cards == null -> Unit
      cards.isEmpty() -> item { EmptyLayoutCard(actions.editLayout) }
      else -> item { CardFlow(cards.map { card -> FlowCard(card.wide) { OverviewCardContent(card, overview, settings, actions) } }) }
    }

    item { Footer(overview) }
  }
}

@Composable
private fun Header(overview: Overview, onRefresh: () -> Unit, onEdit: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.weight(1f)) {
        Text(Format.longDate(overview.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Dnes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
      }
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        IconButton(onClick = onEdit) { AppIcon(R.drawable.ic_tune, contentDescription = "Upravit přehled", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface) }
      }
      Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        IconButton(onClick = onRefresh) { AppIcon(R.drawable.ic_sync, contentDescription = "Obnovit", size = 20.dp, tint = MaterialTheme.colorScheme.onSurface) }
      }
    }
    val source = if (overview.sourceMode == SourceMode.GARMIN_ONLY) "Data z Garminu" else "Data ze všech zdrojů"
    val written = overview.latestSourceWrite?.let { " ${Format.dayAndTime(it).replaceFirstChar { c -> c.lowercase() }}" } ?: ""
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
      Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(6.dp).background(LocalMetricColors.current.steps, CircleShape))
        Text(
          "$source$written · obnoveno ${Format.time(overview.refreshedAt)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}

@Composable
private fun HeroCard(overview: Overview, onClick: () -> Unit) {
  val colors = LocalMetricColors.current
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      val steps = overview.steps ?: 0L
      ProgressRing(
        progress = if (overview.stepsUnknown) 0f else steps.toFloat() / overview.stepGoal,
        color = colors.steps,
        modifier = Modifier.size(116.dp),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(if (overview.stepsUnknown) "–" else Format.int(steps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text("z ${Format.int(overview.stepGoal.toLong())} kroků", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        overview.distanceMeters?.let { HeroStat(R.drawable.ic_route, colors.distance, Format.km(it), "km") }
        overview.floors?.let {
          val n = it.roundToLong()
          HeroStat(R.drawable.ic_stairs, colors.body, Format.int(n), Format.plural(n, "patro", "patra", "pater"))
        }
        overview.totalKcal?.let { HeroStat(R.drawable.ic_flame, colors.calories, Format.int(it), "kcal") }
      }
    }
  }
}

@Composable
private fun HeroStat(icon: Int, color: Color, value: String, unit: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    AppIcon(icon, tint = color)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** One card of the layout: its size and content. */
private class FlowCard(val wide: Boolean, val content: @Composable () -> Unit)

/**
 * The cards in the user's order ([OverviewLayout.slots]): wide ones across the width, half ones two per
 * row. Two half cards side by side are as tall as the taller of them, so neighbours line up even when
 * one has less to show (its content stays at the top); other rows keep their own height.
 */
@Composable
private fun CardFlow(cards: List<FlowCard>, rowGap: Dp = 10.dp, columnGap: Dp = 8.dp) {
  val slots = OverviewLayout.slots(cards.map { it.wide })
  Layout(
    content = { cards.forEach { card -> Box(propagateMinConstraints = true) { card.content() } } },
    modifier = Modifier.fillMaxWidth(),
  ) { measurables, constraints ->
    val full = constraints.maxWidth
    val columnPx = columnGap.roundToPx()
    val rowPx = rowGap.roundToPx()
    val half = (full - columnPx) / 2
    val rowCount = (slots.maxOfOrNull { it.row } ?: -1) + 1
    // Height of the half cards in each row: the taller neighbour's.
    val halfHeights = IntArray(rowCount)
    measurables.forEachIndexed { i, m ->
      if (!cards[i].wide) halfHeights[slots[i].row] = maxOf(halfHeights[slots[i].row], m.maxIntrinsicHeight(half))
    }
    val placeables =
      measurables.mapIndexed { i, m ->
        if (cards[i].wide) m.measure(Constraints(minWidth = full, maxWidth = full))
        else m.measure(Constraints.fixed(half, halfHeights[slots[i].row]))
      }
    val rowHeights = IntArray(rowCount)
    placeables.forEachIndexed { i, p -> rowHeights[slots[i].row] = maxOf(rowHeights[slots[i].row], p.height) }
    val rowTops = IntArray(rowCount)
    var y = 0
    for (row in 0 until rowCount) {
      rowTops[row] = y
      y += rowHeights[row] + rowPx
    }
    layout(full, if (rowCount == 0) 0 else y - rowPx) {
      placeables.forEachIndexed { i, p -> p.place(x = slots[i].column * (half + columnPx), y = rowTops[slots[i].row]) }
    }
  }
}

/** One card of the layout in its size; a card without data says so instead of disappearing. */
@Composable
private fun OverviewCardContent(card: OverviewCard, overview: Overview, settings: Settings?, actions: OverviewActions) {
  val openSteps = { actions.openMetric(MetricDefs.Steps.id) }
  when (card.type) {
    OverviewCardType.STEPS -> if (card.wide) HeroCard(overview, openSteps) else StepsTile(overview, openSteps)
    OverviewCardType.HEART ->
      if (overview.heart != null || overview.restingHeartRate != null) HeartTile(overview, actions.openHeart) else EmptyTile(card.type, actions.openHeart)
    OverviewCardType.SLEEP -> if (overview.sleep != null) SleepTile(overview, actions.openSleep) else EmptyTile(card.type, actions.openSleep)
    OverviewCardType.CALORIES -> {
      val open = { actions.openMetric(MetricDefs.TotalKcal.id) }
      if (overview.totalKcal != null || overview.activeKcal != null) CaloriesTile(overview, open) else EmptyTile(card.type, open)
    }
    OverviewCardType.WEIGHT -> if (overview.weight != null) WeightTile(overview, actions.openBody) else EmptyTile(card.type, actions.openBody)
    OverviewCardType.DISTANCE -> {
      val open = { actions.openMetric(MetricDefs.Distance.id) }
      val goalKm = settings?.distanceGoalKm ?: Settings.DEFAULT_DISTANCE_GOAL_KM
      overview.distanceMeters?.let { GoalTile(card.type, Format.km(it), "km", it / 1000 / goalKm, "cíl $goalKm km", open) } ?: EmptyTile(card.type, open)
    }
    OverviewCardType.FLOORS -> {
      val open = { actions.openMetric(MetricDefs.Floors.id) }
      val goal = settings?.floorsGoal ?: Settings.DEFAULT_FLOORS_GOAL
      overview.floors?.let {
        val n = it.roundToLong()
        GoalTile(card.type, Format.int(n), Format.plural(n, "patro", "patra", "pater"), it / goal, "cíl $goal ${Format.plural(goal.toLong(), "patro", "patra", "pater")}", open)
      } ?: EmptyTile(card.type, open)
    }
    OverviewCardType.WORKOUT -> {
      val w = overview.workout
      if (w == null) EmptyTile(card.type, null, "Žádný trénink za posledních 30 dní")
      else if (card.wide) WorkoutCard(overview) { actions.openWorkout(w.id) }
      else WorkoutTile(overview) { actions.openWorkout(w.id) }
    }
    OverviewCardType.STEPS_WEEK ->
      if (overview.steps7Days.any { it.value != null }) StepsWeekCard(overview, card.wide, openSteps) else EmptyTile(card.type, openSteps)
  }
}

private fun OverviewCardType.icon(): Int =
  when (this) {
    OverviewCardType.STEPS, OverviewCardType.STEPS_WEEK -> R.drawable.ic_walk
    OverviewCardType.HEART -> R.drawable.ic_heart
    OverviewCardType.SLEEP -> R.drawable.ic_moon
    OverviewCardType.CALORIES -> R.drawable.ic_flame
    OverviewCardType.WEIGHT -> R.drawable.ic_scale
    OverviewCardType.DISTANCE -> R.drawable.ic_route
    OverviewCardType.FLOORS -> R.drawable.ic_stairs
    OverviewCardType.WORKOUT -> R.drawable.ic_run
  }

/** Icon and color of a card type, also used by the layout editor. */
@Composable
fun OverviewCardType.iconAndColor(): Pair<Int, Color> {
  val colors = LocalMetricColors.current
  return icon() to
    when (this) {
      OverviewCardType.STEPS, OverviewCardType.STEPS_WEEK -> colors.steps
      OverviewCardType.HEART -> colors.heart
      OverviewCardType.SLEEP -> colors.sleep
      OverviewCardType.CALORIES -> colors.calories
      OverviewCardType.WEIGHT, OverviewCardType.FLOORS -> colors.body
      OverviewCardType.DISTANCE -> colors.distance
      OverviewCardType.WORKOUT -> MaterialTheme.colorScheme.primary
    }
}

/** A card the user put on the layout but nothing to show yet (e.g. no weighing in 90 days). */
@Composable
private fun EmptyTile(type: OverviewCardType, onClick: (() -> Unit)?, text: String = "Zatím bez dat") {
  val (icon, color) = type.iconAndColor()
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(icon, type.label, color)
    Spacer(Modifier.height(6.dp))
    // Two lines: a half card is narrow ("Žádný trénink za posledních 30 dní").
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
}

/** Every card was removed in "Upravit přehled". */
@Composable
private fun EmptyLayoutCard(onEdit: () -> Unit) {
  SurfaceCard(Modifier.fillMaxWidth()) {
    Text("Přehled je prázdný", style = MaterialTheme.typography.titleMedium)
    Text(
      "Všechny karty jsou skryté. Vyber, co tu chceš vidět.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) { Text("Upravit přehled") }
  }
}

/** Steps as a half card: the ring with today's count. */
@Composable
private fun StepsTile(overview: Overview, onClick: () -> Unit) {
  val colors = LocalMetricColors.current
  val steps = overview.steps ?: 0L
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(R.drawable.ic_walk, "Kroky", colors.steps)
    // About as tall as the Tep card, its usual neighbour.
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
      ProgressRing(
        progress = if (overview.stepsUnknown) 0f else steps.toFloat() / overview.stepGoal,
        color = colors.steps,
        modifier = Modifier.size(72.dp),
        strokeWidth = 7.dp,
      ) {
        Text(if (overview.stepsUnknown) "–" else Format.int(steps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
    TileSub("z ${Format.int(overview.stepGoal.toLong())} kroků")
  }
}

/** A value with a daily goal (distance, floors): the value, a progress bar and the goal. */
@Composable
private fun GoalTile(type: OverviewCardType, value: String, unit: String, progress: Double, goalText: String, onClick: () -> Unit) {
  val (icon, color) = type.iconAndColor()
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(icon, type.label, color)
    TileValue(value, unit)
    val done = progress.coerceIn(0.0, 1.0).toFloat()
    SegmentBar(listOf(done to color, (1f - done) to MaterialTheme.colorScheme.surfaceContainerHighest), Modifier.padding(vertical = 6.dp))
    TileSub("${(progress * 100).roundToLong()} % · $goalText")
  }
}

/** The latest workout as a half card: name on up to two lines, then when, how far and how long. */
@Composable
private fun WorkoutTile(overview: Overview, onClick: () -> Unit) {
  val w = overview.workout ?: return
  val (icon, color) = OverviewCardType.WORKOUT.iconAndColor()
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(icon, "Trénink", color)
    Text(
      w.title?.takeIf { it.isNotBlank() } ?: Format.exerciseName(w.exerciseType),
      style = MaterialTheme.typography.titleSmall,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(vertical = 4.dp),
    )
    TileSub(Format.dayAndTime(w.start))
    TileSub(listOfNotNull(w.distanceMeters?.takeIf { it >= 100 }?.let { "${Format.km(it)} km" }, Format.clock(Duration.between(w.start, w.end).seconds)).joinToString(" · "))
    TileSub(listOfNotNull(w.avgHeartRate?.let { "Ø $it bpm" }, w.activeKcal?.let { "${Format.int(it)} kcal" }).joinToString(" · "))
  }
}

/** Steps of the last 7 days with the goal line; as a half card the average moves under the chart. */
@Composable
private fun StepsWeekCard(overview: Overview, wide: Boolean, onClick: () -> Unit) {
  val colors = LocalMetricColors.current
  // Average of finished days only: today is still in progress.
  val finished = overview.steps7Days.filter { it.date != overview.date }.mapNotNull { it.value }
  val average = finished.takeIf { it.isNotEmpty() }?.let { "Ø ${Format.int(it.average())} / den" }
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = if (wide) 14.dp else 12.dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      TileHeader(R.drawable.ic_walk, "Kroky · 7 dní", colors.steps)
      if (wide && average != null) Text(average, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    DayBars(
      values = overview.steps7Days.map { it.value },
      labels = overview.steps7Days.map { Format.weekdayShort(it.date) },
      color = colors.steps,
      goal = overview.stepGoal.toDouble(),
      barHeight = if (wide) 64.dp else 48.dp,
    )
    if (!wide && average != null) TileSub(average)
  }
}

@Composable
private fun TileValue(value: String, unit: String) {
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
  }
}

@Composable
private fun TileSub(text: String) {
  Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HeartTile(overview: Overview, onClick: () -> Unit) {
  val color = LocalMetricColors.current.heart
  val heart = overview.heart
  val resting = overview.restingHeartRate
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(R.drawable.ic_heart, if (heart?.latestBpm != null) "Tep" else "Klidový tep", color)
    if (heart?.latestBpm != null) {
      TileValue(heart.latestBpm.toString(), "bpm")
      TileSub(
        buildString {
          heart.latestTime?.let { append("${Format.time(it)} · ") }
          if (heart.minBpm != null && heart.maxBpm != null) append("dnes ${heart.minBpm}–${heart.maxBpm}")
        }
      )
    } else if (resting != null) {
      TileValue(resting.bpm.toString(), "bpm")
    }
    if (resting != null) {
      TileSub("klidový ${resting.bpm}${if (resting.isEstimate) " (odhad)" else ""}")
      if (resting.last7Days.count { it.value != null } > 1) {
        Sparkline(resting.last7Days.map { it.value }, color, Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp))
      }
    }
  }
}

@Composable
private fun SleepTile(overview: Overview, onClick: () -> Unit) {
  val colors = LocalMetricColors.current
  val sleep = overview.sleep ?: return
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(R.drawable.ic_moon, "Spánek", colors.sleep)
    TileValue("${sleep.asleepMinutes / 60} h ${sleep.asleepMinutes % 60}", "min")
    val stages = sleep.stages
    if (stages != null) {
      SegmentBar(
        listOf(
          stages.deepMinutes.toFloat() to colors.stageDeep,
          stages.lightMinutes.toFloat() to colors.stageLight,
          (stages.remMinutes + stages.unspecifiedMinutes).toFloat() to colors.stageRem,
          stages.awakeMinutes.toFloat() to colors.stageAwake,
        ),
        Modifier.padding(vertical = 6.dp),
      )
    }
    // An older night (e.g. last night not synced yet) carries its date so it is not mistaken for today's.
    val endedToday = sleep.end.atZone(ZoneId.systemDefault()).toLocalDate() == overview.date
    TileSub(
      if (endedToday) "${Format.time(sleep.start)} – ${Format.time(sleep.end)}"
      else "${Format.dayAndTime(sleep.start)} – ${Format.time(sleep.end)}"
    )
  }
}

@Composable
private fun CaloriesTile(overview: Overview, onClick: () -> Unit) {
  val colors = LocalMetricColors.current
  val total = overview.totalKcal
  val active = overview.activeKcal
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(R.drawable.ic_flame, "Kalorie", colors.calories)
    TileValue(Format.int(total ?: active ?: 0.0), "kcal")
    // Garmin writes active calories only for workouts, so the highlighted part is "burned while training".
    if (total != null && active != null && total > 0) {
      SegmentBar(
        listOf(active.toFloat() to colors.calories, (total - active).coerceAtLeast(0.0).toFloat() to MaterialTheme.colorScheme.surfaceContainerHighest),
        Modifier.padding(vertical = 6.dp),
      )
      TileSub("z toho ${Format.int(active)} při tréninku")
    } else if (total != null) {
      TileSub("celkový výdej za den")
    } else {
      TileSub("při tréninku")
    }
  }
}

@Composable
private fun WeightTile(overview: Overview, onClick: () -> Unit) {
  val color = LocalMetricColors.current.body
  val weight = overview.weight ?: return
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
    TileHeader(R.drawable.ic_scale, "Váha", color)
    TileValue(Format.decimal(weight.kilograms), "kg")
    if (weight.recent.size > 1) Sparkline(weight.recent, color, Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp))
    TileSub(
      buildString {
        weight.deltaKilograms?.let { append(Format.signedKg(it)).append(" za týden · ") }
        append(Format.dayAndTime(weight.time).substringBeforeLast(' '))
        weight.bodyFatPercent?.let { append(" · tuk ${Format.decimal(it)} %") }
      }
    )
  }
}

@Composable
private fun WorkoutCard(overview: Overview, onClick: () -> Unit) {
  val w = overview.workout ?: return
  val seconds = Duration.between(w.start, w.end).seconds
  SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_run, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 22.dp) }
      }
      Column(Modifier.weight(1f)) {
        val name = w.title?.takeIf { it.isNotBlank() } ?: Format.exerciseName(w.exerciseType)
        Text(
          buildString {
            append(name)
            w.distanceMeters?.takeIf { it >= 100 }?.let { append(" · ${Format.km(it)} km") }
          },
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          buildString {
            append(Format.dayAndTime(w.start))
            append(" · ").append(Format.clock(seconds))
            w.avgHeartRate?.let { append(" · Ø $it bpm") }
            w.activeKcal?.let { append(" · ${Format.int(it)} kcal") }
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun NoDataCard(overview: Overview, onRefresh: () -> Unit) {
  val failed = overview.failures.isNotEmpty()
  SurfaceCard(Modifier.fillMaxWidth()) {
    Text(if (failed) "Data se nepodařilo načíst" else "Zatím tu nejsou žádná data", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
      when {
        failed -> "Health Connect je možná zaneprázdněné nebo chybí oprávnění. Zkus obnovit za chvíli, případně zkontroluj oprávnění v Nastavení."
        overview.sourceMode == SourceMode.GARMIN_ONLY ->
          "Health Connect zatím neobsahuje žádná aktuální data z Garmin Connect. Zkontroluj v aplikaci Garmin Connect: Více → Nastavení → Propojené aplikace → Health Connect, a pak synchronizuj hodinky."
        else -> "Health Connect zatím neobsahuje žádná aktuální data, která by aplikace mohla číst."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (failed) TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) { Text("Zkusit znovu") }
  }
}

@Composable
private fun Footer(overview: Overview) {
  fun labels(ids: Collection<String>) = ids.mapNotNull { id -> HcTypes.all.firstOrNull { it.id == id }?.label }.joinToString(", ")
  val errors = overview.failures.filterValues { it != FailureKind.NoPermission }.keys
  val denied = overview.notGranted + overview.failures.filterValues { it == FailureKind.NoPermission }.keys
  Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (errors.isNotEmpty()) {
      Text("Nepodařilo se načíst: ${labels(errors)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (denied.isNotEmpty()) {
      Text("Bez oprávnění: ${labels(denied)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
      if (overview.sourceMode == SourceMode.GARMIN_ONLY) "Zdroj: Garmin Connect přes Health Connect" else "Zdroj: všechny aplikace v Health Connect",
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
