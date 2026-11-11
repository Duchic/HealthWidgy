package cz.healthwidgy

import android.app.Application
import android.content.Context
import cz.healthwidgy.data.db.HealthDatabase
import cz.healthwidgy.data.db.RoomTransactionRunner
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.history.HistoryRepository
import cz.healthwidgy.data.inventory.InventoryRepository
import cz.healthwidgy.data.overview.OverviewRepository
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; reachable from activities, view models and (later) workers and widgets. */
class AppContainer(context: Context) {
  /** Outlives screens: syncs keep running when the user navigates away. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val healthConnect = HealthConnectSource(context)
  val settings = SettingsRepository(context)
  val database = HealthDatabase.create(context)
  val overview = OverviewRepository(healthConnect)
  val inventory = InventoryRepository(context, healthConnect)
  val syncEngine = SyncEngine(healthConnect, database.dao(), RoomTransactionRunner(database), settings)
  val history = HistoryRepository(database.dao(), settings, healthConnect)

  /** Starts a sync in the background (throttled inside the engine unless [force]). */
  fun requestSync(force: Boolean = false) {
    appScope.launch { syncEngine.sync(force) }
  }
}

class HealthWidgyApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}

val Context.appContainer: AppContainer
  get() = (applicationContext as HealthWidgyApp).container
