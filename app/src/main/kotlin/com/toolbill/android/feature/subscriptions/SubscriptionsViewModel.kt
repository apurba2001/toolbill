package com.toolbill.android.feature.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.toolbill.android.UserSettings
import com.toolbill.android.core.data.repository.ChargeRepository
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.domain.date.nextChargeDate
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.PricedSubscription
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import com.toolbill.android.core.domain.subscription.pricedIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * A completed row action, and the way back.
 *
 * Every action behind the long-press sheet changes stored data, so every one of them ships its
 * own inverse. The snackbar's Undo calls [undo]; nothing in this app should move a figure
 * without offering the way back.
 */
class Undoable(
    val message: String,
    val undo: suspend () -> Unit,
)

/**
 * The stored subscriptions, priced in the user's home currency.
 *
 * Every screen reads from here rather than deriving its own list, so the Home hero, the All
 * total, the calendar and the widget cannot quote different numbers for the same portfolio.
 */
class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
    private val chargeRepository: ChargeRepository,
    private val settings: UserSettings,
) : ViewModel() {

    val homeCurrency: StateFlow<String> = settings.homeCurrency

    /**
     * Completed row actions, awaiting a snackbar.
     *
     * A [Channel] rather than a StateFlow: each action is an event that must be delivered once.
     * Replaying the last one on every recomposition would re-offer an Undo the user already
     * declined, against data that has since moved on.
     */
    private val _undoables = Channel<Undoable>(Channel.BUFFERED)
    val undoables: Flow<Undoable> = _undoables.receiveAsFlow()

    /**
     * Re-prices whenever the stored rows change *or* the home currency does — switching the
     * home currency in Settings has to move every figure in the app, not just new entries.
     */
    val subscriptions: StateFlow<List<PricedSubscription>> =
        combine(
            repository.subscriptions,
            settings.homeCurrency,
            // A rate refresh moves every figure on every screen without a single stored row
            // changing, so the table is combined in rather than merely read inside the map.
            FxRates.table,
        ) { stored, home, _ ->
            stored.map { it.pricedIn(home) }
        }.stateIn(
            scope = viewModelScope,
            // Outlives a rotation without re-querying, and stops when the app is backgrounded.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Recorded charges for one subscription, newest first. */
    fun chargesFor(subscriptionId: String): Flow<List<Charge>> =
        chargeRepository.chargesFor(subscriptionId)

    fun save(subscription: Subscription) {
        viewModelScope.launch {
            repository.upsert(subscription)
            // A new subscription dated today already has a charge due. Recording it here means
            // its detail screen has history the moment it is opened, rather than after the
            // next cold start.
            runCatching {
                chargeRepository.recordDueCharges(LocalDate.now(), settings.homeCurrency.value)
            }
        }
    }

    // --- Row actions ------------------------------------------------------------------------
    //
    // The charge the sheet acts on is the one the row displays: the next occurrence, counting
    // one falling today. Mark paid and Skip therefore touch the same date, which is the only
    // arrangement a user can predict from looking at the row.

    private fun Subscription.displayedChargeDate(today: LocalDate): LocalDate =
        nextChargeDate(anchorDate, cycle.unit, cycle.count, today.minusDays(1))

    /** Records the displayed charge as paid — the user settled it, early or by hand. */
    fun markPaid(subscription: Subscription, today: LocalDate) {
        val date = subscription.displayedChargeDate(today)
        viewModelScope.launch {
            val previous = chargeRepository.recordCharge(
                subscription = subscription,
                dueDate = date,
                status = ChargeStatus.PAID,
                homeCurrency = settings.homeCurrency.value,
            )
            _undoables.send(
                Undoable("Marked ${subscription.name} as paid") {
                    chargeRepository.revertCharge(subscription.id, date, previous)
                },
            )
        }
    }

    /**
     * Records the displayed charge as skipped.
     *
     * The row stays in the record and exports as a zero — an accountant reconciling the year
     * needs to see that the renewal was accounted for, not find a silent gap in the sequence.
     */
    fun skipCharge(subscription: Subscription, today: LocalDate) {
        val date = subscription.displayedChargeDate(today)
        viewModelScope.launch {
            val previous = chargeRepository.recordCharge(
                subscription = subscription,
                dueDate = date,
                status = ChargeStatus.SKIPPED,
                homeCurrency = settings.homeCurrency.value,
            )
            _undoables.send(
                Undoable("Skipped this charge for ${subscription.name}") {
                    chargeRepository.revertCharge(subscription.id, date, previous)
                },
            )
        }
    }

    /**
     * Pauses an active subscription, or resumes a paused one.
     *
     * One action rather than two: the sheet opens on a row whose state the user can already
     * see, and offering "Pause" on something already paused is an action that cannot do
     * anything.
     */
    fun togglePause(subscription: Subscription) {
        val paused = subscription.status == SubscriptionStatus.PAUSED
        val updated = subscription.copy(
            status = if (paused) SubscriptionStatus.ACTIVE else SubscriptionStatus.PAUSED,
            // Cleared on resume: a resume date describing a pause that has ended is a date that
            // will be read as the next renewal by whatever reads it next.
            resumeDate = null,
        )
        val message = if (paused) "Resumed ${subscription.name}" else "Paused ${subscription.name}"
        viewModelScope.launch {
            repository.upsert(updated)
            _undoables.send(Undoable(message) { repository.upsert(subscription) })
        }
    }

    /**
     * Copies a subscription under a new id.
     *
     * Charges are not copied: they record what was observed on the original, and the copy has
     * been observed for nothing. It starts its own history from the next charge that falls due.
     */
    fun duplicate(subscription: Subscription) {
        val copy = subscription.copy(
            id = UUID.randomUUID().toString(),
            name = "${subscription.name} (copy)",
        )
        viewModelScope.launch {
            repository.upsert(copy)
            _undoables.send(
                Undoable("Duplicated ${subscription.name}") {
                    repository.deleteSubscription(copy.id)
                },
            )
        }
    }

    /** Deletes a subscription, keeping its charges aside so Undo restores the whole record. */
    fun deleteWithUndo(subscription: Subscription) {
        viewModelScope.launch {
            val deleted = repository.deleteRestorably(subscription.id) ?: return@launch
            _undoables.send(
                Undoable("Deleted ${subscription.name}") { repository.restore(deleted) },
            )
        }
    }

    /**
     * Takes back the last import, for as long as its window is open.
     *
     * Deletes only the rows that import wrote, by id. Anything the user has added or edited
     * since is untouched -- an undo that reverted a whole session would be its own disaster.
     */
    fun undoLastImport() {
        val ids = settings.lastImportIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteAll(ids)
            settings.clearImportRecord()
            _undoables.send(
                Undoable("Import undone — ${ids.size} removed") {
                    // Nothing to put back: the rows are gone and the record with them. Re-import
                    // the file rather than pretending a second undo can reverse the first.
                },
            )
        }
    }

    /** Every recorded charge, for the Insights FX figure. */
    val charges: StateFlow<List<Charge>> = chargeRepository.charges.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    companion object {
        fun factory(
            repository: SubscriptionRepository,
            chargeRepository: ChargeRepository,
            settings: UserSettings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SubscriptionsViewModel(repository, chargeRepository, settings) }
        }
    }
}
