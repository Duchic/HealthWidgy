package cz.healthwidgy.widget

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.LocalState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

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

  /** The slot's metric; null when the user left an optional slot empty. */
  fun metric(prefs: Preferences?, slot: Slot): WidgetMetric? {
    val stored = prefs?.get(slot.prefKey) ?: return slot.default
    if (stored == EMPTY && slot.optional) return null
    return WidgetMetric.byName(stored)?.takeIf { it in slot.options } ?: slot.default
  }

  fun period(prefs: Preferences?, config: WidgetConfig): Int = prefs?.get(PERIOD_KEY)?.takeIf { it in config.periods } ?: config.defaultPeriod

  fun write(prefs: MutablePreferences, config: WidgetConfig, metrics: Map<String, WidgetMetric?>, period: Int?) {
    config.slots.forEach { slot -> prefs[slot.prefKey] = metrics[slot.key]?.name ?: EMPTY }
    if (period != null && config.periods.isNotEmpty()) prefs[PERIOD_KEY] = period
  }

  /** "Kroky · Patra · Spánek · 7 dní" for the in-app list of placed widgets. */
  fun summary(prefs: Preferences?, config: WidgetConfig): String =
    (config.slots.mapNotNull { metric(prefs, it)?.shortLabel } + listOfNotNull(config.periods.takeIf { it.isNotEmpty() }?.let { "${period(prefs, config)} dní" }))
      .joinToString(" · ")
}

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
  val all: List<WidgetInfo> =
    listOf(
      WidgetInfo("Dnešní přehled", "4×2", "Kroužek a čtyři hodnoty dle výběru", TodayWidgetReceiver::class.java, ::TodayWidget, TodayWidget.config),
      WidgetInfo("Více hodnot", "4×1", "Dvě až čtyři hodnoty vedle sebe", MultiValueWidgetReceiver::class.java, ::MultiValueWidget, MultiValueWidget.config),
      WidgetInfo("Kroužek", "2×2", "Jeden cíl s procentem splnění", StepsWidgetReceiver::class.java, ::StepsWidget, StepsWidget.config),
      WidgetInfo("Kroužky cílů", "2×2", "Tři cíle v soustředných kroužcích", GoalRingsWidgetReceiver::class.java, ::GoalRingsWidget, GoalRingsWidget.config),
      WidgetInfo("Trend", "4×2", "Graf za 7, 14 nebo 30 dní", StepsTrendWidgetReceiver::class.java, ::StepsTrendWidget, StepsTrendWidget.config),
      WidgetInfo("Kalendář cíle", "4×2", "Posledních 5 týdnů, den po dni", CalendarWidgetReceiver::class.java, ::CalendarWidget, CalendarWidget.config),
      WidgetInfo("Jedna hodnota", "2×1", "Vybraná hodnota velkým písmem", SingleValueWidgetReceiver::class.java, ::SingleValueWidget, SingleValueWidget.config),
      WidgetInfo("Spánek", "4×2", "Délka a fáze poslední noci", SleepWidgetReceiver::class.java, ::SleepWidget),
      WidgetInfo("Srdce", "2×2", "Klidový tep a trend 7 dní", HeartWidgetReceiver::class.java, ::HeartWidget),
      WidgetInfo("Tep dnes", "4×2", "Tep po hodinách od půlnoci", HeartDayWidgetReceiver::class.java, ::HeartDayWidget),
      WidgetInfo("Poslední aktivita", "4×1", "Poslední trénink", LastWorkoutWidgetReceiver::class.java, ::LastWorkoutWidget),
    )

  fun byProvider(className: String?): WidgetInfo? = all.firstOrNull { it.receiver.name == className }
}
