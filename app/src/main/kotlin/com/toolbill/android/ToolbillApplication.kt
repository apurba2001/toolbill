package com.toolbill.android

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.toolbill.android.core.data.repository.ChargeRepository
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.billing.EarlyAccessEntitlements
import com.toolbill.android.core.billing.EntitlementSource
import com.toolbill.android.core.billing.ProFeature
import com.toolbill.android.core.data.fx.FxRefreshWorker
import com.toolbill.android.core.data.fx.FxRepository
import com.toolbill.android.core.database.ToolbillDatabase
import com.toolbill.android.core.reminder.ReminderManager
import com.toolbill.android.core.reminder.ReminderNotifier
import com.toolbill.android.core.reminder.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

class ToolbillApplication : Application() {

    lateinit var database: ToolbillDatabase
        private set

    lateinit var subscriptionRepository: SubscriptionRepository
        private set

    lateinit var chargeRepository: ChargeRepository
        private set

    lateinit var settings: UserSettings
        private set

    /** Owned here so the alarm receiver, the worker and the UI all drive the same scheduler. */
    lateinit var reminderManager: ReminderManager
        private set

    lateinit var fxRepository: FxRepository
        private set

    /**
     * One source of truth for what the user is entitled to.
     *
     * Swapping this for a RevenueCat-backed implementation is the whole of what billing needs
     * to change; every gate in the app already reads through it.
     */
    lateinit var entitlements: EntitlementSource
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        settings = UserSettings(this)

        database = Room.databaseBuilder(
            this,
            ToolbillDatabase::class.java,
            "toolbill_database"
        )
            .addMigrations(
                ToolbillDatabase.MIGRATION_1_2,
                ToolbillDatabase.MIGRATION_2_3,
                ToolbillDatabase.MIGRATION_3_4,
                ToolbillDatabase.MIGRATION_4_5,
            )
            .build()

        subscriptionRepository =
            SubscriptionRepository(database.subscriptionDao(), database.chargeDao())
        fxRepository = FxRepository(database.fxRateDao())
        entitlements = EarlyAccessEntitlements()
        chargeRepository = ChargeRepository(
            database.chargeDao(),
            database.subscriptionDao(),
            fxRepository::tableFor,
        )
        reminderManager = ReminderManager(
            this,
            subscriptionRepository,
            settings,
        ) { entitlements.entitlement.value.allows(ProFeature.RENEWAL_REMINDERS) }

        // Created up front rather than at first post: a channel the user cannot see in system
        // settings is one they cannot tune, and they may well look before the first renewal.
        ReminderNotifier.ensureChannel(this)

        // Catch up on charges that fell due while the app was closed. Off the main thread and
        // never awaited: a slow first query must not hold up the first frame, and the screens
        // observe the table, so rows appear as soon as they land.
        appScope.launch {
            // Rates first: charges record the figure they were converted at, and recording a
            // month of catch-up against the bundled April table would freeze stale numbers into
            // the one place in this app that is never recomputed.
            runCatching { fxRepository.installCached() }
                .onFailure { Log.w("Toolbill", "Cached rates unavailable", it) }

            runCatching {
                chargeRepository.recordDueCharges(LocalDate.now(), settings.homeCurrency.value)
            }.onFailure { Log.w("Toolbill", "Charge catch-up failed", it) }

            // Then go and see whether anything newer is available.
            runCatching { fxRepository.refresh() }
                .onFailure { Log.w("Toolbill", "Rate refresh failed", it) }
        }

        // Re-plan reminders whenever the portfolio or the lead time moves, and once on start.
        //
        // Driven off the data rather than called after each write: a row can change through the
        // add sheet, a row action, an undo, or a restore, and wiring a refresh into each of
        // those is five chances to miss one. The flow sees all of them. collectLatest coalesces
        // a burst -- a bulk import is one re-plan, not thirty.
        appScope.launch {
            combine(
                subscriptionRepository.subscriptions,
                settings.leadTimeDaysFlow,
            ) { _, _ -> Unit }
                .collectLatest {
                    runCatching { reminderManager.refresh() }
                        .onFailure { Log.w("Toolbill", "Reminder refresh failed", it) }
                }
        }

        ReminderWorker.ensureScheduled(this)
        FxRefreshWorker.ensureScheduled(this)
    }
}
