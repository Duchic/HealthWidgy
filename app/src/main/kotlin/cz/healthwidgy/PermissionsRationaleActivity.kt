package cz.healthwidgy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.healthwidgy.data.hc.HcTypes
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.components.AppIcon
import cz.healthwidgy.ui.theme.HealthWidgyTheme

/**
 * Privacy policy required by Health Connect. Opened by Health Connect from its permission dialog
 * (ACTION_SHOW_PERMISSIONS_RATIONALE), from Android settings (VIEW_PERMISSION_USAGE alias) and from the app.
 */
class PermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { HealthWidgyTheme { PrivacyScreen(onClose = ::finish) } }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyScreen(onClose: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(tr("Ochrana soukromí", "Privacy")) },
        navigationIcon = { IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, contentDescription = tr("Zavřít", "Close"), size = 22.dp) } },
      )
    }
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Section(
        tr("Jaká data aplikace čte", "What data the app reads"),
        tr(
          "HealthWidgy z Health Connect jen čte. Nic nezapisuje, nemění ani nemaže. Čte tyto typy dat: ",
          "HealthWidgy only reads from Health Connect. It never writes, changes or deletes anything. It reads these data types: ",
        ) + HcTypes.all.joinToString(", ") { it.label.lowercase() } + ".",
      )
      Section(
        tr("K čemu je používá", "How the data is used"),
        tr(
          "Data slouží k zobrazení přehledu, grafů a widgetů na ploše tohoto telefonu a k inventuře dat, kterou si můžeš sám vyexportovat. Aplikace z nich počítá denní součty a trendy.",
          "The data is used to show the overview, charts and home screen widgets on this phone, and for the data inventory you can export yourself. The app calculates daily totals and trends from it.",
        ),
      )
      Section(
        tr("Kam data odcházejí", "Where the data goes"),
        tr(
          "Aplikace nemá oprávnění k internetu a sama nic neodesílá. Data zůstávají v Health Connect a v soukromém úložišti aplikace. Výjimkou je inventura dat v Nastavení → O aplikaci: tu můžeš tlačítkem „Sdílet JSON“ sám předat jiné aplikaci. Obsahuje souhrny za 30 dní (počty a délky záznamů, součet kroků, minuty spánkových fází, typy tréninků) a model telefonu a hodinek.",
          "The app has no internet permission and sends nothing on its own. The data stays in Health Connect and in the app's private storage. The exception is the data inventory in Settings → About: you can share it with another app yourself using the “Share JSON” button. It contains 30-day summaries (record counts and durations, total steps, sleep stage minutes, workout types) and the phone and watch models.",
        ),
      )
      Section(
        tr("Čtení na pozadí a starší data", "Background reading and older data"),
        tr(
          "Pokud povolíš čtení na pozadí, aplikace data čte i ve chvíli, kdy není otevřená, aby se mohly obnovovat widgety. Oprávnění ke starším datům zpřístupní i data z doby víc než 30 dní před prvním povolením přístupu.",
          "If you allow background reading, the app reads data even when it is not open, so that widgets can refresh. The older data permission also gives access to data from more than 30 days before access was first granted.",
        ),
      )
      Section(
        tr("Jak přístup odebrat", "How to remove access"),
        tr(
          "V Nastavení Androidu otevři Zabezpečení a soukromí → Soukromí → Health Connect → Oprávnění aplikací → HealthWidgy a vypni, co nechceš sdílet. Odinstalací aplikace zanikne veškerý přístup.",
          "In Android Settings, open Security & privacy → Privacy → Health Connect → App permissions → HealthWidgy and turn off what you do not want to share. Uninstalling the app removes all access.",
        ),
      )
    }
  }
}

@Composable
private fun Section(title: String, text: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
