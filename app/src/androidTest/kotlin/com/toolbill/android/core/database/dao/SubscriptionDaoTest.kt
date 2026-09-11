package com.toolbill.android.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Instrumented rather than a JVM unit test: an in-memory Room database is still a real SQLite
 * database and needs a real Context, and the behaviour under test here — `ON DELETE CASCADE`
 * and `ON CONFLICT` — is the engine's, not Kotlin's. Stubbing it would test nothing.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionDaoTest {

    private lateinit var database: ToolbillDatabase
    private lateinit var dao: SubscriptionDao
    private lateinit var charges: ChargeDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ToolbillDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.subscriptionDao()
        charges = database.chargeDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetSubscription() = runTest {
        dao.insertSubscription(netflix())

        val loaded = dao.getSubscriptions().first()
        assertEquals(1, loaded.size)
        assertEquals("Netflix", loaded[0].name)
        assertEquals(1599, loaded[0].amountMinor)
    }

    @Test
    fun listIsNewestFirst() = runTest {
        dao.insertSubscription(netflix(id = "older", name = "Older", createdAt = 1_000))
        dao.insertSubscription(netflix(id = "newer", name = "Newer", createdAt = 2_000))

        assertEquals(listOf("Newer", "Older"), dao.getSubscriptions().first().map { it.name })
    }

    /**
     * `createdAt` is the list's sort key. An edit that took the entity's
     * `System.currentTimeMillis()` default would jump the edited row to the top and lose the
     * real creation date, so the upsert has to carry the stored value forward.
     */
    @Test
    fun upsertKeepsTheOriginalCreatedAt() = runTest {
        dao.insertSubscription(netflix(createdAt = 1_000))

        dao.upsert(netflix(amountMinor = 1_899, createdAt = System.currentTimeMillis()))

        val loaded = dao.getSubscription("test-123")
        assertNotNull(loaded)
        assertEquals(1_899, loaded!!.amountMinor)
        assertEquals(1_000, loaded.createdAt)
    }

    /**
     * `OnConflictStrategy.REPLACE` compiles to DELETE + INSERT, and the DELETE fires the
     * charges table's `ON DELETE CASCADE`. Re-saving a subscription must not take its history
     * with it.
     */
    @Test
    fun upsertLeavesChargeHistoryIntact() = runTest {
        dao.insertSubscription(netflix())
        charges.insertCharge(charge())

        dao.upsert(netflix(amountMinor = 1_899))

        assertEquals(1, charges.getChargesForSubscription("test-123").first().size)
    }

    /** Deleting the subscription, by contrast, is exactly when the cascade should fire. */
    @Test
    fun deleteTakesTheChargesWithIt() = runTest {
        dao.insertSubscription(netflix())
        charges.insertCharge(charge())

        dao.deleteSubscription("test-123")

        assertEquals(0, charges.getChargesForSubscription("test-123").first().size)
    }

    /** Insert means insert. Callers that mean "insert or update" have to say so. */
    @Test
    fun insertRejectsADuplicateId() = runTest {
        dao.insertSubscription(netflix())

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.insertSubscription(netflix(name = "Other")) }
        }
    }

    private fun charge(dueDate: LocalDate = LocalDate.of(2026, 8, 1)) = ChargeEntity(
        id = ChargeEntity.idFor("test-123", dueDate),
        subscriptionId = "test-123",
        dueDate = dueDate,
        amountMinor = 1_599,
        currency = "USD",
        homeAmountMinor = 139_263,
        homeCurrency = "INR",
        fxRate = BigDecimal("87.100000"),
        isUserOverridden = false,
        status = ChargeStatus.PAID,
        recordedAt = 1_700_000_000_000,
    )

    private fun netflix(
        id: String = "test-123",
        name: String = "Netflix",
        amountMinor: Long = 1_599,
        createdAt: Long = System.currentTimeMillis(),
    ) = SubscriptionEntity(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = "USD",
        cycleUnit = CycleUnit.MONTH,
        cycleCount = 1,
        anchorDate = LocalDate.of(2026, 8, 1),
        category = Category.ENTERTAINMENT,
        otherLabel = null,
        isBusiness = false,
        status = SubscriptionStatus.ACTIVE,
        trialEndDate = null,
        resumeDate = null,
        cancelledDate = null,
        notes = "Test note",
        createdAt = createdAt,
    )
}
