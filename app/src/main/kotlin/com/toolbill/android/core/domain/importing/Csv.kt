package com.toolbill.android.core.domain.importing

/**
 * A delimited file as headers and rows.
 *
 * Rows are padded and truncated to the header count, so a ragged file — one stray comma in a
 * note, one trailing delimiter — cannot shift every value after it into the wrong column.
 */
data class Table(
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    val isEmpty: Boolean get() = headers.isEmpty() || rows.isEmpty()

    /** The value under [header] for [row], or empty when the column is absent. */
    fun value(row: List<String>, columnIndex: Int): String =
        row.getOrNull(columnIndex)?.trim().orEmpty()
}

/**
 * Reads CSV and TSV.
 *
 * RFC 4180 quoting, because a spreadsheet exports it: a service named `Adobe, Inc.` arrives
 * quoted, and a note containing a line break arrives as a quoted field spanning two lines. A
 * parser that split on the delimiter would turn either into a row whose every later column is
 * off by one — silently, in a file the user is about to import thirty rows from.
 */
object Csv {

    /** Written as an escape, not the character: a raw one in source is invisible and fragile. */
    private const val BYTE_ORDER_MARK = "\uFEFF"

    /** Delimiters worth guessing between, in the order they are tried. */
    private val DELIMITERS = listOf(',', '\t', ';', '|')

    /**
     * The delimiter that yields the most consistent column count across the first few lines.
     *
     * Guessed rather than assumed: a European export is semicolon-delimited because the comma
     * is its decimal separator, and a file pasted out of a terminal is usually tabs.
     */
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(20).toList()
        if (sample.isEmpty()) return ','

        return DELIMITERS.maxByOrNull { delimiter ->
            val counts = sample.map { line -> countOutsideQuotes(line, delimiter) }
            val most = counts.maxOrNull() ?: 0
            if (most == 0) {
                // Never chosen over a delimiter that actually appears.
                -1
            } else {
                // Consistency first, then how many columns it produces: a file with six equal
                // commas per line beats one with an occasional stray semicolon.
                counts.count { it == most } * 100 + most
            }
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        line.forEach { c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    /**
     * Parses [text], taking the first non-blank row as headers.
     *
     * A leading byte-order mark is stripped: Excel writes one, and without this the first
     * header arrives as `\uFEFFService` and matches nothing the guesser looks for.
     */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): Table {
        val records = parseRecords(text.removePrefix(BYTE_ORDER_MARK), delimiter)
            .filterNot { record -> record.all { it.isBlank() } }
        if (records.isEmpty()) return Table(emptyList(), emptyList())

        val headers = records.first().map { it.trim() }
        val width = headers.size
        val rows = records.drop(1).map { record ->
            // Padded and truncated so every row lines up with the headers.
            List(width) { index -> record.getOrNull(index)?.trim().orEmpty() }
        }
        return Table(headers, rows)
    }

    /**
     * The state machine.
     *
     * A doubled quote inside a quoted field is a literal quote — `"The ""Pro"" plan"` — which is
     * how a spreadsheet escapes them and the only reason this cannot be a split on a regex.
     */
    private fun parseRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(record)
            record = mutableListOf()
        }

        while (index < text.length) {
            val c = text[index]
            when {
                inQuotes && c == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    // Swallow the second half of a CRLF rather than emitting a blank record.
                    if (c == '\r' && text.getOrNull(index + 1) == '\n') index++
                    endRecord()
                }

                else -> field.append(c)
            }
            index++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
        return records
    }
}
