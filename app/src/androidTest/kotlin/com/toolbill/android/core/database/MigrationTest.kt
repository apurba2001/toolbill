package com.toolbill.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A charge is the one thing in this app that cannot be recomputed.
 *
 * Every other figure is derived from the subscriptions table and today's rate, so a migration
 * that mangles it is recoverable. A migration that loses a charge destroys the only record of
 * what a subscription actually cost — which is what the CSV export hands to an accountant.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ToolbillDatabase::class.java,
    )

    /**
     * v3 added the rate applied at record time and the flag for rows a human set.
     *
     * The existing charge must come through with every figure intact, a null rate — no rate was
     * recorded when it was written, and back-filling today's would be a number nothing measured
     * — and `isUserOverridden` false, because until v3 the recorder was the only writer.
     */
    @Test
    fun migrate2To3KeepsChargesAndDefaultsTheNewColumns() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, name, amountMinor, currency, cycleUnit, cycleCount, anchorDate,
                    category, otherLabel, isBusiness, status, trialEndDate, resumeDate,
                    cancelledDate, notes, createdAt
                ) VALUES (
                    'sub', 'Claude Pro', 2000, 'USD', 'MONTH', 1, '2026-01-15',
                    'AI_TOOLS', NULL, 1, 'ACTIVE', NULL, NULL, NULL, NULL, 1750000000000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO charges (
                    id, subscriptionId, dueDate, amountMinor, currency,
                    homeAmountMinor, homeCurrency, status, recordedAt
                ) VALUES (
                    'sub@2026-01-15', 'sub', '2026-01-15', 2000, 'USD',
                    174200, 'INR', 'PAID', 1750000000000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            DB,
            3,
            true,
            ToolbillDatabase.MIGRATION_2_3,
        )

        db.query("SELECT * FROM charges").use { cursor ->
            assertTrue("the charge must survive the migration", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals(
                174_200L,
                cursor.getLong(cursor.getColumnIndexOrThrow("homeAmountMinor")),
            )
            assertEquals("INR", cursor.getString(cursor.getColumnIndexOrThrow("homeCurrency")))
            assertEquals("PAID", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertNull(
                "a rate nothing measured must not be invented",
                cursor.getString(cursor.getColumnIndexOrThrow("fxRate")),
            )
            assertEquals(
                0,
                cursor.getInt(cursor.getColumnIndexOrThrow("isUserOverridden")),
            )
        }
    }

    /**
     * v4 added the published-rate table.
     *
     * Purely additive, and the assertion that matters is the negative one: the rate a charge was
     * captured at lives on the charge and must not be touched. Recomputing it from the new table
     * would rewrite history every time the rates moved.
     */
    @Test
    fun migrate3To4AddsRatesWithoutTouchingCapturedCharges() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, name, amountMinor, currency, cycleUnit, cycleCount, anchorDate,
                    category, otherLabel, isBusiness, status, trialEndDate, resumeDate,
                    cancelledDate, notes, createdAt
                ) VALUES (
                    'sub', 'Claude Pro', 2000, 'USD', 'MONTH', 1, '2026-01-15',
                    'AI_TOOLS', NULL, 1, 'ACTIVE', NULL, NULL, NULL, NULL, 1750000000000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO charges (
                    id, subscriptionId, dueDate, amountMinor, currency,
                    homeAmountMinor, homeCurrency, status, recordedAt, fxRate, isUserOverridden
                ) VALUES (
                    'sub@2026-01-15', 'sub', '2026-01-15', 2000, 'USD',
                    174200, 'INR', 'PAID', 1750000000000, '87.100000', 0
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 4, true, ToolbillDatabase.MIGRATION_3_4)

        db.query("SELECT fxRate, homeAmountMinor FROM charges").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("87.100000", cursor.getString(0))
            assertEquals(174_200L, cursor.getLong(1))
        }
        // The new table exists and starts empty.
        db.query("SELECT COUNT(*) FROM fx_rates").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    /**
     * v5 added `updatedAt`, which last-write-wins restore compares on.
     *
     * The assertion that matters is the back-fill. Stamping every existing row with the
     * migration's own clock would make the whole local database look newer than any backup taken
     * before the update, and the first restore after upgrading would silently discard it. Taking
     * `createdAt` keeps the ordering the data actually had.
     */
    @Test
    fun migrate4To5BackfillsUpdatedAtFromCreatedAt() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, name, amountMinor, currency, cycleUnit, cycleCount, anchorDate,
                    category, otherLabel, isBusiness, status, trialEndDate, resumeDate,
                    cancelledDate, notes, createdAt
                ) VALUES (
                    'sub', 'Claude Pro', 2000, 'USD', 'MONTH', 1, '2026-01-15',
                    'AI_TOOLS', NULL, 1, 'ACTIVE', NULL, NULL, NULL, NULL, 1750000000000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 5, true, ToolbillDatabase.MIGRATION_4_5)

        db.query("SELECT createdAt, updatedAt FROM subscriptions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_750_000_000_000L, cursor.getLong(0))
            assertEquals(
                "updatedAt must inherit createdAt, not the migration's clock",
                1_750_000_000_000L,
                cursor.getLong(1),
            )
        }
    }

    /** The cascade has to survive too — a charge may not outlive the subscription it belongs to. */
    @Test
    fun migrate2To3KeepsTheForeignKeyCascade() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO subscriptions (
                    id, name, amountMinor, currency, cycleUnit, cycleCount, anchorDate,
                    category, otherLabel, isBusiness, status, trialEndDate, resumeDate,
                    cancelledDate, notes, createdAt
                ) VALUES (
                    'sub', 'Vercel', 2000, 'USD', 'MONTH', 1, '2026-01-15',
                    'HOSTING_INFRA', NULL, 1, 'ACTIVE', NULL, NULL, NULL, NULL, 1750000000000
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO charges (
                    id, subscriptionId, dueDate, amountMinor, currency,
                    homeAmountMinor, homeCurrency, status, recordedAt
                ) VALUES (
                    'sub@2026-01-15', 'sub', '2026-01-15', 2000, 'USD',
                    174200, 'INR', 'PAID', 1750000000000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 3, true, ToolbillDatabase.MIGRATION_2_3)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM subscriptions WHERE id = 'sub'")

        db.query("SELECT COUNT(*) FROM charges").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the cascade must still take the charges", 0, cursor.getInt(0))
        }
    }
}
