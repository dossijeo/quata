package com.quata.feature.chat.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quata.QuataApp
import java.util.concurrent.TimeUnit

class ChatOutboxWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? QuataApp ?: return Result.retry()
        return if (app.container.chatRepository.flushPendingMessages()) Result.success() else Result.retry()
    }
}

object ChatOutboxWorkScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun scheduleOneTime(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ChatOutboxWorker>().setConstraints(constraints).build()
        )
    }

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ChatOutboxWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
    }

    private const val ONE_TIME_WORK_NAME = "quata-chat-outbox"
    private const val PERIODIC_WORK_NAME = "quata-chat-outbox-periodic"
}
