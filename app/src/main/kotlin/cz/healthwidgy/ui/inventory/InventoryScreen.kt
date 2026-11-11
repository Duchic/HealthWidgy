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
import cz.healthwidgy.data.inventory.TypeInventory
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
        Text("Data v Health Connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
          "Inventura ukáže, které hodnoty Garmin do Health Connect opravdu zapisuje, odkdy, jak často a jak podrobně. Pracuje s posledními 30 dny a neukládá jednotlivá měření, jen souhrny: počty a délky záznamů, součet kroků, minuty spánkových fází, typy tréninků a model hodinek. Během inventury nech aplikaci otevřenou.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          Button(onClick = viewModel::run, enabled = !state.running) { Text(if (state.report == null) "Spustit inventuru" else "Spustit znovu") }
          state.exportFile?.let { file ->
            OutlinedButton(onClick = { shareJson(context, file) }) {
              AppIcon(R.drawable.ic_share, size = 16.dp, tint = MaterialTheme.colorScheme.primary)
              Text("  Sdílet JSON")
            }
          }
          if (state.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        state.error?.let { Text("Chyba: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      }
    }

    state.report?.let { report ->
      item {
        SurfaceCard(Modifier.fillMaxWidth()) {
          Text("Souhrn", style = MaterialTheme.typography.titleMedium)
          Text(
            buildString {
              append("Kroky za ${report.windowDays} dní: ")
              append(report.stepsInWindowGarminOnly?.let { "Garmin ${Format.int(it)}" } ?: "Garmin –")
              append(", všechny zdroje ")
              append(report.stepsInWindowAllSources?.let { Format.int(it) } ?: "–")
              append("\nČtení na pozadí: ${if (report.backgroundPermission) "povoleno" else "nepovoleno"}")
              append(" · Starší data: ${if (report.historyPermission) "povolena" else "nepovolena"}")
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
      Text(type.label, style = MaterialTheme.typography.titleSmall)
      Text(
        when {
          !type.permissionGranted -> "bez oprávnění"
          type.error != null -> "chyba"
          else -> type.recordsInWindow.toLong().let { n -> "${Format.int(n)} ${Format.plural(n, "záznam", "záznamy", "záznamů")}" }
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
    "Zdroje: " +
      type.origins.entries.sortedByDescending { it.value }.joinToString(", ") { (pkg, n) ->
        val name = if (pkg == GARMIN_CONNECT_PACKAGE) "Garmin" else pkg
        "$name ${(n * 100.0 / total).roundToInt()} %"
      }
  )
  type.earliestAccessibleRecord?.let { add("Nejstarší dostupný záznam: ${Format.dayAndTime(Instant.parse(it))}") }
  type.newestLastModified?.let { add("Poslední zápis: ${Format.dayAndTime(Instant.parse(it))}") }
  type.recordDurationSeconds?.let { add("Délka záznamu: medián ${seconds(it.median)} (${seconds(it.min)}–${seconds(it.max)})") }
  type.samplesPerRecord?.let { add("Vzorků v záznamu: medián ${Format.int(it.median)}") }
  type.sampleSpacingSeconds?.let { add("Rozestup vzorků: medián ${seconds(it.median)}") }
  type.sleepSessionsWithStages?.let { add("Nocí s fázemi spánku: $it z ${type.recordsInWindow}") }
  type.exerciseTypes?.takeIf { it.isNotEmpty() }?.let { types ->
    add("Typy: " + types.entries.joinToString(", ") { (t, n) -> "${Format.exerciseName(t.toInt())} $n×" })
  }
  if (type.devices.isNotEmpty()) add("Zařízení: ${type.devices.joinToString(", ")}")
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
      .putExtra(Intent.EXTRA_SUBJECT, "HealthWidgy – inventura dat")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(send, "Sdílet inventuru"))
}
