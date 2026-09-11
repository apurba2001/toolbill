package com.toolbill.android.core.domain.export

import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.Subscription
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The period a filing covers.
 *
 * [FINANCIAL_YEAR] is April to March, the Indian financial year — the one this app's first
 * users file against. Derived from the date the export is run rather than hardcoded, because
 * an export screen that only knows about FY 2026-27 is wrong from April 2027 onward.
 */
enum class ExportPeriod {
    FINANCIAL_YEAR,
    THIS_QUARTER,
    CUSTOM,
    ;

    fun defaultRange(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        FINANCIAL_YEAR -> {
            val startYear = if (today.monthValue >= 4) today.year else today.year - 1
            LocalDate.of(startYear, 4, 1)..LocalDate.of(startYear + 1, 3, 31)
        }

        THIS_QUARTER -> {
            val firstMonth = ((today.monthValue - 1) / 3) * 3 + 1
            val start = LocalDate.of(today.year, firstMonth, 1)
            start..start.plusMonths(3).minusDays(1)
        }

        // The caller holds the dates; this is only the starting point it opens on.
        CUSTOM -> today.withDayOfMonth(1)..today
    }

    /** How the chip reads, given the range it currently covers. */
    fun label(range: ClosedRange<LocalDate>): String = when (this) {
        FINANCIAL_YEAR -> "FY ${range.start.year}-${(range.endInclusive.year % 100)}"
        THIS_QUARTER -> "This quarter"
        CUSTOM -> "Custom..."
    }
}

/** Which subscriptions a filing includes. */
data class ExportFilters(
    val business: Boolean = true,
    val personal: Boolean = false,
    val cancelled: Boolean = false,
)

/** What the export will contain, for the summary line above the preview. */
data class ExportSummary(
    val chargeCount: Int,
    val serviceCount: Int,
    val totalHomeMinor: Long,
    val homeCurrency: String,
)

/**
 * The columns, fixed.
 *
 * Stable year to year is the whole point: an accountant reconciling FY2026-27 against FY2027-28
 * must not find the shape has moved under them. That is also why the eleven categories are
 * fixed and a custom label rides in its own column rather than replacing the category.
 */
const val CSV_HEADER: String = "date,service,cat,other,type,amt,ccy,rate,home,status"

/**
 * The charges a filing covers, newest first.
 *
 * A skipped renewal is included like any other — status reads `skipped` and the home amount is
 * zero — so nothing quietly vanishes between the app and a spreadsheet. A gap in a monthly
 * sequence is something an accountant has to chase; an explicit zero is something they can read.
 */
fun chargesForExport(
    charges: List<Charge>,
    subscriptions: List<Subscription>,
    range: ClosedRange<LocalDate>,
    filters: ExportFilters,
): List<Pair<Charge, Subscription>> {
    val byId = subscriptions.associateBy { it.id }
    return charges
        .mapNotNull { charge -> byId[charge.subscriptionId]?.let { charge to it } }
        .filter { (charge, subscription) ->
            charge.dueDate in range && subscription.matches(filters)
        }
        .sortedByDescending { (charge, _) -> charge.dueDate }
}

private fun Subscription.matches(filters: ExportFilters): Boolean {
    val cancelled = status == com.toolbill.android.core.domain.subscription.SubscriptionStatus.CANCELLED
    // Cancelled is an addition, not a third category: a cancelled business tool is still
    // business spend, and excluding it would drop a deductible charge from a filing.
    if (cancelled && !filters.cancelled) return false
    return if (isBusiness) filters.business else filters.personal
}

/** One CSV line per charge, header included. */
fun toCsv(rows: List<Pair<Charge, Subscription>>): String = buildString {
    appendLine(CSV_HEADER)
    rows.forEach { (charge, subscription) -> appendLine(csvLine(charge, subscription)) }
}

/**
 * One row.
 *
 * Dates are ISO-8601. The design's preview used `26-08-27` to fit a narrow box, but a filing is
 * read by a spreadsheet and by a person who may not share the app's locale, and `26-08-27` is
 * `DD-MM-YY` to half the world. An unambiguous date is worth four characters.
 *
 * Money is written in major units with the currency's own decimal places — what a spreadsheet
 * sums — rather than the minor units the app stores.
 */
fun csvLine(charge: Charge, subscription: Subscription): String = listOf(
    charge.dueDate.toString(),
    subscription.name,
    subscription.category.csvCode,
    subscription.otherLabel.orEmpty(),
    if (subscription.isBusiness) "biz" else "pers",
    major(charge.amountMinor, charge.currency),
    charge.currency,
    // Empty rather than guessed. A charge recorded before any rate was captured, or one that
    // never happened, has no rate to report -- and a number here that nothing measured is worse
    // than a blank, because a blank is visibly a blank.
    charge.fxRate?.let(::plainRate).orEmpty(),
    if (charge.captured) major(charge.homeAmountMinor, charge.homeCurrency) else "",
    if (charge.status == ChargeStatus.SKIPPED) "skipped" else "paid",
).joinToString(",") { escape(it) }

/** Summary of what the current selection would export. */
fun summarize(
    rows: List<Pair<Charge, Subscription>>,
    homeCurrency: String,
): ExportSummary = ExportSummary(
    chargeCount = rows.size,
    serviceCount = rows.map { (_, subscription) -> subscription.id }.distinct().size,
    // Only charges that actually cost something, and only those with a captured figure. A
    // skipped row is in the file and out of the total, which is the same rule every other
    // total in the app follows.
    totalHomeMinor = rows
        .filter { (charge, _) -> charge.captured && charge.countsTowardSpend }
        .sumOf { (charge, _) -> charge.homeAmountMinor },
    homeCurrency = homeCurrency,
)

/** Minor units as a plain decimal string in the currency's own scale. */
private fun major(amountMinor: Long, currency: String): String =
    BigDecimal(amountMinor)
        .movePointLeft(MoneyFormat.fractionDigits(currency))
        .toPlainString()

/** Trailing zeros trimmed, but never into scientific notation. */
private fun plainRate(rate: BigDecimal): String =
    rate.stripTrailingZeros().toPlainString()

/**
 * RFC 4180 quoting.
 *
 * A service name is free text: "Adobe, Inc." or a name carrying a quote would otherwise shift
 * every column after it by one, silently, in a file nobody re-reads before filing it.
 */
private fun escape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
