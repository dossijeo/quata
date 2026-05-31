package com.quata

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.quata.core.di.AppContainer
import com.quata.core.notifications.BetterMessagesBackgroundPollScheduler
import com.quata.feature.chat.domain.ChatPollingMode

class QuataApp : Application() {
    lateinit var container: AppContainer
        private set
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BetterMessagesBackgroundPollScheduler(this).schedule()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                container.chatRepository.setAppForeground(true)
                container.chatRepository.setPollingMode(ChatPollingMode.MEDIUM)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    container.chatRepository.setAppForeground(false)
                    val mode = if (container.sessionManager.isLoggedIn()) {
                        ChatPollingMode.RELAXED
                    } else {
                        ChatPollingMode.MINIMAL
                    }
                    container.chatRepository.setPollingMode(mode)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
