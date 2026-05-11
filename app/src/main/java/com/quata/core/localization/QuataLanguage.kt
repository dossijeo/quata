package com.quata.core.localization

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class QuataLanguage(val tag: String) {
    Spanish("es"),
    English("en")
}

object QuataLanguageManager {
    var currentLanguage: QuataLanguage = QuataLanguage.English
        private set

    fun wrap(base: Context): Context {
        val deviceLocale = base.resources.configuration.primaryLocale()
        currentLanguage = if (deviceLocale.language.equals("es", ignoreCase = true)) {
            QuataLanguage.Spanish
        } else {
            QuataLanguage.English
        }

        val appLocale = Locale.forLanguageTag(currentLanguage.tag)
        Locale.setDefault(appLocale)

        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(appLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = appLocale
        }
        return base.createConfigurationContext(config)
    }

    private fun Configuration.primaryLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locales[0]
        } else {
            @Suppress("DEPRECATION")
            locale
        }
}
