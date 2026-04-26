package com.quata

import android.app.Application
import com.quata.core.di.AppContainer

class QuataApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
