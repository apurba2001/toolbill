package com.toolbill.android.core.domain.backup

/**
 * What a restore would do, worked out before anything is written.
 *
 * The plan's risk register rates "Drive restore destroys data" as unrecoverable trust loss, and
 * the reason is that a restore looks like a safe, routine action right up until it isn't. So the
 * decision is computed as data first, shown to the user, and only then applied.
 */
data class RestorePlan(
    /** In the backup, absent locally. Straightforwardly added. */
    val subscriptionsToAdd: List<BackupSubscription> = emptyList(),
    /** In both, and the backup's copy is newer. */
    val subscriptionsToUpdate: List<BackupSubscription> = emptyList(),
    /** In both, and the local copy is newer or the same age. Left alone. */
    val subscriptionsKeptLocal: List<String> = emptyList(),
    /** Local only. **Never removed** — see [planRestore]. */
    val subscriptionsOnlyLocal: List<String> = emptyList(),
    val chargesToAdd: List<BackupCharge> = emptyList(),
    /** Already on file. A charge records something observed, so it is never rewritten. */
    val chargesAlreadyPresent: Int = 0,
) {
    val touchesNothing: Boolean
        get() = subscriptionsToAdd.isEmpty() &&
            subscriptionsToUpdate.isEmpty() &&
            chargesToAdd.isEmpty()

    /** A one-line summary for the confirmation the user actually reads. */
    fun summary(): String {
        if (touchesNothing) return "Everything in this backup is already on this device."
        val parts = mutableListOf<String>()
        if (subscriptionsToAdd.isNotEmpty()) parts += "${subscriptionsToAdd.size} to add"
        if (subscriptionsToUpdate.isNotEmpty()) parts += "${subscriptionsToUpdate.size} to update"
        if (chargesToAdd.isNotEmpty()) {
            parts += if (chargesToAdd.size == 1) {
                "1 charge to restore"
            } else {
                "${chargesToAdd.size} charges to restore"
            }
        }
        return parts.joinToString(", ")
    }
}

/** The local state a restore is compared against. Ids to their last-modified stamp. */
data class LocalSnapshot(
    val subscriptionUpdatedAt: Map<String, Long>,
    val chargeIds: Set<String>,
)

/**
 * Works out what restoring [payload] onto [local] would change.
 *
 * Three rules, and the third is the one that matters:
 *
 * 1. A row only in the backup is added.
 * 2. A row in both is taken from whichever copy is newer by `updatedAt`. **A tie keeps local** —
 *    the two are indistinguishable by the only evidence available, and the device in the user's
 *    hand is the one they have been editing.
 * 3. A row only on this device is **kept**. A backup is a snapshot of one moment, not a
 *    statement about what should exist now; deleting local rows because an older file does not
 *    mention them is precisely the path that destroys someone's data while reporting success.
 *
 * Charges are additive and never rewritten. Each one records a charge that was observed, at a
 * rate captured that day — there is no version of it that is "newer", only the one that happened.
 */
fun planRestore(payload: BackupPayload, local: LocalSnapshot): RestorePlan {
    val toAdd = mutableListOf<BackupSubscription>()
    val toUpdate = mutableListOf<BackupSubscription>()
    val keptLocal = mutableListOf<String>()

    payload.subscriptions.forEach { incoming ->
        val localUpdatedAt = local.subscriptionUpdatedAt[incoming.id]
        when {
            localUpdatedAt == null -> toAdd += incoming
            incoming.updatedAt > localUpdatedAt -> toUpdate += incoming
            else -> keptLocal += incoming.id
        }
    }

    val backupIds = payload.subscriptions.map { it.id }.toSet()
    val onlyLocal = local.subscriptionUpdatedAt.keys.filterNot { it in backupIds }

    // A charge whose subscription will not exist after the restore is dropped: the foreign key
    // would refuse it, and a charge with nothing to belong to is not a record of anything.
    val survivingSubscriptions = backupIds + local.subscriptionUpdatedAt.keys
    val incomingCharges = payload.charges.filter { it.subscriptionId in survivingSubscriptions }
    val newCharges = incomingCharges.filterNot { it.id in local.chargeIds }

    return RestorePlan(
        subscriptionsToAdd = toAdd,
        subscriptionsToUpdate = toUpdate,
        subscriptionsKeptLocal = keptLocal,
        subscriptionsOnlyLocal = onlyLocal,
        chargesToAdd = newCharges,
        chargesAlreadyPresent = incomingCharges.size - newCharges.size,
    )
}

/** Why a backup file could not be used. Conditions the UI states, not errors it throws. */
enum class BackupProblem {
    /** Written by a newer Toolbill than this one. */
    NEWER_VERSION,

    /** Parsed, but holds nothing. */
    EMPTY,
}

/**
 * Checks a parsed payload before anything is planned from it.
 *
 * A file from a newer version is refused outright rather than read as far as it can be: dropping
 * fields a later Toolbill added would be data loss wearing the costume of a successful restore.
 */
fun validateBackup(payload: BackupPayload): BackupProblem? = when {
    payload.version > BackupPayload.SUPPORTED_VERSION -> BackupProblem.NEWER_VERSION
    payload.subscriptions.isEmpty() && payload.charges.isEmpty() -> BackupProblem.EMPTY
    else -> null
}
