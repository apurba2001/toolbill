package com.toolbill.android.feature.subscriptions

import com.toolbill.android.core.design.component.RowState
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.date.nextChargeDate
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.chargeDiffersFromMonthly
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Everything a list row needs, pre-computed.
 *
 * The row composable does no formatting and no date arithmetic of its own — it is handed
 * finished strings. That keeps the 35-row list cheap to recompose and makes every one of these
 * strings testable without a UI test.
 */
data class SubscriptionRowUi(
    val id: String,
    val name: String,
    val monogram: String,
    val state: RowState,
    val badgeLabel: String?,
    val badgeSpoken: String?,
    val supportLine: String,
    val isBusiness: Boolean,
    val showsBusinessGlyph: Boolean,
    /** Normalized monthly in the home currency — what every sort and total runs on. */
    val normalizedMonthlyMinor: Long,
    val homeCurrency: String,
    /** `/mo` on rows whose real charge is not what is shown. */
    val amountSuffix: String?,
    /** The real charge, shown only where it differs from the figure above it. */
    val secondaryLine: String?,
    val spokenDescription: String,
)

/**
 * Home's next-7-days row.
 *
 * Carries the **actual charge**, not the normalized monthly share — this block exists to
 * justify a single week's total, and normalized shares would not sum to it. The normalized
 * figure moves into the support line for non-monthly plans, where it is the thing that
 * explains why an annual renewal is ranked where it is.
 */
fun Subscription.toUpcomingRowUi(
    today: LocalDate,
    homeCurrency: String,
    homeAmountMinor: Long,
    chargeDate: LocalDate,
): SubscriptionRowUi {
    val foreign = currency.uppercase(Locale.ROOT) != homeCurrency.uppercase(Locale.ROOT)
    val daysUntil = ChronoUnit.DAYS.between(today, chargeDate)
    val nonMonthly = cycle.chargeDiffersFromMonthly()
    val normalized = normalizedMonthlyMinor(homeAmountMinor, cycle)

    val support = buildString {
        append(relativeDays(daysUntil))
        append(" · ")
        append(if (nonMonthly) "${cycle.label()} charge" else cycle.label())
        if (nonMonthly) append(" · ${MoneyFormat.symbol(normalized, homeCurrency)}/mo")
    }

    val original = MoneyFormat.format(amountMinor, currency).let { "${it.code} ${it.plain}" }
    val charge = MoneyFormat.symbol(homeAmountMinor, homeCurrency)

    return SubscriptionRowUi(
        id = id,
        name = name,
        monogram = name.filter { it.isLetterOrDigit() }.take(2),
        state = RowState.DUE_SOON,
        badgeLabel = null,
        badgeSpoken = null,
        supportLine = support,
        isBusiness = isBusiness,
        showsBusinessGlyph = true,
        normalizedMonthlyMinor = homeAmountMinor,
        homeCurrency = homeCurrency,
        amountSuffix = null,
        secondaryLine = if (foreign) original else null,
        spokenDescription = buildString {
            append("$name charges $charge ${relativeDays(daysUntil)}")
            if (nonMonthly) {
                append(", billed ${cycle.spokenLabel()}, ")
                append("${MoneyFormat.symbol(normalized, homeCurrency)} per month normalized")
            }
            append(if (isBusiness) ". Business expense" else ". Personal")
        },
    )
}

/** The adverb form, for spoken descriptions: "billed annually as ₹4,662". */
fun BillingCycle.spokenLabel(): String = when {
    this == BillingCycle.MONTHLY -> "monthly"
    this == BillingCycle.ANNUAL -> "annually"
    this == BillingCycle.QUARTERLY -> "quarterly"
    this == BillingCycle.WEEKLY -> "weekly"
    else -> "every $count ${unit.name.lowercase(Locale.ENGLISH)}s"
}

/** Formats a billing cycle the way the support line names it. */
fun BillingCycle.label(): String = when {
    this == BillingCycle.MONTHLY -> "monthly"
    this == BillingCycle.ANNUAL -> "annual"
    this == BillingCycle.QUARTERLY -> "quarterly"
    this == BillingCycle.WEEKLY -> "weekly"
    unit == CycleUnit.DAY -> "every $count days"
    unit == CycleUnit.WEEK -> "every $count weeks"
    unit == CycleUnit.MONTH -> "every $count months"
    else -> "every $count years"
}

private fun relativeDays(days: Long): String = when {
    days == 0L -> "today"
    days == 1L -> "in 1 day"
    else -> "in $days days"
}

/**
 * Builds the row model.
 *
 * [homeAmountMinor] is this subscription's charge already converted to the home currency at the
 * current rate — derived by the caller, never stored, so a stale rate cannot freeze itself into
 * the burn total.
 *
 * [overdueSince] is supplied rather than inferred: whether a charge is overdue depends on the
 * charge record, not on the schedule, and the schedule alone cannot tell the difference between
 * "not yet paid" and "paid early".
 */
fun Subscription.toRowUi(
    today: LocalDate,
    homeCurrency: String,
    homeAmountMinor: Long,
    dueSoonWindowDays: Long = 7,
    overdueSince: LocalDate? = null,
    /**
     * Home's next-7-days list sets this false: its rows carry a date block, so a DUE badge
     * beside it would state the same fact twice in the same row.
     */
    showDueBadge: Boolean = true,
): SubscriptionRowUi {
    val normalized = normalizedMonthlyMinor(homeAmountMinor, cycle)
    val cycleLabel = cycle.label()
    val foreign = currency.uppercase(Locale.ROOT) != homeCurrency.uppercase(Locale.ROOT)

    val state: RowState
    val badgeLabel: String?
    val badgeSpoken: String?
    val support: String

    when {
        status == SubscriptionStatus.CANCELLED -> {
            state = RowState.CANCELLED
            badgeLabel = null
            badgeSpoken = null
            support = buildString {
                append("cancelled")
                cancelledDate?.let { append(" ${it.format(dayMonth)}") }
                append(" · kept for records")
            }
        }

        status == SubscriptionStatus.PAUSED -> {
            state = RowState.PAUSED
            badgeLabel = "PAUSED"
            badgeSpoken = "Paused, not counted in your monthly burn"
            support = resumeDate?.let { "resumes ${it.format(dayMonth)}" } ?: "not in burn"
        }

        status == SubscriptionStatus.TRIAL -> {
            state = RowState.TRIAL
            val daysLeft = trialEndDate?.let { ChronoUnit.DAYS.between(today, it) } ?: 0
            badgeLabel = "TRIAL ${daysLeft}d"
            badgeSpoken = "Trial ends in $daysLeft days"
            val monthly = MoneyFormat.symbolWhole(normalized, homeCurrency)
            support = trialEndDate?.let { "then $monthly/mo from ${it.format(dayMonth)}" }
                ?: "not charged yet"
        }

        overdueSince != null -> {
            state = RowState.OVERDUE
            val late = ChronoUnit.DAYS.between(overdueSince, today)
            badgeLabel = "OVERDUE ${late}d"
            badgeSpoken = "Overdue by $late days"
            support = cycleLabel
        }

        else -> {
            val next = nextChargeDate(anchorDate, cycle.unit, cycle.count, today.minusDays(1))
            val daysUntil = ChronoUnit.DAYS.between(today, next)
            val dueSoon = daysUntil <= dueSoonWindowDays
            when {
                dueSoon && showDueBadge -> {
                    state = RowState.DUE_SOON
                    badgeLabel = "DUE ${daysUntil}d"
                    badgeSpoken = "Due in $daysUntil days"
                    support = cycleLabel
                }

                !showDueBadge -> {
                    state = if (dueSoon) RowState.DUE_SOON else RowState.ACTIVE
                    badgeLabel = null
                    badgeSpoken = "Due ${relativeDays(daysUntil)}"
                    support = "${relativeDays(daysUntil)} · $cycleLabel"
                }

                else -> {
                    state = RowState.ACTIVE
                    badgeLabel = null
                    badgeSpoken = null
                    support = "$cycleLabel · ${relativeDays(daysUntil)}"
                }
            }
        }
    }

    val nonMonthly = cycle.chargeDiffersFromMonthly()
    val realCharge = MoneyFormat.symbol(homeAmountMinor, homeCurrency)
    val originalCharge = MoneyFormat.format(amountMinor, currency).let { "${it.code} ${it.plain}" }

    val secondary = when {
        nonMonthly && foreign -> "$realCharge · $originalCharge"
        nonMonthly -> realCharge
        foreign -> originalCharge
        else -> null
    }

    val spokenAmount = MoneyFormat.symbol(normalized, homeCurrency)
    val spoken = buildString {
        append(name)
        badgeSpoken?.let { append(". $it") }
        append(". $spokenAmount per month")
        if (nonMonthly) append(", billed ${cycle.spokenLabel()} as $realCharge")
        append(if (isBusiness) ". Business expense" else ". Personal")
    }

    return SubscriptionRowUi(
        id = id,
        name = name,
        monogram = name.filter { it.isLetterOrDigit() }.take(2),
        state = state,
        badgeLabel = badgeLabel,
        badgeSpoken = badgeSpoken,
        supportLine = support,
        isBusiness = isBusiness,
        showsBusinessGlyph = status != SubscriptionStatus.CANCELLED,
        normalizedMonthlyMinor = normalized,
        homeCurrency = homeCurrency,
        amountSuffix = if (nonMonthly) "/mo" else null,
        secondaryLine = secondary,
        spokenDescription = spoken,
    )
}
