package cz.healthwidgy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import cz.healthwidgy.ui.inventory.InventoryScreen
import cz.healthwidgy.ui.inventory.InventoryViewModel
import cz.healthwidgy.ui.overview.OverviewScreen
import cz.healthwidgy.ui.overview.OverviewViewModel
import cz.healthwidgy.ui.settings.SettingsScreen
import cz.healthwidgy.ui.setup.SetupScreen

/** Root: the setup checklist until Health Connect access exists, then the tabbed app. */
@Composable
fun HealthWidgyRoot() {
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
      )
  }
}

private data class Tab(val key: NavKey, val label: String, val icon: Int)

private val tabs =
  listOf(
    Tab(OverviewKey, "Přehled", R.drawable.ic_grid),
    Tab(InventoryKey, "Data", R.drawable.ic_list),
    Tab(SettingsKey, "Nastavení", R.drawable.ic_tune),
  )

@Composable
private fun MainNavigation(
  access: HcAccess,
  permissionHint: Boolean,
  onRequestPermissions: (Set<String>) -> Unit,
  onOpenHealthConnectSettings: () -> Unit,
  onOpenPrivacy: () -> Unit,
) {
  val container = LocalContext.current.appContainer
  val backStack = rememberNavBackStack(OverviewKey)
  val overviewViewModel: OverviewViewModel = viewModel { OverviewViewModel(container.overview, container.settings) }
  val inventoryViewModel: InventoryViewModel = viewModel { InventoryViewModel(container.inventory, container.healthConnect) }

  // New or revoked permissions change what the overview can show: reload immediately.
  LaunchedEffect(access.granted) { overviewViewModel.onAccessChanged(access.granted) }

  Scaffold(
    bottomBar = {
      NavigationBar {
        tabs.forEach { tab ->
          NavigationBarItem(
            selected = backStack.lastOrNull() == tab.key,
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
      onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
      entryProvider =
        entryProvider {
          entry<OverviewKey> { OverviewScreen(overviewViewModel, padding) }
          entry<InventoryKey> { InventoryScreen(inventoryViewModel, padding) }
          entry<SettingsKey> {
            SettingsScreen(
              settingsRepository = container.settings,
              access = access,
              permissionHint = permissionHint,
              contentPadding = padding,
              onRequestPermissions = onRequestPermissions,
              onOpenHealthConnectSettings = onOpenHealthConnectSettings,
              onOpenPrivacy = onOpenPrivacy,
            )
          }
        },
    )
  }
}

/** Tabs keep a single entry: switching replaces the stack, so Back leaves the app from any tab. */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
  if (lastOrNull() == key) return
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
