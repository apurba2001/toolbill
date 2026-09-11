package com.toolbill.android.core.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Why a backup or restore could not happen.
 *
 * Conditions rather than errors, because every one of them is something the user can act on and
 * none of them is a crash. The plan names four; they are all here.
 */
sealed interface BackupFailure {
    /** No connection. Try again later; nothing was lost. */
    data object Offline : BackupFailure

    /** The Drive account is full. Their storage, their decision. */
    data object QuotaFull : BackupFailure

    /** Access was revoked, or the token expired and could not be refreshed. Sign in again. */
    data object NotAuthorised : BackupFailure

    /**
     * There is no backup in Drive.
     *
     * Expected, not exceptional: a user can delete hidden app data from Drive's own settings
     * without ever opening Toolbill, and a fresh account has never had one.
     */
    data object NoBackup : BackupFailure

    data class Unknown(val message: String) : BackupFailure
}

/** A backup file in Drive, as Drive describes it. */
data class RemoteBackup(
    val fileId: String,
    val sizeBytes: Long,
    val modifiedAt: String,
)

/**
 * Google Drive's `appDataFolder`, over REST v3.
 *
 * A hidden per-app folder: nothing here appears in the user's Drive listing, and no other app
 * can read it. Note that it **does** count against their Drive quota — the app must not claim
 * otherwise, which is why [BackupFailure.QuotaFull] is a first-class outcome rather than a
 * generic failure.
 *
 * The access token is supplied rather than obtained here. Getting one needs a Google Cloud OAuth
 * client and Credential Manager sign-in, which is the single piece of this feature that cannot
 * be built without project credentials — everything below is finished and waiting for it.
 */
class DriveAppData(
    private val accessToken: suspend () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {

    /** Finds the backup, or says there isn't one. */
    suspend fun find(): Result<RemoteBackup> = call { token ->
        val url = "$API/files?spaces=appDataFolder&fields=files(id,size,modifiedTime)" +
            "&q=" + "name='$FILE_NAME'".encoded()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val files = Json.parseToJsonElement(response.body?.string().orEmpty())
                .jsonObject["files"]?.jsonArray.orEmpty()
            val file = files.firstOrNull()?.jsonObject ?: throw NoBackupFound()
            RemoteBackup(
                fileId = file.string("id"),
                sizeBytes = file.string("size").toLongOrNull() ?: 0L,
                modifiedAt = file.string("modifiedTime"),
            )
        }
    }

    suspend fun download(fileId: String): Result<ByteArray> = call { token ->
        val request = Request.Builder()
            .url("$API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            response.body?.bytes() ?: throw HttpFailure(500)
        }
    }

    /**
     * Writes the backup, replacing the existing one in place when there is one.
     *
     * Updating the same file rather than adding another keeps exactly one backup in the folder.
     * A folder that accumulates one file per run is a folder where "restore the backup" stops
     * having an obvious answer.
     */
    suspend fun upload(bytes: ByteArray, existingFileId: String?): Result<String> = call { token ->
        val media = bytes.toRequestBody(GZIP)
        val request = if (existingFileId == null) {
            val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
            Request.Builder()
                .url("$UPLOAD/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $token")
                .post(
                    MultipartBody.Builder().setType("multipart/related".toMediaType())
                        .addPart(metadata.toRequestBody(JSON))
                        .addPart(media)
                        .build(),
                )
                .build()
        } else {
            Request.Builder()
                .url("$UPLOAD/files/$existingFileId?uploadType=media&fields=id")
                .header("Authorization", "Bearer $token")
                .patch(media)
                .build()
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject.string("id")
        }
    }

    /**
     * Runs [block] with a token, translating everything that can go wrong into a condition.
     *
     * Nothing below throws out of this class. A backup that fails is a sentence the user reads,
     * not a crash report.
     */
    private suspend fun <T> call(block: suspend (String) -> T): Result<T> = try {
        val token = accessToken() ?: return Result.failure(BackupException(BackupFailure.NotAuthorised))
        Result.success(block(token))
    } catch (noBackup: NoBackupFound) {
        Result.failure(BackupException(BackupFailure.NoBackup))
    } catch (http: HttpFailure) {
        Result.failure(
            BackupException(
                when (http.code) {
                    401, 403 -> BackupFailure.NotAuthorised
                    // Drive reports a full account as 403 with a reason, and as 507 on upload.
                    507 -> BackupFailure.QuotaFull
                    404 -> BackupFailure.NoBackup
                    else -> BackupFailure.Unknown("Drive returned ${http.code}")
                },
            ),
        )
    } catch (io: java.io.IOException) {
        Result.failure(BackupException(BackupFailure.Offline))
    } catch (t: Throwable) {
        Result.failure(BackupException(BackupFailure.Unknown(t.message ?: "Backup failed")))
    }

    private class HttpFailure(val code: Int) : Exception("HTTP $code")

    private class NoBackupFound : Exception("no backup in appDataFolder")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

        /** One file, one name. See [upload]. */
        const val FILE_NAME = "toolbill-backup.json.gz"

        val JSON = "application/json; charset=utf-8".toMediaType()
        val GZIP = "application/gzip".toMediaType()

        fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

        fun String.encoded(): String = java.net.URLEncoder.encode(this, "UTF-8")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Carries a [BackupFailure] out of a `Result`. */
class BackupException(val failure: BackupFailure) : Exception(failure.toString())
