package com.toolbill.android.feature.insights

import com.toolbill.android.feature.subscriptions.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The breakdown has to close on the headline.
 *
 * If the category split and the Home hero disagree by even a paisa, a user who adds up the
 * categories finds the app lying to them — and then no other figure in it is believable.
 */
class InsightsDataTest {

    @Test
    @DisplayName("Category spend sums to the Home hero, exactly")
    fun `categories sum to the monthly burn`() {
        assertEquals(
            SampleData.MONTHLY_BURN_MINOR,
            sampleCategorySpend.sumOf { it.amountMinor },
        )
    }

    @Test
    @DisplayName("Business plus personal sums to the Home hero, exactly")
    fun `the business split sums to the monthly burn`() {
        assertEquals(SampleData.MONTHLY_BURN_MINOR, BUSINESS_MINOR + PERSONAL_MINOR)
    }

    @Test
    fun `the business share rounds to the 96 percent the design states`() {
        assertEquals(SampleData.BUSINESS_SHARE_PERCENT, businessSharePercent)
        assertEquals(4, personalSharePercent)
    }

    @Test
    fun `annualized tooling is twelve times the monthly burn`() {
        assertEquals(ANNUAL_TOOLING_MINOR, SampleData.MONTHLY_BURN_MINOR * 12)
    }

    @Test
    fun `the three annual switches save the stated total`() {
        assertEquals(1_468_600L, sampleAnnualSavings.sumOf { it.savingMinor })
    }
}
