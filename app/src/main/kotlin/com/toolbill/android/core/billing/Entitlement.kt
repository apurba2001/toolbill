package com.toolbill.android.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user is entitled to. */
enum class Tier { FREE, PRO }

/**
 * The four features the design puts behind Pro.
 *
 * Named rather than checked ad hoc, so every gate in the app reads the same list and turning
 * billing on is a change to one source rather than a hunt through the screens.
 *
 * The widget is deliberately absent. The plan's week-12 decision keeps it free: it is a
 * retention driver and a reason to open the app, and gating it costs more than it earns.
 */
enum class ProFeature(val label: String) {
    RENEWAL_REMINDERS("Renewal notifications"),
    CSV_EXPORT("CSV export"),
    INSIGHTS("Insights and FX history"),
    DRIVE_BACKUP("Google Drive backup"),
}

/**
 * Why the current tier is what it is.
 *
 * Carried alongside the tier because the paywall has to say something true about it, and
 * "you have Pro" is a different sentence from "Pro is unlocked while this is in early access".
 */
enum class EntitlementReason {
    /** No purchase, no grant. */
    NONE,

    /**
     * Everything is unlocked because there is nothing to buy yet.
     *
     * Play Billing needs a Play Console with configured products and RevenueCat needs an
     * account, neither of which exists for this build. Rather than gate four working features
     * behind a checkout that cannot take money, the gates run and resolve to granted — so the
     * call sites are real and exercised, and switching the source over is the only change
     * billing will need.
     */
    EARLY_ACCESS,

    /** A verified purchase. Not reachable until billing is wired. */
    PURCHASED,
}

data class Entitlement(
    val tier: Tier,
    val reason: EntitlementReason,
) {
    val isPro: Boolean get() = tier == Tier.PRO

    /** Whether [feature] is available. One question, asked the same way everywhere. */
    fun allows(feature: ProFeature): Boolean = isPro

    companion object {
        val EarlyAccess = Entitlement(Tier.PRO, EntitlementReason.EARLY_ACCESS)
        val Free = Entitlement(Tier.FREE, EntitlementReason.NONE)
    }
}

/**
 * Where entitlement comes from.
 *
 * An interface with one implementation today, because the point of it is the seam: a
 * RevenueCat-backed source replaces [EarlyAccessEntitlements] without any screen changing.
 */
interface EntitlementSource {
    val entitlement: StateFlow<Entitlement>

    /** Re-checks with the store. Returns what it found. */
    suspend fun restore(): Entitlement
}

/**
 * The source in force before billing exists.
 *
 * Grants Pro to everyone and says why. This is not a stub standing in for a purchase — nothing
 * here claims a purchase happened, and the paywall reads [EntitlementReason.EARLY_ACCESS] and
 * tells the user plainly that there is nothing to buy yet.
 */
class EarlyAccessEntitlements : EntitlementSource {
    private val _entitlement = MutableStateFlow(Entitlement.EarlyAccess)
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    override suspend fun restore(): Entitlement = _entitlement.value
}
