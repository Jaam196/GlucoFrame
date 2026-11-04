package com.example.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.GlucoseFrameApplication
import java.util.concurrent.TimeUnit

class SyncWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "GlucoseFrame_SyncWatchdog"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? GlucoseFrameApplication
            SyncAlarm.ensureScheduled(applicationContext, 30_000L)
            app?.syncEngine?.syncOnce(forceGraph = true)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
