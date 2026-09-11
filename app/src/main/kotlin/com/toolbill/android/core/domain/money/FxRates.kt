package com.toolbill.android.core.domain.money

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The rates every figure in the app is currently priced at.
 *
 * A single swappable [FxRateTable] rather than a suspending lookup. Conversion happens inside
 * `map` blocks on the subscription list, inside the widget, and inside the charge recorder —
 * making it asynchronous would push a coroutine through every one of those for a lookup that is
 * a hash-map read. Instead the data layer fetches in the background and swaps the whole table
 * in one assignment, and every caller stays synchronous.
 *
 * The swap is atomic and the table is immutable, so a read can never see half of one refresh.
 * Anything that needs to recompute when rates move observes [generation].
 */
object FxRates {

    /**
     * The rates Toolbill ships with.
     *
     * Dated to the capture the design's worked example is drawn at, where USD/INR 87.10 is
     * exactly the rate those figures were computed with (Vercel: 2 000 US cents × 87.10 =
     * 174 200 paise). This is what a fresh install prices with before its first refresh, and
     * what any currency the live source does not publish keeps using for good.
     */
    val BUNDLED: FxRateTable = FxRateTable(
        capturedOn = LocalDate.of(2026, 4, 12),
        inrPer = mapOf(
            "INR" to BigDecimal("1.00"),
            "USD" to BigDecimal("87.10"),
            "EUR" to BigDecimal("94.30"),
            "GBP" to BigDecimal("111.20"),
            "CAD" to BigDecimal("62.40"),
            "AUD" to BigDecimal("56.80"),
            "SGD" to BigDecimal("67.90"),
            "AED" to BigDecimal("23.71"),
        ),
    )

    private val _table = MutableStateFlow(BUNDLED)

    /**
     * The table in force, as a stream.
     *
     * The subscription list is a Flow of stored rows, and a rate change moves every figure
     * derived from those rows without any row itself changing — so the list has to be told to
     * re-derive. Anything holding a derived figure combines against this.
     */
    val table: StateFlow<FxRateTable> = _table.asStateFlow()

    /** The table in force, read directly. Safe from any thread. */
    val snapshot: FxRateTable get() = _table.value

    fun install(newTable: FxRateTable) {
        _table.value = newTable
    }

    // --- Delegating API, so the whole app keeps calling FxRates directly ---

    val capturedOn: LocalDate get() = snapshot.capturedOn
    val supported: List<String> get() = snapshot.supported
    val isLive: Boolean get() = snapshot.isLive

    fun isSupported(currency: String): Boolean = snapshot.isSupported(currency)

    fun rateFor(from: String, to: String): BigDecimal? = snapshot.rateFor(from, to)

    fun convertMinor(amountMinor: Long, from: String, to: String): Long? =
        snapshot.convertMinor(amountMinor, from, to)
}
