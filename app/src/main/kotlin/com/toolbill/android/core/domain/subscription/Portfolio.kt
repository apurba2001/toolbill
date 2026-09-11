package com.toolbill.android.core.domain.subscription

import com.toolbill.android.core.domain.date.chargeDatesInRange
import com.toolbill.android.core.domain.money.divideRoundingHalfUp
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import java.time.LocalDate
import java.util.Locale

/**
 * Everything the screens state about a set of subscriptions, derived in one place.
 *
 * Home, Insights, the calendar and the widget all quote figures that have to agree with each
 * other — the category breakdown sums to the hero, the business split sums to the hero, the
 * widget shows the same burn as the app. Deriving each one separately at its call site is how
 * they drift apart, so every figure any screen shows comes from this file.
 *
 * All of it is pure and date-injected, so it is unit-testable without a device.
 */

/** A charge that falls due on [date], with the home-currency amount that will be taken. */
data class UpcomingCharge(
    val priced: PricedSubscription,
    val date: LocalDate,
) {
    val amountMinor: Long get() = priced.homeAmountMinor
}

/** Why a subscription is surfaced above the week's schedule. */
enum class AttentionKind { OVERDUE, TRIAL_ENDING }

/** Something that costs money if ignored. */
data class AttentionItem(
    val priced: PricedSubscription,
    val date: LocalDate,
    val kind: AttentionKind,
)

/** A category and its normalized monthly spend, in home-currency minor units. */
data class CategorySpend(val label: String, val amountMinor: Long)

/**
 * A subscription that would cost less billed annually.
 *
 * [annualPriceMinor] is the list price of the annual plan. Nothing here can know that price —
 * it is not on the invoice Toolbill sees — so this is only ever populated from a plan the user
 * has entered themselves. Until then the list is empty and the section does not appear.
 */
data class AnnualSaving(
    val service: String,
    val monthlyTotalMinor: Long,
    val annualPriceMinor: Long,
) {
    val savingMinor: Long get() = monthlyTotalMinor - annualPriceMinor
}

/** Only ACTIVE counts toward money. TRIAL is stated separately, as what it *will* add. */
val List<PricedSubscription>.active: List<PricedSubscription>
    get() = filter { it.subscription.status.countsTowardBurn }

val List<PricedSubscription>.trials: List<PricedSubscription>
    get() = filter { it.subscription.status == SubscriptionStatus.TRIAL }

/** The headline: every active subscription reduced to a true per-month figure and summed. */
fun List<PricedSubscription>.monthlyBurnMinor(): Long =
    active.sumOf { normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle) }

/** Twelve times the burn — the same relationship the row-level annualized figure uses. */
fun List<PricedSubscription>.annualizedBurnMinor(): Long = monthlyBurnMinor() * 12

fun List<PricedSubscription>.activeCount(): Int = active.size

fun List<PricedSubscription>.businessBurnMinor(): Long = active
    .filter { it.subscription.isBusiness }
    .sumOf { normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle) }

fun List<PricedSubscription>.personalBurnMinor(): Long = monthlyBurnMinor() - businessBurnMinor()

/**
 * The business share as a whole percent, rounded rather than truncated.
 *
 * 95.95% truncates to 95 and the split reads "95 / 4", which does not add to 100 — the kind of
 * arithmetic slip that costs a money app its credibility. Zero burn reports 0 rather than
 * dividing by it.
 */
fun List<PricedSubscription>.businessSharePercent(): Int {
    val total = monthlyBurnMinor()
    if (total == 0L) return 0
    return divideRoundingHalfUp(businessBurnMinor() * 100, total).toInt()
}

/**
 * The burn attributable to subscriptions billed in something other than [homeCurrency].
 *
 * This is the figure the FX notice is a percentage *of*, so it is normalized the same way the
 * hero is — an annual USD plan contributes its monthly share, not its whole invoice.
 */
fun List<PricedSubscription>.foreignBilledBurnMinor(homeCurrency: String): Long {
    val home = homeCurrency.uppercase(Locale.ROOT)
    return active
        .filter { it.subscription.currency.uppercase(Locale.ROOT) != home }
        .sumOf { normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle) }
}

/**
 * Every charge falling in [from]..[to], in date order, ties broken by name so the list is
 * stable between recompositions.
 *
 * Runs the same [chargeDatesInRange] the calendar and the charge recorder use, so the three
 * cannot disagree about which day a subscription lands on.
 */
fun List<PricedSubscription>.upcomingCharges(
    from: LocalDate,
    to: LocalDate,
): List<UpcomingCharge> = active
    .flatMap { priced ->
        val s = priced.subscription
        chargeDatesInRange(s.anchorDate, s.cycle.unit, s.cycle.count, from, to)
            .map { UpcomingCharge(priced, it) }
    }
    .sortedWith(compareBy({ it.date }, { it.priced.subscription.name.lowercase(Locale.ROOT) }))

/**
 * Overdue charges and trials about to convert — the two things that cost money if ignored.
 *
 * Overdue outranks a converting trial: one is money already taken, the other is money about to
 * be. Within a kind, the oldest date first.
 */
fun List<PricedSubscription>.needsAttention(today: LocalDate): List<AttentionItem> {
    val overdue = mapNotNull { priced ->
        priced.overdueSince?.let { AttentionItem(priced, it, AttentionKind.OVERDUE) }
    }
    val converting = trials.mapNotNull { priced ->
        priced.subscription.trialEndDate
            ?.takeIf { !it.isBefore(today) }
            ?.let { AttentionItem(priced, it, AttentionKind.TRIAL_ENDING) }
    }
    return (overdue.sortedBy { it.date } + converting.sortedBy { it.date })
}

/**
 * Normalized monthly spend per category, largest first.
 *
 * Sums to [monthlyBurnMinor] by construction — every active subscription lands in exactly one
 * bucket — which is what lets Insights claim the breakdown matches the Home hero.
 */
fun List<PricedSubscription>.categorySpend(): List<CategorySpend> = active
    .groupBy { it.subscription.categoryLabel }
    .map { (label, group) ->
        CategorySpend(
            label = label,
            amountMinor = group.sumOf {
                normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle)
            },
        )
    }
    .sortedWith(compareByDescending<CategorySpend> { it.amountMinor }.thenBy { it.label })
