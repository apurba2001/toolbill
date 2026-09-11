package com.toolbill.android.feature.`import`

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.toolbill.android.core.domain.importing.Csv
import com.toolbill.android.core.domain.importing.Table
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a file the user picked into headers and rows.
 *
 * CSV, TSV and JSON all reduce to the same shape, so the mapping screen that follows does not
 * need to know which one arrived — the columns it offers are the file's own headings either way.
 */
object ImportSource {

    /** Files bigger than this are refused rather than read into memory on the main process. */
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported file"
    }

    /**
     * Reads and parses [uri].
     *
     * Failures come back as a message rather than an exception: every one of them is something
     * the user can act on — pick a different file, export it again — and none is a crash.
     */
    fun read(context: Context, uri: Uri): Result<Table> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: error("That file could not be opened.")

        if (bytes.isEmpty()) error("That file is empty.")
        if (bytes.size > MAX_BYTES) error("That file is too large to import.")

        val text = bytes.toString(Charsets.UTF_8)
        val table = if (looksLikeJson(text)) parseJson(text) else Csv.parse(text)

        if (table.headers.isEmpty()) error("No column headings found in that file.")
        if (table.rows.isEmpty()) error("That file has headings but no rows.")
        table
    }

    private fun looksLikeJson(text: String): Boolean =
        text.trimStart().firstOrNull()?.let { it == '[' || it == '{' } == true

    /**
     * A JSON export as a table.
     *
     * Accepts an array of flat objects, which is what every tracker that offers JSON emits. The
     * union of every object's keys becomes the headings, so a row missing a field lines up with
     * the rest instead of shifting the ones that have it.
     */
    private fun parseJson(text: String): Table {
        val element = json.parseToJsonElement(text)
        val array = element as? JsonArray
            ?: (element as? JsonObject)
                ?.values
                ?.filterIsInstance<JsonArray>()
                ?.firstOrNull()
            ?: error("That JSON is not a list of subscriptions.")

        val objects = array.filterIsInstance<JsonObject>()
        if (objects.isEmpty()) error("That JSON has no subscription entries.")

        val headers = objects.flatMap { it.keys }.distinct()
        val rows = objects.map { obj ->
            headers.map { key ->
                when (val value = obj[key]) {
                    null -> ""
                    is JsonPrimitive -> value.content
                    // A nested object or list has no single cell value; the mapping screen
                    // shows it as blank rather than dumping raw JSON into a service name.
                    else -> ""
                }
            }
        }
        return Table(headers, rows)
    }
}
