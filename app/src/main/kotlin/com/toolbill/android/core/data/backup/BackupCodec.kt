package com.toolbill.android.core.data.backup

import com.toolbill.android.core.database.model.ChargeEntity
import com.toolbill.android.core.database.model.ChargeStatus
import com.toolbill.android.core.database.model.SubscriptionEntity
import com.toolbill.android.core.domain.backup.BackupCharge
import com.toolbill.android.core.domain.backup.BackupPayload
import com.toolbill.android.core.domain.backup.BackupSubscription
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Turns the database into a backup file and back.
 *
 * Gzipped JSON. At thirty-five subscriptions and a few hundred charges the whole thing is well
 * under 100KB compressed, which is why there is no delta sync here and should not be one: the
 * plan is explicit that a full blob is the right shape at this size, and a delta protocol is a
 * second way for a restore to go wrong.
 */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(payload: BackupPayload): ByteArray {
        val bytes = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(bytes) }
        }.toByteArray()
    }

    /**
     * Reads a backup, accepting plain JSON as well as gzip.
     *
     * A file the user fished out of Drive by hand and un-gzipped should still restore; refusing
     * it on a container detail when the contents are perfectly readable helps nobody.
     */
    fun decode(bytes: ByteArray): Result<BackupPayload> = runCatching {
        val text = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        json.decodeFromString(BackupPayload.serializer(), text)
    }

    // --- entity <-> payload -------------------------------------------------------------------
    //
    // Written out field by field rather than reflected over. The file has to keep meaning the
    // same thing after the entities move, and an automatic mapping is exactly what silently
    // stops being true when a column is renamed.

    fun toPayload(
        subscriptions: List<SubscriptionEntity>,
        charges: List<ChargeEntity>,
        homeCurrency: String,
        now: Long = System.currentTimeMillis(),
    ) = BackupPayload(
        createdAt = now,
        homeCurrency = homeCurrency,
        subscriptions = subscriptions.map { it.toBackup() },
        charges = charges.map { it.toBackup() },
    )

    private fun SubscriptionEntity.toBackup() = BackupSubscription(
        id = id,
        name = name,
        amountMinor = amountMinor,
        currency = currency,
        cycleUnit = cycleUnit.name,
        cycleCount = cycleCount,
        anchorDate = anchorDate.toString(),
        category = category.name,
        otherLabel = otherLabel,
        isBusiness = isBusiness,
        status = status.name,
        trialEndDate = trialEndDate?.toString(),
        resumeDate = resumeDate?.toString(),
        cancelledDate = cancelledDate?.toString(),
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ChargeEntity.toBackup() = BackupCharge(
        id = id,
        subscriptionId = subscriptionId,
        dueDate = dueDate.toString(),
        amountMinor = amountMinor,
        currency = currency,
        homeAmountMinor = homeAmountMinor,
        homeCurrency = homeCurrency,
        fxRate = fxRate?.toPlainString(),
        status = status.name,
        isUserOverridden = isUserOverridden,
        recordedAt = recordedAt,
    )

    /**
     * A backed-up subscription as an entity, or null when it cannot be read.
     *
     * An unknown enum or an unparseable date skips that one row rather than failing the whole
     * restore. A file written by a build that added a category this one does not know should
     * still bring back the other thirty-four subscriptions.
     */
    fun BackupSubscription.toEntityOrNull(): SubscriptionEntity? = runCatching {
        SubscriptionEntity(
            id = id,
            name = name,
            amountMinor = amountMinor,
            currency = currency,
            cycleUnit = CycleUnit.valueOf(cycleUnit),
            cycleCount = cycleCount,
            anchorDate = LocalDate.parse(anchorDate),
            category = Category.valueOf(category),
            otherLabel = otherLabel,
            isBusiness = isBusiness,
            status = SubscriptionStatus.valueOf(status),
            trialEndDate = trialEndDate?.let(LocalDate::parse),
            resumeDate = resumeDate?.let(LocalDate::parse),
            cancelledDate = cancelledDate?.let(LocalDate::parse),
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }.getOrNull()

    fun BackupCharge.toEntityOrNull(): ChargeEntity? = runCatching {
        ChargeEntity(
            id = id,
            subscriptionId = subscriptionId,
            dueDate = LocalDate.parse(dueDate),
            amountMinor = amountMinor,
            currency = currency,
            homeAmountMinor = homeAmountMinor,
            homeCurrency = homeCurrency,
            fxRate = fxRate?.let(::BigDecimal),
            isUserOverridden = isUserOverridden,
            status = ChargeStatus.valueOf(status),
            recordedAt = recordedAt,
        )
    }.getOrNull()
}
