package com.toolbill.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.toolbill.android.core.database.model.ChargeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeDao {
    @Query("SELECT * FROM charges WHERE subscriptionId = :subscriptionId ORDER BY dueDate DESC")
    fun getChargesForSubscription(subscriptionId: String): Flow<List<ChargeEntity>>

    @Query("SELECT * FROM charges ORDER BY dueDate DESC")
    fun getCharges(): Flow<List<ChargeEntity>>

    @Query("SELECT dueDate FROM charges WHERE subscriptionId = :subscriptionId ORDER BY dueDate DESC LIMIT 1")
    suspend fun latestDueDate(subscriptionId: String): java.time.LocalDate?

    /** One row by id. Row actions read this before they overwrite, so Undo has something to put back. */
    @Query("SELECT * FROM charges WHERE id = :id")
    suspend fun getCharge(id: String): ChargeEntity?

    /**
     * Every charge of one subscription, one-shot.
     *
     * Deleting a subscription cascades its charges away, so Undo has to have captured them
     * first -- restoring the row alone would silently drop its whole payment history.
     */
    @Query("SELECT * FROM charges WHERE subscriptionId = :subscriptionId")
    suspend fun getChargesForSubscriptionOnce(subscriptionId: String): List<ChargeEntity>

    /**
     * The ids already on file.
     *
     * The recorder writes only what is missing, so a charge the user marked skipped -- or one
     * captured at a rate that has since moved -- is never overwritten by a later run.
     */
    @Query("SELECT id FROM charges")
    suspend fun existingChargeIds(): List<String>

    // Upsert rather than REPLACE: REPLACE is a DELETE + INSERT, which is a cascade waiting to
    // matter the first time anything hangs off a charge.
    @Upsert
    suspend fun insertCharge(charge: ChargeEntity)

    @Upsert
    suspend fun insertCharges(charges: List<ChargeEntity>)

    /** Every charge, one-shot, for writing a backup. */
    @Query("SELECT * FROM charges")
    suspend fun getChargesOnce(): List<ChargeEntity>

    @Query("DELETE FROM charges WHERE id = :id")
    suspend fun deleteCharge(id: String)
}
