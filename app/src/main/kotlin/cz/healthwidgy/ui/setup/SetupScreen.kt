package cz.healthwidgy.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import cz.healthwidgy.R
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.components.SurfaceCard

enum class StepState { Done, Todo, Unavailable, Info }

/**
 * First-run checklist. Shown until Health Connect is available and at least one data type may be read.
 * Each item states what is missing and offers the action that fixes it.
 */
@Composable
fun SetupScreen(
  access: HcAccess,
  permissionHint: Boolean,
  onRequestPermissions: () -> Unit,
  onOpenHealthConnectSettings: () -> Unit,
  onOpenGarmin: (() -> Unit)?,
  onOpenPrivacy: () -> Unit,
) {
  Column(
    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Vítej v HealthWidgy", style = MaterialTheme.typography.headlineMedium)
    Text(
      "Aplikace čte data, která do Health Connect zapisují tvoje hodinky Garmin, a ukazuje je v přehledu. Data zůstávají v telefonu.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))

    val hcState =
      when (access.sdkStatus) {
        HealthConnectClient.SDK_AVAILABLE -> StepState.Done
        else -> StepState.Unavailable
      }
    ChecklistItem(
      state = hcState,
      title = "Health Connect",
      text =
        when (access.sdkStatus) {
          HealthConnectClient.SDK_AVAILABLE -> "Je součástí systému a je připravené."
          HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect potřebuje aktualizaci systému. Nainstaluj dostupné aktualizace a vrať se."
          else -> "Na tomto telefonu není Health Connect dostupné. Aplikace potřebuje Android 14 nebo novější."
        },
    )

    ChecklistItem(
      state = if (access.garminInstalled) StepState.Done else StepState.Todo,
      title = "Garmin Connect",
      text =
        if (access.garminInstalled)
          "V aplikaci Garmin Connect zapni sdílení: Více → Nastavení → Propojené aplikace → Health Connect a povol všechny kategorie."
        else "Aplikace Garmin Connect není nainstalovaná. Bez ní se data z hodinek do Health Connect nedostanou.",
      action = if (access.garminInstalled && onOpenGarmin != null) ("Otevřít Garmin Connect" to onOpenGarmin) else null,
    )

    val total = HcTypes.dataPermissions.size
    ChecklistItem(
      state =
        when {
          !access.isAvailable -> StepState.Unavailable
          access.hasAllDataPermissions -> StepState.Done
          else -> StepState.Todo
        },
      title = "Přístup k datům",
      text =
        if (access.hasAnyDataPermission) "Povoleno ${access.grantedDataPermissions} z $total typů dat."
        else "Povol čtení kroků, tepu, spánku, tréninků, váhy a dalších hodnot, které Garmin zapisuje.",
    )

    ChecklistItem(
      state =
        when {
          !access.backgroundFeatureAvailable -> StepState.Unavailable
          access.hasBackground -> StepState.Done
          else -> StepState.Info
        },
      title = "Čtení na pozadí",
      text =
        if (access.backgroundFeatureAvailable) "Volitelné. Widgety se díky němu obnoví samy, i když aplikace není otevřená."
        else "Tento telefon čtení na pozadí nepodporuje. Widgety se obnoví při otevření aplikace.",
    )

    ChecklistItem(
      state =
        when {
          !access.historyFeatureAvailable -> StepState.Unavailable
          access.hasHistory -> StepState.Done
          else -> StepState.Info
        },
      title = "Starší data",
      text = "Volitelné. Bez tohoto oprávnění aplikace nevidí data starší než 30 dní před prvním povolením přístupu.",
    )

    Spacer(Modifier.height(4.dp))
    if (permissionHint) {
      Text(
        "Health Connect nepovolil nic nového. Pokud se dialog vůbec neukázal, povol přístup ručně: Nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Button(onClick = onRequestPermissions, enabled = access.isAvailable, modifier = Modifier.fillMaxWidth()) { Text("Povolit přístup k datům") }
    OutlinedButton(onClick = onOpenHealthConnectSettings, enabled = access.isAvailable, modifier = Modifier.fillMaxWidth()) {
      Text("Otevřít nastavení Health Connect")
    }
    Text(
      "Když některé oprávnění dvakrát nepovolíš, Health Connect se na něj už nezeptá. Pak ho povol v nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onOpenPrivacy) { Text("Jak aplikace s daty zachází") }
  }
}

@Composable
private fun ChecklistItem(state: StepState, title: String, text: String, action: Pair<String, () -> Unit>? = null) {
  SurfaceCard(Modifier.fillMaxWidth()) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
      StateBadge(state)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = action.second, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(action.first) }
      }
    }
  }
}

@Composable
private fun StateBadge(state: StepState) {
  val (bg, fg, icon) =
    when (state) {
      StepState.Done -> Triple(Color(0xFF239163), Color.White, R.drawable.ic_check)
      StepState.Todo -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, R.drawable.ic_chevron_right)
      StepState.Info -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, R.drawable.ic_info)
      StepState.Unavailable -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, R.drawable.ic_close)
    }
  Surface(shape = CircleShape, color = bg, modifier = Modifier.size(28.dp)) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { AppIcon(icon, tint = fg, size = 16.dp) }
  }
}
