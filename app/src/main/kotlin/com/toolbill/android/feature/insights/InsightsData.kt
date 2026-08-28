package com.toolbill.android.feature.insights

import com.toolbill.android.core.domain.money.divideRoundingHalfUp

/** A category and its normalized monthly spend, in minor units. */
data class CategorySpend(val label: String, val amountMinor: Long)

/** An annual-billing saving the user could take. */
data class AnnualSaving(
    val service: String,
    val monthlyTotalMinor: Long,
    val annualPriceMinor: Long,
) {
    val savingMinor: Long get() = monthlyTotalMinor - annualPriceMinor
}

/**
 * The category split from the design.
 *
 * These sum to ₹44,170.50 — the same figure as the Home hero, which is the point: if the
 * breakdown and the headline disagree, neither is believable. Pinned by a test.
 */
val sampleCategorySpend = listOf(
    CategorySpend("Hosting & infra", 1_707_500),
    CategorySpend("Email & marketing", 784_500),
    CategorySpend("Analytics", 701_800),
    CategorySpend("AI tools", 435_200),
    CategorySpend("Productivity", 347_258),
    CategorySpend("Design", 185_900),
    CategorySpend("Other · commerce", 144_800),
    CategorySpend("Storage", 83_600),
    CategorySpend("Domains", 26_492),
)

val sampleAnnualSavings = listOf(
    AnnualSaving("Figma Professional", 1_608_000, 1_340_000),
    AnnualSaving("Posthog Scale", 5_220_000, 4_350_000),
    AnnualSaving("Slack Pro", 1_983_600, 1_653_000),
)

/** The business / personal split, which must also sum to the hero. */
const val BUSINESS_MINOR = 4_238_592L
const val PERSONAL_MINOR = 178_458L
const val ANNUAL_TOOLING_MINOR = 53_004_600L

/**
 * The business share, rounded rather than truncated.
 *
 * 95.95% truncates to 95 and the split would read "95 / 4", which does not add to 100 and is
 * the kind of arithmetic slip that costs a finance app its credibility.
 */
val businessSharePercent: Int =
    divideRoundingHalfUp(BUSINESS_MINOR * 100, BUSINESS_MINOR + PERSONAL_MINOR).toInt()

val personalSharePercent: Int = 100 - businessSharePercent
