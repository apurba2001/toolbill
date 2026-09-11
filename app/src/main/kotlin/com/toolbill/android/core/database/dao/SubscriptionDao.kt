package com.toolbill.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.toolbill.android.core.database.model.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/** One row's identity and age. See [SubscriptionDao.updatedStamps]. */
data class SubscriptionStamp(val id: String, val updatedAt: Long)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun getSubscriptions(): Flow<List<SubscriptionEntity>>

    /** A one-shot read, for work that runs once rather than observing — charge recording. */
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    suspend fun getSubscriptionsOnce(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscription(id: String): SubscriptionEntity?

    /**
     * Aborts on a duplicate id rather than replacing.
     *
     * `REPLACE` compiles to DELETE + INSERT, and the DELETE fires the charges table's
     * `ON DELETE CASCADE` — so re-inserting a subscription would silently take its whole
     * charge history with it. Callers that mean "insert or update" want [upsert].
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Query("SELECT createdAt FROM subscriptions WHERE id = :id")
    suspend fun createdAt(id: String): Long?

    /**
     * Every row's id and last-modified stamp, for planning a restore.
     *
     * Ids and timestamps only: a merge decision needs nothing else, and loading whole rows to
     * compare one long each would read the entire table into memory to answer a question about
     * two columns.
     */
    @Query("SELECT id, updatedAt FROM subscriptions")
    suspend fun updatedStamps(): List<SubscriptionStamp>

    @Upsert
    suspend fun upsertSubscription(subscription: SubscriptionEntity)

    /**
     * Insert-or-update that leaves `createdAt` alone.
     *
     * The column is the list's sort key, so letting an edit take the entity's
     * `System.currentTimeMillis()` default would jump the edited row to the top and lose the
     * real creation date for good. Transactional so the read and the write cannot interleave.
     */
    @Transaction
    suspend fun upsert(subscription: SubscriptionEntity) {
        val existing = createdAt(subscription.id)
        upsertSubscription(
            subscription.copy(
                createdAt = existing ?: subscription.createdAt,
                // Stamped here rather than left to callers: every edit in the app goes through
                // this method, and a last-write-wins merge is only as good as the one field it
                // compares. A caller that forgot would make its row permanently lose.
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** One transaction, so an import lands whole or not at all. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)

    @Query("DELETE FROM subscriptions WHERE id IN (:ids)")
    suspend fun deleteSubscriptions(ids: List<String>)
}
