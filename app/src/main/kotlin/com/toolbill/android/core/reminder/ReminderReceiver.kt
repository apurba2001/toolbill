package com.toolbill.android.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.toolbill.android.ToolbillApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The renewal alarm going off.
 *
 * Delivery and rescheduling both happen inside [ReminderManager.refresh], so this receiver has
 * no logic of its own beyond keeping the process alive long enough for a database read.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ToolbillApplication ?: return
        // A broadcast receiver is dead the moment onReceive returns, and reading the portfolio
        // is a suspending database call. goAsync holds the process open until it finishes.
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.reminderManager.refresh()
            } catch (t: Throwable) {
                Log.w("Toolbill", "Reminder alarm failed", t)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * Everything that invalidates an armed alarm.
 *
 * A reboot clears the alarm table outright. A clock or timezone change moves the wall-clock
 * instant the alarm was set against, so a 09:00 reminder set before a flight would otherwise
 * arrive at 04:00 after it. An app update cancels alarms on some versions and not others,
 * which is reason enough to re-arm on all of them.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ToolbillApplication ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.reminderManager.refresh()
                // The safety net is registered here too: a reboot is exactly when a device is
                // most likely to have dropped both the alarm and the periodic work.
                ReminderWorker.ensureScheduled(context)
            } catch (t: Throwable) {
                Log.w("Toolbill", "Reminder reschedule failed on ${intent.action}", t)
            } finally {
                result.finish()
            }
        }
    }
}
