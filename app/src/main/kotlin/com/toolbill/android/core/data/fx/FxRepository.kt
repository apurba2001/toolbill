package com.toolbill.android.core.data.fx

import android.util.Log
import com.toolbill.android.core.database.dao.FxRateDao
import com.toolbill.android.core.database.model.FxRateEntity
import com.toolbill.android.core.domain.money.FxRateTable
import com.toolbill.android.core.domain.money.FxRates
import java.time.LocalDate

/**
 * Keeps [FxRates] holding the best rates available.
 *
 * Three sources, in descending order of currency: a live fetch, the last fetch still on disk,
 * and the rates Toolbill shipped with. Whichever wins is merged *over* the bundled table rather
 * than replacing it, so a currency the ECB does not publish — AED, among others — keeps working
 * instead of disappearing from an app that still offers it in the add sheet.
 */
class FxRepository(
    private val dao: FxRateDao,
    private val api: FrankfurterApi = FrankfurterApi(),
) {

    /**
     * Installs the newest rates already on disk. Cheap, synchronous-ish, no network.
     *
     * Called before the first frame so a device that has been offline for a week still prices
     * at last week's real rates rather than falling back to the bundled April table.
     */
    suspend fun installCached() {
        val latest = dao.latestCapturedOn() ?: return
        val cached = dao.ratesOn(latest)
        if (cached.isEmpty()) return
        FxRates.install(FxRates.BUNDLED.mergedWith(cached.toTable(latest)))
    }

    /**
     * Fetches, stores and installs today's rates.
     *
     * Returns false on any failure, having changed nothing. A refresh that cannot reach the
     * network is not an error condition to surface — the rates already in hand are still the
     * best available, and the Settings row says how old they are.
     */
    suspend fun refresh(today: LocalDate = LocalDate.now()): Boolean {
        val fetched = api.fetchLatest(FxRates.BUNDLED.supported).getOrElse { failure ->
            // The class as well as the message. A timeout and a parse error are different
            // problems with different fixes, and several of the exceptions that land here carry
            // no message at all -- "skipped: null" told nobody anything.
            Log.i(TAG, "Rate refresh skipped: ${failure::class.java.simpleName}: ${failure.message}")
            return false
        }

        val now = System.currentTimeMillis()
        dao.upsertAll(
            fetched.inrPer.map { (currency, rate) ->
                FxRateEntity(
                    capturedOn = fetched.capturedOn,
                    currency = currency,
                    inrPerUnit = rate,
                    fetchedAt = now,
                )
            },
        )
        // Two years covers any financial year the export can ask for, and keeps a table that
        // gains one row per currency per business day from growing for the life of the install.
        dao.deleteBefore(today.minusYears(2))

        FxRates.install(FxRates.BUNDLED.mergedWith(fetched))
        return true
    }

    /**
     * The rates in force on [date], for pricing a charge at what it actually cost that day.
     *
     * Falls back to whatever is currently installed when the table has nothing that old — which
     * is every charge on a fresh install, and is why a charge can carry no captured rate at all.
     */
    suspend fun tableFor(date: LocalDate): FxRateTable {
        val rows = dao.ratesInForceOn(date)
        if (rows.isEmpty()) return FxRates.snapshot
        val capturedOn = rows.first().capturedOn
        return FxRates.BUNDLED.mergedWith(rows.toTable(capturedOn))
    }

    private fun List<FxRateEntity>.toTable(capturedOn: LocalDate) = FxRateTable(
        inrPer = associate { it.currency to it.inrPerUnit },
        capturedOn = capturedOn,
        liveCodes = map { it.currency }.toSet(),
    )

    private companion object {
        const val TAG = "Toolbill"
    }
}
