package com.toolbill.android.core.domain.subscription

import com.toolbill.android.core.database.model.ChargeStatus
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A charge Toolbill observed falling due.
 *
 * [homeAmountMinor] is what it cost on the day, captured then and never recomputed — unlike
 * every other home-currency figure in the app, which is derived from today's rate. A charge
 * with no captured figure ([captured] false) predates the capture and is shown as a dash
 * rather than converted at a rate that was not in force when it was taken.
 */
data class Charge(
    val id: String,
    val subscriptionId: String,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val homeAmountMinor: Long,
    val homeCurrency: String,
    /** The rate applied when this row was written, or `null` where none was. Renders as "—". */
    val fxRate: BigDecimal? = null,
    val status: ChargeStatus = ChargeStatus.PAID,
    /** True when a person set this row, rather than the recorder observing the schedule. */
    val isUserOverridden: Boolean = false,
) {
    val captured: Boolean get() = homeCurrency.isNotEmpty()

    /** A skipped charge is in the record and out of every total. */
    val countsTowardSpend: Boolean get() = status == ChargeStatus.PAID
}
