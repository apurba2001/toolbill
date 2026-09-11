package com.toolbill.android.core.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.toolbill.android.ToolbillApplication
import java.util.concurrent.TimeUnit

/**
 * The safety net.
 *
 * AlarmManager is the primary path and it is the one that gets dropped: an OEM battery policy
 * that puts the app to sleep takes the alarm with it, silently, with nothing in the UI to say
 * so. This runs daily through WorkManager — a different mechanism with different constraints —
 * and delivers anything the alarm missed. Late is a worse reminder than on time, and a far
 * better one than none.
 *
 * It shares [ReminderManager.refresh] with the alarm, so a reminder already delivered is not
 * repeated and the alarm is re-armed as a side effect of every pass.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ToolbillApplication ?: return Result.success()
        return try {
            app.reminderManager.refresh()
            Result.success()
        } catch (t: Throwable) {
            // Retry rather than fail: the next window is a day away, and a transient database
            // error should not cost a whole day of catch-up.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "toolbill-reminder-net"

        /**
         * Registers the daily pass, keeping any existing schedule.
         *
         * KEEP rather than UPDATE: re-registering on every app start would restart the period
         * each time, and an app opened daily would push the net out indefinitely — leaving the
         * one user who never opens the app with the one thing that was meant to cover them.
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                // A wide flex window: this is a catch-up, so the system is free to fold it into
                // whatever maintenance pass it was already going to run.
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
