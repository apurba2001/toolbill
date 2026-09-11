package com.toolbill.android.core.domain.importing

import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * An import is thirty rows the user will never re-read. A column shifted by one, a date read
 * month-first, or a duplicate that silently doubles the burn are all mistakes whose result still
 * looks plausible — which is exactly why they have to be caught here.
 */
class ImportParseTest {

    private val today = LocalDate.of(2026, 9, 11)

    // --- parsing the file ---

    @Test
    fun `a quoted field containing the delimiter stays one column`() {
        val table = Csv.parse("name,note\n\"Adobe, Inc.\",design\n")

        assertEquals(listOf("name", "note"), table.headers)
        assertEquals(listOf("Adobe, Inc.", "design"), table.rows.single())
    }

    @Test
    fun `a doubled quote inside a quoted field is a literal quote`() {
        val table = Csv.parse("name\n\"The \"\"Pro\"\" plan\"\n")
        assertEquals("The \"Pro\" plan", table.rows.single().first())
    }

    @Test
    fun `a line break inside a quoted field does not start a new row`() {
        val table = Csv.parse("name,note\nClaude,\"line one\nline two\"\n")

        assertEquals(1, table.rows.size)
        assertEquals("line one\nline two", table.rows.single()[1])
    }

    @Test
    fun `a ragged row is padded so later columns do not shift`() {
        val table = Csv.parse("a,b,c\n1,2\n1,2,3,4\n")

        assertEquals(listOf("1", "2", ""), table.rows[0])
        assertEquals(listOf("1", "2", "3"), table.rows[1])
    }

    @Test
    fun `tabs and semicolons are detected`() {
        assertEquals('\t', Csv.detectDelimiter("a\tb\tc\n1\t2\t3"))
        assertEquals(';', Csv.detectDelimiter("a;b;c\n1;2;3"))
        assertEquals(',', Csv.detectDelimiter("a,b,c\n1,2,3"))
    }

    /** Excel writes a byte-order mark, and without stripping it the first heading matches nothing. */
    @Test
    fun `a byte order mark is stripped from the first heading`() {
        val table = Csv.parse("\uFEFFService,Price\nClaude,20\n")
        assertEquals("Service", table.headers.first())
    }

    @Test
    fun `carriage returns do not produce blank rows`() {
        val table = Csv.parse("a,b\r\n1,2\r\n")
        assertEquals(1, table.rows.size)
    }

    // --- guessing the mapping ---

    @Test
    fun `headings are matched to fields`() {
        val mapping = guessMapping(listOf("Service", "Price", "Currency", "Billed", "Next charge"))

        assertEquals(0, mapping.columnFor(ImportField.SERVICE))
        assertEquals(1, mapping.columnFor(ImportField.AMOUNT))
        assertEquals(2, mapping.columnFor(ImportField.CURRENCY))
        assertEquals(3, mapping.columnFor(ImportField.CYCLE))
        assertEquals(4, mapping.columnFor(ImportField.NEXT_CHARGE))
        assertTrue(mapping.isUsable)
    }

    /** "Next charge date" and "Monthly cost" both contain "charge"/"cost"-ish words. */
    @Test
    fun `a column is claimed by only one field`() {
        val mapping = guessMapping(listOf("Subscription", "Monthly cost", "Next charge date"))

        assertEquals(0, mapping.columnFor(ImportField.SERVICE))
        assertEquals(1, mapping.columnFor(ImportField.AMOUNT))
        assertEquals(2, mapping.columnFor(ImportField.NEXT_CHARGE))
    }

    @Test
    fun `a file missing an amount column is not usable`() {
        val mapping = guessMapping(listOf("Service", "Notes"))

        assertFalse(mapping.isUsable)
        assertEquals(listOf(ImportField.AMOUNT), mapping.missingRequired)
    }

    @Test
    fun `reassigning a column releases its previous field`() {
        val mapping = guessMapping(listOf("Service", "Price"))
            .with(ImportField.NOTES, 1)

        assertEquals(1, mapping.columnFor(ImportField.NOTES))
        assertNull(mapping.columnFor(ImportField.AMOUNT))
    }

    // --- amounts ---

    @Test
    fun `amounts are read in both separator conventions`() {
        assertEquals(129_900L, parseAmountMinor("1,299.00", "INR"))
        assertEquals(129_900L, parseAmountMinor("1.299,00", "INR"))
        assertEquals(129_900L, parseAmountMinor("1299", "INR"))
        assertEquals(2_000L, parseAmountMinor("20.00", "USD"))
    }

    /** "1,299" is one thousand two hundred and ninety-nine, not one rupee and 299 paise. */
    @Test
    fun `three digits after a lone separator is a thousands group`() {
        assertEquals(129_900L, parseAmountMinor("1,299", "INR"))
        assertEquals(129_900L, parseAmountMinor("1.299", "INR"))
    }

    @Test
    fun `currency symbols and spacing are ignored`() {
        assertEquals(174_200L, parseAmountMinor("₹1,742.00", "INR"))
        assertEquals(2_000L, parseAmountMinor("$ 20", "USD"))
        assertEquals(2_000L, parseAmountMinor("USD 20.00", "USD"))
    }

    @Test
    fun `a zero-decimal currency is not inflated`() {
        assertEquals(5_000L, parseAmountMinor("5000", "JPY"))
    }

    @Test
    fun `text with no digits is not an amount`() {
        assertNull(parseAmountMinor("free", "INR"))
        assertNull(parseAmountMinor("", "INR"))
    }

    // --- dates ---

    /**
     * The one that matters. 03/04/2026 is 3 April to most of the world and 4 March in the US,
     * and guessing wrong moves every renewal in the import by up to eleven months.
     */
    @Test
    fun `date order is decided from the whole column`() {
        val dayFirst = Csv.parse("date\n03/04/2026\n25/12/2026\n")
        assertTrue(detectDayFirst(dayFirst, 0), "a 25 in first position proves day-first")

        val monthFirst = Csv.parse("date\n03/04/2026\n12/25/2026\n")
        assertFalse(detectDayFirst(monthFirst, 0), "a 25 in second position proves month-first")
    }

    @Test
    fun `an ambiguous column falls back to day first`() {
        val table = Csv.parse("date\n03/04/2026\n05/06/2026\n")
        assertTrue(detectDayFirst(table, 0))
    }

    @Test
    fun `the detected order is applied to every row`() {
        assertEquals(LocalDate.of(2026, 4, 3), parseDate("03/04/2026", dayFirst = true))
        assertEquals(LocalDate.of(2026, 3, 4), parseDate("03/04/2026", dayFirst = false))
    }

    @Test
    fun `iso dates are read regardless of the detected order`() {
        assertEquals(LocalDate.of(2026, 4, 3), parseDate("2026-04-03", dayFirst = false))
    }

    @Test
    fun `written month names are read in either order`() {
        assertEquals(LocalDate.of(2026, 8, 27), parseDate("27 Aug 2026", dayFirst = true))
        assertEquals(LocalDate.of(2026, 8, 27), parseDate("Aug 27, 2026", dayFirst = true))
    }

    @Test
    fun `a two digit year is this century`() {
        assertEquals(LocalDate.of(2026, 4, 3), parseDate("03/04/26", dayFirst = true))
    }

    @Test
    fun `an impossible date is not invented`() {
        assertNull(parseDate("31/02/2026", dayFirst = true))
        assertNull(parseDate("not a date", dayFirst = true))
    }

    // --- cycles, currency, category ---

    @Test
    fun `cycles are read from the words people write`() {
        assertEquals(BillingCycle.MONTHLY, parseCycle("Monthly"))
        assertEquals(BillingCycle.ANNUAL, parseCycle("yearly"))
        assertEquals(BillingCycle.ANNUAL, parseCycle("per year"))
        assertEquals(BillingCycle.QUARTERLY, parseCycle("Quarterly"))
        assertEquals(BillingCycle.WEEKLY, parseCycle("weekly"))
        assertEquals(BillingCycle(CycleUnit.MONTH, 3), parseCycle("every 3 months"))
        assertEquals(BillingCycle(CycleUnit.WEEK, 2), parseCycle("fortnightly"))
        assertNull(parseCycle("whenever"))
    }

    @Test
    fun `currency comes from a code or a symbol`() {
        assertEquals("USD", parseCurrency("USD"))
        assertEquals("INR", parseCurrency("₹1,742"))
        assertEquals("USD", parseCurrency("$20"))
        assertNull(parseCurrency("20"))
    }

    @Test
    fun `categories match by name or export code`() {
        assertEquals(Category.AI_TOOLS, parseCategory("AI tools"))
        assertEquals(Category.AI_TOOLS, parseCategory("AI"))
        assertEquals(Category.HOSTING_INFRA, parseCategory("Host"))
        assertEquals(Category.OTHER, parseCategory("Something else"))
    }

    // --- whole rows ---

    private fun preview(csv: String, existing: List<Subscription> = emptyList()): ImportPreview {
        val table = Csv.parse(csv)
        return parseImport(table, guessMapping(table.headers), "INR", today, existing)
    }

    @Test
    fun `a complete row becomes a subscription`() {
        val result = preview(
            "Service,Price,Currency,Billed,Next charge\nClaude Pro,20.00,USD,Monthly,27/08/2026\n",
        )
        val subscription = result.candidates.single().subscription!!

        assertEquals("Claude Pro", subscription.name)
        assertEquals(2_000L, subscription.amountMinor)
        assertEquals("USD", subscription.currency)
        assertEquals(BillingCycle.MONTHLY, subscription.cycle)
        assertEquals(LocalDate.of(2026, 8, 27), subscription.anchorDate)
        assertTrue(result.candidates.single().isUsable)
    }

    /**
     * A file with no category column is the common case. Dropping every row into Other makes the
     * Insights breakdown useless on the very portfolio it exists to explain.
     */
    @Test
    fun `a missing category is inferred from the catalogue`() {
        val result = preview("Service,Price\nClaude Pro,20\nFigma Professional,15\nSome Tool,10\n")
        val categories = result.candidates.map { it.subscription!!.category }

        assertEquals(
            listOf(Category.AI_TOOLS, Category.DESIGN, Category.OTHER),
            categories,
        )
    }

    /**
     * The same shape of file a real export has: extra columns, a "Kind" column that BUSINESS
     * claims, and no category column at all. This is where the inference actually has to work.
     */
    @Test
    fun `categories are inferred through a realistic header set`() {
        val csv = "Service,Monthly cost,Currency,Billed,Next charge,Kind,Notes\n" +
            "Claude Pro,20.00,USD,Monthly,27/08/2026,Business,AI assistant\n" +
            "iCloud+,749,INR,Monthly,16/08/2026,Personal,Storage\n"
        val table = Csv.parse(csv)
        val mapping = guessMapping(table.headers)

        assertNull(mapping.columnFor(ImportField.CATEGORY))
        val result = parseImport(table, mapping, "USD", today)
        assertEquals(
            listOf(Category.AI_TOOLS, Category.STORAGE),
            result.candidates.map { it.subscription!!.category },
        )
    }

    @Test
    fun `an explicit category in the file beats the catalogue`() {
        val result = preview("Service,Price,Category\nClaude Pro,20,Productivity\n")

        assertEquals(Category.PRODUCTIVITY, result.candidates.single().subscription!!.category)
    }

    @Test
    fun `a row without a name or amount is reported rather than dropped`() {
        val result = preview("Service,Price\n,20.00\nVercel,\n")

        assertEquals(2, result.candidates.size)
        assertEquals(2, result.unusable.size)
        assertTrue(result.candidates[0].problems.any { it.contains("name") })
        assertTrue(result.candidates[1].problems.any { it.contains("amount") })
        // An unusable row is never ticked for import.
        assertTrue(result.candidates.none { it.include })
    }

    @Test
    fun `the row number matches what a spreadsheet shows`() {
        val result = preview("Service,Price\nClaude,20\nVercel,20\n")
        assertEquals(listOf(2, 3), result.candidates.map { it.rowNumber })
    }

    @Test
    fun `missing optional values are defaulted and said so`() {
        val result = preview("Service,Price\nClaude Pro,1299\n")
        val candidate = result.candidates.single()

        assertEquals(BillingCycle.MONTHLY, candidate.subscription!!.cycle)
        assertEquals(today, candidate.subscription.anchorDate)
        assertTrue(candidate.notes.any { it.contains("monthly") })
        assertTrue(candidate.notes.any { it.contains("today") })
        assertTrue(candidate.needsAttention)
    }

    /**
     * Importing the same spreadsheet twice is the one mistake whose result still looks
     * plausible, so a repeat arrives unticked and has to be chosen deliberately.
     */
    @Test
    fun `a likely duplicate is flagged and left unticked`() {
        val existing = Subscription(
            id = "existing",
            name = "Claude Pro",
            amountMinor = 2_000,
            currency = "USD",
            cycle = BillingCycle.MONTHLY,
            anchorDate = today,
            category = Category.AI_TOOLS,
            status = SubscriptionStatus.ACTIVE,
        )
        val result = preview(
            "Service,Price,Currency\nClaude Pro,20.00,USD\n",
            existing = listOf(existing),
        )
        val candidate = result.candidates.single()

        assertEquals("existing", candidate.duplicateOf)
        assertFalse(candidate.include)
        assertTrue(candidate.needsAttention)
    }

    @Test
    fun `a currency with no rate is refused rather than priced wrongly`() {
        val result = preview("Service,Price,Currency\nSomething,100,ZAR\n")

        assertTrue(result.candidates.single().problems.any { it.contains("ZAR") })
    }

    @Test
    fun `only usable and ticked rows are selected`() {
        val result = preview("Service,Price\nClaude,20\n,20\nVercel,notanumber\n")

        assertEquals(1, result.selected.size)
        assertEquals("Claude", result.selected.single().subscription!!.name)
    }
}
