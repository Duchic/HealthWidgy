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
import cz.healthwidgy.data.settings.ThemeMode
import cz.healthwidgy.i18n.tr
import cz.healthwidgy.ui.Format
import cz.healthwidgy.ui.components.SurfaceCard
import cz.healthwidgy.ui.theme.applyAppTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.platform.LocalContext
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
    Text(tr("Nastavení", "Settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text(tr("Denní cíle", "Daily goals"), style = MaterialTheme.typography.titleMedium)
      GoalChips(tr("Kroky", "Steps"), Settings.STEP_GOAL_OPTIONS, settings.stepGoal, { Format.int(it.toLong()) }) { scope.launch { settingsRepository.setStepGoal(it) } }
      GoalChips(tr("Vzdálenost", "Distance"), Settings.DISTANCE_GOAL_OPTIONS, settings.distanceGoalKm, { "$it km" }) { scope.launch { settingsRepository.setDistanceGoalKm(it) } }
      GoalChips(tr("Patra", "Floors"), Settings.FLOORS_GOAL_OPTIONS, settings.floorsGoal, { it.toString() }) { scope.launch { settingsRepository.setFloorsGoal(it) } }
      GoalChips(tr("Spánek", "Sleep"), Settings.SLEEP_GOAL_OPTIONS, settings.sleepGoalMinutes, { Format.hours(it) }) { scope.launch { settingsRepository.setSleepGoalMinutes(it) } }
      Text(
        tr(
          "Cíle z Garminu se do Health Connect nepřenášejí, proto je nastav tady. Platí pro grafy i widgety.",
          "Garmin goals are not passed on to Health Connect, so set them here. They apply to charts and widgets.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
      val context = LocalContext.current
      Text(tr("Vzhled", "Appearance"), style = MaterialTheme.typography.titleMedium)
      ThemeChoice(tr("Aplikace", "App"), settings.appTheme) { mode ->
        // Apply after the choice is stored: switching recreates this screen.
        scope.launch {
          settingsRepository.setAppTheme(mode)
          applyAppTheme(context, mode)
        }
      }
      ThemeChoice(tr("Widgety", "Widgets"), settings.widgetTheme) { scope.launch { settingsRepository.setWidgetTheme(it) } }
      Text(
        tr(
          "Systém: světlý a tmavý vzhled se přepíná spolu s telefonem (i podle plánu). Barvy se řídí tapetou. " +
            "Jednotlivý widget můžeš přepnout i zvlášť v jeho nastavení (Widgety na plochu → Upravit).",
          "System: light and dark switch together with the phone (also on a schedule). Colors follow the wallpaper. " +
            "You can also switch a single widget in its own settings (Home screen widgets → Edit).",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    WidgetsCard()

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text(tr("Zdroj dat", "Data source"), style = MaterialTheme.typography.titleMedium)
      SourceOption(
        selected = settings.sourceMode == SourceMode.GARMIN_ONLY,
        title = tr("Jen Garmin Connect", "Garmin Connect only"),
        text =
          tr(
            "Doporučeno. Kroky, vzdálenost, kalorie, tep, spánek a tréninky jen z Garminu, bez krokoměru telefonu. Váhu a tělesný tuk aplikace bere z kterékoli aplikace (např. chytré váhy).",
            "Recommended. Steps, distance, calories, heart rate, sleep and workouts from Garmin only, without the phone's step counter. Weight and body fat come from any app (e.g. a smart scale).",
          ),
        onSelect = { scope.launch { settingsRepository.setSourceMode(SourceMode.GARMIN_ONLY) } },
      )
      SourceOption(
        selected = settings.sourceMode == SourceMode.ALL_SOURCES,
        title = tr("Všechny zdroje", "All sources"),
        text =
          tr(
            "Vše z Health Connect. Součty kroků a kalorií Health Connect slučuje podle priority aplikací.",
            "Everything in Health Connect. Health Connect merges step and calorie totals by app priority.",
          ),
        onSelect = { scope.launch { settingsRepository.setSourceMode(SourceMode.ALL_SOURCES) } },
      )
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text(tr("Oprávnění", "Permissions"), style = MaterialTheme.typography.titleMedium)
      Text(
        buildString {
          val granted = access.grantedDataPermissions
          val total = HcTypes.dataPermissions.size
          append(tr("Typy dat: $granted z $total\n", "Data types: $granted of $total\n"))
          append(tr("Čtení na pozadí: ", "Background reading: "))
          append(
            when {
              !access.backgroundFeatureAvailable -> tr("telefon nepodporuje", "not supported by the phone")
              access.hasBackground -> tr("povoleno", "allowed")
              else -> tr("nepovoleno", "not allowed")
            }
          )
          append(tr("\nData starší než 30 dní před prvním povolením: ", "\nData older than 30 days before the first grant: "))
          append(
            when {
              !access.historyFeatureAvailable -> tr("telefon nepodporuje", "not supported by the phone")
              access.hasHistory -> tr("povoleno", "allowed")
              else -> tr("nepovoleno", "not allowed")
            }
          )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // Separate requests: a permission denied twice blocks the whole dialog for any request that contains it.
      val missingData = access.missingPermissions.intersect(HcTypes.dataPermissions)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (missingData.isNotEmpty()) OutlinedButton(onClick = { onRequestPermissions(missingData) }) { Text(tr("Chybějící typy dat", "Missing data types")) }
        if (access.backgroundFeatureAvailable && !access.hasBackground) {
          OutlinedButton(onClick = { onRequestPermissions(setOf(ExtraPermissions.Background)) }) { Text(tr("Čtení na pozadí", "Background reading")) }
        }
        if (access.historyFeatureAvailable && !access.hasHistory) {
          OutlinedButton(onClick = { onRequestPermissions(setOf(ExtraPermissions.History)) }) { Text(tr("Starší data", "Older data")) }
        }
        OutlinedButton(onClick = onOpenHealthConnectSettings) { Text(tr("Nastavení Health Connect", "Health Connect settings")) }
      }
      if (permissionHint) {
        Text(
          tr(
            "Health Connect nepovolil nic nového. Pokud se dialog vůbec neukázal, povol oprávnění ručně: Nastavení Health Connect → Oprávnění aplikací → HealthWidgy.",
            "Health Connect granted nothing new. If the dialog did not appear at all, grant the permissions manually: Health Connect settings → App permissions → HealthWidgy.",
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
      Text(tr("O aplikaci", "About"), style = MaterialTheme.typography.titleMedium)
      Text("HealthWidgy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(onClick = onOpenInventory, contentPadding = PaddingValues(0.dp)) { Text(tr("Data v Health Connect (inventura)", "Data in Health Connect (inventory)")) }
      TextButton(onClick = onOpenPrivacy, contentPadding = PaddingValues(0.dp)) { Text(tr("Ochrana soukromí", "Privacy")) }
    }
  }
}

@Composable
private fun GoalChips(title: String, options: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
  Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option -> FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(label(option)) }) }
  }
}

@Composable
private fun ThemeChoice(title: String, selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
  Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    ThemeMode.entries.forEachIndexed { i, mode ->
      SegmentedButton(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
        label = { Text(mode.label, maxLines = 1) },
      )
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
