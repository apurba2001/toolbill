package com.toolbill.android.core.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backup file's shape.
 *
 * Deliberately its own set of flat types rather than the Room entities. The file outlives the
 * code that wrote it — someone restores on a new phone months later, onto a newer build — so its
 * format has to be free to stay still while the database moves. Every field is a primitive for
 * the same reason: no enum ordinals, no platform date types, nothing whose meaning can drift.
 */
@Serializable
data class BackupPayload(
    /** Bumped when the shape changes incompatibly. See [SUPPORTED_VERSION]. */
    @SerialName("version") val version: Int = SUPPORTED_VERSION,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("homeCurrency") val homeCurrency: String,
    @SerialName("subscriptions") val subscriptions: List<BackupSubscription> = emptyList(),
    @SerialName("charges") val charges: List<BackupCharge> = emptyList(),
) {
    companion object {
        /**
         * The format this build writes and can read.
         *
         * A file from a *newer* version is refused rather than partially read: silently dropping
         * fields a later Toolbill added would turn a restore into data loss disguised as success.
         */
        const val SUPPORTED_VERSION = 1
    }
}

@Serializable
data class BackupSubscription(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycleUnit: String,
    val cycleCount: Int,
    /** ISO-8601. Text, so a reader in another timezone cannot shift it. */
    val anchorDate: String,
    val category: String,
    val otherLabel: String? = null,
    val isBusiness: Boolean = true,
    val status: String,
    val trialEndDate: String? = null,
    val resumeDate: String? = null,
    val cancelledDate: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupCharge(
    val id: String,
    val subscriptionId: String,
    val dueDate: String,
    val amountMinor: Long,
    val currency: String,
    val homeAmountMinor: Long,
    val homeCurrency: String,
    /** Plain decimal string, never a float — see the rest of this app's money handling. */
    val fxRate: String? = null,
    val status: String,
    val isUserOverridden: Boolean = false,
    val recordedAt: Long,
)
