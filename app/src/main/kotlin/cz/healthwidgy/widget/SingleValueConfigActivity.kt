package cz.healthwidgy.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import cz.healthwidgy.ui.theme.HealthWidgyTheme
import kotlinx.coroutines.launch

/** Lets the user pick which value the "Jedna hodnota" widget shows (on placement and via reconfigure). */
class SingleValueConfigActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    // Backing out leaves the widget unplaced.
    setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }
    enableEdgeToEdge()
    setContent {
      HealthWidgyTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Co má widget ukazovat?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            SingleMetric.entries.forEach { metric ->
              Text(
                metric.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable { choose(appWidgetId, metric) }.padding(vertical = 14.dp),
              )
            }
          }
        }
      }
    }
  }

  private fun choose(appWidgetId: Int, metric: SingleMetric) {
    lifecycleScope.launch {
      val glanceId = GlanceAppWidgetManager(this@SingleValueConfigActivity).getGlanceIdBy(appWidgetId)
      updateAppWidgetState(this@SingleValueConfigActivity, glanceId) { prefs -> prefs[SingleValueWidget.METRIC_KEY] = metric.name }
      SingleValueWidget().update(this@SingleValueConfigActivity, glanceId)
      setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
      finish()
    }
  }
}
