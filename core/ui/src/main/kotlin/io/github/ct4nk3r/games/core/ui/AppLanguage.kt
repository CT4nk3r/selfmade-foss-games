package io.github.ct4nk3r.games.core.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat

/**
 * In-app language choice. Uses the per-app language API, so on Android 13+ the choice
 * also shows up in the system's "App languages" settings.
 * To add a language: add `values-xx/strings.xml` to each module, an entry here,
 * and a `<locale>` line in each app's `res/xml/locales_config.xml`.
 */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    HUNGARIAN("hu", "Magyar");

    fun apply() {
        val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales[0]?.language
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}

@Composable
fun AppLanguage.label(): String = nativeName ?: stringResource(R.string.settings_language_system)
