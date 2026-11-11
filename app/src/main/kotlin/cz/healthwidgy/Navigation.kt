package cz.healthwidgy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cz.healthwidgy.data.hc.HcAccess
import cz.healthwidgy.ui.AccessViewModel
import cz.healthwidgy.ui.components.AppIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.LifecycleStartEffect
import cz.healthwidgy.ui.history.ActivitiesScreen
import cz.healthwidgy.ui.history.BodyScreen
import cz.healthwidgy.ui.history.DetailTopBar
import cz.healthwidgy.ui.history.HeartScreen
import cz.healthwidgy.ui.history.MetricDefs
import cz.healthwidgy.ui.history.MetricDetailScreen
import cz.healthwidgy.ui.history.SleepScreen
import cz.healthwidgy.ui.history.TrendsScreen
import cz.healthwidgy.ui.history.WorkoutDetailScreen
import cz.healthwidgy.ui.inventory.InventoryScreen
import cz.healthwidgy.ui.inventory.InventoryViewModel
import cz.healthwidgy.ui.overview.OverviewActions
import cz.healthwidgy.ui.overview.OverviewScreen
import cz.healthwidgy.ui.overview.OverviewViewModel
import cz.healthwidgy.ui.settings.SettingsScreen
import cz.healthwidgy.ui.setup.SetupScreen

/** Root: the setup checklist until Health Connect access exists, then the tabbed app. */
@Composable
fun HealthWidgyRoot(destination: String? = null, onDestinationHandled: () -> Unit = {}) {
  val context = LocalContext.current
  val container = context.appContainer
  val accessViewModel: AccessViewModel = viewModel { AccessViewModel(container.healthConnect) }
  val access by accessViewModel.access.collectAsStateWithLifecycle()
  val permissionHint by accessViewModel.permissionHint.collectAsStateWithLifecycle()

  // Re-check permissions whenever the app comes back (e.g. from Health Connect settings).
  LifecycleResumeEffect(accessViewModel) {
    accessViewModel.refresh()
    onPauseOrDispose {}
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(container.healthConnect.permissionContract()) { granted -> accessViewModel.onPermissionResult(granted) }
  val requestPermissions: (Set<String>) -> Unit = { permissions ->
    if (permissions.isNotEmpty()) {
      accessViewModel.beforeRequest(permissions)
      permissionLauncher.launch(permissions)
    }
  }

  val current = access
  when {
    current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    !current.isAvailable || !current.hasAnyDataPermission ->
      SetupScreen(
        access = current,
        permissionHint = permissionHint,
        onRequestPermissions = { requestPermissions(current.missingPermissions) },
        onOpenHealthConnectSettings = { openHealthConnectSettings(context) },
        onOpenGarmin = container.healthConnect.garminLaunchIntent()?.let { intent -> { context.startActivity(intent) } },
        onOpenPrivacy = { openPrivacy(context) },
      )
    else ->
      MainNavigation(
        access = current,
        permissionHint = permissionHint,
        onRequestPermissions = requestPermissions,
        onOpenHealthConnectSettings = { openHealthConnectSettings(context) },
        onOpenPrivacy = { openPrivacy(context) },
        destination = destination,
        onDestinationHandled = onDestinationHandled,
      )
  }
}

private data class Tab(val key: NavKey, val label: String, val icon: Int)

private val tabs =
  listOf(
    Tab(OverviewKey, "Přehled", R.drawable.ic_grid),
    Tab(TrendsKey, "Trendy", R.drawable.ic_trend),
    Tab(ActivitiesKey, "Aktivity", R.drawable.ic_run),
    Tab(SettingsKey, "Nastavení", R.drawable.ic_tune),
  )

@Composable
private fun MainNavigation(
  access: HcAccess,
  permissionHint: Boolean,
  onRequestPermissions: (Set<String>) -> Unit,
  onOpenHealthConnectSettings: () -> Unit,
  onOpenPrivacy: () -> Unit,
  destination: String?,
  onDestinationHandled: () -> Unit,
) {
  val container = LocalContext.current.appContainer
  val backStack = rememberNavBackStack(OverviewKey)
  val overviewViewModel: OverviewViewModel = viewModel { OverviewViewModel(container.overview, container.settings) }
  val inventoryViewModel: InventoryViewModel = viewModel { InventoryViewModel(container.inventory, container.healthConnect) }
  val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)

  // New or revoked permissions change what the overview can show: reload immediately.
  LaunchedEffect(access.granted) { overviewViewModel.onAccessChanged(access.granted) }
  // Keep the history cache fresh: on every start (throttled in the engine) and right after a source or
  // permission change (forced, so the throttle does not swallow it).
  LifecycleStartEffect(Unit) {
    container.requestSync()
    onStopOrDispose {}
  }
  var syncKey by remember { mutableStateOf<Pair<Any?, Set<String>>?>(null) }
  LaunchedEffect(settings?.sourceMode, access.granted) {
    if (settings == null) return@LaunchedEffect
    val key = settings?.sourceMode to access.granted
    container.requestSync(force = syncKey != null && syncKey != key)
    syncKey = key
  }

  // A tap on a widget opens the matching screen.
  LaunchedEffect(destination) {
    val target = destination ?: return@LaunchedEffect
    onDestinationHandled()
    val key: NavKey? =
      when {
        target == "overview" -> null
        target == "heart" -> HeartKey
        target == "sleep" -> SleepKey
        target == "body" -> BodyKey
        target == "activities" -> null
        target.startsWith("metric:") -> MetricKey(target.removePrefix("metric:"))
        target.startsWith("workout:") -> WorkoutKey(target.removePrefix("workout:"))
        else -> null
      }
    backStack.switchTo(if (target == "activities") ActivitiesKey else OverviewKey)
    key?.let { backStack.add(it) }
  }

  // Back on the root of another tab returns to Přehled instead of leaving the app.
  BackHandler(enabled = backStack.size == 1 && backStack.firstOrNull() != OverviewKey) { backStack.switchTo(OverviewKey) }

  val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
  val overviewActions =
    OverviewActions(
      openMetric = { backStack.add(MetricKey(it)) },
      openHeart = { backStack.add(HeartKey) },
      openSleep = { backStack.add(SleepKey) },
      openBody = { backStack.add(BodyKey) },
      openWorkout = { backStack.add(WorkoutKey(it)) },
      syncHistory = { container.requestSync(force = true) },
    )

  Scaffold(
    bottomBar = {
      NavigationBar {
        tabs.forEach { tab ->
          NavigationBarItem(
            selected = backStack.firstOrNull() == tab.key,
            onClick = { backStack.switchTo(tab.key) },
            icon = { AppIcon(tab.icon, size = 22.dp, tint = LocalContentColor.current) },
            label = { Text(tab.label) },
          )
        }
      }
    }
  ) { padding ->
    NavDisplay(
      backStack = backStack,
      onBack = back,
      entryProvider =
        entryProvider {
          entry<OverviewKey> { OverviewScreen(overviewViewModel, padding, overviewActions) }
          entry<TrendsKey> {
            TrendsScreen(
              contentPadding = padding,
              onOpenMetric = { backStack.add(MetricKey(it)) },
              onOpenHeart = { backStack.add(HeartKey) },
              onOpenSleep = { backStack.add(SleepKey) },
              onOpenBody = { backStack.add(BodyKey) },
            )
          }
          entry<ActivitiesKey> { ActivitiesScreen(padding) { backStack.add(WorkoutKey(it)) } }
          entry<SettingsKey> {
            SettingsScreen(
              settingsRepository = container.settings,
              access = access,
              permissionHint = permissionHint,
              contentPadding = padding,
              onRequestPermissions = onRequestPermissions,
              onOpenHealthConnectSettings = onOpenHealthConnectSettings,
              onOpenPrivacy = onOpenPrivacy,
              onOpenInventory = { backStack.add(InventoryKey) },
            )
          }
          entry<MetricKey> { key -> MetricDetailScreen(key.metric, back, padding) }
          entry<HeartKey> { HeartScreen(back, onOpenResting = { backStack.add(MetricKey(MetricDefs.RestingHr.id)) }, contentPadding = padding) }
          entry<SleepKey> { SleepScreen(back, onOpenHistory = { backStack.add(MetricKey(MetricDefs.Sleep.id)) }, contentPadding = padding) }
          entry<BodyKey> { BodyScreen(back, padding) }
          entry<WorkoutKey> { key -> WorkoutDetailScreen(key.id, back, padding) }
          entry<InventoryKey> {
            Column(Modifier.fillMaxSize().padding(padding)) {
              DetailTopBar("Data v Health Connect", back)
              InventoryScreen(inventoryViewModel, PaddingValues())
            }
          }
        },
    )
  }
}

/** Switching tabs replaces the whole stack with the tab root (detail screens of the old tab are dropped). */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
  if (size == 1 && firstOrNull() == key) return
  add(key)
  while (size > 1) removeAt(0)
}

private fun openHealthConnectSettings(context: Context) {
  try {
    context.startActivity(context.appContainer.healthConnect.healthConnectSettingsIntent())
  } catch (e: ActivityNotFoundException) {
    Toast.makeText(context, "Nastavení Health Connect se nepodařilo otevřít.", Toast.LENGTH_LONG).show()
  } catch (e: SecurityException) {
    Toast.makeText(context, "Nastavení Health Connect se nepodařilo otevřít.", Toast.LENGTH_LONG).show()
  }
}

private fun openPrivacy(context: Context) {
  context.startActivity(Intent(context, PermissionsRationaleActivity::class.java))
}
