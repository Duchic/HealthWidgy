package cz.healthwidgy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.healthwidgy.BuildConfig
import cz.healthwidgy.data.hc.ExtraPermissions
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.data.settings.Settings
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.data.settings.SourceMode
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.SurfaceCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  settingsRepository: SettingsRepository,
  access: HcAccess,
  permissionHint: Boolean,
  contentPadding: PaddingValues,
  onRequestPermissions: (Set<String>) -> Unit,
  onOpenHealthConnectSettings: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenInventory: () -> Unit,
) {
  val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
  val scope = rememberCoroutineScope()

  Column(
    Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("Nastavení", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text("Denní cíl kroků", style = MaterialTheme.typography.titleMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Settings.STEP_GOAL_OPTIONS.forEach { goal ->
          FilterChip(
            selected = settings.stepGoal == goal,
            onClick = { scope.launch { settingsRepository.setStepGoal(goal) } },
            label = { Text(Format.int(goal.toLong())) },
          )
        }
      }
      Text(
        "Automatický cíl z Garminu se do Health Connect nepřenáší, proto ho nastav tady.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    WidgetsCard()

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text("Zdroj dat", style = MaterialTheme.typography.titleMedium)
      SourceOption(
        selected = settings.sourceMode == SourceMode.GARMIN_ONLY,
        title = "Jen Garmin Connect",
        text = "Doporučeno. Kroky, vzdálenost, kalorie, tep, spánek a tréninky jen z Garminu, bez krokoměru telefonu. Váhu a tělesný tuk aplikace bere z kterékoli aplikace (např. chytré váhy).",
        onSelect = { scope.launch { settingsRepository.setSourceMode(SourceMode.GARMIN_ONLY) } },
      )
      SourceOption(
        selected = settings.sourceMode == SourceMode.ALL_SOURCES,
        title = "Všechny zdroje",
        text = "Vše z Health Connect. Součty kroků a kalorií Health Connect slučuje podle priority aplikací.",
        onSelect = { scope.launch { settingsRepository.setSourceMode(SourceMode.ALL_SOURCES) } },
      )
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text("Oprávnění", style = MaterialTheme.typography.titleMedium)
      Text(
        buildString {
          append("Typy dat: ${access.grantedDataPermissions} z ${HcTypes.dataPermissions.size}\n")
          append("Čtení na pozadí: ")
          append(
            when {
              !access.backgroundFeatureAvailable -> "telefon nepodporuje"
              access.hasBackground -> "povoleno"
              else -> "nepovoleno"
            }
          )
          append("\nData starší než 30 dní před prvním povolením: ")
          append(
            when {
              !access.historyFeatureAvailable -> "telefon nepodporuje"
              access.hasHistory -> "povoleno"
              else -> "nepovoleno"
            }
          )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // Separate requests: a permission denied twice blocks the whole dialog for any request that contains it.
      val missingData = access.missingPermissions.intersect(HcTypes.dataPermissions)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (missingData.isNotEmpty()) OutlinedButton(onClick = { onRequestPermissions(missingData) }) { Text("Chybějící typy dat") }
        if (access.backgroundFeatureAvailable && !access.hasBackground) {
          OutlinedButton(onClick = { onRequestPermissions(setOf(ExtraPermissions.Background)) }) { Text("Čtení na pozadí") }
        }
        if (access.historyFeatureAvailable && !access.hasHistory) {
          OutlinedButton(onClick = { onRequestPermissions(setOf(ExtraPermissions.History)) }) { Text("Starší data") }
        }
        OutlinedButton(onClick = onOpenHealthConnectSettings) { Text("Nastavení Health Connect") }
      }
      if (permissionHint) {
        Text(
          "Health Connect nepovolil nic nového. Pokud se dialog vůbec neukázal, povol oprávnění ručně: Nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text("O aplikaci", style = MaterialTheme.typography.titleMedium)
      Text("HealthWidgy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(onClick = onOpenInventory, contentPadding = PaddingValues(0.dp)) { Text("Data v Health Connect (inventura)") }
      TextButton(onClick = onOpenPrivacy, contentPadding = PaddingValues(0.dp)) { Text("Ochrana soukromí") }
    }
  }
}

@Composable
private fun SourceOption(selected: Boolean, title: String, text: String, onSelect: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
    Column {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
