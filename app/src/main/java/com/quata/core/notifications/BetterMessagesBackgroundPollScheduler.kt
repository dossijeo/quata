package com.quata.core.notifications

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

class BetterMessagesBackgroundPollScheduler(private val context: Context) {
    fun schedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val component = ComponentName(context, BetterMessagesBackgroundPollService::class.java)
        val job = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(POLL_INTERVAL_MILLIS, POLL_FLEX_MILLIS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    companion object {
        private const val JOB_ID = 43_210
        private const val POLL_INTERVAL_MILLIS = 15L * 60L * 1000L
        private const val POLL_FLEX_MILLIS = 5L * 60L * 1000L
    }
}
