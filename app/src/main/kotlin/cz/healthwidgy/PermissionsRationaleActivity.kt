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
        title = { Text("Ochrana soukromí") },
        navigationIcon = { IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, contentDescription = "Zavřít", size = 22.dp) } },
      )
    }
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Section(
        "Jaká data aplikace čte",
        "HealthWidgy z Health Connect jen čte. Nic nezapisuje, nemění ani nemaže. Čte tyto typy dat: " +
          HcTypes.all.joinToString(", ") { it.label.lowercase() } + ".",
      )
      Section(
        "K čemu je používá",
        "Data slouží k zobrazení přehledu, grafů a widgetů na ploše tohoto telefonu a k inventuře dat, kterou si můžeš sám vyexportovat. Aplikace z nich počítá denní součty a trendy.",
      )
      Section(
        "Kam data odcházejí",
        "Aplikace nemá oprávnění k internetu a sama nic neodesílá. Data zůstávají v Health Connect a v soukromém úložišti aplikace. Výjimkou je inventura na záložce Data: tu můžeš tlačítkem „Sdílet JSON“ sám předat jiné aplikaci. Obsahuje souhrny za 30 dní (počty a délky záznamů, součet kroků, minuty spánkových fází, typy tréninků) a model telefonu a hodinek.",
      )
      Section(
        "Čtení na pozadí a starší data",
        "Pokud povolíš čtení na pozadí, aplikace data čte i ve chvíli, kdy není otevřená, aby se mohly obnovovat widgety. Oprávnění ke starším datům zpřístupní i data z doby víc než 30 dní před prvním povolením přístupu.",
      )
      Section(
        "Jak přístup odebrat",
        "V Nastavení Androidu otevři Zabezpečení a soukromí → Soukromí → Health Connect → Oprávnění aplikací → HealthWidgy a vypni, co nechceš sdílet. Odinstalací aplikace zanikne veškerý přístup.",
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
