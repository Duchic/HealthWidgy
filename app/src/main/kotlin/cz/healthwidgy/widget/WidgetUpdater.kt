package cz.healthwidgy.widget

import android.content.Context
import android.os.Build
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
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

  suspend fun updateAll(context: Context) {
    _tick.update { it + 1 }
    WidgetCatalog.all.forEach { it.create().updateAll(context) }
  }

  /**
   * Android 15+: publishes previews rendered from the real data to the widget picker. The system allows
   * only a couple of calls per widget and hour, so call it occasionally (app start, widget theme change).
   * Returns false when any preview was rejected (rate limited or failed), so the caller can try later.
   */
  suspend fun publishPreviews(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return true
    val manager = GlanceAppWidgetManager(context)
    var allSet = true
    WidgetCatalog.all.forEach { info ->
      val result = runCatching { manager.setWidgetPreviews(info.receiver.kotlin) }.getOrNull()
      if (result != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) allSet = false
    }
    return allSet
  }
}

/** Tap on "↻" in a widget: sync from Health Connect now and redraw. */
class RefreshCallback : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    context.appContainer.syncEngine.sync(force = true)
    WidgetUpdater.updateAll(context)
  }
}
