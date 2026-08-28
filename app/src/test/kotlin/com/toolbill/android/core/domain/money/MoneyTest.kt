package com.toolbill.android.core.domain.money

import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle
import java.util.Currency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Rupees to paise. */
private fun inr(rupees: String): Long {
    val (whole, paise) = if ("." in rupees) rupees.split(".") else listOf(rupees, "00")
    return whole.replace(",", "").toLong() * 100 + paise.padEnd(2, '0').toLong()
}

class NormalizationTest {

    @Nested
    @DisplayName("Every annual figure published in the design document")
    inner class DesignDocumentFigures {

        @Test
        fun `Adobe Creative Cloud annual 4,662 is 388-50 a month`() {
            assertEquals(inr("388.50"), normalizedMonthlyMinor(inr("4662.00"), BillingCycle.ANNUAL))
        }

        @Test
        fun `Namecheap annual 3,179 rounds up to 264-92 a month`() {
            // 3179 / 12 = 264.9166. Truncating would give 264.91 and the list total would not close.
            assertEquals(inr("264.92"), normalizedMonthlyMinor(inr("3179.00"), BillingCycle.ANNUAL))
        }

        @Test
        fun `Fathom Analytics annual 1,218 is 101-50 a month`() {
            assertEquals(inr("101.50"), normalizedMonthlyMinor(inr("1218.00"), BillingCycle.ANNUAL))
        }

        @Test
        fun `Grammarly annual 1,000 is 83-33 a month`() {
            assertEquals(inr("83.33"), normalizedMonthlyMinor(inr("1000.00"), BillingCycle.ANNUAL))
        }

        @Test
        fun `a monthly plan normalizes to itself`() {
            assertEquals(inr("1742.00"), normalizedMonthlyMinor(inr("1742.00"), BillingCycle.MONTHLY))
        }

        @Test
        fun `the eight-item list totals 11,987-42 across its five active rows`() {
            val active = listOf(
                inr("7412.00") to BillingCycle.MONTHLY,   // AWS
                inr("2180.00") to BillingCycle.MONTHLY,   // Heroku Dynos
                inr("1742.00") to BillingCycle.MONTHLY,   // Vercel Pro
                inr("4662.00") to BillingCycle.ANNUAL,    // Adobe Creative Cloud
                inr("3179.00") to BillingCycle.ANNUAL,    // Namecheap
            )
            val total = active.sumOf { (amount, cycle) -> normalizedMonthlyMinor(amount, cycle) }
            assertEquals(inr("11987.42"), total)
        }

        @Test
        fun `the home hero annualizes to 5,30,046`() {
            assertEquals(
                inr("530046.00"),
                annualizedMinor(inr("44170.50"), BillingCycle.MONTHLY),
            )
        }
    }

    @Nested
    @DisplayName("Day and week cycles use the average-month factor")
    inner class AverageMonthCycles {

        @Test
        fun `a daily charge uses 30-4375 days per month`() {
            // 365.25 / 12 = 30.4375. The plan's 3653/1200 constant is a factor of ten out and
            // would report 304.42 here — a daily subscription understated by 90%.
            assertEquals(inr("3043.75"), normalizedMonthlyMinor(inr("100.00"), BillingCycle(CycleUnit.DAY, 1)))
        }

        @Test
        fun `every 30 days is slightly more than a month`() {
            val monthly = normalizedMonthlyMinor(inr("3000.00"), BillingCycle.MONTHLY)
            val every30Days = normalizedMonthlyMinor(inr("3000.00"), BillingCycle(CycleUnit.DAY, 30))
            assertTrue(every30Days > monthly) {
                "30-day cycles bill more often than monthly ones: $every30Days vs $monthly"
            }
        }

        @Test
        fun `a weekly charge uses 4-3483 weeks per month`() {
            assertEquals(inr("434.83"), normalizedMonthlyMinor(inr("100.00"), BillingCycle.WEEKLY))
        }

        @Test
        fun `quarterly divides by three`() {
            assertEquals(inr("500.00"), normalizedMonthlyMinor(inr("1500.00"), BillingCycle.QUARTERLY))
        }
    }

    @Nested
    inner class Rounding {

        @Test
        fun `halves round away from zero`() {
            assertEquals(3L, divideRoundingHalfUp(5L, 2L))
            assertEquals(-3L, divideRoundingHalfUp(-5L, 2L))
            assertEquals(2L, divideRoundingHalfUp(4L, 2L))
            assertEquals(0L, divideRoundingHalfUp(1L, 3L))
            assertEquals(1L, divideRoundingHalfUp(2L, 3L))
        }
    }

    @Nested
    inner class ChargeLineVisibility {

        @Test
        fun `only plain monthly rows hide the real charge line`() {
            assertFalse(BillingCycle.MONTHLY.chargeDiffersFromMonthly())
            assertTrue(BillingCycle.ANNUAL.chargeDiffersFromMonthly())
            assertTrue(BillingCycle.QUARTERLY.chargeDiffersFromMonthly())
            assertTrue(BillingCycle.WEEKLY.chargeDiffersFromMonthly())
            assertTrue(BillingCycle(CycleUnit.MONTH, 2).chargeDiffersFromMonthly())
        }
    }
}

class MoneyFormatTest {

    @Nested
    @DisplayName("Indian digit grouping")
    inner class IndianGrouping {

        @Test
        fun `the annualized figure groups Indian-style`() {
            val formatted = MoneyFormat.format(inr("530046.00"), "INR")
            assertEquals("5,30,046", formatted.integer)
        }

        @Test
        fun `the hero figure groups Indian-style and splits the paise`() {
            val formatted = MoneyFormat.format(inr("44170.50"), "INR")
            assertEquals("44,170", formatted.integer)
            assertEquals("50", formatted.fraction)
            assertEquals(".", formatted.decimalSeparator)
        }
    }

    @Nested
    @DisplayName("Currency notation · one rule")
    inner class CurrencyNotation {

        @Test
        fun `the hero and detail header carry the ISO code`() {
            assertEquals("INR 44,170.50", MoneyFormat.format(inr("44170.50"), "INR").withCode())
        }

        @Test
        fun `rows and captions carry the symbol`() {
            assertEquals("₹1,742.00", MoneyFormat.format(inr("1742.00"), "INR").withSymbol())
        }

        @Test
        fun `a code and a symbol never appear in the same figure`() {
            val formatted = MoneyFormat.format(inr("1742.00"), "INR")
            assertFalse(formatted.withCode().contains("₹")) { formatted.withCode() }
            assertFalse(formatted.withSymbol().contains("INR")) { formatted.withSymbol() }
        }

        @Test
        fun `signed deltas use a true minus sign`() {
            assertEquals("+₹696", MoneyFormat.signedSymbol(inr("696.00"), "INR"))
            assertEquals("−₹1,254", MoneyFormat.signedSymbol(-inr("1254.00"), "INR"))
        }
    }

    @Nested
    @DisplayName("Hero autosize · the three worked examples")
    inner class HeroAutosize {

        @Test
        fun `INR 44,170-50 is seven digits and stays at 45sp`() {
            val formatted = MoneyFormat.format(inr("44170.50"), "INR")
            assertEquals(7, formatted.significantDigits)
            assertEquals(HeroStep.LARGE, formatted.heroStep)
            assertEquals(45, formatted.heroStep.sp)
        }

        @Test
        fun `VND 132 million is nine digits and steps to 36sp`() {
            val minorPerMajor = tenPow(Currency.getInstance("VND").defaultFractionDigits)
            val formatted = MoneyFormat.format(132_480_000L * minorPerMajor, "VND")
            assertEquals("132.480.000", formatted.integer)
            assertEquals(9, formatted.significantDigits)
            assertEquals(HeroStep.MEDIUM, formatted.heroStep)
            assertEquals(36, formatted.heroStep.sp)
        }

        @Test
        fun `IDR 1-28 billion steps to 32sp and drops the minor unit`() {
            val minorPerMajor = tenPow(Currency.getInstance("IDR").defaultFractionDigits)
            val amount = 1_284_600_000L * minorPerMajor
            val formatted = MoneyFormat.format(amount, "IDR")
            assertEquals("1.284.600.000", formatted.integer)
            assertEquals(HeroStep.SMALL, formatted.heroStep)
            assertEquals(32, formatted.heroStep.sp)
            assertTrue(formatted.heroStep.dropsMinorUnits)

            val hero = MoneyFormat.hero(amount, "IDR")
            assertEquals(null, hero.fraction)
            assertEquals("IDR 1.284.600.000", hero.withCode())
        }

        @Test
        fun `dropping the minor unit does not change the step that dropped it`() {
            val minorPerMajor = tenPow(Currency.getInstance("IDR").defaultFractionDigits)
            val hero = MoneyFormat.hero(1_284_600_000L * minorPerMajor, "IDR")
            assertEquals(HeroStep.SMALL, hero.heroStep)
        }

        @Test
        fun `the step boundary sits between seven and eight digits`() {
            assertEquals(HeroStep.LARGE, MoneyFormat.format(inr("99999.99"), "INR").heroStep)
            assertEquals(HeroStep.MEDIUM, MoneyFormat.format(inr("100000.00"), "INR").heroStep)
        }
    }

    @Nested
    inner class ForeignCurrencies {

        @Test
        fun `USD renders with a dollar sign and Western grouping`() {
            assertEquals("$20.00", MoneyFormat.format(2000L, "USD").withSymbol())
        }

        @Test
        fun `a zero amount renders cleanly for the empty state`() {
            assertEquals("0.00", MoneyFormat.format(0L, "INR").plain)
        }
    }
}

private fun tenPow(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10 }
    return result
}
