package com.toolbill.android.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.toolbill.android.UserSettings
import com.toolbill.android.core.data.backup.BackupCodec
import com.toolbill.android.core.database.dao.ChargeDao
import com.toolbill.android.core.database.dao.SubscriptionDao
import com.toolbill.android.core.domain.backup.BackupProblem
import com.toolbill.android.core.domain.backup.LocalSnapshot
import com.toolbill.android.core.domain.backup.RestorePlan
import com.toolbill.android.core.domain.backup.planRestore
import com.toolbill.android.core.domain.backup.validateBackup
import com.toolbill.android.core.data.backup.BackupCodec.toEntityOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** A restore that has been worked out and is waiting to be confirmed. */
    val pending: RestorePlan? = null,
)

/**
 * Backup and restore to a file the user picks.
 *
 * The same payload and the same merge the Drive path uses — Drive is a destination, not a
 * different feature. Building it this way round means the dangerous part is finished and proven
 * before any credentials exist, and the user has a real safety net in the meantime.
 */
class BackupViewModel(
    private val subscriptionDao: SubscriptionDao,
    private val chargeDao: ChargeDao,
    private val settings: UserSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun dismiss() {
        _state.value = BackupUiState()
    }

    /** `toolbill-backup-2026-09-11.json.gz` — the date is in the name, not only inside. */
    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "toolbill-backup-$today.json.gz"

    fun backUpTo(context: Context, uri: Uri) {
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val subscriptions = subscriptionDao.getSubscriptionsOnce()
                    val charges = chargeDao.getChargesOnce()
                    // An empty backup quietly replacing a good one is how a fresh install
                    // destroys the thing it was meant to restore from.
                    require(subscriptions.isNotEmpty() || charges.isNotEmpty()) {
                        "There is nothing to back up yet."
                    }
                    val bytes = BackupCodec.encode(
                        BackupCodec.toPayload(subscriptions, charges, settings.homeCurrency.value),
                    )
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("That file could not be written.")
                    subscriptions.size to charges.size
                }
            }
            _state.value = result.fold(
                onSuccess = { (subs, charges) ->
                    BackupUiState(
                        message = "Backed up ${plural(subs, "subscription")} and " +
                            "${plural(charges, "charge")}.",
                    )
                },
                onFailure = {
                    BackupUiState(message = it.message ?: "Backup failed.", isError = true)
                },
            )
        }
    }

    /** Reads a file and works out what restoring it would change. Writes nothing. */
    fun previewRestore(context: Context, uri: Uri) {
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("That file could not be opened.")
                    val payload = BackupCodec.decode(bytes).getOrElse {
                        error("That does not look like a Toolbill backup.")
                    }
                    validateBackup(payload)?.let { problem ->
                        error(
                            when (problem) {
                                BackupProblem.NEWER_VERSION ->
                                    "That backup was written by a newer version of Toolbill."

                                BackupProblem.EMPTY -> "That backup is empty."
                            },
                        )
                    }
                    val local = LocalSnapshot(
                        subscriptionUpdatedAt = subscriptionDao.updatedStamps()
                            .associate { it.id to it.updatedAt },
                        chargeIds = chargeDao.existingChargeIds().toSet(),
                    )
                    planRestore(payload, local)
                }
            }
            _state.value = result.fold(
                onSuccess = { plan ->
                    if (plan.touchesNothing) {
                        BackupUiState(message = plan.summary())
                    } else {
                        BackupUiState(pending = plan)
                    }
                },
                onFailure = {
                    BackupUiState(message = it.message ?: "Restore failed.", isError = true)
                },
            )
        }
    }

    /** Applies a plan the user has seen and confirmed. */
    fun applyPending() {
        val plan = _state.value.pending ?: return
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Subscriptions first: a charge's foreign key needs its row to exist, and
                    // the plan already dropped any charge whose subscription will not.
                    //
                    // Written with the backup's own timestamps rather than today's, so a row
                    // restored from an old file does not come back looking like the newest thing
                    // on the device and win every future merge.
                    val subscriptions = (plan.subscriptionsToAdd + plan.subscriptionsToUpdate)
                        .mapNotNull { it.toEntityOrNull() }
                    subscriptions.forEach { subscriptionDao.upsertSubscription(it) }

                    val charges = plan.chargesToAdd.mapNotNull { it.toEntityOrNull() }
                    if (charges.isNotEmpty()) chargeDao.insertCharges(charges)
                    subscriptions.size to charges.size
                }
            }
            _state.value = result.fold(
                onSuccess = { (subs, charges) ->
                    BackupUiState(
                        message = "Restored ${plural(subs, "subscription")} and " +
                            "${plural(charges, "charge")}.",
                    )
                },
                onFailure = {
                    BackupUiState(message = it.message ?: "Restore failed.", isError = true)
                },
            )
        }
    }

    companion object {
        fun factory(
            subscriptionDao: SubscriptionDao,
            chargeDao: ChargeDao,
            settings: UserSettings,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(subscriptionDao, chargeDao, settings) }
        }
    }
}

/** "1 charge", "2 charges" — a count that reads as a bug undermines the number beside it. */
private fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"
