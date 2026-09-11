package com.toolbill.android.core.data.fx

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.toolbill.android.ToolbillApplication
import java.util.concurrent.TimeUnit

/**
 * Pulls exchange rates once a day.
 *
 * Daily because that is how often the source changes: the ECB publishes reference rates once
 * each business day, so polling harder would spend the user's battery and data to fetch the
 * same numbers back.
 */
class FxRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ToolbillApplication ?: return Result.success()
        // A failed refresh is not a failed job. The rates already in hand remain the best
        // available, and retrying immediately on a connection that just dropped helps nobody.
        app.fxRepository.refresh()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "toolbill-fx-refresh"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<FxRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            // KEEP, not UPDATE: re-registering on every app start restarts the period, and an
            // app opened daily would push the refresh out indefinitely.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
