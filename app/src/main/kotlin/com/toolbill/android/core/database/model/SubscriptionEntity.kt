package com.toolbill.android.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val cycleUnit: CycleUnit,
    val cycleCount: Int,
    val anchorDate: LocalDate,
    val category: Category,
    val otherLabel: String?,
    val isBusiness: Boolean,
    val status: SubscriptionStatus,
    val trialEndDate: LocalDate?,
    val resumeDate: LocalDate?,
    val cancelledDate: LocalDate?,
    val notes: String?,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * When this row last changed, in epoch millis.
     *
     * Drives last-write-wins on restore. Without it a restore can only overwrite -- it would
     * have no way to tell an edit made on this phone from the older copy of the same row sitting
     * in the backup, and would silently throw one of them away.
     */
    val updatedAt: Long = System.currentTimeMillis(),
)
