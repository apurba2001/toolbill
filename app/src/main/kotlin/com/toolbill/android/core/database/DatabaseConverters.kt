package com.toolbill.android.core.database

import androidx.room.TypeConverter
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.math.BigDecimal
import java.time.LocalDate

class DatabaseConverters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    @TypeConverter
    fun fromCycleUnit(value: CycleUnit): String {
        return value.name
    }

    @TypeConverter
    fun toCycleUnit(value: String): CycleUnit {
        return CycleUnit.valueOf(value)
    }

    @TypeConverter
    fun fromCategory(value: Category): String {
        return value.name
    }

    @TypeConverter
    fun toCategory(value: String): Category {
        return Category.valueOf(value)
    }

    @TypeConverter
    fun fromSubscriptionStatus(value: SubscriptionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toSubscriptionStatus(value: String): SubscriptionStatus {
        return SubscriptionStatus.valueOf(value)
    }
    
    @TypeConverter
    fun fromChargeStatus(value: ChargeStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toChargeStatus(value: String): ChargeStatus {
        return ChargeStatus.valueOf(value)
    }

    // TEXT, not REAL. A rate is money arithmetic, and the whole app stores money as exact
    // integers precisely so a binary float cannot round a figure nobody can then reconcile.
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}
