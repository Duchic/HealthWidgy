package cz.healthwidgy.i18n

import java.util.Locale

/** The languages of the app. Czech for a Czech locale, English for every other. */
enum class Lang(val tag: String) {
  CS("cs"),
  EN("en"),
}

/**
 * The language texts are shown in. It follows the app's locale: the language picked in Settings (a per-app
 * language kept by Android, see [AppLanguage]) or, with "Podle systému", the phone's. Android updates the
 * default locale of the process when it changes and recreates the screens, so reading it on every use
 * is enough.
 */
object AppLang {
  /** Tests pin a language here. */
  @Volatile var override: Lang? = null

  val current: Lang
    get() = override ?: of(Locale.getDefault())

  fun of(locale: Locale): Lang = if (locale.language == Lang.CS.tag) Lang.CS else Lang.EN

  val isCzech: Boolean
    get() = current == Lang.CS
}

/** The text for the current language: `tr("Dnes", "Today")`. */
fun <T> tr(cs: T, en: T): T = if (AppLang.isCzech) cs else en
