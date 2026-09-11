package com.toolbill.android.core.data.backup

import com.toolbill.android.core.data.backup.BackupCodec.toEntityOrNull
import com.toolbill.android.core.database.dao.ChargeDao
import com.toolbill.android.core.database.dao.SubscriptionDao
import com.toolbill.android.core.domain.backup.BackupPayload
import com.toolbill.android.core.domain.backup.BackupProblem
import com.toolbill.android.core.domain.backup.LocalSnapshot
import com.toolbill.android.core.domain.backup.RestorePlan
import com.toolbill.android.core.domain.backup.planRestore
import com.toolbill.android.core.domain.backup.validateBackup

/** What a completed backup wrote. */
data class BackupSummary(
    val subscriptions: Int,
    val charges: Int,
    val sizeBytes: Int,
)

/** A restore that has been worked out but not applied. */
data class PendingRestore(
    val payload: BackupPayload,
    val plan: RestorePlan,
)

/**
 * Backup and restore against the user's own Drive.
 *
 * Restore is deliberately two steps: work out what would change, show it, then apply it only if
 * the user says so. The plan's risk register calls this the one path that can destroy data
 * unrecoverably, and the specific way that happens is a single tap that looks routine.
 */
class BackupRepository(
    private val subscriptionDao: SubscriptionDao,
    private val chargeDao: ChargeDao,
    private val drive: DriveAppData,
) {

    /** Writes the whole database to Drive, replacing the previous file. */
    suspend fun backUp(homeCurrency: String): Result<BackupSummary> {
        val subscriptions = subscriptionDao.getSubscriptionsOnce()
        val charges = chargeDao.getChargesOnce()

        // Refused rather than written. An empty backup silently replacing a good one is how a
        // fresh install on a new phone destroys the thing it was meant to restore from.
        if (subscriptions.isEmpty() && charges.isEmpty()) {
            return Result.failure(BackupException(BackupFailure.Unknown("Nothing to back up yet.")))
        }

        val bytes = BackupCodec.encode(
            BackupCodec.toPayload(subscriptions, charges, homeCurrency),
        )
        val existing = drive.find().getOrNull()?.fileId
        return drive.upload(bytes, existing).map {
            BackupSummary(subscriptions.size, charges.size, bytes.size)
        }
    }

    /**
     * Fetches the backup and works out what restoring it would change. Writes nothing.
     */
    suspend fun previewRestore(): Result<PendingRestore> {
        val remote = drive.find().getOrElse { return Result.failure(it) }
        val bytes = drive.download(remote.fileId).getOrElse { return Result.failure(it) }

        val payload = BackupCodec.decode(bytes).getOrElse {
            return Result.failure(
                BackupException(BackupFailure.Unknown("That backup could not be read.")),
            )
        }

        validateBackup(payload)?.let { problem ->
            return Result.failure(
                BackupException(
                    when (problem) {
                        BackupProblem.NEWER_VERSION -> BackupFailure.Unknown(
                            "That backup was written by a newer version of Toolbill. " +
                                "Update the app and try again.",
                        )

                        BackupProblem.EMPTY -> BackupFailure.NoBackup
                    },
                ),
            )
        }

        val local = LocalSnapshot(
            subscriptionUpdatedAt = subscriptionDao.updatedStamps().associate { it.id to it.updatedAt },
            chargeIds = chargeDao.existingChargeIds().toSet(),
        )
        return Result.success(PendingRestore(payload, planRestore(payload, local)))
    }

    /**
     * Applies a plan the user has seen.
     *
     * Subscriptions first, then charges: a charge's foreign key needs its subscription to exist,
     * and the plan already dropped any charge whose subscription will not.
     *
     * Written with the backup's own `createdAt` and `updatedAt` rather than today's, so a row
     * restored from an old file does not come back looking like the newest thing on the device
     * and win every future merge against a phone that actually has newer data.
     */
    suspend fun applyRestore(plan: RestorePlan): Result<Int> = runCatching {
        val subscriptions = (plan.subscriptionsToAdd + plan.subscriptionsToUpdate)
            .mapNotNull { it.toEntityOrNull() }
        subscriptions.forEach { subscriptionDao.upsertSubscription(it) }

        val charges = plan.chargesToAdd.mapNotNull { it.toEntityOrNull() }
        if (charges.isNotEmpty()) chargeDao.insertCharges(charges)

        subscriptions.size + charges.size
    }
}
