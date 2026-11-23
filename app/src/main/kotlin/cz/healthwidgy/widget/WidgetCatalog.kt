package cz.healthwidgy.widget

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.LocalState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import cz.healthwidgy.data.settings.ThemeMode

/** A place in a widget the user fills with a metric. [optional] slots can also be left empty. */
data class Slot(val key: String, val title: String, val options: List<WidgetMetric>, val default: WidgetMetric, val optional: Boolean = false) {
  val prefKey: Preferences.Key<String> = stringPreferencesKey(key)
}

/** What can be configured on a widget: its slots and, for charts, the number of days. */
data class WidgetConfig(val slots: List<Slot>, val periods: List<Int> = emptyList(), val defaultPeriod: Int = 0)

/** Per-instance choices, stored in the widget's Glance state (Preferences). */
object WidgetChoices {
  const val EMPTY = "NONE"
  val PERIOD_KEY = intPreferencesKey("period")

  /** This widget's own theme; absent = follow Settings → Vzhled → Widgety. */
  val THEME_KEY = stringPreferencesKey("theme")

  fun theme(prefs: Preferences?): ThemeMode? = prefs?.get(THEME_KEY)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }

  /** The slot's metric; null when the user left an optional slot empty. */
  fun metric(prefs: Preferences?, slot: Slot): WidgetMetric? {
    val stored = prefs?.get(slot.prefKey) ?: return slot.default
    if (stored == EMPTY && slot.optional) return null
    return WidgetMetric.byName(stored)?.takeIf { it in slot.options } ?: slot.default
  }

  fun period(prefs: Preferences?, config: WidgetConfig): Int = prefs?.get(PERIOD_KEY)?.takeIf { it in config.periods } ?: config.defaultPeriod

  fun write(prefs: MutablePreferences, config: WidgetConfig, metrics: Map<String, WidgetMetric?>, period: Int?, theme: ThemeMode?) {
    config.slots.forEach { slot -> prefs[slot.prefKey] = metrics[slot.key]?.name ?: EMPTY }
    if (period != null && config.periods.isNotEmpty()) prefs[PERIOD_KEY] = period
    if (theme != null) prefs[THEME_KEY] = theme.name else prefs.remove(THEME_KEY)
  }

  /** "Kroky · Patra · Spánek · 7 dní · tmavý vzhled" for the in-app list of placed widgets. */
  fun summary(prefs: Preferences?, config: WidgetConfig): String {
    val parts =
      config.slots.mapNotNull { metric(prefs, it)?.shortLabel } +
        listOfNotNull(
          config.periods.takeIf { it.isNotEmpty() }?.let { "${period(prefs, config)} dní" },
          theme(prefs)?.let { themeLabel(it) },
        )
    return parts.joinToString(" · ").ifEmpty { "Vzhled podle nastavení" }
  }

  /** "světlý vzhled", "tmavý vzhled", "vzhled podle systému". */
  fun themeLabel(theme: ThemeMode): String =
    when (theme) {
      ThemeMode.SYSTEM -> "vzhled podle systému"
      ThemeMode.LIGHT -> "světlý vzhled"
      ThemeMode.DARK -> "tmavý vzhled"
    }
}

/** The current widget instance's own theme, or null to follow the app setting. */
@Composable
fun chosenTheme(): ThemeMode? = WidgetChoices.theme(LocalState.current as? Preferences)

/** The current widget instance's choice for [slot] (defaults while the picker renders a preview). */
@Composable
fun chosen(slot: Slot): WidgetMetric? = WidgetChoices.metric(LocalState.current as? Preferences, slot)

@Composable
fun chosenPeriod(config: WidgetConfig): Int = WidgetChoices.period(LocalState.current as? Preferences, config)

/** Everything the app knows about one widget type: picker texts, classes and what can be configured. */
data class WidgetInfo(
  val name: String,
  val size: String,
  val description: String,
  val receiver: Class<out GlanceAppWidgetReceiver>,
  val create: () -> GlanceAppWidget,
  val config: WidgetConfig? = null,
)

object WidgetCatalog {
  /** Widgets without values to pick still get their own theme (declared before [all], which uses it). */
  private val THEME_ONLY = WidgetConfig(emptyList())

  val all: List<WidgetInfo> =
    listOf(
      WidgetInfo("Dnešní přehled", "4×2", "Kroužek a čtyři hodnoty dle výběru", TodayWidgetReceiver::class.java, ::TodayWidget, TodayWidget.config),
      WidgetInfo("Více hodnot", "4×1", "Dvě až čtyři hodnoty vedle sebe", MultiValueWidgetReceiver::class.java, ::MultiValueWidget, MultiValueWidget.config),
      WidgetInfo("Kroužek", "2×2", "Jeden cíl s procentem splnění", StepsWidgetReceiver::class.java, ::StepsWidget, StepsWidget.config),
      WidgetInfo("Malý kroužek", "1×1", "Jeden cíl v kroužku na nejmenší ploše", MiniRingWidgetReceiver::class.java, ::MiniRingWidget, MiniRingWidget.config),
      WidgetInfo("Kroužky cílů", "2×2", "Tři cíle v soustředných kroužcích", GoalRingsWidgetReceiver::class.java, ::GoalRingsWidget, GoalRingsWidget.config),
      WidgetInfo("Trend", "4×2", "Graf za 7, 14 nebo 30 dní", StepsTrendWidgetReceiver::class.java, ::StepsTrendWidget, StepsTrendWidget.config),
      WidgetInfo("Kalendář cíle", "4×2", "Posledních 5 týdnů, den po dni", CalendarWidgetReceiver::class.java, ::CalendarWidget, CalendarWidget.config),
      WidgetInfo("Jedna hodnota", "2×1", "Vybraná hodnota velkým písmem", SingleValueWidgetReceiver::class.java, ::SingleValueWidget, SingleValueWidget.config),
      WidgetInfo("Malá hodnota", "1×1", "Vybraná hodnota na nejmenší ploše", MiniValueWidgetReceiver::class.java, ::MiniValueWidget, MiniValueWidget.config),
      // Nothing to pick but the theme.
      WidgetInfo("Spánek", "4×2", "Délka a fáze poslední noci", SleepWidgetReceiver::class.java, ::SleepWidget, THEME_ONLY),
      WidgetInfo("Srdce", "2×2", "Klidový tep a trend 7 dní", HeartWidgetReceiver::class.java, ::HeartWidget, THEME_ONLY),
      WidgetInfo("Tep dnes", "4×2", "Tep po hodinách od půlnoci", HeartDayWidgetReceiver::class.java, ::HeartDayWidget, THEME_ONLY),
      WidgetInfo("Poslední aktivita", "4×1", "Poslední trénink", LastWorkoutWidgetReceiver::class.java, ::LastWorkoutWidget, THEME_ONLY),
    )

  fun byProvider(className: String?): WidgetInfo? = all.firstOrNull { it.receiver.name == className }
}
