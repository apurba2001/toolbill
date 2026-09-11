package com.toolbill.android.feature.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/**
 * Puts a generated CSV somewhere the user chose.
 *
 * Two destinations, and neither of them is a server. Saving goes through the storage picker,
 * which is also how a file reaches Google Drive, Dropbox or a USB stick without this app
 * knowing anything about any of them. Sharing hands the file to the share sheet. Toolbill
 * never uploads it anywhere itself, which is a claim the export screen makes out loud.
 */
object ExportWriter {

    /** `toolbill-2026-04-01-to-2027-03-31.csv` — the period is in the name, not just the file. */
    fun suggestedName(range: ClosedRange<LocalDate>): String =
        "toolbill-${range.start}-to-${range.endInclusive}.csv"

    /** Writes [csv] to a location the storage picker returned. */
    fun writeTo(context: Context, uri: Uri, csv: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open $uri for writing")
    }

    /**
     * Stages the file in cache and returns a share intent for it.
     *
     * Cache rather than external storage: the file exists to be handed to one app through a
     * grant that expires, and leaving a copy of someone's spend history in a world-readable
     * directory afterwards is not a trade this app should make on their behalf.
     */
    fun shareIntent(
        context: Context,
        csv: String,
        range: ClosedRange<LocalDate>,
    ): Result<Intent> = runCatching {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, suggestedName(range)).apply {
            writeText(csv, Charsets.UTF_8)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.export", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
