package com.toolbill.android.core.data.fx

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.database.model.FxRateEntity
import com.toolbill.android.core.domain.money.FxRates
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Charges store the rate they were converted at and that figure is never recomputed, so pricing
 * one at the wrong day's rate is a mistake that stays in the record and ends up in a filing.
 */
@RunWith(AndroidJUnit4::class)
class FxRepositoryTest {

    private lateinit var database: ToolbillDatabase
    private lateinit var repository: FxRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ToolbillDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = FxRepository(database.fxRateDao())
    }

    @After
    fun teardown() {
        // The installed table is global, so a test that swaps it has to put it back or every
        // later test in the run prices against whatever this one happened to store.
        FxRates.install(FxRates.BUNDLED)
        database.close()
    }

    private suspend fun store(date: LocalDate, usdRate: String) {
        database.fxRateDao().upsertAll(
            listOf(
                FxRateEntity(date, "INR", BigDecimal("1.00"), 0L),
                FxRateEntity(date, "USD", BigDecimal(usdRate), 0L),
            ),
        )
    }

    @Test
    fun installCachedPrefersStoredRatesOverTheBundledTable() = runTest {
        store(LocalDate.of(2026, 9, 10), "95.42")

        repository.installCached()

        assertEquals(BigDecimal("95.42"), FxRates.snapshot.inrPer["USD"])
        assertTrue(FxRates.snapshot.isLive)
    }

    /** A currency the ECB does not publish must not vanish from an app that still offers it. */
    @Test
    fun installCachedKeepsBundledCurrenciesTheSourceDoesNotPublish() = runTest {
        store(LocalDate.of(2026, 9, 10), "95.42")

        repository.installCached()

        assertEquals(BigDecimal("23.71"), FxRates.snapshot.inrPer["AED"])
        assertTrue(FxRates.snapshot.isSupported("AED"))
        assertTrue("a bundled rate must not claim to be live", !FxRates.snapshot.isLive("AED"))
    }

    @Test
    fun installCachedLeavesTheBundledTableInPlaceWhenNothingIsStored() = runTest {
        repository.installCached()

        assertEquals(FxRates.BUNDLED.inrPer, FxRates.snapshot.inrPer)
        assertTrue(!FxRates.snapshot.isLive)
    }

    @Test
    fun installCachedTakesTheMostRecentPublication() = runTest {
        store(LocalDate.of(2026, 9, 4), "93.10")
        store(LocalDate.of(2026, 9, 10), "95.42")

        repository.installCached()

        assertEquals(BigDecimal("95.42"), FxRates.snapshot.inrPer["USD"])
        assertEquals(LocalDate.of(2026, 9, 10), FxRates.snapshot.capturedOn)
    }

    /**
     * A charge falling on a date the source did not publish for — a weekend, a holiday — is
     * priced at the last rate actually in force, which is what a card would have been charged at.
     */
    @Test
    fun tableForUsesTheLastRatePublishedBeforeTheDate() = runTest {
        store(LocalDate.of(2026, 9, 4), "93.10")
        store(LocalDate.of(2026, 9, 10), "95.42")

        val friday = repository.tableFor(LocalDate.of(2026, 9, 6))

        assertEquals(BigDecimal("93.10"), friday.inrPer["USD"])
        assertEquals(LocalDate.of(2026, 9, 4), friday.capturedOn)
    }

    @Test
    fun tableForPricesAnOldChargeAtTheRateOfItsOwnDay() = runTest {
        store(LocalDate.of(2026, 9, 4), "93.10")
        store(LocalDate.of(2026, 9, 10), "95.42")
        repository.installCached()

        val then = repository.tableFor(LocalDate.of(2026, 9, 4))

        // 20.00 USD on the 4th, not at the 10th's rate that is currently installed.
        assertEquals(186_200L, then.convertMinor(2_000, "USD", "INR"))
        assertEquals(190_840L, FxRates.snapshot.convertMinor(2_000, "USD", "INR"))
    }

    /** Before the first refresh there is nothing older to fall back to, only what is installed. */
    @Test
    fun tableForFallsBackToTheInstalledTableWhenNothingIsThatOld() = runTest {
        store(LocalDate.of(2026, 9, 10), "95.42")

        val older = repository.tableFor(LocalDate.of(2026, 1, 1))

        assertEquals(FxRates.snapshot.inrPer, older.inrPer)
    }
}
