package cz.healthwidgy.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import cz.healthwidgy.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Re-renders all widgets after the cache changed (sync, settings). */
object WidgetUpdater {
  /** Bumped on every update; running widget sessions reload their snapshot when it changes. */
  private val _tick = MutableStateFlow(0)
  val tick: StateFlow<Int> = _tick.asStateFlow()

  private fun widgets(): List<GlanceAppWidget> =
    listOf(TodayWidget(), StepsWidget(), SleepWidget(), HeartWidget(), StepsTrendWidget(), SingleValueWidget(), LastWorkoutWidget())

  suspend fun updateAll(context: Context) {
    _tick.update { it + 1 }
    widgets().forEach { it.updateAll(context) }
  }

  /**
   * Android 15+: publishes previews rendered from the real data to the widget picker (rate limited by
   * the system, so only call it occasionally, e.g. once per app start).
   */
  suspend fun publishPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val manager = GlanceAppWidgetManager(context)
    listOf(
      TodayWidgetReceiver::class,
      StepsWidgetReceiver::class,
      SleepWidgetReceiver::class,
      HeartWidgetReceiver::class,
      StepsTrendWidgetReceiver::class,
      SingleValueWidgetReceiver::class,
      LastWorkoutWidgetReceiver::class,
    ).forEach { receiver -> runCatching { manager.setWidgetPreviews(receiver) } }
  }
}

/** Tap on "↻" in a widget: sync from Health Connect now and redraw. */
class RefreshCallback : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    context.appContainer.syncEngine.sync(force = true)
    WidgetUpdater.updateAll(context)
  }
}
