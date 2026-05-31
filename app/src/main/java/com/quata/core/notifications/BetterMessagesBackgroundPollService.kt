package com.quata.core.notifications

import android.app.job.JobParameters
import android.app.job.JobService
import com.quata.QuataApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BetterMessagesBackgroundPollService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        currentJob = scope.launch {
            val shouldRetry = runCatching {
                (application as QuataApp).container.chatRepository
                    .pollForBackgroundNotifications()
                    .isFailure
            }.getOrDefault(true)
            jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        currentJob?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
