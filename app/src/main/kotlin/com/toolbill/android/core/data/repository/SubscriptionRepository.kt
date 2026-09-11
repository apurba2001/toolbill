package com.toolbill.android.core.data.repository

import com.toolbill.android.core.database.dao.ChargeDao
import com.toolbill.android.core.database.dao.SubscriptionDao
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A deleted subscription, and every charge the delete cascaded away with it.
 *
 * Opaque on purpose: callers hold one only to hand it back to [SubscriptionRepository.restore].
 * It carries the stored `createdAt`, which the domain object does not -- restoring through the
 * ordinary write path would stamp a fresh one and jump the row to the top of a list sorted by it.
 */
class DeletedSubscription internal constructor(
    internal val entity: SubscriptionEntity,
    internal val charges: List<ChargeEntity>,
)

class SubscriptionRepository(
    /** Exposed for backup, which reads and writes rows wholesale rather than as domain objects. */
    val subscriptionDao: SubscriptionDao,
    private val chargeDao: ChargeDao,
) {
    val subscriptions: Flow<List<Subscription>> = subscriptionDao.getSubscriptions().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getSubscription(id: String): Subscription? =
        subscriptionDao.getSubscription(id)?.toDomain()

    /** One-shot, for work that runs once rather than observing -- reminder scheduling. */
    suspend fun subscriptionsOnce(): List<Subscription> =
        subscriptionDao.getSubscriptionsOnce().map { it.toDomain() }

    /**
     * The one write path for the add / edit sheet, which does not know whether it is creating
     * or editing until it looks. [SubscriptionDao.upsert] keeps the original `createdAt`.
     */
    suspend fun upsert(subscription: Subscription) {
        subscriptionDao.upsert(subscription.toEntity())
    }

    /**
     * Inserts many rows in one transaction.
     *
     * All or nothing: an import that half-landed would leave the user with a burn figure built
     * from part of a file and no way to tell which part.
     */
    suspend fun insertAll(subscriptions: List<Subscription>) {
        subscriptionDao.insertAll(subscriptions.map { it.toEntity() })
    }

    /** Removes many rows, for taking a whole import back. */
    suspend fun deleteAll(ids: List<String>) {
        ids.chunked(500).forEach { subscriptionDao.deleteSubscriptions(it) }
    }

    suspend fun deleteSubscription(id: String) {
        subscriptionDao.deleteSubscription(id)
    }

    /**
     * Deletes [id], returning everything needed to put it back exactly as it was.
     *
     * The charges are read *before* the delete, because the foreign key cascades them away the
     * moment it runs. Undoing a delete that restored the row but not its payment history would
     * be worse than offering no undo at all -- the row would come back visibly intact and
     * quietly empty.
     */
    suspend fun deleteRestorably(id: String): DeletedSubscription? {
        val entity = subscriptionDao.getSubscription(id) ?: return null
        val charges = chargeDao.getChargesForSubscriptionOnce(id)
        subscriptionDao.deleteSubscription(id)
        return DeletedSubscription(entity, charges)
    }

    /** Puts back what [deleteRestorably] captured -- the row first, then its charges. */
    suspend fun restore(deleted: DeletedSubscription) {
        subscriptionDao.upsertSubscription(deleted.entity)
        if (deleted.charges.isNotEmpty()) chargeDao.insertCharges(deleted.charges)
    }

    private fun SubscriptionEntity.toDomain() = Subscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        cycle = BillingCycle(cycleUnit, cycleCount),
        anchorDate = anchorDate,
        category = category,
        otherLabel = otherLabel,
        isBusiness = isBusiness,
        status = status,
        trialEndDate = trialEndDate,
        resumeDate = resumeDate,
        cancelledDate = cancelledDate,
        notes = notes
    )

    // createdAt is deliberately absent: it belongs to the row, not to the domain object, and
    // the DAO's upsert carries the stored value forward. See SubscriptionDao.upsert.
    private fun Subscription.toEntity() = SubscriptionEntity(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        cycleUnit = cycle.unit,
        cycleCount = cycle.count,
        anchorDate = anchorDate,
        category = category,
        otherLabel = otherLabel,
        isBusiness = isBusiness,
        status = status,
        trialEndDate = trialEndDate,
        resumeDate = resumeDate,
        cancelledDate = cancelledDate,
        notes = notes
    )
}
