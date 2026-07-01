package com.quata.core.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class QuataLanguage(val tag: String) {
    Spanish("es"),
    French("fr"),
    English("en")
}

object QuataLanguageManager {
    var currentLanguage: QuataLanguage = QuataLanguage.English
        private set

    fun wrap(base: Context): Context {
        val deviceLocale = base.resources.configuration.primaryLocale()
        currentLanguage = when {
            deviceLocale.language.equals("es", ignoreCase = true) -> QuataLanguage.Spanish
            deviceLocale.language.equals("fr", ignoreCase = true) -> QuataLanguage.French
            else -> QuataLanguage.English
        }

        val appLocale = Locale.forLanguageTag(currentLanguage.tag)
        Locale.setDefault(appLocale)

        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(appLocale))
        return base.createConfigurationContext(config)
    }

    private fun Configuration.primaryLocale(): Locale =
        locales[0]
}
