package com.toolbill.android.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One currency's rate on one published date.
 *
 * Kept as rows rather than a single overwritten blob so the table accumulates a real history:
 * a charge recorded weeks late can still be priced at the rate that was in force on the day it
 * fell due, rather than at whatever today happens to be. That is the difference between FX
 * drift the app can evidence and a number it is guessing at.
 *
 * The last fetched table is also what a cold start prices with before its first refresh, so a
 * device that is offline all week is not thrown back onto the rates Toolbill shipped with.
 */
@Entity(tableName = "fx_rates", primaryKeys = ["capturedOn", "currency"])
data class FxRateEntity(
    val capturedOn: LocalDate,
    val currency: String,
    /** INR per one unit of [currency] — the base [com.toolbill.android.core.domain.money.FxRateTable] holds. */
    val inrPerUnit: BigDecimal,
    val fetchedAt: Long,
)
