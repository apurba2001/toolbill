package com.toolbill.android.core.domain.subscription

import com.toolbill.android.feature.subscriptions.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The invariants the screens state out loud.
 *
 * Insights prints "sums to X — the same figure as the Home hero" and a "96 / 4" split. Those
 * are claims about arithmetic, and a claim printed on a money screen has to be enforced
 * somewhere or it becomes a lie the first time the derivation changes.
 */
class PortfolioTest {

    private val all = SampleData.all
    private val today = SampleData.today

    @Nested
    @DisplayName("The breakdowns close against the headline")
    inner class Invariants {

        @Test
        fun `category spend sums to the monthly burn`() {
            assertEquals(all.monthlyBurnMinor(), all.categorySpend().sumOf { it.amountMinor })
        }

        @Test
        fun `business and personal sum to the monthly burn`() {
            assertEquals(all.monthlyBurnMinor(), all.businessBurnMinor() + all.personalBurnMinor())
        }

        @Test
        fun `the business share is a sane percentage`() {
            val business = all.businessSharePercent()
            assertTrue(business in 0..100, "business share was $business")
        }

        @Test
        fun `annualized is twelve months of the burn`() {
            assertEquals(all.monthlyBurnMinor() * 12, all.annualizedBurnMinor())
        }

        @Test
        fun `only active subscriptions reach the burn`() {
            val counted = all.active.map { it.subscription.status }.distinct()
            assertEquals(listOf(SubscriptionStatus.ACTIVE), counted)
        }

        @Test
        fun `an empty portfolio reports zero rather than dividing by it`() {
            val none = emptyList<PricedSubscription>()
            assertEquals(0L, none.monthlyBurnMinor())
            assertEquals(0, none.businessSharePercent())
            assertEquals(0, none.activeCount())
            assertTrue(none.categorySpend().isEmpty())
        }
    }

    @Nested
    @DisplayName("The week's schedule")
    inner class Schedule {

        @Test
        fun `every charge falls inside the window`() {
            val to = today.plusDays(6)
            val charges = all.upcomingCharges(today, to)
            assertTrue(charges.isNotEmpty(), "the fixture should have charges this week")
            assertTrue(
                charges.all { it.date >= today && it.date <= to },
                "a charge fell outside $today..$to",
            )
        }

        @Test
        fun `charges come out in date order`() {
            val dates = all.upcomingCharges(today, today.plusDays(30)).map { it.date }
            assertEquals(dates.sorted(), dates)
        }

        @Test
        fun `paused and cancelled plans do not appear in the schedule`() {
            val ids = all.upcomingCharges(today, today.plusDays(365))
                .map { it.priced.subscription.id }
                .toSet()
            assertTrue("shopify" !in ids, "a paused plan was scheduled")
            assertTrue("notion" !in ids, "a cancelled plan was scheduled")
        }
    }

    @Nested
    @DisplayName("Needs attention")
    inner class Attention {

        @Test
        fun `overdue outranks a converting trial`() {
            val kinds = all.needsAttention(today).map { it.kind }
            val firstTrial = kinds.indexOf(AttentionKind.TRIAL_ENDING)
            val lastOverdue = kinds.lastIndexOf(AttentionKind.OVERDUE)
            if (firstTrial >= 0 && lastOverdue >= 0) {
                assertTrue(lastOverdue < firstTrial, "a trial sorted above an overdue charge")
            }
        }

        @Test
        fun `a trial that already ended is not still pending`() {
            val wellPast = LocalDate.of(2030, 1, 1)
            assertTrue(
                all.needsAttention(wellPast).none { it.kind == AttentionKind.TRIAL_ENDING },
            )
        }
    }

    @Nested
    @DisplayName("Foreign-billed burn")
    inner class Foreign {

        @Test
        fun `excludes plans billed in the home currency`() {
            val foreign = all.foreignBilledBurnMinor(SampleData.HOME_CURRENCY)
            assertTrue(foreign > 0, "the fixture bills in USD")
            assertTrue(foreign < all.monthlyBurnMinor(), "the fixture also bills in INR")
        }

        @Test
        fun `is everything when nothing is billed at home`() {
            assertEquals(all.monthlyBurnMinor(), all.foreignBilledBurnMinor("JPY"))
        }
    }
}
