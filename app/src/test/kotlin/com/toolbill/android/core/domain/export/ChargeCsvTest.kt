package com.toolbill.android.core.domain.export

import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * This file is handed to an accountant. A column that shifts, a date that reads as the wrong
 * day, or a rate nobody can source is a mistake that surfaces months later in a filing.
 */
class ChargeCsvTest {

    private fun sub(
        id: String = "a",
        name: String = "Claude Pro",
        business: Boolean = true,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        category: Category = Category.AI_TOOLS,
        otherLabel: String? = null,
    ) = Subscription(
        id = id,
        name = name,
        amountMinor = 2_000,
        currency = "USD",
        cycle = BillingCycle(CycleUnit.MONTH, 1),
        anchorDate = LocalDate.of(2026, 8, 27),
        category = category,
        otherLabel = otherLabel,
        isBusiness = business,
        status = status,
    )

    private fun charge(
        id: String = "c1",
        subscriptionId: String = "a",
        date: LocalDate = LocalDate.of(2026, 8, 27),
        amountMinor: Long = 2_000,
        currency: String = "USD",
        homeAmountMinor: Long = 174_200,
        homeCurrency: String = "INR",
        fxRate: BigDecimal? = BigDecimal("87.100000"),
        status: ChargeStatus = ChargeStatus.PAID,
    ) = Charge(
        id = id,
        subscriptionId = subscriptionId,
        dueDate = date,
        amountMinor = amountMinor,
        currency = currency,
        homeAmountMinor = homeAmountMinor,
        homeCurrency = homeCurrency,
        fxRate = fxRate,
        status = status,
    )

    @Test
    fun `a paid charge writes every column`() {
        assertEquals(
            "2026-08-27,Claude Pro,AI,,biz,20.00,USD,87.1,1742.00,paid",
            csvLine(charge(), sub()),
        )
    }

    /** `26-08-27` is DD-MM-YY to half the world. A filing cannot afford the ambiguity. */
    @Test
    fun `dates are ISO 8601`() {
        assertTrue(csvLine(charge(), sub()).startsWith("2026-08-27,"))
    }

    @Test
    fun `a skipped charge exports as a zero row rather than vanishing`() {
        val line = csvLine(
            charge(homeAmountMinor = 0, fxRate = null, status = ChargeStatus.SKIPPED),
            sub(),
        )
        assertEquals("2026-08-27,Claude Pro,AI,,biz,20.00,USD,,0.00,skipped", line)
    }

    @Test
    fun `a charge with no captured rate leaves the rate and home columns empty`() {
        val line = csvLine(
            charge(fxRate = null, homeAmountMinor = 0, homeCurrency = ""),
            sub(),
        )
        assertEquals("2026-08-27,Claude Pro,AI,,biz,20.00,USD,,,paid", line)
    }

    @Test
    fun `the custom label rides in its own column, not over the category`() {
        val line = csvLine(
            charge(),
            sub(category = Category.OTHER, otherLabel = "Shopify apps"),
        )
        assertTrue(line.contains(",Other,Shopify apps,biz,"), line)
    }

    /**
     * A comma in a service name would otherwise shift every column after it by one, silently,
     * in a file nobody re-reads before filing it.
     */
    @Test
    fun `a name containing a comma is quoted`() {
        val line = csvLine(charge(), sub(name = "Adobe, Inc."))
        assertEquals("2026-08-27,\"Adobe, Inc.\",AI,,biz,20.00,USD,87.1,1742.00,paid", line)
    }

    @Test
    fun `a name containing a quote doubles it`() {
        val line = csvLine(charge(), sub(name = "The \"Pro\" plan"))
        assertTrue(line.contains("\"The \"\"Pro\"\" plan\""), line)
    }

    @Test
    fun `a zero-decimal currency is not inflated`() {
        val line = csvLine(charge(amountMinor = 5_000, currency = "JPY"), sub())
        assertTrue(line.contains(",5000,JPY,"), line)
    }

    @Test
    fun `the file starts with the fixed header`() {
        val csv = toCsv(listOf(charge() to sub()))
        assertEquals(CSV_HEADER, csv.lineSequence().first())
    }

    // --- period ---

    @Test
    fun `the financial year runs April to March and follows the date it is run on`() {
        val range = ExportPeriod.FINANCIAL_YEAR.defaultRange(LocalDate.of(2026, 8, 27))
        assertEquals(LocalDate.of(2026, 4, 1), range.start)
        assertEquals(LocalDate.of(2027, 3, 31), range.endInclusive)
    }

    @Test
    fun `a date in January belongs to the financial year that began the previous April`() {
        val range = ExportPeriod.FINANCIAL_YEAR.defaultRange(LocalDate.of(2027, 1, 15))
        assertEquals(LocalDate.of(2026, 4, 1), range.start)
        assertEquals(LocalDate.of(2027, 3, 31), range.endInclusive)
    }

    @Test
    fun `the quarter is the calendar quarter containing the date`() {
        val range = ExportPeriod.THIS_QUARTER.defaultRange(LocalDate.of(2026, 8, 27))
        assertEquals(LocalDate.of(2026, 7, 1), range.start)
        assertEquals(LocalDate.of(2026, 9, 30), range.endInclusive)
    }

    @Test
    fun `the financial year label names the years it spans`() {
        val range = ExportPeriod.FINANCIAL_YEAR.defaultRange(LocalDate.of(2026, 8, 27))
        assertEquals("FY 2026-27", ExportPeriod.FINANCIAL_YEAR.label(range))
    }

    // --- filtering ---

    private val range = LocalDate.of(2026, 4, 1)..LocalDate.of(2027, 3, 31)

    @Test
    fun `a charge outside the period is excluded`() {
        val rows = chargesForExport(
            charges = listOf(charge(date = LocalDate.of(2026, 3, 31))),
            subscriptions = listOf(sub()),
            range = range,
            filters = ExportFilters(),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `personal charges are excluded unless asked for`() {
        val charges = listOf(charge(), charge(id = "c2", subscriptionId = "b"))
        val subs = listOf(sub(), sub(id = "b", name = "iCloud+", business = false))

        val businessOnly = chargesForExport(charges, subs, range, ExportFilters(personal = false))
        assertEquals(listOf("a"), businessOnly.map { it.second.id })

        val both = chargesForExport(charges, subs, range, ExportFilters(personal = true))
        assertEquals(2, both.size)
    }

    /** A cancelled business tool is still business spend that was actually paid for. */
    @Test
    fun `cancelled charges are included only when asked for`() {
        val charges = listOf(charge())
        val subs = listOf(sub(status = SubscriptionStatus.CANCELLED))

        assertTrue(chargesForExport(charges, subs, range, ExportFilters()).isEmpty())
        assertEquals(
            1,
            chargesForExport(charges, subs, range, ExportFilters(cancelled = true)).size,
        )
    }

    @Test
    fun `a charge whose subscription is gone is dropped rather than exported nameless`() {
        val rows = chargesForExport(
            charges = listOf(charge(subscriptionId = "missing")),
            subscriptions = listOf(sub()),
            range = range,
            filters = ExportFilters(),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `rows come out newest first`() {
        val charges = listOf(
            charge(id = "old", date = LocalDate.of(2026, 5, 1)),
            charge(id = "new", date = LocalDate.of(2026, 9, 1)),
        )
        val rows = chargesForExport(charges, listOf(sub()), range, ExportFilters())
        assertEquals(listOf("new", "old"), rows.map { it.first.id })
    }

    // --- summary ---

    @Test
    fun `the total counts only charges that cost something`() {
        val charges = listOf(
            charge(id = "paid"),
            charge(id = "skipped", homeAmountMinor = 0, status = ChargeStatus.SKIPPED),
            charge(id = "uncaptured", homeAmountMinor = 0, homeCurrency = "", fxRate = null),
        )
        val summary = summarize(
            chargesForExport(charges, listOf(sub()), range, ExportFilters()),
            "INR",
        )

        assertEquals(3, summary.chargeCount)
        assertEquals(1, summary.serviceCount)
        assertEquals(174_200L, summary.totalHomeMinor)
    }
}
