package com.toolbill.android.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.toolbill.android.UserSettings
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.domain.reminder.PendingReminder
import com.toolbill.android.core.domain.reminder.nextReminderAfter
import com.toolbill.android.core.domain.reminder.overdueReminders
import com.toolbill.android.core.domain.reminder.pendingReminders
import com.toolbill.android.core.domain.reminder.pruneDelivered
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps the device's single renewal alarm in step with the portfolio.
 *
 * There is exactly one entry point, [refresh], and everything calls it: app start, any change
 * to a subscription, the alarm firing, a reboot, a clock or timezone change, and the daily
 * worker. That is deliberate — a reminder system with one path through it has one thing to get
 * right, where a separate "on alarm" path is a second place for the two to drift apart.
 *
 * Firing is idempotent by construction. When the alarm goes off, the reminders it was set for
 * have by definition passed their moment, so they are already what [overdueReminders] returns.
 */
class ReminderManager(
    private val context: Context,
    private val repository: SubscriptionRepository,
    private val settings: UserSettings,
    /**
     * Renewal reminders are a Pro feature.
     *
     * Checked at delivery rather than at scheduling, so a lapse stops the notifications without
     * losing the plan -- the design is explicit that nothing is deleted or hidden on lapse, and
     * re-subscribing should not mean rebuilding a schedule from scratch.
     */
    private val isEntitled: () -> Boolean = { true },
) {

    /**
     * Delivers anything owed, then arms the alarm for the next thing due.
     *
     * Safe to call as often as anything likes: delivery is guarded by the recorded keys, and
     * setting an alarm replaces the one already held rather than adding to it.
     */
    suspend fun refresh() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()

        val reminders = pendingReminders(
            subscriptions = repository.subscriptionsOnce(),
            leadDays = settings.leadTimeDays,
            today = today,
        )

        deliverOverdue(reminders, now, today)
        arm(nextReminderAfter(reminders, now), zone)
    }

    private fun deliverOverdue(
        reminders: List<PendingReminder>,
        now: LocalDateTime,
        today: LocalDate,
    ) {
        val delivered = settings.deliveredReminderKeys
        val owed = overdueReminders(reminders, now, delivered)
        if (owed.isEmpty()) {
            // Still worth pruning: keys outlive the charges they describe.
            settings.deliveredReminderKeys = pruneDelivered(delivered, today)
            return
        }

        // Nothing is recorded as delivered unless it could actually be delivered. Marking a
        // reminder sent while notifications are off -- or while the feature is not held --
        // would silently swallow it for good.
        if (!isEntitled() || !ReminderNotifier.canNotify(context)) return

        val home = settings.homeCurrency.value
        owed.forEach { ReminderNotifier.notify(context, it, home, today) }

        settings.deliveredReminderKeys = pruneDelivered(delivered + owed.map { it.key }, today)
        settings.recordReminderFired(wasTest = false)
    }

    /** Arms the single alarm, or cancels it when the portfolio owes nothing. */
    private fun arm(next: LocalDateTime?, zone: ZoneId) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pending = alarmIntent(context)

        if (next == null) {
            alarms.cancel(pending)
            // Release the PendingIntent as well. AlarmManager.cancel drops the alarm but leaves
            // the intent registered on the device, so a portfolio owing nothing would still
            // look, to anything inspecting it, like something was scheduled.
            pending.cancel()
            return
        }

        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()
        // setWindow, not setExact. An hour of tolerance on a three-day warning costs the user
        // nothing and keeps this clear of the exact-alarm permission, which Play restricts to
        // alarm clocks and calendar apps -- a restriction this app could not justify clearing.
        runCatching {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, pending)
        }.onFailure { Log.w(TAG, "Could not arm the renewal alarm", it) }
    }

    companion object {
        private const val TAG = "Toolbill"
        private const val REQUEST_CODE = 4001

        /** One hour of slack. See [arm]. */
        private const val WINDOW_MILLIS = 60L * 60L * 1000L

        fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
