package com.toolbill.android.core.domain.money

import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle

/**
 * Reduces any billing cycle to a true per-month figure in minor units.
 *
 * This is the number every total, every sort and every list in the app runs on, so that a
 * ₹4,662/year tool and a ₹388/month tool rank against each other correctly.
 *
 * MONTH and YEAR divide exactly — an annual ₹4,662 plan is ₹388.50 a month, full stop. Only
 * DAY and WEEK need the average-month approximation, because a "every 10 days" cycle genuinely
 * does not divide into a calendar month.
 *
 *  - DAY:  365.25 / 12 = 30.4375 days per average month, as 36525/1200
 *  - WEEK: 52.18  / 12 =  4.3483 weeks per average month, as  5218/1200
 *
 * Integer arithmetic throughout — money is never a Double. The multiplication is applied before
 * the division so the average-month factor keeps its precision.
 *
 * Rounds half-up rather than truncating. An annual ₹3,179 plan is ₹264.92 a month, not ₹264.91,
 * and the per-row figures have to round the same way the column total does or the arithmetic
 * stops closing on screen.
 *
 * Derived, never stored: the home-currency amount it is given changes whenever the rate does.
 */
fun normalizedMonthlyMinor(amountMinorHome: Long, cycle: BillingCycle): Long =
    when (cycle.unit) {
        CycleUnit.DAY -> divideRoundingHalfUp(amountMinorHome * 36525, cycle.count * 1200L)
        CycleUnit.WEEK -> divideRoundingHalfUp(amountMinorHome * 5218, cycle.count * 1200L)
        CycleUnit.MONTH -> divideRoundingHalfUp(amountMinorHome, cycle.count.toLong())
        CycleUnit.YEAR -> divideRoundingHalfUp(amountMinorHome, cycle.count * 12L)
    }

/** Integer division rounding halves away from zero, so ₹264.9166 becomes ₹264.92. */
fun divideRoundingHalfUp(numerator: Long, denominator: Long): Long {
    require(denominator != 0L) { "denominator must not be zero" }
    val quotient = numerator / denominator
    val remainder = numerator % denominator
    if (remainder == 0L) return quotient
    val roundsAway = Math.abs(remainder) * 2 >= Math.abs(denominator)
    if (!roundsAway) return quotient
    val sameSign = (numerator xor denominator) >= 0
    return if (sameSign) quotient + 1 else quotient - 1
}

/** The annualized figure shown beside the hero: twelve times the normalized monthly. */
fun annualizedMinor(amountMinorHome: Long, cycle: BillingCycle): Long =
    normalizedMonthlyMinor(amountMinorHome, cycle) * 12

/**
 * Whether a subscription's real charge differs from its normalized monthly share.
 *
 * Drives the second figure on a list row: monthly rows carry one number, everything else
 * carries the real charge underneath, so the extra line appears only where it changes ranking.
 */
fun BillingCycle.chargeDiffersFromMonthly(): Boolean =
    !(unit == CycleUnit.MONTH && count == 1)
