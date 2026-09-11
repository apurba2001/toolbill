package com.toolbill.android.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One charge Toolbill observed falling due.
 *
 * [homeAmountMinor] is captured at record time and never recomputed. That is the whole point of
 * the table: the burn total is derived from today's rate and moves with it, but what a charge
 * actually cost on the day it was taken is a fact, and a fact has to be stored to survive the
 * next rate change.
 *
 * The id is deterministic — `subscriptionId@dueDate` — so re-running the recorder over a window
 * it has already covered updates those rows instead of duplicating them.
 */
@Entity(
    tableName = "charges",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subscriptionId")]
)
data class ChargeEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val dueDate: LocalDate,
    /** What the service billed, in [currency]'s minor units. */
    val amountMinor: Long,
    val currency: String,
    /** What that came to in [homeCurrency] at the rate captured on [recordedAt]. */
    val homeAmountMinor: Long,
    val homeCurrency: String,
    val status: ChargeStatus,
    /**
     * Units of [homeCurrency] per one unit of [currency], as applied on [recordedAt].
     *
     * Stored rather than re-derived: it is what the CSV export's rate column reports to an
     * accountant, and what drift is measured against. `null` where no rate was in force --
     * a skipped charge, or a currency the rate source does not cover -- and renders as a dash
     * rather than as a number nobody can source.
     */
    val fxRate: BigDecimal?,
    /**
     * True when the user set this row by hand rather than the recorder observing it.
     *
     * The recorder writes only dates it has no row for, so this does not gate anything today.
     * It is what lets a later rate correction sweep recomputed figures across the table while
     * leaving every figure a human entered exactly as they entered it.
     */
    val isUserOverridden: Boolean,
    val recordedAt: Long,
) {
    companion object {
        /** Stable across re-runs, so recording is idempotent. */
        fun idFor(subscriptionId: String, dueDate: LocalDate): String = "$subscriptionId@$dueDate"
    }
}

enum class ChargeStatus {
    PAID,
    SKIPPED
}
