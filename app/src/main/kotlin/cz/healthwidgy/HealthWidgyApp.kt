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
import cz.healthwidgy.data.sync.SyncWorker
import cz.healthwidgy.widget.WidgetUpdater
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container; reachable from activities, view models, workers and widgets. */
@OptIn(FlowPreview::class)
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  /** Outlives screens: syncs keep running when the user navigates away. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val healthConnect = HealthConnectSource(appContext)
  val settings = SettingsRepository(appContext)
  val database = HealthDatabase.create(appContext)
  val overview = OverviewRepository(healthConnect)
  val inventory = InventoryRepository(appContext, healthConnect)
  val syncEngine = SyncEngine(healthConnect, database.dao(), RoomTransactionRunner(database), settings)
  val history = HistoryRepository(database.dao(), settings, healthConnect)

  private val previewsPublished = AtomicBoolean(false)

  init {
    // A new step goal or data source changes what widgets show.
    appScope.launch { settings.settings.distinctUntilChanged().drop(1).collect { WidgetUpdater.updateAll(appContext) } }
    // The widget picker shows previews in the widget theme: publish them again when it changes, after a
    // pause, so trying the options one after another uses only one of the few calls allowed per hour.
    appScope.launch {
      settings.settings.map { it.widgetTheme }.distinctUntilChanged().drop(1).debounce(3.seconds).collect {
        previewsPublished.set(true)
        publishPreviews()
      }
    }
  }

  /** Starts a sync in the background (throttled inside the engine unless [force]), then redraws widgets. */
  fun requestSync(force: Boolean = false) {
    appScope.launch {
      syncEngine.sync(force)
      WidgetUpdater.updateAll(appContext)
      // Widget picker previews from real data, once per app process (the system rate-limits them).
      if (previewsPublished.compareAndSet(false, true)) publishPreviews()
    }
  }

  /** When the system rejected a preview (rate limit), the next sync of this process tries again. */
  private suspend fun publishPreviews() {
    if (!WidgetUpdater.publishPreviews(appContext)) previewsPublished.set(false)
  }
}

class HealthWidgyApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    SyncWorker.schedule(this)
  }
}

val Context.appContainer: AppContainer
  get() = (applicationContext as HealthWidgyApp).container
