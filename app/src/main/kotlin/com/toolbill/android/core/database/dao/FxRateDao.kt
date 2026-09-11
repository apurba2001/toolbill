package com.toolbill.android.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.toolbill.android.core.database.model.FxRateEntity
import java.time.LocalDate

@Dao
interface FxRateDao {

    @Upsert
    suspend fun upsertAll(rates: List<FxRateEntity>)

    /** The most recent published date on file, or null before the first successful refresh. */
    @Query("SELECT MAX(capturedOn) FROM fx_rates")
    suspend fun latestCapturedOn(): LocalDate?

    @Query("SELECT * FROM fx_rates WHERE capturedOn = :date")
    suspend fun ratesOn(date: LocalDate): List<FxRateEntity>

    /**
     * The rates in force on [date] — that date's publication, or the most recent one before it.
     *
     * The ECB publishes on business days, so a charge falling on a Sunday is priced at Friday's
     * rate, which is the rate a card would actually have been charged at.
     */
    @Query(
        """
        SELECT * FROM fx_rates
        WHERE capturedOn = (
            SELECT MAX(capturedOn) FROM fx_rates WHERE capturedOn <= :date
        )
        """,
    )
    suspend fun ratesInForceOn(date: LocalDate): List<FxRateEntity>

    /** Keeps the table bounded; two years covers any financial year the export can ask for. */
    @Query("DELETE FROM fx_rates WHERE capturedOn < :before")
    suspend fun deleteBefore(before: LocalDate)
}
