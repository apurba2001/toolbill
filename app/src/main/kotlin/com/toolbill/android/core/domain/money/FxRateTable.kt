package com.toolbill.android.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/**
 * A set of exchange rates and the date they were published for.
 *
 * Rates are held as INR per one unit of each currency — an arbitrary internal base, chosen
 * because the design's worked example is drawn in rupees. Every pair is derived from it, so the
 * home currency can be anything without the table needing to know what it is.
 *
 * Pure: no network, no Android, no clock. The thing that fetches rates builds one of these and
 * hands it over, which is what keeps the conversion math testable against fixed numbers.
 */
data class FxRateTable(
    val inrPer: Map<String, BigDecimal>,
    /** The date the rates were published for — not the date they were fetched. */
    val capturedOn: LocalDate,
    /**
     * Which codes came from a live source.
     *
     * The ECB publishes thirty currencies and AED is not among them, so a live refresh covers
     * most of the table and leaves the rest on their bundled values. Naming the difference is
     * what lets the UI say which figures are current instead of implying all of them are.
     */
    val liveCodes: Set<String> = emptySet(),
) {
    val isLive: Boolean get() = liveCodes.isNotEmpty()

    /** Every currency that can be priced, in the order the pickers offer them. */
    val supported: List<String> get() = inrPer.keys.toList()

    fun isSupported(currency: String): Boolean =
        inrPer.containsKey(currency.uppercase(Locale.ROOT))

    fun isLive(currency: String): Boolean = currency.uppercase(Locale.ROOT) in liveCodes

    /**
     * Units of [to] per one unit of [from], or `null` when either has no rate.
     *
     * Six decimal places is enough to reproduce any home amount this app can compute back to the
     * minor unit, and few enough that the number stays readable in an exported spreadsheet.
     */
    fun rateFor(from: String, to: String): BigDecimal? {
        val source = inrPer[from.uppercase(Locale.ROOT)] ?: return null
        val target = inrPer[to.uppercase(Locale.ROOT)] ?: return null
        return source.divide(target, 6, RoundingMode.HALF_UP)
    }

    /**
     * [amountMinor] in [from]'s minor units, expressed in [to]'s minor units, or `null` when
     * either code has no rate.
     *
     * An unlisted code returns null rather than falling back to 1:1 — a wrong rate is worse than
     * a missing one, because it lands silently in the burn total.
     *
     * Minor-unit digits are read per currency rather than assumed to be two, so a zero-decimal
     * currency is not silently inflated a hundredfold on the way through.
     */
    fun convertMinor(amountMinor: Long, from: String, to: String): Long? {
        val source = from.uppercase(Locale.ROOT)
        val target = to.uppercase(Locale.ROOT)
        if (source == target) return amountMinor

        val sourceInr = inrPer[source] ?: return null
        val targetInr = inrPer[target] ?: return null

        val major = BigDecimal(amountMinor).movePointLeft(MoneyFormat.fractionDigits(source))
        return major
            .multiply(sourceInr)
            .divide(targetInr, 12, RoundingMode.HALF_UP)
            .movePointRight(MoneyFormat.fractionDigits(target))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /**
     * This table with [live] laid over it, keeping any code the live source does not publish.
     *
     * Merged rather than replaced: a refresh that dropped AED would take a currency the add
     * sheet still offers out of service, and a subscription the user can create must always be
     * priceable.
     */
    fun mergedWith(live: FxRateTable): FxRateTable = FxRateTable(
        inrPer = inrPer + live.inrPer,
        capturedOn = live.capturedOn,
        liveCodes = live.inrPer.keys,
    )
}
