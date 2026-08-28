package com.toolbill.android.core.domain.subscription

import com.toolbill.android.core.domain.date.CycleUnit
import java.time.LocalDate

/**
 * The eleven fixed categories, plus [OTHER] which carries a user-supplied label.
 *
 * Fixed so the CSV export columns stay stable year to year — an accountant reconciling
 * FY2026-27 against FY2027-28 must not find the category set has moved under them.
 *
 * [csvCode] is the short token written to the export's `cat` column.
 */
enum class Category(val displayName: String, val csvCode: String) {
    AI_TOOLS("AI tools", "AI"),
    HOSTING_INFRA("Hosting & infra", "Host"),
    ANALYTICS("Analytics", "Anly"),
    EMAIL_MARKETING("Email & marketing", "Email"),
    PRODUCTIVITY("Productivity", "Prod"),
    DESIGN("Design", "Dsgn"),
    STORAGE("Storage", "Stor"),
    DOMAINS("Domains", "Dom"),
    DEVELOPMENT("Development", "Dev"),
    SECURITY("Security", "Sec"),
    ENTERTAINMENT("Entertainment", "Ent"),
    OTHER("Other", "Other"),
    ;

    val isFixed: Boolean get() = this != OTHER

    companion object {
        /** The eleven fixed categories, excluding [OTHER]. */
        val fixed: List<Category> = entries.filter { it.isFixed }
    }
}

/**
 * Lifecycle state of a subscription.
 *
 * Only [ACTIVE] contributes to the burn total. [TRIAL] is surfaced separately as an upcoming
 * addition ("+₹696/mo from 30 Aug"), [PAUSED] and [CANCELLED] are listed but never counted.
 */
enum class SubscriptionStatus {
    ACTIVE,
    TRIAL,
    PAUSED,
    CANCELLED,
    ;

    /** Whether this status contributes to the normalized monthly burn. */
    val countsTowardBurn: Boolean get() = this == ACTIVE

    /**
     * Where this status sorts in a list, before the chosen sort key is applied.
     *
     * Live subscriptions rank together whatever the sort; paused and cancelled fall to the
     * bottom. A ₹2,499 paused plan ranking second by cost puts a row the total explicitly
     * excludes above five rows it includes.
     */
    val listRank: Int
        get() = when (this) {
            ACTIVE, TRIAL -> 0
            PAUSED -> 1
            CANCELLED -> 2
        }
}

/**
 * A billing cycle: a unit and a count, never a day count.
 *
 * "Monthly on the 31st" has to land on real calendar dates, which a day count cannot express.
 */
data class BillingCycle(
    val unit: CycleUnit,
    val count: Int,
) {
    init {
        require(count >= 1) { "cycle count must be at least 1, was $count" }
    }

    /** True for the four presets the add sheet offers as chips; everything else is "custom". */
    val isPreset: Boolean
        get() = this == MONTHLY || this == ANNUAL || this == QUARTERLY || this == WEEKLY

    companion object {
        val MONTHLY = BillingCycle(CycleUnit.MONTH, 1)
        val ANNUAL = BillingCycle(CycleUnit.YEAR, 1)
        val QUARTERLY = BillingCycle(CycleUnit.MONTH, 3)
        val WEEKLY = BillingCycle(CycleUnit.WEEK, 1)
    }
}

/**
 * The domain view of a subscription. The Room entity mirrors this; this is the shape the UI
 * and the normalization math work against.
 *
 * [amountMinor] is always in [currency] — the currency the service actually bills in. The home
 * currency figure is derived at read time from the current rate, never stored, so a stale rate
 * cannot freeze itself into the burn total.
 */
data class Subscription(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycle: BillingCycle,
    val anchorDate: LocalDate,
    val category: Category,
    val otherLabel: String? = null,
    val isBusiness: Boolean = true,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val trialEndDate: LocalDate? = null,
    val resumeDate: LocalDate? = null,
    val cancelledDate: LocalDate? = null,
    val notes: String? = null,
) {
    /** The label shown for the category, honouring the custom label on [Category.OTHER]. */
    val categoryLabel: String
        get() = if (category == Category.OTHER && !otherLabel.isNullOrBlank()) {
            "${category.displayName} · $otherLabel"
        } else {
            category.displayName
        }
}
