package com.toolbill.android.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Delete is the one row action that cannot be reconstructed from what is left on screen, so
 * the way back has to survive everything the delete takes with it.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionRepositoryTest {

    private lateinit var database: ToolbillDatabase
    private lateinit var repository: SubscriptionRepository
    private lateinit var charges: ChargeRepository

    private val anchor = LocalDate.of(2026, 1, 15)
    private val today = LocalDate.of(2026, 6, 20)
    private val createdAt = anchor.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ToolbillDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SubscriptionRepository(database.subscriptionDao(), database.chargeDao())
        // Pinned to the bundled table. These tests assert how a charge is converted and
        // recorded, not which rates happen to be installed -- and the application under test
        // fetches live rates on start, so reading the global table made them depend on today's
        // USD/INR and fail the moment it moved.
        charges = ChargeRepository(
            database.chargeDao(),
            database.subscriptionDao(),
        ) { FxRates.BUNDLED }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun deleteRestorablyRemovesTheRowAndItsCharges() = runTest {
        insert()
        charges.recordDueCharges(today, "INR")
        assertEquals(6, charges.chargesFor("sub").first().size)

        val deleted = repository.deleteRestorably("sub")

        assertTrue(repository.subscriptions.first().isEmpty())
        assertTrue(charges.chargesFor("sub").first().isEmpty())
        assertEquals(6, deleted!!.charges.size)
    }

    /**
     * Restoring the row without its charges would put back something that looks intact and is
     * quietly empty -- worse than offering no undo at all.
     */
    @Test
    fun restoreBringsBackTheChargeHistoryToo() = runTest {
        insert()
        charges.recordDueCharges(today, "INR")
        val before = charges.chargesFor("sub").first()

        val deleted = repository.deleteRestorably("sub")!!
        repository.restore(deleted)

        assertEquals(1, repository.subscriptions.first().size)
        assertEquals(before, charges.chargesFor("sub").first())
    }

    /**
     * The list sorts on createdAt, so a restore that stamped a fresh one would silently move
     * the row to the top -- undo has to be invisible.
     */
    @Test
    fun restorePreservesTheOriginalCreatedAt() = runTest {
        insert()

        val deleted = repository.deleteRestorably("sub")!!
        repository.restore(deleted)

        assertEquals(createdAt, database.subscriptionDao().createdAt("sub"))
    }

    @Test
    fun deleteRestorablyReturnsNullForARowThatIsNotThere() = runTest {
        assertNull(repository.deleteRestorably("missing"))
    }

    private suspend fun insert() {
        database.subscriptionDao().insertSubscription(
            SubscriptionEntity(
                id = "sub",
                name = "Claude Pro",
                amountMinor = 2_000,
                currency = "USD",
                cycleUnit = CycleUnit.MONTH,
                cycleCount = 1,
                anchorDate = anchor,
                category = Category.AI_TOOLS,
                otherLabel = null,
                isBusiness = true,
                status = SubscriptionStatus.ACTIVE,
                trialEndDate = null,
                resumeDate = null,
                cancelledDate = null,
                notes = null,
                createdAt = createdAt,
            ),
        )
    }
}
