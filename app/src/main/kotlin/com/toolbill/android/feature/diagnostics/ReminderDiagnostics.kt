package com.toolbill.android.feature.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.toolbill.android.UserSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line of the self-check. [ok] false renders in the overdue colour, never as a scare. */
data class SelfCheck(val label: String, val detail: String, val ok: Boolean)

private val firedFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

/**
 * The manufacturer and build the user is actually on.
 *
 * The OEM guidance below it is only useful if it names the right OEM, and the one thing a user
 * checks first on a screen like this is whether it recognises their phone.
 */
fun detectedDevice(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    return "$maker ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
}

/**
 * What the system actually reports, read fresh every time the screen is opened.
 *
 * Nothing here is asserted. A self-check that claims reminders work while they silently do not
 * is worse than no screen at all — it converts a fixable settings problem into a trust problem.
 *
 * There is deliberately no "exact alarms" line: reminders are scheduled with
 * `AlarmManager.setWindow()`, so exact-alarm access is neither held nor needed, and a row
 * reporting on it would describe a mechanism this app does not use.
 */
fun selfChecks(
    context: Context,
    settings: UserSettings,
    now: Instant = Instant.now(),
): List<SelfCheck> {
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val unrestricted = context.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    return listOf(
        SelfCheck(
            label = "Notification permission",
            detail = if (notificationsOn) "granted" else "not granted",
            ok = notificationsOn,
        ),
        SelfCheck(
            label = "Battery optimization",
            detail = if (unrestricted) "unrestricted" else "restricted",
            ok = unrestricted,
        ),
        lastSeen("Last reminder fired", settings.lastReminderFiredAt, now),
        lastSeen("Widget last updated", settings.lastWidgetUpdateAt, now),
    )
}

/**
 * A timestamp rendered as how long ago it was, or "never".
 *
 * Anything older than two days counts as not ok: a daily-scheduled app that has not woken in
 * two days is being held down, whatever the permission rows say.
 */
private fun lastSeen(label: String, epochMillis: Long, now: Instant): SelfCheck {
    if (epochMillis <= 0L) return SelfCheck(label, "never", ok = false)

    val at = Instant.ofEpochMilli(epochMillis)
    val age = Duration.between(at, now)
    val detail = when {
        age.toMinutes() < 1 -> "just now"
        age.toHours() < 1 -> "${age.toMinutes()}m ago"
        age.toDays() < 1 -> "${age.toHours()}h ago"
        age.toDays() < 7 -> "${age.toDays()} days ago"
        else -> LocalDateTime.ofInstant(at, ZoneId.systemDefault()).format(firedFormat)
    }
    return SelfCheck(label, detail, ok = age.toDays() < 2)
}

/**
 * The system battery screen for this app, falling back to the general one.
 *
 * No OEM deep links: the package names differ per manufacturer and per build, and an intent
 * that resolves to nothing leaves the user on a screen that did not open with no idea why.
 */
fun openBatterySettings(context: Context) {
    val perApp = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(perApp) }
        .recoverCatching { context.startActivity(fallback) }
}

/** The system notification screen for this app, where the permission row is turned back on. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
