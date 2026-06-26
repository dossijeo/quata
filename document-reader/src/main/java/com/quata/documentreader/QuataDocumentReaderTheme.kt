package com.quata.documentreader

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

object QuataDocumentReaderTheme {
    @JvmStatic
    fun apply(activity: AppCompatActivity) {
        if (!activity.intent.hasExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE)) return
        val isDarkMode = activity.intent.getBooleanExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE, false)
        activity.delegate.localNightMode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    @JvmStatic
    fun copyThemeExtra(from: android.content.Intent, to: android.content.Intent) {
        if (from.hasExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE)) {
            to.putExtra(
                QuataDocumentReader.EXTRA_IS_DARK_MODE,
                from.getBooleanExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE, false)
            )
        }
    }
}
