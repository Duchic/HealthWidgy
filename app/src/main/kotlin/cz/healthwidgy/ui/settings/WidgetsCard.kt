package cz.healthwidgy.ui.settings

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.LifecycleResumeEffect
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.widget.WidgetCatalog
import cz.healthwidgy.widget.WidgetChoices
import cz.healthwidgy.widget.WidgetConfigActivity
import cz.healthwidgy.widget.WidgetInfo

/** A configurable widget already on the home screen. */
private data class PlacedWidget(val info: WidgetInfo, val appWidgetId: Int, val summary: String)

private suspend fun placedWidgets(context: Context): List<PlacedWidget> {
  val manager = GlanceAppWidgetManager(context)
  return WidgetCatalog.all.flatMap { info ->
    val config = info.config ?: return@flatMap emptyList()
    manager.getGlanceIds(info.create().javaClass).map { id ->
      val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
      PlacedWidget(info, manager.getAppWidgetId(id), WidgetChoices.summary(prefs, config))
    }
  }
}

/**
 * Widgets already on the home screen (with "Upravit" for the configurable ones, which works on any
 * launcher) and the list of all widgets with a button that asks the launcher to place one.
 */
@Composable
fun WidgetsCard() {
  val context = LocalContext.current
  var refresh by remember { mutableIntStateOf(0) }
  // Coming back from the launcher or the configuration screen: re-read what is placed.
  LifecycleResumeEffect(Unit) {
    refresh++
    onPauseOrDispose {}
  }
  val placed by produceState(emptyList<PlacedWidget>(), refresh) { value = runCatching { placedWidgets(context) }.getOrDefault(emptyList()) }

  SurfaceCard(Modifier.fillMaxWidth()) {
    Text("Widgety na plochu", style = MaterialTheme.typography.titleMedium)
    Text(
      "Ťukni na Přidat, nebo dlouze podrž prázdné místo na ploše → Widgety → HealthWidgy. Co widget ukazuje, vybereš při přidání a později tady přes Upravit. Widgety se obnovují zhruba jednou za hodinu, při otevření aplikace a ťuknutím na ↻.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 4.dp),
    )
    if (placed.isNotEmpty()) {
      Text("Na ploše", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
      placed.forEach { p ->
        WidgetRow(p.info.name, p.summary, "Upravit") {
          context.startActivity(
            Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.appWidgetId)
          )
        }
      }
      Text("Přidat další", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    }
    WidgetCatalog.all.forEach { info -> WidgetRow("${info.name} · ${info.size}", info.description, "Přidat") { pin(context, info) } }
  }
}

@Composable
private fun WidgetRow(title: String, text: String, action: String, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    TextButton(onClick = onClick) { Text(action) }
  }
}

private fun pin(context: Context, info: WidgetInfo) {
  val manager = context.getSystemService(AppWidgetManager::class.java)
  if (manager?.isRequestPinAppWidgetSupported != true) {
    Toast.makeText(context, "Tento launcher přidání z aplikace nepodporuje. Přidej widget z plochy.", Toast.LENGTH_LONG).show()
    return
  }
  // The launcher does not open the configuration screen for widgets pinned from an app: the callback
  // (sent with the new widget's id) opens it instead. The app is visible then, so it may start an activity.
  val callback =
    info.config?.let {
      val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        else @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
      PendingIntent.getActivity(
        context,
        info.receiver.name.hashCode(),
        Intent(context, WidgetConfigActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(mode).toBundle(),
      )
    }
  manager.requestPinAppWidget(ComponentName(context, info.receiver), null, callback)
}
