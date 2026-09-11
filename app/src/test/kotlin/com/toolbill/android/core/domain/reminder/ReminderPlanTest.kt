package com.toolbill.android.core.domain.reminder

import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A renewal reminder that silently does not fire is a one-star review, so the decision about
 * *when* to fire is kept pure and pinned here rather than discovered on a device.
 */
class ReminderPlanTest {

    private val today = LocalDate.of(2026, 6, 10)

    private fun sub(
        id: String = "a",
        anchor: LocalDate = LocalDate.of(2026, 6, 20),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        cycle: BillingCycle = BillingCycle.MONTHLY,
    ) = Subscription(
        id = id,
        name = "Claude Pro",
        amountMinor = 2_000,
        currency = "USD",
        cycle = cycle,
        anchorDate = anchor,
        category = Category.AI_TOOLS,
        status = status,
    )

    @Test
    fun `reminds the lead time ahead of the charge, at the reminder hour`() {
        val plan = pendingReminders(listOf(sub()), leadDays = 3, today = today).single()

        assertEquals(LocalDate.of(2026, 6, 20), plan.chargeDate)
        assertEquals(LocalDateTime.of(2026, 6, 17, 9, 0), plan.remindAt)
    }

    @Test
    fun `a paused subscription is not billing, so it is not reminded`() {
        val plan = pendingReminders(
            listOf(sub(status = SubscriptionStatus.PAUSED)),
            leadDays = 3,
            today = today,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a cancelled subscription has stopped, so it is not reminded`() {
        val plan = pendingReminders(
            listOf(sub(status = SubscriptionStatus.CANCELLED)),
            leadDays = 3,
            today = today,
        )
        assertTrue(plan.isEmpty())
    }

    /** The first charge after a trial ends is the most valuable notification this app sends. */
    @Test
    fun `a trial is reminded`() {
        val plan = pendingReminders(
            listOf(sub(status = SubscriptionStatus.TRIAL)),
            leadDays = 3,
            today = today,
        )
        assertEquals(1, plan.size)
    }

    @Test
    fun `the next alarm is the earliest reminder still ahead`() {
        val reminders = pendingReminders(
            listOf(
                sub(id = "a", anchor = LocalDate.of(2026, 6, 20)),
                sub(id = "b", anchor = LocalDate.of(2026, 6, 14)),
                sub(id = "c", anchor = LocalDate.of(2026, 7, 1)),
            ),
            leadDays = 3,
            today = today,
        )

        val next = nextReminderAfter(reminders, LocalDateTime.of(2026, 6, 10, 8, 0))

        // b renews on the 14th, so its reminder lands first, on the 11th.
        assertEquals(LocalDateTime.of(2026, 6, 11, 9, 0), next)
    }

    @Test
    fun `an instant already passed is not a candidate for the next alarm`() {
        val reminders = pendingReminders(listOf(sub()), leadDays = 3, today = today)

        // Standing at the 18th, the 17th reminder is behind us.
        assertNull(nextReminderAfter(reminders, LocalDateTime.of(2026, 6, 18, 9, 0)))
    }

    /**
     * The phone was off, or a battery optimiser dropped the alarm. The charge is still ahead,
     * so the warning is still worth sending — late beats never.
     */
    @Test
    fun `a missed reminder whose charge is still ahead is overdue`() {
        val reminders = pendingReminders(listOf(sub()), leadDays = 3, today = today)

        val overdue = overdueReminders(
            reminders,
            now = LocalDateTime.of(2026, 6, 18, 10, 0),
            delivered = emptySet(),
        )

        assertEquals(1, overdue.size)
        assertEquals(LocalDate.of(2026, 6, 20), overdue.single().chargeDate)
    }

    /** A warning about a renewal that has already happened is noise, not a reminder. */
    @Test
    fun `a charge in the past is not overdue`() {
        val reminders = listOf(
            PendingReminder(
                subscription = sub(),
                chargeDate = LocalDate.of(2026, 6, 5),
                remindAt = LocalDateTime.of(2026, 6, 2, 9, 0),
            ),
        )

        val overdue = overdueReminders(
            reminders,
            now = LocalDateTime.of(2026, 6, 10, 9, 0),
            delivered = emptySet(),
        )

        assertTrue(overdue.isEmpty())
    }

    @Test
    fun `a reminder already delivered is not sent twice`() {
        val reminders = pendingReminders(listOf(sub()), leadDays = 3, today = today)
        val key = reminders.single().key

        val overdue = overdueReminders(
            reminders,
            now = LocalDateTime.of(2026, 6, 18, 10, 0),
            delivered = setOf(key),
        )

        assertTrue(overdue.isEmpty())
    }

    /** Next month is a different charge, so last month's delivery must not suppress it. */
    @Test
    fun `the delivered key is per charge, not per subscription`() {
        val june = pendingReminders(listOf(sub()), leadDays = 3, today = today).single()
        val july = pendingReminders(
            listOf(sub()),
            leadDays = 3,
            today = LocalDate.of(2026, 6, 21),
        ).single()

        assertEquals("a@2026-06-20", june.key)
        assertEquals("a@2026-07-20", july.key)
    }

    @Test
    fun `lengthening the lead time surfaces a reminder that is already due`() {
        val reminders = pendingReminders(listOf(sub()), leadDays = 7, today = today)

        // Lead of 7 puts the reminder on the 13th; at the 15th it is already owed.
        assertEquals(LocalDateTime.of(2026, 6, 13, 9, 0), reminders.single().remindAt)
        assertEquals(
            1,
            overdueReminders(
                reminders,
                now = LocalDateTime.of(2026, 6, 15, 9, 0),
                delivered = emptySet(),
            ).size,
        )
    }

    @Test
    fun `delivered keys are pruned once their charge is behind`() {
        val kept = pruneDelivered(
            setOf("a@2026-06-20", "b@2026-05-01", "c@2026-06-09"),
            today = today,
        )

        assertEquals(setOf("a@2026-06-20", "c@2026-06-09"), kept)
    }

    @Test
    fun `a malformed delivered key is dropped rather than throwing`() {
        assertEquals(emptySet<String>(), pruneDelivered(setOf("nonsense"), today))
    }

    /** Annual renewals must land on the anchor day, not drift onto a clamped February date. */
    @Test
    fun `an annual charge reminds against the real calendar date`() {
        val plan = pendingReminders(
            listOf(
                sub(
                    anchor = LocalDate.of(2024, 2, 29),
                    cycle = BillingCycle.ANNUAL,
                ),
            ),
            leadDays = 3,
            today = LocalDate.of(2028, 1, 1),
        ).single()

        assertEquals(LocalDate.of(2028, 2, 29), plan.chargeDate)
        assertEquals(LocalDateTime.of(2028, 2, 26, 9, 0), plan.remindAt)
    }

    @Test
    fun `a charge due today still reminds`() {
        val plan = pendingReminders(
            listOf(sub(anchor = LocalDate.of(2026, 6, 10))),
            leadDays = 3,
            today = today,
        ).single()

        assertEquals(today, plan.chargeDate)
        assertEquals(0L, plan.daysUntil(today))
    }
}
