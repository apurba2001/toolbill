package com.toolbill.android.core.domain.importing

import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.catalogueCategoryFor
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * One row of the file, read as far as it could be.
 *
 * A row that cannot become a subscription still appears, carrying why. The alternative — quietly
 * dropping it — means a user imports thirty rows, gets twenty-seven, and has no way to find out
 * which three are missing or what was wrong with them.
 */
data class ImportCandidate(
    /** 1-based, counting the header as row 1, so it matches what a spreadsheet shows. */
    val rowNumber: Int,
    val subscription: Subscription?,
    val problems: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    /** The id of an existing subscription this looks like a repeat of. */
    val duplicateOf: String? = null,
    val include: Boolean = true,
) {
    val isUsable: Boolean get() = subscription != null && problems.isEmpty()

    /** Usable, but worth a look before it lands: a guess was made, or it may be a duplicate. */
    val needsAttention: Boolean get() = isUsable && (notes.isNotEmpty() || duplicateOf != null)
}

/** Everything the parse produced, plus what it had to decide for itself. */
data class ImportPreview(
    val candidates: List<ImportCandidate>,
    val dayFirstDates: Boolean,
) {
    val ready: List<ImportCandidate> get() = candidates.filter { it.isUsable && !it.needsAttention }
    val attention: List<ImportCandidate> get() = candidates.filter { it.needsAttention }
    val unusable: List<ImportCandidate> get() = candidates.filterNot { it.isUsable }
    val selected: List<ImportCandidate> get() = candidates.filter { it.include && it.isUsable }
}

/**
 * Turns mapped rows into subscriptions.
 *
 * [existing] is compared against by name and amount so a second import of the same spreadsheet
 * flags repeats rather than silently doubling someone's burn figure — which is the single worst
 * thing an import can do, because the total still looks plausible.
 */
fun parseImport(
    table: Table,
    mapping: ColumnMapping,
    homeCurrency: String,
    today: LocalDate,
    existing: List<Subscription> = emptyList(),
): ImportPreview {
    val dateColumn = mapping.columnFor(ImportField.NEXT_CHARGE)
    val dayFirst = detectDayFirst(table, dateColumn)

    val candidates = table.rows.mapIndexed { index, row ->
        parseRow(
            rowNumber = index + 2,
            row = row,
            table = table,
            mapping = mapping,
            homeCurrency = homeCurrency,
            today = today,
            existing = existing,
            dayFirst = dayFirst,
        )
    }
    return ImportPreview(candidates, dayFirst)
}

private fun parseRow(
    rowNumber: Int,
    row: List<String>,
    table: Table,
    mapping: ColumnMapping,
    homeCurrency: String,
    today: LocalDate,
    existing: List<Subscription>,
    dayFirst: Boolean,
): ImportCandidate {
    fun cell(field: ImportField): String =
        mapping.columnFor(field)?.let { table.value(row, it) }.orEmpty()

    val problems = mutableListOf<String>()
    val notes = mutableListOf<String>()

    val name = cell(ImportField.SERVICE)
    if (name.isBlank()) problems += "No service name"

    val rawAmount = cell(ImportField.AMOUNT)
    val currency = parseCurrency(cell(ImportField.CURRENCY))
        ?: parseCurrency(rawAmount)
        ?: homeCurrency.also {
            if (rawAmount.isNotBlank()) notes += "Currency assumed $homeCurrency"
        }

    val amountMinor = parseAmountMinor(rawAmount, currency)
    when {
        rawAmount.isBlank() -> problems += "No amount"
        amountMinor == null -> problems += "Amount not understood: \"$rawAmount\""
        amountMinor <= 0L -> problems += "Amount is not positive"
    }

    if (!FxRates.isSupported(currency)) {
        problems += "No exchange rate for $currency"
    }

    val cycleText = cell(ImportField.CYCLE)
    val cycle = parseCycle(cycleText)
    if (cycle == null && cycleText.isNotBlank()) {
        notes += "Cycle not understood, assumed monthly"
    } else if (cycleText.isBlank()) {
        notes += "Cycle assumed monthly"
    }

    val dateText = cell(ImportField.NEXT_CHARGE)
    val anchor = parseDate(dateText, dayFirst)
    if (anchor == null && dateText.isNotBlank()) {
        notes += "Date not understood, first charge set to today"
    } else if (dateText.isBlank()) {
        notes += "No date, first charge set to today"
    }

    // A file with no category column is the common case, and dropping every row into Other
    // makes the Insights breakdown useless on the very portfolio it was meant to explain. The
    // add sheet already infers from the name -- "Auto, from name" -- so the import does too.
    val categoryCell = cell(ImportField.CATEGORY)
    val category = if (categoryCell.isBlank()) {
        catalogueCategoryFor(name) ?: Category.OTHER
    } else {
        parseCategory(categoryCell)
    }
    val otherLabel = categoryCell.takeIf { it.isNotBlank() && category == Category.OTHER }

    if (problems.isNotEmpty()) {
        return ImportCandidate(rowNumber, null, problems, notes, include = false)
    }

    val subscription = Subscription(
        id = UUID.randomUUID().toString(),
        name = name,
        amountMinor = amountMinor!!,
        currency = currency,
        cycle = cycle ?: BillingCycle.MONTHLY,
        anchorDate = anchor ?: today,
        category = category,
        otherLabel = otherLabel,
        isBusiness = parseBusiness(cell(ImportField.BUSINESS)) ?: true,
        status = SubscriptionStatus.ACTIVE,
        notes = cell(ImportField.NOTES).takeIf { it.isNotBlank() },
    )

    val duplicate = existing.firstOrNull {
        it.name.equals(subscription.name, ignoreCase = true) &&
            it.amountMinor == subscription.amountMinor &&
            it.currency.equals(subscription.currency, ignoreCase = true)
    }

    return ImportCandidate(
        rowNumber = rowNumber,
        subscription = subscription,
        notes = notes,
        duplicateOf = duplicate?.id,
        // A likely duplicate arrives unticked. Doubling a burn figure is the one import mistake
        // whose result still looks plausible, so the safe default is the one that needs a
        // deliberate tap to undo.
        include = duplicate == null,
    )
}

// --- value parsing ------------------------------------------------------------------------

/**
 * Whether this file writes dates day-first.
 *
 * `03/04/2026` is the 3rd of April to most of the world and the 4th of March in the US, and
 * guessing wrong moves every renewal in the import by up to eleven months. Decided from the
 * whole column rather than per row: one value with a first number above twelve settles it for
 * every other value, which is the only evidence a file actually offers.
 */
internal fun detectDayFirst(table: Table, dateColumn: Int?): Boolean {
    if (dateColumn == null) return true
    var firstOverTwelve = false
    var secondOverTwelve = false

    table.rows.forEach { row ->
        val parts = splitDateParts(table.value(row, dateColumn)) ?: return@forEach
        if (parts.first > 12) firstOverTwelve = true
        if (parts.second > 12) secondOverTwelve = true
    }
    return when {
        firstOverTwelve -> true
        secondOverTwelve -> false
        // No evidence either way. Day-first is both the ISO ordering's neighbour and what most
        // of the world writes, including the markets this app is aimed at.
        else -> true
    }
}

private fun splitDateParts(text: String): Pair<Int, Int>? {
    val parts = text.trim().split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size < 2) return null
    // An ISO date leads with the year and settles nothing about day/month order.
    if (parts[0] > 31) return null
    return parts[0] to parts[1]
}

private val isoLike = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
private val numeric = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})$""")
private val textual = Regex("""^(\d{1,2})\s+([A-Za-z]{3,})\.?,?\s+(\d{2,4})$""")
private val textualFirst = Regex("""^([A-Za-z]{3,})\.?\s+(\d{1,2}),?\s+(\d{2,4})$""")

private val months = listOf(
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
)

internal fun parseDate(text: String, dayFirst: Boolean): LocalDate? {
    val value = text.trim()
    if (value.isEmpty()) return null

    isoLike.find(value)?.let { m ->
        return runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }
    numeric.find(value)?.let { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val year = fullYear(m.groupValues[3].toInt())
        val (day, month) = if (dayFirst) a to b else b to a
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
    textual.find(value)?.let { m ->
        val month = monthOf(m.groupValues[2]) ?: return null
        return runCatching {
            LocalDate.of(fullYear(m.groupValues[3].toInt()), month, m.groupValues[1].toInt())
        }.getOrNull()
    }
    textualFirst.find(value)?.let { m ->
        val month = monthOf(m.groupValues[1]) ?: return null
        return runCatching {
            LocalDate.of(fullYear(m.groupValues[3].toInt()), month, m.groupValues[2].toInt())
        }.getOrNull()
    }
    return null
}

private fun monthOf(name: String): Int? =
    months.indexOf(name.lowercase(Locale.ROOT).take(3)).takeIf { it >= 0 }?.plus(1)

/** Two-digit years are this century. A subscription dated 1998 is a typo, not a renewal. */
private fun fullYear(year: Int): Int = if (year < 100) 2000 + year else year

/**
 * An amount in [currency]'s minor units.
 *
 * Handles both separator conventions. `1.299,00` and `1,299.00` are the same number written by
 * different spreadsheets, and the rule that tells them apart is that the *last* separator is the
 * decimal one — unless it groups three digits, which makes it a thousands separator.
 */
internal fun parseAmountMinor(text: String, currency: String): Long? {
    val digitsAndSeparators = text.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
    if (digitsAndSeparators.none { it.isDigit() }) return null

    val negative = text.trimStart().startsWith('-')
    val cleaned = digitsAndSeparators.removePrefix("-").replace("-", "")

    val lastDot = cleaned.lastIndexOf('.')
    val lastComma = cleaned.lastIndexOf(',')
    val decimalAt = maxOf(lastDot, lastComma)

    val normalised = when {
        decimalAt < 0 -> cleaned
        // Exactly three digits after the final separator, and no other separator: a thousands
        // group, not a decimal. "1,299" is one thousand two hundred and ninety-nine.
        cleaned.length - decimalAt - 1 == 3 && lastDot < 0 != (lastComma < 0) ->
            cleaned.replace(",", "").replace(".", "")

        else -> cleaned.take(decimalAt).replace(",", "").replace(".", "") +
            "." + cleaned.substring(decimalAt + 1)
    }

    val amount = normalised.toBigDecimalOrNull() ?: return null
    val minor = amount
        .movePointRight(MoneyFormat.fractionDigits(currency))
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
    return if (negative) -minor else minor
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

private val symbolCurrencies = mapOf(
    '₹' to "INR", '$' to "USD", '€' to "EUR", '£' to "GBP", '¥' to "JPY", '₩' to "KRW",
)

internal fun parseCurrency(text: String): String? {
    val value = text.trim()
    if (value.isEmpty()) return null

    // A three-letter code anywhere in the value wins over a symbol: "USD $20" is unambiguous.
    Regex("""\b([A-Za-z]{3})\b""").find(value)?.let { match ->
        val code = match.groupValues[1].uppercase(Locale.ROOT)
        if (FxRates.isSupported(code) || runCatching { java.util.Currency.getInstance(code) }.isSuccess) {
            return code
        }
    }
    value.forEach { c -> symbolCurrencies[c]?.let { return it } }
    return null
}

internal fun parseCycle(text: String): BillingCycle? {
    val value = text.lowercase(Locale.ROOT).trim()
    if (value.isEmpty()) return null

    // "every 3 months", "per 2 weeks", "3 monthly"
    Regex("""(\d+)\s*(day|week|month|year)""").find(value)?.let { match ->
        val count = match.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val unit = when (match.groupValues[2]) {
            "day" -> CycleUnit.DAY
            "week" -> CycleUnit.WEEK
            "year" -> CycleUnit.YEAR
            else -> CycleUnit.MONTH
        }
        return BillingCycle(unit, count)
    }

    return when {
        value.contains("quarter") -> BillingCycle.QUARTERLY
        value.contains("fortnight") || value.contains("biweek") -> BillingCycle(CycleUnit.WEEK, 2)
        value.contains("year") || value.contains("annual") || value.contains("yr") ->
            BillingCycle.ANNUAL

        value.contains("month") || value.contains("mo") -> BillingCycle.MONTHLY
        value.contains("week") || value.contains("wk") -> BillingCycle.WEEKLY
        value.contains("da") -> BillingCycle(CycleUnit.DAY, 1)
        else -> null
    }
}

internal fun parseBusiness(text: String): Boolean? {
    val value = text.lowercase(Locale.ROOT).trim()
    return when {
        value.isEmpty() -> null
        value.startsWith("b") || value.startsWith("w") || value == "true" || value == "yes" -> true
        value.startsWith("p") || value == "false" || value == "no" -> false
        else -> null
    }
}

/** Matches a category by its display name or its export code; anything else becomes Other. */
internal fun parseCategory(text: String): Category {
    val value = text.trim()
    if (value.isEmpty()) return Category.OTHER
    return Category.entries.firstOrNull {
        it.displayName.equals(value, ignoreCase = true) || it.csvCode.equals(value, ignoreCase = true)
    } ?: Category.entries.firstOrNull {
        it != Category.OTHER && value.contains(it.csvCode, ignoreCase = true)
    } ?: Category.OTHER
}
