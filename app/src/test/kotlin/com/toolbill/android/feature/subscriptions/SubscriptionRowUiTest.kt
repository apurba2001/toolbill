package com.toolbill.android.feature.subscriptions

import com.toolbill.android.core.design.component.RowState
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.PricedSubscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun PricedSubscription.row() = subscription.toRowUi(
    today = SampleData.today,
    homeCurrency = SampleData.HOME_CURRENCY,
    homeAmountMinor = homeAmountMinor,
    overdueSince = overdueSince,
)

/**
 * Reproduces the eight-row list from the design document, row by row.
 *
 * If a support line or an amount here changes, the app has stopped matching the design that was
 * signed off — which is exactly what these assertions exist to catch.
 */
class SubscriptionRowUiTest {

    @Nested
    @DisplayName("Support lines match the design's copy")
    inner class SupportLines {

        @Test
        fun `an active monthly row names the cycle then the countdown`() {
            val row = SampleData.aws.row()
            assertEquals(RowState.ACTIVE, row.state)
            assertEquals("monthly · in 9 days", row.supportLine)
            assertNull(row.badgeLabel)
        }

        @Test
        fun `an annual row 41 days out`() {
            val row = SampleData.adobe.row()
            assertEquals("annual · in 41 days", row.supportLine)
        }

        @Test
        fun `a charge tomorrow gets a due badge`() {
            val row = SampleData.vercel.row()
            assertEquals(RowState.DUE_SOON, row.state)
            assertEquals("DUE 1d", row.badgeLabel)
            assertEquals("monthly", row.supportLine)
        }

        @Test
        fun `an annual charge five days out gets a due badge`() {
            val row = SampleData.namecheap.row()
            assertEquals("DUE 5d", row.badgeLabel)
            assertEquals("annual", row.supportLine)
        }

        @Test
        fun `overdue counts days late, not days until`() {
            val row = SampleData.heroku.row()
            assertEquals(RowState.OVERDUE, row.state)
            assertEquals("OVERDUE 2d", row.badgeLabel)
            assertEquals("Overdue by 2 days", row.badgeSpoken)
        }

        @Test
        fun `a trial states what it will add and when`() {
            val row = SampleData.linear.row()
            assertEquals(RowState.TRIAL, row.state)
            assertEquals("TRIAL 6d", row.badgeLabel)
            assertEquals("then ₹696/mo from 30 Aug", row.supportLine)
        }

        @Test
        fun `a paused row shows its resume date`() {
            val row = SampleData.shopify.row()
            assertEquals(RowState.PAUSED, row.state)
            assertEquals("PAUSED", row.badgeLabel)
            assertEquals("resumes 1 Oct", row.supportLine)
        }

        @Test
        fun `a cancelled row says it is kept for records`() {
            val row = SampleData.notion.row()
            assertEquals(RowState.CANCELLED, row.state)
            assertEquals("cancelled 12 Jul · kept for records", row.supportLine)
        }
    }

    @Nested
    @DisplayName("The second figure appears only where it changes the ranking")
    inner class SecondaryLines {

        @Test
        fun `a non-monthly foreign row carries both the real charge and the original`() {
            val row = SampleData.adobe.row()
            assertEquals(38_850L, row.normalizedMonthlyMinor)
            assertEquals("/mo", row.amountSuffix)
            assertEquals("₹4,662.00 · USD 54.99", row.secondaryLine)
        }

        @Test
        fun `a monthly foreign row carries only the original currency`() {
            val row = SampleData.aws.row()
            assertNull(row.amountSuffix)
            assertEquals("USD 85.10", row.secondaryLine)
        }

        @Test
        fun `a monthly home-currency row carries no second figure at all`() {
            val row = SampleData.googleWorkspace.row()
            assertNull(row.amountSuffix)
            assertNull(row.secondaryLine)
        }

        @Test
        fun `Namecheap normalizes to the design's 264-92`() {
            val row = SampleData.namecheap.row()
            assertEquals(26_492L, row.normalizedMonthlyMinor)
            assertEquals("/mo", row.amountSuffix)
        }
    }

    @Nested
    @DisplayName("Accessibility strings say what the abbreviations mean")
    inner class Semantics {

        @Test
        fun `the amount reads its monthly figure and its real billing`() {
            val spoken = SampleData.adobe.row().spokenDescription
            assertTrue(spoken.contains("₹388.50 per month")) { spoken }
            assertTrue(spoken.contains("billed annually as ₹4,662.00")) { spoken }
            assertTrue(spoken.endsWith("Business expense")) { spoken }
        }

        @Test
        fun `personal rows say personal`() {
            assertTrue(SampleData.spotify.row().spokenDescription.endsWith("Personal"))
        }
    }

    @Nested
    @DisplayName("Home's next-7-days list carries actual charges, not normalized shares")
    inner class UpcomingRows {

        @Test
        fun `the eight charges sum to the design's week total`() {
            val total = SampleData.nextSevenDays.sumOf { (priced, _) -> priced.homeAmountMinor }
            assertEquals(1_444_600L, total)
        }

        @Test
        fun `an annual renewal shows its real charge with the monthly share in support`() {
            val (priced, date) = SampleData.nextSevenDays.first {
                it.first.subscription.id == "namecheap"
            }
            val row = priced.subscription.toUpcomingRowUi(
                today = SampleData.today,
                homeCurrency = SampleData.HOME_CURRENCY,
                homeAmountMinor = priced.homeAmountMinor,
                chargeDate = date,
            )
            assertEquals(317_900L, row.normalizedMonthlyMinor)
            assertEquals("in 5 days · annual charge · ₹264.92/mo", row.supportLine)
            assertEquals("USD 36.51", row.secondaryLine)
        }

        @Test
        fun `a monthly row states the countdown before the cycle`() {
            val (priced, date) = SampleData.nextSevenDays.first()
            val row = priced.subscription.toUpcomingRowUi(
                today = SampleData.today,
                homeCurrency = SampleData.HOME_CURRENCY,
                homeAmountMinor = priced.homeAmountMinor,
                chargeDate = date,
            )
            assertEquals("in 1 day · monthly", row.supportLine)
        }
    }

    @Nested
    @DisplayName("The eight-item list total")
    inner class ListTotal {

        @Test
        fun `five active rows total 11,987-42 a month`() {
            val total = SampleData.eightItemList
                .filter { it.subscription.status == SubscriptionStatus.ACTIVE }
                .sumOf { normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle) }
            assertEquals(1_198_742L, total)
        }
    }
}
