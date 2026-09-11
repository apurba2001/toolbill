package com.toolbill.android.core.domain.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FxRatesTest {

    @Test
    fun `same currency is returned untouched`() {
        assertEquals(741_200L, FxRates.convertMinor(741_200, "INR", "INR"))
    }

    @Test
    fun `casing does not change the answer`() {
        assertEquals(
            FxRates.convertMinor(2_000, "USD", "INR"),
            FxRates.convertMinor(2_000, "usd", "inr"),
        )
    }

    /**
     * The captured USD rate is the one the design's worked example was computed at, so this
     * pins the table to figures that appear on screen: Vercel bills 20.00 USD and shows as
     * ₹1,742.00.
     */
    @Test
    fun `usd converts at the rate the sample figures were drawn with`() {
        assertEquals(174_200L, FxRates.convertMinor(2_000, "USD", "INR"))
    }

    @Test
    fun `converting there and back returns roughly the original`() {
        val inr = FxRates.convertMinor(8_510, "USD", "INR")!!
        val backToUsd = FxRates.convertMinor(inr, "INR", "USD")!!
        assertTrue(kotlin.math.abs(backToUsd - 8_510) <= 1, "round trip drifted to $backToUsd")
    }

    @Test
    fun `an uncaptured currency reports itself rather than guessing`() {
        assertNull(FxRates.convertMinor(1_000, "JPY", "INR"))
        assertNull(FxRates.convertMinor(1_000, "INR", "JPY"))
        assertTrue(!FxRates.isSupported("JPY"))
    }

    @Test
    fun `every currency the add sheet offers can be priced`() {
        listOf("INR", "USD", "EUR", "GBP", "CAD", "AUD", "SGD", "AED").forEach { code ->
            assertTrue(FxRates.isSupported(code), "$code has no captured rate")
        }
    }

    @Test
    fun `the stored rate reproduces the converted figure`() {
        val rate = FxRates.rateFor("USD", "INR")!!
        assertEquals(java.math.BigDecimal("87.100000"), rate)
        // 20.00 USD at 87.10 is what the charge row records as its home figure.
        val home = rate.multiply(java.math.BigDecimal("20.00"))
        assertEquals(174_200L, home.movePointRight(2).toLong())
    }

    @Test
    fun `a currency to itself rates at one`() {
        assertEquals(0, FxRates.rateFor("INR", "INR")!!.compareTo(java.math.BigDecimal.ONE))
    }

    @Test
    fun `an uncaptured currency has no rate rather than a default of one`() {
        assertNull(FxRates.rateFor("JPY", "INR"))
        assertNull(FxRates.rateFor("INR", "JPY"))
    }
}
