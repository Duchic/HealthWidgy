package cz.healthwidgy.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.healthwidgy.appContainer
import cz.healthwidgy.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Hourly background sync so widgets stay fresh without opening the app. Reading Health Connect from the
 * background needs READ_HEALTH_DATA_IN_BACKGROUND; without it the reads fail and the cache stays as it was.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val container = applicationContext.appContainer
    container.syncEngine.sync(force = true)
    WidgetUpdater.updateAll(applicationContext)
    return Result.success()
  }

  companion object {
    private const val NAME = "health-sync"

    fun schedule(context: Context) {
      val request =
        PeriodicWorkRequestBuilder<SyncWorker>(60, TimeUnit.MINUTES, 15, TimeUnit.MINUTES).build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
  }
}
