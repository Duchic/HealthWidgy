package cz.healthwidgy.demo

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import cz.healthwidgy.AppContainer
import cz.healthwidgy.HealthWidgyApp
import cz.healthwidgy.data.hc.HealthConnectSource

/**
 * The demo build's application: the same app, but Health Connect is replaced by [DemoHealthConnectClient],
 * so everything on screen (and in the widgets) comes from a fictional person. Used for README screenshots:
 *   ./gradlew installDemo
 *   adb shell am start -n cz.healthwidgy.demo/cz.healthwidgy.MainActivity [--es cz.healthwidgy.OPEN sleep]
 */
class DemoApp : HealthWidgyApp() {

  override fun createContainer(): AppContainer =
    AppContainer(this, HealthConnectSource(this) { DemoHealthConnectClient(DemoData()) })

  override fun onCreate() {
    super.onCreate()
    // Screenshots from adb: show above the lock screen and keep the display on (the data is fictional).
    registerActivityLifecycleCallbacks(
      object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
          activity.setShowWhenLocked(true)
          activity.setTurnScreenOn(true)
          activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      }
    )
  }
}
