package com.toolbill.android.core.domain.importing

import java.util.Locale

/**
 * A field of a subscription an imported column can fill.
 *
 * [SERVICE] and [AMOUNT] are the only two that cannot be defaulted: everything else has a
 * sensible fallback, and refusing an import for a missing category would send the user back to
 * a spreadsheet to add a column Toolbill is about to guess anyway.
 */
enum class ImportField(
    val label: String,
    val required: Boolean,
    /** Lowercase fragments that suggest a heading belongs to this field. */
    val hints: List<String>,
) {
    SERVICE("Service name", true, listOf("service", "name", "subscription", "product", "tool", "item", "vendor", "description")),
    AMOUNT("Amount", true, listOf("amount", "price", "cost", "value", "charge", "total", "fee")),
    CURRENCY("Currency", false, listOf("currency", "ccy", "curr")),
    CYCLE("Billing cycle", false, listOf("cycle", "billed", "billing", "frequency", "period", "interval", "recurrence", "renews")),
    NEXT_CHARGE("Next charge", false, listOf("next", "renewal", "due", "date", "charge", "start", "anchor")),
    CATEGORY("Category", false, listOf("category", "cat", "type", "group", "tag")),
    BUSINESS("Business or personal", false, listOf("business", "personal", "biz", "work", "expense type", "kind")),
    NOTES("Notes", false, listOf("note", "notes", "comment", "remark", "memo")),
    ;

    companion object {
        val required: List<ImportField> = entries.filter { it.required }
    }
}

/** Which column of the file fills which field. `null` means the field is left to its default. */
data class ColumnMapping(val byField: Map<ImportField, Int?> = emptyMap()) {

    fun columnFor(field: ImportField): Int? = byField[field]

    fun with(field: ImportField, columnIndex: Int?): ColumnMapping {
        // A column can only fill one field. Assigning it elsewhere clears the old claim rather
        // than silently importing the same values into two fields.
        val cleared = byField.filterValues { it != columnIndex || columnIndex == null }
        return ColumnMapping(cleared + (field to columnIndex))
    }

    val missingRequired: List<ImportField>
        get() = ImportField.required.filter { byField[it] == null }

    val isUsable: Boolean get() = missingRequired.isEmpty()
}

/**
 * A first guess at the mapping from the file's own headings.
 *
 * Scored rather than matched outright, because headings are written by people: "Monthly cost",
 * "What I pay", "Renewal date". An exact hit beats a contained one, and each column is claimed
 * by at most one field so a "Next charge date" heading does not fill both the date and the
 * amount because both look for "charge".
 */
fun guessMapping(headers: List<String>): ColumnMapping {
    val normalised = headers.map { it.lowercase(Locale.ROOT).trim() }
    val claimed = mutableSetOf<Int>()
    val result = mutableMapOf<ImportField, Int?>()

    // Strongest signals first, so AMOUNT claims "cost" before NEXT_CHARGE can take it via
    // "charge", and SERVICE claims "name" before CATEGORY takes it via "type".
    val order = listOf(
        ImportField.SERVICE,
        ImportField.AMOUNT,
        ImportField.CURRENCY,
        ImportField.CYCLE,
        ImportField.NEXT_CHARGE,
        ImportField.BUSINESS,
        ImportField.CATEGORY,
        ImportField.NOTES,
    )

    order.forEach { field ->
        val best = normalised.withIndex()
            .filter { (index, _) -> index !in claimed }
            .mapNotNull { (index, header) ->
                val score = field.hints.maxOfOrNull { hint ->
                    when {
                        header == hint -> 100
                        header.split(' ', '_', '-').any { it == hint } -> 80
                        header.contains(hint) -> 50
                        else -> 0
                    }
                } ?: 0
                if (score > 0) index to score else null
            }
            .maxByOrNull { it.second }

        if (best != null) {
            claimed += best.first
            result[field] = best.first
        } else {
            result[field] = null
        }
    }
    return ColumnMapping(result)
}
