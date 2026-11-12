package cz.healthwidgy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import cz.healthwidgy.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Redraws widgets. Glance normally renders in a WorkManager job (SessionWorker); One UI stops such jobs
 * about a second after the app leaves the foreground ("olaf" in the job history), so a widget saved in
 * the configuration screen kept its old look. Widgets are therefore rendered here, in the calling process,
 * and handed to the launcher directly.
 */
object WidgetUpdater {
  private const val TAG = "WidgetUpdater"

  /** Bumped on every update; Glance sessions that happen to run reload their snapshot when it changes. */
  private val _tick = MutableStateFlow(0)
  val tick: StateFlow<Int> = _tick.asStateFlow()

  /** Re-renders every placed widget (after a sync or a settings change). */
  suspend fun updateAll(context: Context) {
    _tick.update { it + 1 }
    val manager = AppWidgetManager.getInstance(context)
    WidgetCatalog.all.forEach { info ->
      manager.getAppWidgetIds(ComponentName(context, info.receiver)).forEach { id -> render(context, id, info) }
    }
  }

  /**
   * Renders one widget now and pushes it to the launcher. Returns false when it could not be drawn (the
   * widget was removed meanwhile, or it is not one of ours).
   */
  suspend fun render(context: Context, appWidgetId: Int, info: WidgetInfo? = null): Boolean =
    // Glance allows one composition per widget at a time ("Another session for N has started"), and a
    // sync, a resize and the configuration screen can ask at the same moment.
    renderLock.withLock { renderLocked(context, appWidgetId, info) }

  private val renderLock = Mutex()

  @OptIn(ExperimentalGlanceApi::class)
  private suspend fun renderLocked(context: Context, appWidgetId: Int, info: WidgetInfo?): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    val provider = manager.getAppWidgetInfo(appWidgetId) ?: return false
    val widgetInfo = info ?: WidgetCatalog.byProvider(provider.provider.className) ?: return false
    return try {
      val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
      val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
      val options = manager.getAppWidgetOptions(appWidgetId)
      // Before the launcher reports sizes (e.g. during placement) draw at the widget's minimum size.
      val size = if (hasSizes(options)) null else DpSize(px2dp(context, provider.minWidth), px2dp(context, provider.minHeight))
      val views = widgetInfo.create().compose(context, glanceId, options, size, state)
      manager.updateAppWidget(appWidgetId, views)
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Widget $appWidgetId not rendered", e)
      false
    }
  }

  private fun hasSizes(options: Bundle): Boolean =
    !options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java).isNullOrEmpty() ||
      options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0

  private fun px2dp(context: Context, px: Int) = (px / context.resources.displayMetrics.density).dp

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
