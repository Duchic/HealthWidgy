package cz.healthwidgy.ui.inventory

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.R
import cz.healthwidgy.data.hc.GARMIN_CONNECT_PACKAGE
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.inventory.TypeInventory
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.SurfaceCard
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt

/** "Data v Health Connect": what each type holds over the last 30 days, with a JSON export. */
@Composable
fun InventoryScreen(viewModel: InventoryViewModel, contentPadding: PaddingValues) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LifecycleStartEffect(viewModel) { onStopOrDispose { viewModel.cancelIfRunning() } }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Data v Health Connect", "Data in Health Connect"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
          tr(
            "Inventura ukáže, které hodnoty Garmin do Health Connect opravdu zapisuje, odkdy, jak často a jak podrobně. Pracuje s posledními 30 dny a neukládá jednotlivá měření, jen souhrny: počty a délky záznamů, součet kroků, minuty spánkových fází, typy tréninků a model telefonu a hodinek. Během inventury nech aplikaci otevřenou.",
            "The inventory shows which values Garmin actually writes to Health Connect, how far back they go, how often and in how much detail. It covers the last 30 days and stores no individual measurements, only summaries: record counts and durations, total steps, sleep stage minutes, workout types, and the phone and watch models. Keep the app open while it runs.",
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Button(onClick = viewModel::run, enabled = !state.running) {
            Text(if (state.report == null) tr("Spustit inventuru", "Run inventory") else tr("Spustit znovu", "Run again"))
          }
          state.exportFile?.let { file ->
            OutlinedButton(onClick = { shareJson(context, file) }) {
              AppIcon(R.drawable.ic_share, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
              Text(tr("  Sdílet JSON", "  Share JSON"))
            }
          }
          if (state.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        val error =
          if (state.interrupted)
            tr(
              "Inventura byla přerušena, protože aplikace přešla do pozadí. Spusť ji znovu.",
              "The inventory was interrupted because the app went to the background. Run it again.",
            )
          else state.error
        error?.let { Text(tr("Chyba: $it", "Error: $it"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }

    state.report?.let { report ->
      item {
        SurfaceCard(Modifier.fillMaxWidth()) {
          Text(tr("Souhrn", "Summary"), style = MaterialTheme.typography.titleMedium)
          Text(
            buildString {
              append(tr("Kroky za ${report.windowDays} dní: ", "Steps in ${report.windowDays} days: "))
              append(report.stepsInWindowGarminOnly?.let { "Garmin ${Format.int(it)}" } ?: "Garmin –")
              append(tr(", všechny zdroje ", ", all sources "))
              append(report.stepsInWindowAllSources?.let { Format.int(it) } ?: "–")
              val background = if (report.backgroundPermission) tr("povoleno", "allowed") else tr("nepovoleno", "not allowed")
              append(tr("\nČtení na pozadí: $background", "\nBackground reading: $background"))
              val history = if (report.historyPermission) tr("povolena", "allowed") else tr("nepovolena", "not allowed")
              append(tr(" · Starší data: $history", " · Older data: $history"))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    items(state.types, key = { it.id }) { type -> TypeRow(type) }
  }
}

@Composable
private fun TypeRow(type: TypeInventory) {
  SurfaceCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      // The stored label is in the language of the run: show the current one.
      Text(HcTypes.all.firstOrNull { it.id == type.id }?.label ?: type.label, style = MaterialTheme.typography.titleSmall)
      Text(
        when {
          !type.permissionGranted -> tr("bez oprávnění", "no permission")
          type.error != null -> tr("chyba", "error")
          else ->
            type.recordsInWindow.toLong().let { n ->
              tr(
                "${Format.int(n)} ${Format.plural(n, "záznam", "záznamy", "záznamů")}",
                "${Format.int(n)} ${Format.pluralEn(n, "record", "records")}",
              )
            }
        },
        style = MaterialTheme.typography.labelMedium,
        color = if (type.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    val lines = describe(type)
    if (lines.isNotEmpty()) {
      Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun describe(type: TypeInventory): List<String> = buildList {
  type.error?.let { add(it) }
  if (type.recordsInWindow == 0) return@buildList
  val total = type.origins.values.sum().coerceAtLeast(1)
  add(
    tr("Zdroje: ", "Sources: ") +
      type.origins.entries.sortedByDescending { it.value }.joinToString(", ") { (pkg, n) ->
        val name = if (pkg == GARMIN_CONNECT_PACKAGE) "Garmin" else pkg
        val percent = (n * 100.0 / total).roundToInt()
        "$name ${Format.percent(percent)}"
      }
  )
  type.earliestAccessibleRecord?.let {
    val time = Format.dayAndTime(Instant.parse(it))
    add(tr("Nejstarší dostupný záznam: $time", "Oldest available record: $time"))
  }
  type.newestLastModified?.let {
    val time = Format.dayAndTime(Instant.parse(it))
    add(tr("Poslední zápis: $time", "Last written: $time"))
  }
  type.recordDurationSeconds?.let {
    val median = seconds(it.median)
    val range = "${seconds(it.min)}–${seconds(it.max)}"
    add(tr("Délka záznamu: medián $median ($range)", "Record duration: median $median ($range)"))
  }
  type.samplesPerRecord?.let {
    val median = Format.int(it.median)
    add(tr("Vzorků v záznamu: medián $median", "Samples per record: median $median"))
  }
  type.sampleSpacingSeconds?.let {
    val median = seconds(it.median)
    add(tr("Rozestup vzorků: medián $median", "Sample spacing: median $median"))
  }
  type.sleepSessionsWithStages?.let { add(tr("Nocí s fázemi spánku: $it z ${type.recordsInWindow}", "Nights with sleep stages: $it of ${type.recordsInWindow}")) }
  type.exerciseTypes?.takeIf { it.isNotEmpty() }?.let { types ->
    add(tr("Typy: ", "Types: ") + types.entries.joinToString(", ") { (t, n) -> "${Format.exerciseName(t.toInt())} $n×" })
  }
  if (type.devices.isNotEmpty()) {
    val devices = type.devices.joinToString(", ")
    add(tr("Zařízení: $devices", "Devices: $devices"))
  }
}

private fun seconds(value: Double): String =
  when {
    value < 120 -> "${value.roundToInt()} s"
    value < 7200 -> "${(value / 60).roundToInt()} min"
    else -> "${Format.decimal(value / 3600)} h"
  }

private fun shareJson(context: Context, file: File) {
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val send =
    Intent(Intent.ACTION_SEND)
      .setType("application/json")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, tr("HealthWidgy – inventura dat", "HealthWidgy – data inventory"))
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(send, tr("Sdílet inventuru", "Share inventory")))
}
