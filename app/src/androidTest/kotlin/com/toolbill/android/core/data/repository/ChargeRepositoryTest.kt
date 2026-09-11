package com.toolbill.android.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * The recorder is the only thing in the app that writes a fact rather than a derivation, so
 * what it refuses to write matters as much as what it writes.
 */
@RunWith(AndroidJUnit4::class)
class ChargeRepositoryTest {

    private lateinit var database: ToolbillDatabase
    private lateinit var repository: ChargeRepository

    private val anchor = LocalDate.of(2026, 1, 15)
    private val today = LocalDate.of(2026, 6, 20)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ToolbillDatabase::class.java,
        ).allowMainThreadQueries().build()
        // Pinned to the bundled table. These tests assert how a charge is converted and
        // recorded, not which rates happen to be installed -- and the application under test
        // fetches live rates on start, so reading the global table made them depend on today's
        // USD/INR and fail the moment it moved.
        repository = ChargeRepository(
            database.chargeDao(),
            database.subscriptionDao(),
        ) { FxRates.BUNDLED }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun recordsEveryChargeSinceTrackingBegan() = runTest {
        insert(createdAt = anchor)

        repository.recordDueCharges(today, "INR")

        // 15 Jan through 15 Jun inclusive.
        val dates = repository.chargesFor("sub").first().map { it.dueDate }
        assertEquals(6, dates.size)
        assertTrue(dates.all { it.dayOfMonth == 15 })
    }

    /** Toolbill cannot have observed a charge before it knew the subscription existed. */
    @Test
    fun doesNotBackfillBeforeTheSubscriptionWasCreated() = runTest {
        insert(createdAt = LocalDate.of(2026, 4, 1))

        repository.recordDueCharges(today, "INR")

        val dates = repository.chargesFor("sub").first().map { it.dueDate }
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 4, 15),
            ),
            dates,
        )
        assertTrue("nothing before 1 April should exist", dates.none { it.monthValue < 4 })
    }

    @Test
    fun isIdempotentAcrossRuns() = runTest {
        insert(createdAt = anchor)

        repository.recordDueCharges(today, "INR")
        repository.recordDueCharges(today, "INR")
        repository.recordDueCharges(today, "INR")

        assertEquals(6, repository.chargesFor("sub").first().size)
    }

    @Test
    fun capturesTheHomeCurrencyFigureAtRecordTime() = runTest {
        insert(createdAt = LocalDate.of(2026, 6, 15))

        repository.recordDueCharges(today, "INR")

        val charge = repository.chargesFor("sub").first().single()
        assertEquals("INR", charge.homeCurrency)
        assertTrue(charge.captured)
        // 20.00 USD at the captured rate of 87.10.
        assertEquals(174_200L, charge.homeAmountMinor)
    }

    /** Nothing records when the pause began, so any end date would be a guess. */
    @Test
    fun skipsPausedSubscriptions() = runTest {
        insert(createdAt = anchor, status = SubscriptionStatus.PAUSED)

        repository.recordDueCharges(today, "INR")

        assertTrue(repository.chargesFor("sub").first().isEmpty())
    }

    @Test
    fun stopsACancelledSubscriptionAtItsCancellationDate() = runTest {
        insert(
            createdAt = anchor,
            status = SubscriptionStatus.CANCELLED,
            cancelledDate = LocalDate.of(2026, 3, 20),
        )

        repository.recordDueCharges(today, "INR")

        val dates = repository.chargesFor("sub").first().map { it.dueDate }
        assertEquals(listOf(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 2, 15), anchor), dates)
    }

    @Test
    fun recordsWithoutAHomeFigureWhenTheRateIsUnknown() = runTest {
        insert(createdAt = LocalDate.of(2026, 6, 15), currency = "JPY")

        repository.recordDueCharges(today, "INR")

        val charge = repository.chargesFor("sub").first().single()
        assertTrue("an uncaptured charge should not claim a home figure", !charge.captured)
        assertEquals(0L, charge.homeAmountMinor)
    }

    /**
     * The recorder must never overwrite a row that already exists.
     *
     * It used to upsert every due date on every run, which silently reverted a charge the user
     * had marked skipped and re-stamped every captured home figure at today's rate -- undoing
     * the one thing the table exists to preserve.
     */
    @Test
    fun leavesAChargeTheUserMarkedSkippedAlone() = runTest {
        insert(createdAt = anchor)
        val subscription = database.subscriptionDao().getSubscription("sub")!!.toDomainForTest()
        val skipped = LocalDate.of(2026, 3, 15)

        repository.recordCharge(subscription, skipped, ChargeStatus.SKIPPED, "INR")
        repository.recordDueCharges(today, "INR")

        val row = database.chargeDao().getCharge(ChargeEntity.idFor("sub", skipped))!!
        assertEquals(ChargeStatus.SKIPPED, row.status)
        assertEquals(0L, row.homeAmountMinor)
        // The rest of the schedule still records around it.
        assertEquals(6, repository.chargesFor("sub").first().size)
    }

    @Test
    fun recordChargeReturnsTheRowItDisplacedSoUndoCanRestoreIt() = runTest {
        insert(createdAt = anchor)
        val subscription = database.subscriptionDao().getSubscription("sub")!!.toDomainForTest()
        val date = LocalDate.of(2026, 3, 15)
        repository.recordDueCharges(today, "INR")

        val previous = repository.recordCharge(subscription, date, ChargeStatus.SKIPPED, "INR")

        assertEquals(ChargeStatus.PAID, previous!!.status)
        assertEquals(174_200L, previous.homeAmountMinor)

        repository.revertCharge("sub", date, previous)

        val restored = database.chargeDao().getCharge(ChargeEntity.idFor("sub", date))!!
        assertEquals(ChargeStatus.PAID, restored.status)
        assertEquals(174_200L, restored.homeAmountMinor)
    }

    /** Undoing a charge that displaced nothing has to leave nothing behind. */
    @Test
    fun revertRemovesARowThatDisplacedNothing() = runTest {
        insert(createdAt = anchor)
        val subscription = database.subscriptionDao().getSubscription("sub")!!.toDomainForTest()
        // Well past today, so the recorder has not written it.
        val future = LocalDate.of(2026, 9, 15)

        val previous = repository.recordCharge(subscription, future, ChargeStatus.PAID, "INR")
        assertEquals(null, previous)

        repository.revertCharge("sub", future, previous)

        assertEquals(null, database.chargeDao().getCharge(ChargeEntity.idFor("sub", future)))
    }

    /**
     * A skipped charge cost zero, and that is a known figure rather than a missing one.
     *
     * It keeps the home currency so it exports as a real zero row, and carries no rate --
     * nothing was converted, so there is no rate to report.
     */
    @Test
    fun aSkippedChargeRecordsAKnownZeroAndNoRate() = runTest {
        insert(createdAt = anchor)
        val subscription = database.subscriptionDao().getSubscription("sub")!!.toDomainForTest()
        val date = LocalDate.of(2026, 9, 15)

        repository.recordCharge(subscription, date, ChargeStatus.SKIPPED, "INR")

        val row = database.chargeDao().getCharge(ChargeEntity.idFor("sub", date))!!
        assertEquals(0L, row.homeAmountMinor)
        assertEquals("INR", row.homeCurrency)
        assertEquals(null, row.fxRate)
        assertTrue("a hand-set row must be marked as one", row.isUserOverridden)
        // The contracted amount survives: what the row was for and what it came to are
        // different facts, and only the second one is zero.
        assertEquals(2_000L, row.amountMinor)
    }

    @Test
    fun theRecorderStoresTheRateItApplied() = runTest {
        insert(createdAt = LocalDate.of(2026, 6, 15))

        repository.recordDueCharges(today, "INR")

        val row = database.chargeDao().getCharge(ChargeEntity.idFor("sub", today.withDayOfMonth(15)))!!
        assertEquals(BigDecimal("87.100000"), row.fxRate)
        assertTrue("the recorder is not a person", !row.isUserOverridden)
        // The stored rate reproduces the stored figure.
        assertEquals(
            174_200L,
            row.fxRate!!.multiply(BigDecimal(row.amountMinor)).toLong(),
        )
    }

    private fun SubscriptionEntity.toDomainForTest() = Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        cycle = BillingCycle(cycleUnit, cycleCount),
        anchorDate = anchorDate,
        category = category,
        isBusiness = isBusiness,
        status = status,
    )

    private suspend fun insert(
        createdAt: LocalDate,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        cancelledDate: LocalDate? = null,
        currency: String = "USD",
    ) {
        database.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = "sub",
                name = "Claude Pro",
                amountMinor = 2_000,
                currency = currency,
                cycleUnit = CycleUnit.MONTH,
                cycleCount = 1,
                anchorDate = anchor,
                category = Category.AI_TOOLS,
                otherLabel = null,
                isBusiness = true,
                status = status,
                trialEndDate = null,
                resumeDate = null,
                cancelledDate = cancelledDate,
                notes = null,
                createdAt = createdAt.atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli(),
            ),
        )
    }
}
