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
import cz.healthwidgy.i18n.tr
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
    Text(tr("Vítej v HealthWidgy", "Welcome to HealthWidgy"), style = MaterialTheme.typography.headlineMedium)
    Text(
      tr(
        "Aplikace čte data, která do Health Connect zapisují tvoje hodinky Garmin, a ukazuje je v přehledu. Data zůstávají v telefonu.",
        "The app reads the data your Garmin watch writes to Health Connect and shows it in an overview. The data stays on your phone.",
      ),
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
          HealthConnectClient.SDK_AVAILABLE -> tr("Je součástí systému a je připravené.", "Built into the system and ready.")
          HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            tr(
              "Health Connect potřebuje aktualizaci systému. Nainstaluj dostupné aktualizace a vrať se.",
              "Health Connect needs a system update. Install the available updates and come back.",
            )
          else ->
            tr(
              "Na tomto telefonu není Health Connect dostupné. Aplikace potřebuje Android 14 nebo novější.",
              "Health Connect is not available on this phone. The app needs Android 14 or later.",
            )
        },
    )

    ChecklistItem(
      state = if (access.garminInstalled) StepState.Done else StepState.Todo,
      title = "Garmin Connect",
      text =
        if (access.garminInstalled)
          tr(
            "V aplikaci Garmin Connect zapni sdílení: Více → Nastavení → Propojené aplikace → Health Connect a povol všechny kategorie.",
            "Turn on sharing in Garmin Connect: More → Settings → Connected Apps → Health Connect, and allow all categories.",
          )
        else
          tr(
            "Aplikace Garmin Connect není nainstalovaná. Bez ní se data z hodinek do Health Connect nedostanou.",
            "Garmin Connect is not installed. Without it, data from the watch cannot reach Health Connect.",
          ),
      action = if (access.garminInstalled && onOpenGarmin != null) (tr("Otevřít Garmin Connect", "Open Garmin Connect") to onOpenGarmin) else null,
    )

    val total = HcTypes.dataPermissions.size
    ChecklistItem(
      state =
        when {
          !access.isAvailable -> StepState.Unavailable
          access.hasAllDataPermissions -> StepState.Done
          else -> StepState.Todo
        },
      title = tr("Přístup k datům", "Data access"),
      text =
        if (access.hasAnyDataPermission)
          tr("Povoleno ${access.grantedDataPermissions} z $total typů dat.", "${access.grantedDataPermissions} of $total data types allowed.")
        else
          tr(
            "Povol čtení kroků, tepu, spánku, tréninků, váhy a dalších hodnot, které Garmin zapisuje.",
            "Allow reading steps, heart rate, sleep, workouts, weight and the other values Garmin writes.",
          ),
    )

    ChecklistItem(
      state =
        when {
          !access.backgroundFeatureAvailable -> StepState.Unavailable
          access.hasBackground -> StepState.Done
          else -> StepState.Info
        },
      title = tr("Čtení na pozadí", "Background reading"),
      text =
        if (access.backgroundFeatureAvailable)
          tr(
            "Volitelné. Widgety se díky němu obnoví samy, i když aplikace není otevřená.",
            "Optional. Lets widgets refresh on their own, even when the app is not open.",
          )
        else
          tr(
            "Tento telefon čtení na pozadí nepodporuje. Widgety se obnoví při otevření aplikace.",
            "This phone does not support background reading. Widgets refresh when you open the app.",
          ),
    )

    ChecklistItem(
      state =
        when {
          !access.historyFeatureAvailable -> StepState.Unavailable
          access.hasHistory -> StepState.Done
          else -> StepState.Info
        },
      title = tr("Starší data", "Older data"),
      text =
        tr(
          "Volitelné. Bez tohoto oprávnění aplikace nevidí data starší než 30 dní před prvním povolením přístupu.",
          "Optional. Without this permission the app cannot see data older than 30 days before access was first granted.",
        ),
    )

    Spacer(Modifier.height(4.dp))
    if (permissionHint) {
      Text(
        tr(
          "Health Connect nepovolil nic nového. Pokud se dialog vůbec neukázal, povol přístup ručně: Nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
          "Health Connect granted nothing new. If the dialog did not appear at all, allow access manually: Health Connect settings → App permissions → HealthWidgy.",
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Button(onClick = onRequestPermissions, enabled = access.isAvailable, modifier = Modifier.fillMaxWidth()) {
      Text(tr("Povolit přístup k datům", "Allow data access"))
    }
    OutlinedButton(onClick = onOpenHealthConnectSettings, enabled = access.isAvailable, modifier = Modifier.fillMaxWidth()) {
      Text(tr("Otevřít nastavení Health Connect", "Open Health Connect settings"))
    }
    Text(
      tr(
        "Když některé oprávnění dvakrát nepovolíš, Health Connect se na něj už nezeptá. Pak ho povol v nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
        "If you deny a permission twice, Health Connect will not ask for it again. Then allow it in Health Connect settings → App permissions → HealthWidgy.",
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onOpenPrivacy) { Text(tr("Jak aplikace s daty zachází", "How the app handles your data")) }
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
