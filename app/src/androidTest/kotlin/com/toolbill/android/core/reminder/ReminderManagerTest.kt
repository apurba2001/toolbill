package com.toolbill.android.core.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.toolbill.android.UserSettings
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The scheduling decision is pinned on the JVM by ReminderPlanTest. What is left to check on a
 * device is the part that needs one: that a refresh records what it delivered, and that a
 * second refresh over the same state stays silent.
 *
 * A reminder delivered twice is the fastest way to teach someone to swipe the app's
 * notifications away without reading them.
 */
@RunWith(AndroidJUnit4::class)
class ReminderManagerTest {

    @get:Rule
    val notifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var context: Context
    private lateinit var database: ToolbillDatabase
    private lateinit var settings: UserSettings
    private lateinit var manager: ReminderManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ToolbillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = UserSettings(context)
        clearReminderState()
        manager = ReminderManager(
            context,
            SubscriptionRepository(database.subscriptionDao(), database.chargeDao()),
            settings,
        )
    }

    @After
    fun teardown() {
        clearReminderState()
        database.close()
    }

    private fun clearReminderState() {
        settings.deliveredReminderKeys = emptySet()
        settings.lastReminderFiredAt = 0L
        settings.setLeadTimeDays(3)
    }

    @Test
    fun deliversAReminderWhoseMomentHasPassedAndRecordsIt() = runTest {
        // Renews tomorrow. With a three-day lead the reminder was owed two days ago.
        val charge = LocalDate.now().plusDays(1)
        insert(anchor = charge)

        manager.refresh()

        assertEquals(setOf("sub@$charge"), settings.deliveredReminderKeys)
        assertTrue("a delivery must be recorded", settings.lastReminderFiredAt > 0L)
    }

    @Test
    fun doesNotDeliverTheSameReminderTwice() = runTest {
        val charge = LocalDate.now().plusDays(1)
        insert(anchor = charge)

        manager.refresh()
        val firstFire = settings.lastReminderFiredAt
        manager.refresh()

        assertEquals(setOf("sub@$charge"), settings.deliveredReminderKeys)
        assertEquals("nothing was delivered, so nothing fired", firstFire, settings.lastReminderFiredAt)
    }

    /** The moment is still ahead, so the alarm holds it — nothing goes out yet. */
    @Test
    fun holdsAReminderWhoseMomentIsStillAhead() = runTest {
        insert(anchor = LocalDate.now().plusDays(30))

        manager.refresh()

        assertTrue(settings.deliveredReminderKeys.isEmpty())
        assertEquals(0L, settings.lastReminderFiredAt)
    }

    @Test
    fun ignoresPausedSubscriptions() = runTest {
        insert(anchor = LocalDate.now().plusDays(1), status = SubscriptionStatus.PAUSED)

        manager.refresh()

        assertTrue(settings.deliveredReminderKeys.isEmpty())
        assertEquals(0L, settings.lastReminderFiredAt)
    }

    /**
     * Lengthening the lead time can make a reminder owed that was not owed a moment ago, which
     * is why the Settings control re-plans rather than only affecting the next renewal.
     */
    @Test
    fun aLongerLeadTimeSurfacesAReminderThatWasNotYetOwed() = runTest {
        val charge = LocalDate.now().plusDays(5)
        insert(anchor = charge)

        manager.refresh()
        assertTrue("five days out, a three-day lead owes nothing", settings.deliveredReminderKeys.isEmpty())

        settings.setLeadTimeDays(7)
        manager.refresh()

        assertEquals(setOf("sub@$charge"), settings.deliveredReminderKeys)
    }

    /**
     * The alarm is the primary delivery path, so its existence is worth asserting directly
     * rather than inferred from a reminder having gone out.
     */
    @Test
    fun armsTheAlarmWhenSomethingIsStillAhead() = runTest {
        insert(anchor = LocalDate.now().plusDays(30))

        manager.refresh()

        assertTrue("an alarm should be held", alarmIsArmed())
    }

    /** Nothing to warn about means nothing holding a wakeup on the device. */
    @Test
    fun cancelsTheAlarmWhenNothingIsDue() = runTest {
        insert(anchor = LocalDate.now().plusDays(30))
        manager.refresh()
        assertTrue(alarmIsArmed())

        database.subscriptionDao().deleteSubscription("sub")
        manager.refresh()

        assertTrue("the alarm should be released", !alarmIsArmed())
    }

    /** FLAG_NO_CREATE reports whether one exists without creating it. */
    private fun alarmIsArmed(): Boolean = PendingIntent.getBroadcast(
        context,
        4001,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    private suspend fun insert(
        anchor: LocalDate,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    ) {
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
                status = status,
                trialEndDate = null,
                resumeDate = null,
                cancelledDate = null,
                notes = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
