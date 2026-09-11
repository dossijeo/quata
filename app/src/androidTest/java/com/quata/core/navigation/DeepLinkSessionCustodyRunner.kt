package com.quata.core.navigation

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** No QuataApp startup observers, session migration, refresh or background work in custody. */
class DeepLinkSessionCustodyRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
