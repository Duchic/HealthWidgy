package cz.healthwidgy.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import cz.healthwidgy.ui.theme.HealthWidgyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Picks what a configurable widget shows: one metric per slot and, for charts, the period. Opens when a
 * widget is placed, from the launcher's "widget settings" (reconfigurable) and from Settings in the app.
 */
class WidgetConfigActivity : ComponentActivity() {
  private data class Choices(val metrics: Map<String, WidgetMetric?>, val period: Int) {
    /** "ring=STEPS;slot3=NONE|7": survives activity recreation in the saved instance state. */
    fun encode(): String = metrics.entries.joinToString(";") { (k, v) -> "$k=${v?.name ?: WidgetChoices.EMPTY}" } + "|$period"
  }

  private fun decode(text: String, config: WidgetConfig): Choices? {
    val (slots, period) = text.split('|').takeIf { it.size == 2 } ?: return null
    val map = slots.split(';').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    return Choices(config.slots.associate { it.key to WidgetMetric.byName(map[it.key]) }, period.toIntOrNull() ?: config.defaultPeriod)
  }

  /** The widget may have been removed meanwhile (e.g. while this screen waited in Recents). */
  private fun widgetExists(appWidgetId: Int) = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId) != null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    // Backing out of a new placement leaves the widget unplaced; an existing one keeps its choices.
    setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
    val info =
      if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null
      else WidgetCatalog.byProvider(AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className)
    val config = info?.config
    if (info == null || config == null) {
      finish()
      return
    }
    enableEdgeToEdge()
    setContent {
      HealthWidgyTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // Picks not saved yet; kept across rotation, dark mode switches etc.
          var edited by rememberSaveable { mutableStateOf<String?>(null) }
          val stored by produceState<Choices?>(null) {
            val loaded = runCatching {
              val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
              val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
              Choices(config.slots.associate { it.key to WidgetChoices.metric(prefs, it) }, WidgetChoices.period(prefs, config))
            }
            loaded.onSuccess { value = it }.onFailure { if (it is CancellationException) throw it else finish() }
          }
          val current = edited?.let { decode(it, config) } ?: stored
          current?.let { choices ->
            Form(info, config, choices, onChange = { edited = it.encode() }, onSave = { save(appWidgetId, info, config, it) }, onCancel = ::finish)
          }
        }
      }
    }
  }

  @Composable
  private fun Form(info: WidgetInfo, config: WidgetConfig, choices: Choices, onChange: (Choices) -> Unit, onSave: (Choices) -> Unit, onCancel: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
      Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column {
        Text(info.name, style = MaterialTheme.typography.headlineSmall)
        Text("Vyber, co má widget ukazovat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      config.slots.forEach { slot ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(slot.title, style = MaterialTheme.typography.titleSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slot.options.forEach { metric ->
              FilterChip(
                selected = choices.metrics[slot.key] == metric,
                onClick = { onChange(choices.copy(metrics = choices.metrics + (slot.key to metric))) },
                label = { Text(metric.label) },
                leadingIcon = {
                  Icon(painterResource(metric.icon), contentDescription = null, tint = Color(if (dark) metric.accent.night else metric.accent.day), modifier = Modifier.size(18.dp))
                },
              )
            }
            if (slot.optional) {
              FilterChip(
                selected = choices.metrics[slot.key] == null,
                onClick = { onChange(choices.copy(metrics = choices.metrics + (slot.key to null))) },
                label = { Text("Nic") },
              )
            }
          }
        }
      }
      if (config.periods.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Období", style = MaterialTheme.typography.titleSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            config.periods.forEach { days ->
              FilterChip(selected = choices.period == days, onClick = { onChange(choices.copy(period = days)) }, label = { Text("$days dní") })
            }
          }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
        TextButton(onClick = onCancel) { Text("Zrušit") }
        Button(onClick = { onSave(choices) }) { Text("Uložit") }
      }
    }
  }

  private fun save(appWidgetId: Int, info: WidgetInfo, config: WidgetConfig, choices: Choices) {
    lifecycleScope.launch {
      val context = this@WidgetConfigActivity
      // Never write state for a widget that no longer exists (Glance throws for unknown ids).
      val saved =
        widgetExists(appWidgetId) &&
          try {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { prefs -> WidgetChoices.write(prefs, config, choices.metrics, choices.period) }
            info.create().update(context, glanceId)
            true
          } catch (e: IllegalArgumentException) {
            false
          }
      if (saved) setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
      finish()
    }
  }
}
