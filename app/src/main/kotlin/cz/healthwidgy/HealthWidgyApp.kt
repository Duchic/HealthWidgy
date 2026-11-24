package cz.healthwidgy

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import cz.healthwidgy.data.db.HealthDatabase
import cz.healthwidgy.data.db.RoomTransactionRunner
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.history.HistoryRepository
import cz.healthwidgy.data.inventory.InventoryRepository
import cz.healthwidgy.data.overview.OverviewRepository
import cz.healthwidgy.data.settings.SettingsRepository
import cz.healthwidgy.data.sync.SyncEngine
import cz.healthwidgy.data.sync.SyncWorker
import cz.healthwidgy.i18n.AppLang
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
class AppContainer(
  context: Context,
  val healthConnect: HealthConnectSource = HealthConnectSource(context.applicationContext),
) {
  private val appContext = context.applicationContext

  /** Outlives screens: syncs keep running when the user navigates away. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val settings = SettingsRepository(appContext)
  val database = HealthDatabase.create(appContext)
  val overview = OverviewRepository(healthConnect)
  val inventory = InventoryRepository(appContext, healthConnect)
  val syncEngine = SyncEngine(healthConnect, database.dao(), RoomTransactionRunner(database), settings)
  val history = HistoryRepository(database.dao(), settings, healthConnect)

  private val previewsPublished = AtomicBoolean(false)

  init {
    // A new step goal or data source changes what widgets show.
    // (The Přehled card layout is not shown in widgets: editing it must not redraw them on every tap.)
    appScope.launch { settings.settings.map { it.copy(overviewCards = emptyList()) }.distinctUntilChanged().drop(1).collect { WidgetUpdater.updateAll(appContext) } }
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

  /**
   * Placed widgets keep the text they were drawn with: redraw them now. The picker previews are published
   * again by the next sync (not here, so switching back and forth does not use up the hourly allowance).
   */
  fun onLanguageChanged(onRedrawn: () -> Unit) {
    previewsPublished.set(false)
    appScope.launch {
      WidgetUpdater.updateAll(appContext)
      onRedrawn()
    }
  }

  /** When the system rejected a preview (rate limit), the next sync of this process tries again. */
  private suspend fun publishPreviews() {
    if (!WidgetUpdater.publishPreviews(appContext)) previewsPublished.set(false)
  }
}

open class HealthWidgyApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = createContainer()
    SyncWorker.schedule(this)
    // The language may have changed while the app was not running (phone language, Settings → Apps).
    redrawWidgetsIfLanguageChanged()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    redrawWidgetsIfLanguageChanged()
  }

  /**
   * Compares the current language with the one the widgets were last drawn in. It is stored only after the
   * redraw, so a process killed in between tries again on its next start.
   */
  private fun redrawWidgetsIfLanguageChanged() {
    val prefs = getSharedPreferences(LANGUAGE_PREFS, MODE_PRIVATE)
    val language = AppLang.current.tag
    if (prefs.getString(KEY_WIDGET_LANGUAGE, null) == language) return
    container.onLanguageChanged { prefs.edit().putString(KEY_WIDGET_LANGUAGE, language).apply() }
  }

  private companion object {
    const val LANGUAGE_PREFS = "language"
    const val KEY_WIDGET_LANGUAGE = "widgets_drawn_in"
  }

  /** The demo build (src/demo) overrides this to read generated data instead of Health Connect. */
  protected open fun createContainer(): AppContainer = AppContainer(this)
}

val Context.appContainer: AppContainer
  get() = (applicationContext as HealthWidgyApp).container
