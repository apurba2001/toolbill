package com.toolbill.android.core.data.backup

import com.toolbill.android.core.data.backup.BackupCodec.toEntityOrNull
import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A backup is read back months later, on a different phone, by a different build. Anything this
 * round trip loses is lost for good, because by then the original is gone.
 */
class BackupCodecTest {

    private val subscription = SubscriptionEntity(
        id = "sub",
        name = "Adobe, Inc. \"CC\"",
        amountMinor = 179_900,
        currency = "INR",
        cycleUnit = CycleUnit.YEAR,
        cycleCount = 1,
        anchorDate = LocalDate.of(2026, 12, 15),
        category = Category.DESIGN,
        otherLabel = null,
        isBusiness = true,
        status = SubscriptionStatus.ACTIVE,
        trialEndDate = LocalDate.of(2026, 1, 1),
        resumeDate = null,
        cancelledDate = null,
        notes = "line one\nline two",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_800_000_000_000,
    )

    private val charge = ChargeEntity(
        id = "sub@2026-12-15",
        subscriptionId = "sub",
        dueDate = LocalDate.of(2026, 12, 15),
        amountMinor = 179_900,
        currency = "INR",
        homeAmountMinor = 179_900,
        homeCurrency = "INR",
        fxRate = BigDecimal("95.419847"),
        isUserOverridden = true,
        status = ChargeStatus.SKIPPED,
        recordedAt = 1_750_000_000_000,
    )

    @Test
    fun `a subscription survives the round trip exactly`() {
        val payload = BackupCodec.toPayload(listOf(subscription), emptyList(), "INR")
        val decoded = BackupCodec.decode(BackupCodec.encode(payload)).getOrThrow()

        assertEquals(subscription, decoded.subscriptions.single().toEntityOrNull())
    }

    /** The rate and the override flag are the two fields nothing can recompute. */
    @Test
    fun `a charge survives with its rate and override flag intact`() {
        val payload = BackupCodec.toPayload(emptyList(), listOf(charge), "INR")
        val decoded = BackupCodec.decode(BackupCodec.encode(payload)).getOrThrow()
        val restored = decoded.charges.single().toEntityOrNull()!!

        assertEquals(charge, restored)
        assertEquals(BigDecimal("95.419847"), restored.fxRate)
        assertTrue(restored.isUserOverridden)
        assertEquals(ChargeStatus.SKIPPED, restored.status)
    }

    @Test
    fun `quotes and newlines in text survive`() {
        val payload = BackupCodec.toPayload(listOf(subscription), emptyList(), "INR")
        val decoded = BackupCodec.decode(BackupCodec.encode(payload)).getOrThrow()
        val restored = decoded.subscriptions.single().toEntityOrNull()!!

        assertEquals("Adobe, Inc. \"CC\"", restored.name)
        assertEquals("line one\nline two", restored.notes)
    }

    @Test
    fun `the file is gzipped`() {
        val bytes = BackupCodec.encode(
            BackupCodec.toPayload(listOf(subscription), listOf(charge), "INR"),
        )
        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(0x8b.toByte(), bytes[1])
    }

    /** Someone who un-gzipped the file by hand should still be able to restore it. */
    @Test
    fun `plain json is accepted as well as gzip`() {
        val json = """
            {"version":1,"createdAt":1,"homeCurrency":"INR","subscriptions":[],"charges":[]}
        """.trimIndent()
        val decoded = BackupCodec.decode(json.toByteArray()).getOrThrow()

        assertEquals("INR", decoded.homeCurrency)
    }

    @Test
    fun `unreadable bytes fail rather than throw`() {
        assertTrue(BackupCodec.decode(byteArrayOf(1, 2, 3)).isFailure)
    }

    /**
     * A file written by a build that added a category this one does not know should still bring
     * back everything else, rather than failing the whole restore on one row.
     */
    @Test
    fun `an unreadable row is skipped, not fatal`() {
        val payload = BackupCodec.toPayload(listOf(subscription), emptyList(), "INR")
        val broken = payload.subscriptions.single().copy(category = "A_CATEGORY_FROM_THE_FUTURE")

        assertNull(broken.toEntityOrNull())
        assertEquals(subscription, payload.subscriptions.single().toEntityOrNull())
    }

    @Test
    fun `the payload records the home currency it was written under`() {
        val payload = BackupCodec.toPayload(listOf(subscription), emptyList(), "GBP")
        assertEquals("GBP", BackupCodec.decode(BackupCodec.encode(payload)).getOrThrow().homeCurrency)
    }
}
