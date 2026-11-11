package cz.healthwidgy

import android.app.Application
import android.content.Context
import cz.healthwidgy.data.hc.HealthConnectSource
import cz.healthwidgy.data.inventory.InventoryRepository
import cz.healthwidgy.data.overview.OverviewRepository
import cz.healthwidgy.data.settings.SettingsRepository

/** Manual dependency container; reachable from activities, view models and (later) workers and widgets. */
class AppContainer(context: Context) {
  val healthConnect = HealthConnectSource(context)
  val settings = SettingsRepository(context)
  val overview = OverviewRepository(healthConnect)
  val inventory = InventoryRepository(context, healthConnect)
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
