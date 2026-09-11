package com.toolbill.android.core.data.repository

import com.toolbill.android.core.database.dao.ChargeDao
import com.toolbill.android.core.database.dao.SubscriptionDao
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.chargeDatesInRange
import com.toolbill.android.core.domain.money.FxRateTable
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads charges, and records the ones that have fallen due.
 *
 * Recording only ever runs forward from the day Toolbill started tracking a subscription. A
 * charge from before that was never observed, and inventing one — at today's rate, at today's
 * price — would put a number in the payment history that nothing ever measured. That is the
 * same rule the detail screen's FX chart states: nothing before the first capture is drawn.
 */
class ChargeRepository(
    /** Exposed for backup; see SubscriptionRepository. */
    val chargeDao: ChargeDao,
    private val subscriptionDao: SubscriptionDao,
    /**
     * The rates in force on a given date.
     *
     * Injected rather than reached for, so a charge recorded three weeks late is priced at what
     * it cost on the day it fell due rather than at today's rate. Defaults to the rates
     * currently installed, which is all a fresh install has and all the tests need.
     */
    private val rateTableFor: suspend (LocalDate) -> FxRateTable = { FxRates.snapshot },
) {
    fun chargesFor(subscriptionId: String): Flow<List<Charge>> =
        chargeDao.getChargesForSubscription(subscriptionId).map { rows -> rows.map { it.toDomain() } }

    val charges: Flow<List<Charge>> =
        chargeDao.getCharges().map { rows -> rows.map { it.toDomain() } }

    /**
     * Materialises every charge that has fallen due since tracking began, up to and including
     * [today], and returns how many rows it wrote.
     *
     * Idempotent: the row id is derived from the subscription and the due date, so running this
     * on every launch re-writes the same rows rather than accumulating duplicates. Cheap enough
     * to do exactly that — the window is bounded by how long the app has been installed.
     */
    suspend fun recordDueCharges(today: LocalDate, homeCurrency: String): Int {
        val now = System.currentTimeMillis()
        // Only what is missing. Re-writing a row that already exists would recompute its home
        // figure at today's rate -- destroying the capture the whole table exists to preserve --
        // and would silently undo a charge the user had marked skipped.
        val alreadyRecorded = chargeDao.existingChargeIds().toSet()
        // One lookup per distinct date rather than per charge: a month of catch-up across thirty
        // subscriptions lands on a handful of dates, not nine hundred.
        val tables = mutableMapOf<LocalDate, FxRateTable>()
        val rows = mutableListOf<ChargeEntity>()

        subscriptionDao.getSubscriptionsOnce().forEach { entity ->
            dueDates(entity, today)
                .filterNot { ChargeEntity.idFor(entity.id, it) in alreadyRecorded }
                .forEach { dueDate ->
                    val table = tables.getOrPut(dueDate) { rateTableFor(dueDate) }
                    val home = table.convertMinor(entity.amountMinor, entity.currency, homeCurrency)
                    rows += ChargeEntity(
                        id = ChargeEntity.idFor(entity.id, dueDate),
                        subscriptionId = entity.id,
                        dueDate = dueDate,
                        amountMinor = entity.amountMinor,
                        currency = entity.currency,
                        // An unconvertible currency records the charge without a home figure
                        // rather than guessing one. The row still exists; only the conversion
                        // is missing.
                        homeAmountMinor = home ?: 0L,
                        homeCurrency = if (home == null) "" else homeCurrency,
                        // Stored beside the figure it produced, so the export can state the
                        // rate this charge was actually converted at rather than today's.
                        fxRate = table.rateFor(entity.currency, homeCurrency),
                        isUserOverridden = false,
                        status = ChargeStatus.PAID,
                        recordedAt = now,
                    )
                }
        }
        if (rows.isNotEmpty()) chargeDao.insertCharges(rows)
        return rows.size
    }

    /**
     * Records one charge at [dueDate] with [status], and returns whatever occupied that slot
     * before -- `null` when nothing did.
     *
     * The caller keeps that return value and hands it back to [revertCharge] to undo. Marking a
     * charge paid or skipped is the one place the user overrides what the recorder observed, so
     * the previous row is the only thing standing between an accidental tap and a lost figure.
     */
    suspend fun recordCharge(
        subscription: Subscription,
        dueDate: LocalDate,
        status: ChargeStatus,
        homeCurrency: String,
    ): ChargeEntity? {
        val id = ChargeEntity.idFor(subscription.id, dueDate)
        val previous = chargeDao.getCharge(id)
        val skipped = status == ChargeStatus.SKIPPED
        val table = rateTableFor(dueDate)
        val home = table.convertMinor(subscription.amountMinor, subscription.currency, homeCurrency)
        chargeDao.insertCharge(
            ChargeEntity(
                id = id,
                subscriptionId = subscription.id,
                dueDate = dueDate,
                // The contracted amount, even when skipped: what the row was for is a
                // different fact from what it came to, and only the second one is zero.
                amountMinor = subscription.amountMinor,
                currency = subscription.currency,
                // A skipped charge cost nothing, and that is a known figure rather than a
                // missing one -- it keeps the home currency and exports as a zero row.
                homeAmountMinor = if (skipped) 0L else home ?: 0L,
                homeCurrency = when {
                    skipped -> homeCurrency
                    home == null -> ""
                    else -> homeCurrency
                },
                // No rate was applied to a charge that never happened.
                fxRate = if (skipped) null else table.rateFor(subscription.currency, homeCurrency),
                isUserOverridden = true,
                status = status,
                recordedAt = System.currentTimeMillis(),
            ),
        )
        return previous
    }

    /** Puts back what [recordCharge] displaced, or removes the row when it displaced nothing. */
    suspend fun revertCharge(subscriptionId: String, dueDate: LocalDate, previous: ChargeEntity?) {
        if (previous == null) chargeDao.deleteCharge(ChargeEntity.idFor(subscriptionId, dueDate))
        else chargeDao.insertCharge(previous)
    }

    /** One-shot read of a subscription's charges, captured before a delete cascades them away. */
    suspend fun chargesForOnce(subscriptionId: String): List<ChargeEntity> =
        chargeDao.getChargesForSubscriptionOnce(subscriptionId)

    /** Restores charges captured by [chargesForOnce], after their subscription is back. */
    suspend fun restoreCharges(rows: List<ChargeEntity>) {
        if (rows.isNotEmpty()) chargeDao.insertCharges(rows)
    }

    /**
     * The charge dates worth recording for one subscription.
     *
     * Starts at the later of the anchor and the day the row was created — Toolbill cannot have
     * observed a charge before it knew the subscription existed. Ends at [today], or at the
     * cancellation date, since a cancelled plan stops billing. Paused plans are skipped
     * outright: nothing records when the pause began, so any end date would be a guess.
     */
    private fun dueDates(entity: SubscriptionEntity, today: LocalDate): List<LocalDate> {
        if (entity.status == SubscriptionStatus.PAUSED) return emptyList()

        val trackedFrom = maxOf(entity.anchorDate, entity.createdAt.toLocalDate())
        val trackedTo = when (entity.status) {
            SubscriptionStatus.CANCELLED -> entity.cancelledDate?.let { minOf(it, today) } ?: today
            else -> today
        }
        return chargeDatesInRange(
            anchor = entity.anchorDate,
            unit = entity.cycleUnit,
            count = entity.cycleCount,
            from = trackedFrom,
            to = trackedTo,
        )
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun ChargeEntity.toDomain() = Charge(
        id = id,
        subscriptionId = subscriptionId,
        dueDate = dueDate,
        amountMinor = amountMinor,
        currency = currency,
        homeAmountMinor = homeAmountMinor,
        homeCurrency = homeCurrency,
        fxRate = fxRate,
        status = status,
        isUserOverridden = isUserOverridden,
    )
}
