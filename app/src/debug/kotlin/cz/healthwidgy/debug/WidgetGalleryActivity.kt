package cz.healthwidgy.debug

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import cz.healthwidgy.data.settings.ThemeMode
import cz.healthwidgy.widget.CalendarWidget
import cz.healthwidgy.widget.GoalRingsWidget
import cz.healthwidgy.widget.HealthWidget
import cz.healthwidgy.widget.HeartDayWidget
import cz.healthwidgy.widget.HeartWidget
import cz.healthwidgy.widget.LastWorkoutWidget
import cz.healthwidgy.widget.MultiValueWidget
import cz.healthwidgy.widget.SingleValueWidget
import cz.healthwidgy.widget.SleepWidget
import cz.healthwidgy.widget.StepsTrendWidget
import cz.healthwidgy.widget.StepsWidget
import cz.healthwidgy.widget.TodayWidget
import cz.healthwidgy.widget.WidgetData
import cz.healthwidgy.widget.WidgetTheme
import kotlinx.coroutines.launch

/**
 * Debug only: renders every widget (real cached data, One UI cell sizes) inside this activity, so widgets
 * can be checked without touching the home screen.
 *
 *   adb shell am start -n cz.healthwidgy/.debug.WidgetGalleryActivity [--ez night true] [--es theme DARK] [--es only Trend]
 *
 * night: render as if the system were in dark mode; theme: override Settings → Vzhled → Widgety;
 * only: show variants whose name contains this text.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetGalleryActivity : ComponentActivity() {
  private class Variant(val name: String, val widget: HealthWidget, val wDp: Int, val hDp: Int, val prefs: Preferences? = null)

  private fun prefs(vararg slots: Pair<String, String>, period: Int? = null): Preferences =
    mutablePreferencesOf(*slots.map { (k, v) -> stringPreferencesKey(k) to v }.toTypedArray()).apply {
      if (period != null) this[intPreferencesKey("period")] = period
    }

  private val variants =
    listOf(
      Variant("Dnešní přehled 4×2", TodayWidget(), 401, 218),
      Variant("Dnešní přehled 4×2: spánek + tep, tuk, váha, kroky", TodayWidget(), 401, 218, prefs("ring" to "SLEEP", "slot1" to "HEART_RATE", "slot2" to "BODY_FAT", "slot3" to "WEIGHT", "slot4" to "STEPS")),
      Variant("Více hodnot 4×1", MultiValueWidget(), 401, 96),
      Variant("Více hodnot 2×2 (3 hodnoty)", MultiValueWidget(), 187, 218, prefs("slot4" to "NONE")),
      Variant("Více hodnot 4×2", MultiValueWidget(), 401, 218),
      Variant("Více hodnot 2×1 (2 hodnoty)", MultiValueWidget(), 187, 96, prefs("slot3" to "NONE", "slot4" to "NONE")),
      Variant("Více hodnot 2×1 (4 hodnoty)", MultiValueWidget(), 187, 96),
      Variant("Více hodnot 3×1 (4 hodnoty)", MultiValueWidget(), 294, 96),
      Variant("Úzký Trend 2×2: váha 30 dní", StepsTrendWidget(), 187, 218, prefs("metric" to "WEIGHT", period = 30)),
      Variant("Úzký Trend 2×2: vzdálenost", StepsTrendWidget(), 187, 218, prefs("metric" to "DISTANCE")),
      Variant("Úzký Kalendář 2×2", CalendarWidget(), 187, 218),
      Variant("Kroužek 2×2", StepsWidget(), 187, 218),
      Variant("Kroužek 2×2: patra", StepsWidget(), 187, 218, prefs("metric" to "FLOORS")),
      Variant("Kroužky cílů 2×2", GoalRingsWidget(), 187, 218),
      Variant("Kroužky cílů 4×2", GoalRingsWidget(), 401, 218),
      Variant("Kroužky cílů 3×2", GoalRingsWidget(), 294, 218),
      Variant("Trend 4×2: kroky 7 dní", StepsTrendWidget(), 401, 218),
      Variant("Trend 4×2: spánek 30 dní", StepsTrendWidget(), 401, 218, prefs("metric" to "SLEEP", period = 30)),
      Variant("Trend 4×2: klidový tep 14 dní", StepsTrendWidget(), 401, 218, prefs("metric" to "RESTING_HR", period = 14)),
      Variant("Trend 4×2: váha 30 dní", StepsTrendWidget(), 401, 218, prefs("metric" to "WEIGHT", period = 30)),
      Variant("Kalendář cíle 4×2", CalendarWidget(), 401, 218),
      Variant("Kalendář cíle 4×3: spánek", CalendarWidget(), 401, 330, prefs("metric" to "SLEEP")),
      Variant("Jedna hodnota 2×1", SingleValueWidget(), 187, 96),
      Variant("Jedna hodnota 2×1: klidový tep", SingleValueWidget(), 187, 96, prefs("metric" to "RESTING_HR")),
      Variant("Jedna hodnota 2×2: spánek", SingleValueWidget(), 187, 218, prefs("metric" to "SLEEP")),
      Variant("Spánek 4×2", SleepWidget(), 401, 218),
      Variant("Srdce 2×2", HeartWidget(), 187, 218),
      Variant("Tep dnes 4×2", HeartDayWidget(), 401, 218),
      Variant("Poslední aktivita 4×1", LastWorkoutWidget(), 401, 96),
    )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Screenshots over adb: turn the screen on and keep it on; show above the lock screen (this activity
    // only, nothing else becomes reachable).
    setShowWhenLocked(true)
    setTurnScreenOn(true)
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val night = intent.getBooleanExtra("night", false)
    val themeOverride = intent.getStringExtra("theme")?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
    val only = intent.getStringExtra("only")
    val density = resources.displayMetrics.density

    // The widgets are inflated in a context with the requested day/night mode, like a launcher would.
    val hostContext =
      createConfigurationContext(
        Configuration(resources.configuration).apply {
          uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
      )
    val list = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding((12 * density).toInt(), (40 * density).toInt(), (12 * density).toInt(), (40 * density).toInt())
    }
    setContentView(ScrollView(this).apply {
      setBackgroundColor(if (night) Color.rgb(40, 44, 52) else Color.rgb(150, 170, 190))
      addView(list)
    })

    lifecycleScope.launch {
      val snapshot = WidgetData.load(this@WidgetGalleryActivity)
      val mode = themeOverride ?: snapshot.theme
      // Four cells on One UI report about 401 dp; fit them on this screen.
      val maxW = (resources.displayMetrics.widthPixels / density).toInt() - 24
      variants.filter { only == null || it.name.contains(only, ignoreCase = true) }.forEach { original ->
        val v = Variant(original.name, original.widget, minOf(original.wDp, maxW), original.hDp, original.prefs)
        val rv =
          GlanceRemoteViews().compose(this@WidgetGalleryActivity, DpSize(v.wDp.dp, v.hDp.dp), state = v.prefs) {
            WidgetTheme(mode) { v.widget.Content(this@WidgetGalleryActivity, snapshot) }
          }.remoteViews
        list.addView(TextView(this@WidgetGalleryActivity).apply {
          text = "${v.name} · ${mode.name}${if (night) " · systém tmavý" else ""}"
          setTextColor(Color.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
          setPadding(0, (14 * density).toInt(), 0, (4 * density).toInt())
        })
        val frame = FrameLayout(this@WidgetGalleryActivity)
        list.addView(frame, LinearLayout.LayoutParams((v.wDp * density).toInt(), (v.hDp * density).toInt()))
        frame.addView(rv.apply(hostContext, frame))
      }
    }
  }
}
