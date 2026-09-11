package com.toolbill.android.core.domain.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Live rates are the differentiator, and the thing that makes them dangerous is that they move.
 * A merge that drops a currency, or an inversion off by a factor, lands silently in the burn
 * total and in charge rows that are never recomputed.
 */
class FxRateTableTest {

    private val bundled = FxRates.BUNDLED

    private fun live(vararg pairs: Pair<String, String>, on: LocalDate = LocalDate.of(2026, 9, 10)) =
        FxRateTable(
            inrPer = pairs.associate { (code, rate) -> code to BigDecimal(rate) },
            capturedOn = on,
            liveCodes = pairs.map { it.first }.toSet(),
        )

    @Test
    fun `a merge keeps currencies the live source does not publish`() {
        // The ECB publishes thirty currencies and AED is not among them.
        val merged = bundled.mergedWith(live("INR" to "1.00", "USD" to "95.42"))

        assertTrue(merged.isSupported("AED"), "AED must survive a refresh that omits it")
        assertEquals(BigDecimal("23.71"), merged.inrPer["AED"])
        assertEquals(BigDecimal("95.42"), merged.inrPer["USD"])
    }

    @Test
    fun `a merge marks only the codes that were actually refreshed as live`() {
        val merged = bundled.mergedWith(live("INR" to "1.00", "USD" to "95.42"))

        assertTrue(merged.isLive("USD"))
        assertFalse(merged.isLive("AED"), "a bundled rate must not be reported as live")
        assertTrue(merged.isLive)
    }

    @Test
    fun `a merge takes the live publication date`() {
        val merged = bundled.mergedWith(live("INR" to "1.00", "USD" to "95.42"))
        assertEquals(LocalDate.of(2026, 9, 10), merged.capturedOn)
    }

    @Test
    fun `the bundled table is not live`() {
        assertFalse(bundled.isLive)
        assertFalse(bundled.isLive("USD"))
    }

    @Test
    fun `conversion follows the installed rate, not the bundled one`() {
        val merged = bundled.mergedWith(live("INR" to "1.00", "USD" to "95.42"))

        // 20.00 USD at the bundled 87.10 against the live 95.42.
        assertEquals(174_200L, bundled.convertMinor(2_000, "USD", "INR"))
        assertEquals(190_840L, merged.convertMinor(2_000, "USD", "INR"))
    }

    @Test
    fun `an uncaptured currency reports itself rather than guessing`() {
        assertNull(bundled.convertMinor(1_000, "JPY", "INR"))
        assertNull(bundled.rateFor("JPY", "INR"))
        assertFalse(bundled.isSupported("JPY"))
    }

    @Test
    fun `a zero-decimal currency is not inflated on the way through`() {
        val table = FxRateTable(
            inrPer = mapOf("INR" to BigDecimal.ONE, "JPY" to BigDecimal("0.58")),
            capturedOn = LocalDate.of(2026, 9, 10),
        )
        // 5000 yen, a zero-decimal currency, is 5000 major units -> 2900 rupees -> 290000 paise.
        assertEquals(290_000L, table.convertMinor(5_000, "JPY", "INR"))
    }

    @Test
    fun `the stored rate reproduces the converted figure`() {
        val merged = bundled.mergedWith(live("INR" to "1.00", "USD" to "95.42"))
        val rate = merged.rateFor("USD", "INR")!!

        assertEquals(BigDecimal("95.420000"), rate)
        assertEquals(
            merged.convertMinor(2_000, "USD", "INR"),
            rate.multiply(BigDecimal("20.00")).movePointRight(2).toLong(),
        )
    }

    @Test
    fun `a currency to itself rates at one and converts unchanged`() {
        assertEquals(0, bundled.rateFor("INR", "INR")!!.compareTo(BigDecimal.ONE))
        assertEquals(1_234L, bundled.convertMinor(1_234, "USD", "USD"))
    }

    @Test
    fun `every currency the add sheet offers can still be priced after a partial refresh`() {
        val merged = bundled.mergedWith(
            live("INR" to "1.00", "USD" to "95.42", "EUR" to "111.80"),
        )
        listOf("INR", "USD", "EUR", "GBP", "CAD", "AUD", "SGD", "AED").forEach { code ->
            assertTrue(merged.isSupported(code), "$code lost its rate in the merge")
        }
    }
}
