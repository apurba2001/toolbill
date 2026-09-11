package com.toolbill.android.core.domain.subscription

import com.toolbill.android.core.domain.money.FxRates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The catalogue is the one place this app ships data about the outside world, so what it must
 * not contain matters as much as what it does.
 */
class ServiceCatalogueTest {

    /** The single rule: a bundled price is stale the day it ships. The type cannot hold one. */
    @Test
    fun `no entry can carry an amount`() {
        val fields = CatalogueEntry::class.java.declaredFields.map { it.name }
        assertTrue(
            fields.none { it.contains("amount", ignoreCase = true) || it.contains("price", ignoreCase = true) },
            "a catalogue entry must never carry a price: $fields",
        )
    }

    @Test
    fun `every entry bills in a currency the app can price`() {
        serviceCatalogue.forEach { entry ->
            assertTrue(
                FxRates.isSupported(entry.currency),
                "${entry.name} bills in ${entry.currency}, which has no rate",
            )
        }
    }

    @Test
    fun `every entry names a real category`() {
        val names = Category.entries.map { it.displayName }.toSet()
        serviceCatalogue.forEach { entry ->
            assertTrue(entry.category in names, "${entry.name} has category ${entry.category}")
        }
    }

    @Test
    fun `names are unique`() {
        val duplicates = serviceCatalogue.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }
        assertTrue(duplicates.isEmpty(), "duplicate entries: ${duplicates.keys}")
    }

    @Test
    fun `the catalogue is big enough to be worth having`() {
        assertTrue(serviceCatalogue.size >= 200, "only ${serviceCatalogue.size} entries")
    }

    // --- suggestions ---

    @Test
    fun `a prefix match ranks above a contains match`() {
        val results = suggestServices("not").map { it.name }
        assertEquals("Notion", results.first())
    }

    /**
     * A pure prefix match would hide iCloud+ behind knowing it starts with a lowercase i.
     *
     * Asked with room to spare: "cloud" prefix-matches Cloudflare twice and Cloudways, which
     * rightly fill the default cap before any contains-match is reached.
     */
    @Test
    fun `a mid-name match is still found`() {
        val results = suggestServices("cloud", limit = 10).map { it.name }
        assertTrue(results.any { it == "iCloud+" }, "expected iCloud+ in $results")
        assertTrue(
            results.indexOf("Cloudways") < results.indexOf("iCloud+"),
            "a prefix match should still rank first: $results",
        )
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(suggestServices("CLAUDE").any { it.name == "Claude Pro" })
    }

    @Test
    fun `an empty query suggests nothing`() {
        assertTrue(suggestServices("").isEmpty())
        assertTrue(suggestServices("   ").isEmpty())
    }

    @Test
    fun `suggestions are capped`() {
        assertTrue(suggestServices("a", limit = 4).size <= 4)
    }

    // --- category lookup ---

    @Test
    fun `an exact name resolves to its category`() {
        assertEquals(Category.AI_TOOLS, catalogueCategoryFor("Claude Pro"))
        assertEquals(Category.HOSTING_INFRA, catalogueCategoryFor("Vercel Pro"))
        assertEquals(Category.ENTERTAINMENT, catalogueCategoryFor("Netflix"))
    }

    @Test
    fun `an unknown name resolves to nothing rather than guessing`() {
        assertNull(catalogueCategoryFor("Some Tool Nobody Bundled"))
    }
}
