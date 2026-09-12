package com.toolbill.android.core.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.toolbill.android.MainActivity
import com.toolbill.android.R
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.reminder.PendingReminder
import com.toolbill.android.core.domain.subscription.pricedIn
import java.time.LocalDate

/**
 * Posts renewal reminders.
 *
 * One notification per renewal, never a digest: a single line naming one service and one figure
 * is actionable at a glance, where "4 renewals this week" makes the user open the app to find
 * out whether any of them matters.
 */
object ReminderNotifier {

    /** Renewal warnings. The only channel this app has, and the only one it intends to have. */
    const val CHANNEL_RENEWALS = "renewals"

    private const val TEST_NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_RENEWALS,
            "Renewals",
            // DEFAULT, not HIGH: a renewal three days out is worth a glance, not an interruption.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A heads-up before a subscription renews."
            setShowBadge(true)
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * Whether anything posted here would actually reach the user.
     *
     * Two questions, both of which have to be yes. The runtime permission is what the system
     * enforces from API 33; `areNotificationsEnabled` also covers the user switching the whole
     * channel off afterwards, which leaves the permission granted and the notification silent.
     */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(
        context: Context,
        reminder: PendingReminder,
        homeCurrency: String,
        today: LocalDate,
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val priced = reminder.subscription.pricedIn(homeCurrency)
        val amount = MoneyFormat.symbol(priced.homeAmountMinor, homeCurrency)
        val whenText = when (val days = reminder.daysUntil(today)) {
            0L -> "Renews today"
            1L -> "Renews tomorrow"
            else -> "Renews in $days days"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.subscription.name)
            .setContentText("$whenText · $amount")
            .setContentIntent(openApp(context, reminder.key.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        // Keyed on the charge, so a re-delivery replaces the same notification rather than
        // stacking a second copy of a renewal the user has already seen.
        // Guarded by canNotify above, which checks the runtime permission explicitly.
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context)
            .notify(reminder.key.hashCode(), notification)
    }

    /**
     * The diagnostic screen's proof-of-life.
     *
     * Deliberately indistinguishable in delivery from a real reminder: if this one arrives and
     * real ones do not, the problem is scheduling rather than the notification path, and that
     * is exactly the distinction the screen exists to draw.
     */
    fun notifyTest(context: Context): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Toolbill test reminder")
            .setContentText("Delivery works. Real renewal reminders arrive the same way.")
            .setContentIntent(openApp(context, TEST_NOTIFICATION_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        // Guarded by canNotify above.
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
