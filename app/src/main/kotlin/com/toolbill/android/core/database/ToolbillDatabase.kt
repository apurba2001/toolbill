package com.toolbill.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.toolbill.android.core.database.dao.ChargeDao
import com.toolbill.android.core.database.dao.SubscriptionDao
import com.toolbill.android.core.database.dao.FxRateDao
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.FxRateEntity
import com.toolbill.android.core.database.model.SubscriptionEntity

@Database(
    entities = [
        SubscriptionEntity::class,
        ChargeEntity::class,
        FxRateEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class ToolbillDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun chargeDao(): ChargeDao
    abstract fun fxRateDao(): FxRateDao

    companion object {
        /**
         * Charges gained the home-currency figure captured at record time.
         *
         * Written out rather than dropped and recreated: a charge is the one thing in this app
         * that cannot be recomputed, so a migration that discards the table would destroy the
         * only record of what a subscription actually cost. Existing rows keep their native
         * amount and carry a zero home figure, which the UI reads as "not captured".
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charges ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE charges ADD COLUMN homeAmountMinor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charges ADD COLUMN homeCurrency TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE charges ADD COLUMN recordedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Charges gained the rate applied at record time, and a flag for rows a human set.
         *
         * [ChargeEntity.fxRate] is nullable and existing rows get NULL rather than a
         * back-filled guess: no rate was recorded when they were written, and inventing one
         * would put a number in the export's rate column that nothing ever measured.
         *
         * `isUserOverridden` defaults to 0 -- everything already on file was written by the
         * recorder, because until this version there was no other way for a charge to exist.
         */
        /**
         * A table of published exchange rates.
         *
         * Purely additive: nothing already stored is touched, because the rate a charge was
         * captured at lives on the charge and must never be recomputed from this table. What
         * this adds is the ability to price a *new* charge at the rate in force on the day it
         * fell due, rather than at whatever today happens to be.
         */
        /**
         * Subscriptions gained `updatedAt`, which last-write-wins restore compares on.
         *
         * Back-filled from `createdAt` rather than from the migration's own clock. Stamping
         * every existing row with "now" would make the whole local database look newer than any
         * backup taken before the update, and the first restore after upgrading would quietly
         * discard it.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE subscriptions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("UPDATE subscriptions SET updatedAt = createdAt")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fx_rates` (
                        `capturedOn` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `inrPerUnit` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`capturedOn`, `currency`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charges ADD COLUMN fxRate TEXT")
                db.execSQL(
                    "ALTER TABLE charges ADD COLUMN isUserOverridden INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
