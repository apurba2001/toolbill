package com.toolbill.android.core.billing

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate has to work in both directions before billing exists, because the day it is switched
 * over is the worst possible day to discover that the free branch was never exercised.
 */
class EntitlementTest {

    @Test
    fun `a free entitlement allows no pro feature`() {
        ProFeature.entries.forEach { feature ->
            assertFalse(Entitlement.Free.allows(feature), "${feature.label} should be gated")
        }
    }

    @Test
    fun `early access allows every pro feature`() {
        ProFeature.entries.forEach { feature ->
            assertTrue(Entitlement.EarlyAccess.allows(feature), "${feature.label} should be open")
        }
    }

    /**
     * The reason is what the paywall reads to decide whether to offer a checkout, so it has to
     * distinguish "you bought this" from "nothing is on sale yet".
     */
    @Test
    fun `early access is pro but not purchased`() {
        assertTrue(Entitlement.EarlyAccess.isPro)
        assertEquals(EntitlementReason.EARLY_ACCESS, Entitlement.EarlyAccess.reason)
        assertEquals(EntitlementReason.NONE, Entitlement.Free.reason)
    }

    @Test
    fun `a purchase is pro for the right reason`() {
        val purchased = Entitlement(Tier.PRO, EntitlementReason.PURCHASED)
        assertTrue(purchased.isPro)
        assertTrue(ProFeature.entries.all { purchased.allows(it) })
    }

    /** The widget is free by decision, so it is not in the list at all. */
    @Test
    fun `the gated features are exactly the four the design names`() {
        assertEquals(
            listOf(
                "Renewal notifications",
                "CSV export",
                "Insights and FX history",
                "Google Drive backup",
            ),
            ProFeature.entries.map { it.label },
        )
    }

    @Test
    fun `the early access source reports early access`() = runTest {
        val source = EarlyAccessEntitlements()

        assertEquals(Entitlement.EarlyAccess, source.entitlement.value)
        assertEquals(Entitlement.EarlyAccess, source.restore())
    }
}
