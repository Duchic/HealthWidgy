package cz.healthwidgy.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/** The language choice in Settings. [tag] null = follow the phone. */
enum class LanguageChoice(val tag: String?) {
  SYSTEM(null),
  CZECH("cs"),
  ENGLISH("en");

  /** "Podle systému" / "System"; the languages are named in their own language. */
  val label: String
    get() =
      when (this) {
        SYSTEM -> tr("Podle systému", "System")
        CZECH -> "Čeština"
        ENGLISH -> "English"
      }
}

/**
 * The app's own language, kept by Android (per-app language, Android 13+), so it also shows in the phone's
 * Settings → Apps → HealthWidgy → Language and survives updates. Setting it recreates the open screens.
 */
object AppLanguage {
  fun current(context: Context): LanguageChoice {
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return LanguageChoice.SYSTEM
    if (locales.isEmpty) return LanguageChoice.SYSTEM
    return LanguageChoice.entries.firstOrNull { it.tag != null && it.tag == locales[0].language } ?: LanguageChoice.SYSTEM
  }

  fun set(context: Context, choice: LanguageChoice) {
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    manager.applicationLocales = choice.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
  }
}
