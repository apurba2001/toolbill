package com.toolbill.android.core.domain.reminder

import com.toolbill.android.core.domain.date.nextChargeDate
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The local time of day reminders land at.
 *
 * Morning, so a renewal three days out is read over coffee rather than at 2am, and fixed rather
 * than configurable: the lead time is the thing worth choosing, and a second control for the
 * hour would double the settings surface to move a notification by four hours.
 */
val REMINDER_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

/** The lead times Settings offers, in days. */
val LEAD_TIME_CHOICES: List<Int> = listOf(1, 2, 3, 5, 7)

/** The default, matching what the Settings row has always displayed. */
const val DEFAULT_LEAD_DAYS: Int = 3

/**
 * A reminder owed for one subscription's next charge.
 *
 * [key] identifies the charge rather than the subscription, so a delivered reminder can be
 * recorded and the same renewal never announced twice — while next month's, which is a
 * different charge, still gets through.
 */
data class PendingReminder(
    val subscription: Subscription,
    val chargeDate: LocalDate,
    val remindAt: LocalDateTime,
) {
    val key: String get() = "${subscription.id}@$chargeDate"

    /** Days from [today] to the charge. Zero reads as "renews today", never "in 0 days". */
    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, chargeDate)
}

/**
 * Every reminder the portfolio currently owes, earliest first.
 *
 * Paused and cancelled subscriptions are excluded: a paused plan is not billing, and a
 * cancelled one has stopped. Trials are included — the first charge after a trial ends is the
 * single most valuable notification this app can send.
 *
 * Pure, so the whole scheduling decision is testable on the JVM without an emulator. Turning a
 * [LocalDateTime] into an alarm instant happens at the Android boundary, which is what keeps
 * DST and timezone changes out of this function.
 */
fun pendingReminders(
    subscriptions: List<Subscription>,
    leadDays: Int,
    today: LocalDate,
): List<PendingReminder> = subscriptions
    .filter { it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.TRIAL }
    .map { subscription ->
        // The charge the row itself displays: the next occurrence, counting one falling today.
        val chargeDate = nextChargeDate(
            subscription.anchorDate,
            subscription.cycle.unit,
            subscription.cycle.count,
            today.minusDays(1),
        )
        PendingReminder(
            subscription = subscription,
            chargeDate = chargeDate,
            remindAt = chargeDate.minusDays(leadDays.toLong()).atTime(REMINDER_TIME_OF_DAY),
        )
    }
    .sortedBy { it.remindAt }

/**
 * The earliest reminder still ahead of [now] — the one instant worth holding an alarm for.
 *
 * One alarm for the whole portfolio rather than one per subscription. Thirty-five alarms is
 * thirty-five things for a battery optimiser to drop; one is a single point of failure that
 * the safety net can check on. When it fires, everything due at that moment goes out together.
 */
fun nextReminderAfter(reminders: List<PendingReminder>, now: LocalDateTime): LocalDateTime? =
    reminders.map { it.remindAt }.filter { it > now }.minOrNull()

/**
 * Reminders that should already have been delivered and have not been.
 *
 * A reminder falls in here when its moment has passed — the alarm was dropped, the phone was
 * off, the lead time was lengthened after the fact — while the charge it warns about is still
 * ahead. Late is worth sending; a warning about a renewal that has already happened is not.
 *
 * [delivered] holds the keys already announced, so a catch-up that runs on every app open and
 * every daily worker pass cannot re-announce the same renewal.
 */
fun overdueReminders(
    reminders: List<PendingReminder>,
    now: LocalDateTime,
    delivered: Set<String>,
): List<PendingReminder> = reminders.filter {
    it.remindAt <= now && it.chargeDate >= now.toLocalDate() && it.key !in delivered
}

/**
 * Delivered keys worth keeping.
 *
 * A key stops mattering once its charge is in the past — that renewal cannot come round again,
 * and the next one carries a different date. Pruned rather than accumulated so the record does
 * not grow without limit for the life of the install.
 */
fun pruneDelivered(delivered: Set<String>, today: LocalDate): Set<String> =
    delivered.filter { key ->
        val date = key.substringAfterLast('@')
        runCatching { LocalDate.parse(date) >= today.minusDays(1) }.getOrDefault(false)
    }.toSet()
