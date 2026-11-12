package cz.healthwidgy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import cz.healthwidgy.appContainer
import kotlinx.coroutines.launch

/**
 * Glance receiver that also renders the widget right away in this process when the system asks for an
 * update (placement, reboot, app update) or the widget is resized. Glance's own rendering runs in a
 * background job that One UI may stop before it draws (see [WidgetUpdater]).
 */
abstract class HealthWidgetReceiver : GlanceAppWidgetReceiver() {
  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    renderNow(context, appWidgetIds)
  }

  override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    renderNow(context, intArrayOf(appWidgetId))
  }

  private fun renderNow(context: Context, ids: IntArray) {
    val app = context.applicationContext
    app.appContainer.appScope.launch { ids.forEach { WidgetUpdater.render(app, it) } }
  }
}
