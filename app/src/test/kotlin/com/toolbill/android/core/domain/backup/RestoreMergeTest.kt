package com.toolbill.android.core.domain.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The restore merge is the one path in this app that can destroy data the user cannot get back.
 *
 * The plan's risk register rates it Severe and unrecoverable, and the reason it is dangerous is
 * that it looks routine: a restore that quietly drops half someone's portfolio still reports
 * success. Every rule below exists to make a specific way of losing data impossible.
 */
class RestoreMergeTest {

    private fun sub(id: String, updatedAt: Long, name: String = id) = BackupSubscription(
        id = id,
        name = name,
        amountMinor = 2_000,
        currency = "USD",
        cycleUnit = "MONTH",
        cycleCount = 1,
        anchorDate = "2026-01-15",
        category = "AI_TOOLS",
        status = "ACTIVE",
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    private fun charge(id: String, subscriptionId: String = "a") = BackupCharge(
        id = id,
        subscriptionId = subscriptionId,
        dueDate = "2026-01-15",
        amountMinor = 2_000,
        currency = "USD",
        homeAmountMinor = 174_200,
        homeCurrency = "INR",
        fxRate = "87.100000",
        status = "PAID",
        recordedAt = 1_000,
    )

    private fun payload(
        subscriptions: List<BackupSubscription> = emptyList(),
        charges: List<BackupCharge> = emptyList(),
    ) = BackupPayload(createdAt = 2_000, homeCurrency = "INR", subscriptions = subscriptions, charges = charges)

    private fun local(
        subs: Map<String, Long> = emptyMap(),
        chargeIds: Set<String> = emptySet(),
    ) = LocalSnapshot(subs, chargeIds)

    // --- the rule that prevents data loss ---

    /**
     * The one that matters most. A backup is a snapshot of one moment, not a statement about
     * what should exist now. Anything added since the backup was taken must survive it.
     */
    @Test
    fun `a subscription that exists only on this device is never removed`() {
        val plan = planRestore(
            payload(listOf(sub("a", updatedAt = 5_000))),
            local(mapOf("a" to 1_000, "added-since" to 9_000)),
        )

        assertEquals(listOf("added-since"), plan.subscriptionsOnlyLocal)
        // Nothing in the plan can delete it: there is no removal list at all.
        assertTrue(RestorePlan::class.java.declaredFields.none { it.name.contains("Delete", true) })
    }

    @Test
    fun `a subscription only in the backup is added`() {
        val plan = planRestore(payload(listOf(sub("a", 5_000))), local())

        assertEquals(listOf("a"), plan.subscriptionsToAdd.map { it.id })
        assertTrue(plan.subscriptionsToUpdate.isEmpty())
    }

    @Test
    fun `the newer copy wins`() {
        val plan = planRestore(
            payload(listOf(sub("a", updatedAt = 9_000))),
            local(mapOf("a" to 1_000)),
        )

        assertEquals(listOf("a"), plan.subscriptionsToUpdate.map { it.id })
        assertTrue(plan.subscriptionsKeptLocal.isEmpty())
    }

    @Test
    fun `a local edit made after the backup is not overwritten by it`() {
        val plan = planRestore(
            payload(listOf(sub("a", updatedAt = 1_000))),
            local(mapOf("a" to 9_000)),
        )

        assertEquals(listOf("a"), plan.subscriptionsKeptLocal)
        assertTrue(plan.subscriptionsToUpdate.isEmpty())
    }

    /** Indistinguishable by the only evidence there is, so the device in hand keeps its copy. */
    @Test
    fun `a tie keeps the local copy`() {
        val plan = planRestore(
            payload(listOf(sub("a", updatedAt = 5_000))),
            local(mapOf("a" to 5_000)),
        )

        assertEquals(listOf("a"), plan.subscriptionsKeptLocal)
        assertTrue(plan.subscriptionsToUpdate.isEmpty())
    }

    // --- charges ---

    /** A charge records something that happened. There is no newer version of it. */
    @Test
    fun `a charge already on file is never rewritten`() {
        val plan = planRestore(
            payload(listOf(sub("a", 5_000)), listOf(charge("a@2026-01-15"))),
            local(mapOf("a" to 9_000), setOf("a@2026-01-15")),
        )

        assertTrue(plan.chargesToAdd.isEmpty())
        assertEquals(1, plan.chargesAlreadyPresent)
    }

    @Test
    fun `a charge missing locally is restored`() {
        val plan = planRestore(
            payload(listOf(sub("a", 5_000)), listOf(charge("a@2026-01-15"))),
            local(mapOf("a" to 9_000)),
        )

        assertEquals(listOf("a@2026-01-15"), plan.chargesToAdd.map { it.id })
    }

    /** The foreign key would refuse it, and a charge belonging to nothing records nothing. */
    @Test
    fun `a charge whose subscription will not exist is dropped`() {
        val plan = planRestore(
            payload(charges = listOf(charge("ghost@2026-01-15", subscriptionId = "ghost"))),
            local(),
        )

        assertTrue(plan.chargesToAdd.isEmpty())
    }

    @Test
    fun `a charge belonging to a subscription this device already has is kept`() {
        val plan = planRestore(
            payload(charges = listOf(charge("a@2026-01-15", subscriptionId = "a"))),
            local(mapOf("a" to 9_000)),
        )

        assertEquals(1, plan.chargesToAdd.size)
    }

    // --- restoring onto an empty device, and onto an identical one ---

    @Test
    fun `restoring onto a fresh install adds everything`() {
        val plan = planRestore(
            payload(listOf(sub("a", 5_000), sub("b", 5_000)), listOf(charge("a@2026-01-15"))),
            local(),
        )

        assertEquals(2, plan.subscriptionsToAdd.size)
        assertEquals(1, plan.chargesToAdd.size)
        assertTrue(!plan.touchesNothing)
    }

    @Test
    fun `restoring a backup this device is already ahead of changes nothing`() {
        val plan = planRestore(
            payload(listOf(sub("a", 1_000)), listOf(charge("a@2026-01-15"))),
            local(mapOf("a" to 9_000), setOf("a@2026-01-15")),
        )

        assertTrue(plan.touchesNothing)
        assertEquals("Everything in this backup is already on this device.", plan.summary())
    }

    @Test
    fun `the summary names what will change`() {
        val plan = planRestore(
            payload(
                listOf(sub("new", 5_000), sub("older", 9_000)),
                listOf(charge("older@2026-01-15", subscriptionId = "older")),
            ),
            local(mapOf("older" to 1_000)),
        )

        assertEquals("1 to add, 1 to update, 1 charge to restore", plan.summary())
    }

    // --- validation ---

    /** Reading a newer file as far as it parses is data loss wearing a success message. */
    @Test
    fun `a backup from a newer version is refused`() {
        val newer = payload(listOf(sub("a", 5_000)))
            .copy(version = BackupPayload.SUPPORTED_VERSION + 1)

        assertEquals(BackupProblem.NEWER_VERSION, validateBackup(newer))
    }

    @Test
    fun `an empty backup is refused rather than applied`() {
        assertEquals(BackupProblem.EMPTY, validateBackup(payload()))
    }

    @Test
    fun `a good backup validates`() {
        assertNull(validateBackup(payload(listOf(sub("a", 5_000)))))
    }
}
