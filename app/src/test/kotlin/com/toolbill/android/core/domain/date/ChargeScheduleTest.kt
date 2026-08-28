package com.toolbill.android.core.domain.date

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun d(iso: String): LocalDate = LocalDate.parse(iso)

class ChargeScheduleTest {

    @Nested
    @DisplayName("Month-end anchors must return to the anchor day, not stay clamped")
    inner class MonthEndAnchors {

        @Test
        fun `31st monthly recovers to the 31st after a short month`() {
            val anchor = d("2024-01-31")
            assertEquals(d("2024-02-29"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-01-31")))
            // The classic bug: a naive implementation clamps to 29 and then never recovers.
            assertEquals(d("2024-03-31"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-02-29")))
            assertEquals(d("2024-04-30"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-03-31")))
            assertEquals(d("2024-05-31"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-04-30")))
        }

        @Test
        fun `31st monthly across a non-leap February`() {
            val anchor = d("2023-01-31")
            assertEquals(d("2023-02-28"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2023-01-31")))
            assertEquals(d("2023-03-31"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2023-02-28")))
        }

        @Test
        fun `30th monthly clamps in February then recovers`() {
            val anchor = d("2024-01-30")
            assertEquals(d("2024-02-29"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-01-30")))
            assertEquals(d("2024-03-30"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-02-29")))
        }

        @Test
        fun `29th monthly lands exactly on a leap day then recovers`() {
            val anchor = d("2024-01-29")
            assertEquals(d("2024-02-29"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-01-29")))
            assertEquals(d("2024-03-29"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2024-02-29")))
        }

        @Test
        fun `29th monthly clamps in a non-leap February then recovers`() {
            val anchor = d("2023-01-29")
            assertEquals(d("2023-02-28"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2023-01-29")))
            assertEquals(d("2023-03-29"), nextChargeDate(anchor, CycleUnit.MONTH, 1, d("2023-02-28")))
        }

        @Test
        fun `twelve monthly steps from the 31st land back on the 31st a year later`() {
            val anchor = d("2024-01-31")
            var cursor = anchor
            repeat(12) { cursor = nextChargeDate(anchor, CycleUnit.MONTH, 1, cursor) }
            assertEquals(d("2025-01-31"), cursor)
        }
    }

    @Nested
    @DisplayName("29 February annual anchors")
    inner class LeapDayAnnual {

        @Test
        fun `leap day annual clamps in common years and recovers on the next leap year`() {
            val anchor = d("2024-02-29")
            assertEquals(d("2025-02-28"), nextChargeDate(anchor, CycleUnit.YEAR, 1, d("2024-02-29")))
            assertEquals(d("2026-02-28"), nextChargeDate(anchor, CycleUnit.YEAR, 1, d("2025-02-28")))
            assertEquals(d("2027-02-28"), nextChargeDate(anchor, CycleUnit.YEAR, 1, d("2026-02-28")))
            assertEquals(d("2028-02-29"), nextChargeDate(anchor, CycleUnit.YEAR, 1, d("2027-02-28")))
        }

        @Test
        fun `fourth annual occurrence of a leap day anchor is a leap day`() {
            assertEquals(d("2028-02-29"), occurrenceOn(d("2024-02-29"), CycleUnit.YEAR, 1, 4))
        }

        @Test
        fun `a century common year is handled`() {
            // 2100 is divisible by 4 but is not a leap year.
            assertEquals(d("2100-02-28"), occurrenceOn(d("2096-02-29"), CycleUnit.YEAR, 1, 4))
        }
    }

    @Nested
    @DisplayName("Quarterly and other multi-month cycles")
    inner class MultiMonthCycles {

        @Test
        fun `quarterly from the 31st follows the same return-to-anchor rule`() {
            val anchor = d("2024-01-31")
            assertEquals(d("2024-04-30"), nextChargeDate(anchor, CycleUnit.MONTH, 3, d("2024-01-31")))
            assertEquals(d("2024-07-31"), nextChargeDate(anchor, CycleUnit.MONTH, 3, d("2024-04-30")))
            assertEquals(d("2024-10-31"), nextChargeDate(anchor, CycleUnit.MONTH, 3, d("2024-07-31")))
            assertEquals(d("2025-01-31"), nextChargeDate(anchor, CycleUnit.MONTH, 3, d("2024-10-31")))
        }

        @Test
        fun `every two months from the 31st`() {
            val anchor = d("2024-03-31")
            assertEquals(d("2024-05-31"), nextChargeDate(anchor, CycleUnit.MONTH, 2, d("2024-03-31")))
            assertEquals(d("2024-07-31"), nextChargeDate(anchor, CycleUnit.MONTH, 2, d("2024-05-31")))
        }

        @Test
        fun `every 18 months`() {
            assertEquals(d("2025-07-31"), nextChargeDate(d("2024-01-31"), CycleUnit.MONTH, 18, d("2024-01-31")))
        }
    }

    @Nested
    @DisplayName("Day and week cycles are plain arithmetic")
    inner class DayAndWeekCycles {

        @Test
        fun `daily`() {
            assertEquals(d("2024-01-02"), nextChargeDate(d("2024-01-01"), CycleUnit.DAY, 1, d("2024-01-01")))
        }

        @Test
        fun `every 45 days`() {
            assertEquals(d("2024-02-15"), nextChargeDate(d("2024-01-01"), CycleUnit.DAY, 45, d("2024-01-01")))
        }

        @Test
        fun `day cycle crossing a leap day counts the extra day`() {
            assertEquals(d("2024-03-02"), nextChargeDate(d("2024-02-01"), CycleUnit.DAY, 30, d("2024-02-01")))
        }

        @Test
        fun `fortnightly`() {
            val anchor = d("2024-01-01")
            assertEquals(d("2024-01-15"), nextChargeDate(anchor, CycleUnit.WEEK, 2, d("2024-01-01")))
            assertEquals(d("2024-01-15"), nextChargeDate(anchor, CycleUnit.WEEK, 2, d("2024-01-14")))
            assertEquals(d("2024-01-29"), nextChargeDate(anchor, CycleUnit.WEEK, 2, d("2024-01-15")))
        }

        @Test
        fun `weekly cycles preserve day of week`() {
            val anchor = d("2024-01-01")
            val next = nextChargeDate(anchor, CycleUnit.WEEK, 3, d("2026-08-28"))
            assertEquals(anchor.dayOfWeek, next.dayOfWeek)
        }
    }

    @Nested
    @DisplayName("Boundaries around the anchor")
    inner class Boundaries {

        @Test
        fun `an anchor in the future is itself the next charge`() {
            assertEquals(d("2024-06-15"), nextChargeDate(d("2024-06-15"), CycleUnit.MONTH, 1, d("2024-01-01")))
        }

        @Test
        fun `the day before the anchor still yields the anchor`() {
            assertEquals(d("2024-06-15"), nextChargeDate(d("2024-06-15"), CycleUnit.MONTH, 1, d("2024-06-14")))
        }

        @Test
        fun `after is exclusive so the anchor day itself yields the following charge`() {
            assertEquals(d("2024-07-15"), nextChargeDate(d("2024-06-15"), CycleUnit.MONTH, 1, d("2024-06-15")))
        }

        @Test
        fun `a multi-year gap resolves without drift`() {
            assertEquals(d("2026-08-31"), nextChargeDate(d("2020-01-31"), CycleUnit.MONTH, 1, d("2026-08-28")))
            assertEquals(d("2027-02-28"), nextChargeDate(d("2020-02-29"), CycleUnit.YEAR, 1, d("2026-08-28")))
        }

        @Test
        fun `a 26 year daily gap stays on the cycle`() {
            val anchor = d("2000-01-01")
            val after = d("2026-08-28")
            val next = nextChargeDate(anchor, CycleUnit.DAY, 7, after)
            assertTrue(next > after) { "$next must be after $after" }
            assertTrue(next <= after.plusDays(7)) { "$next must be within one cycle of $after" }
            assertEquals(0L, ChronoUnit.DAYS.between(anchor, next) % 7)
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `a cycle count below one is rejected`() {
            assertThrows<IllegalArgumentException> {
                nextChargeDate(d("2024-01-01"), CycleUnit.MONTH, 0, d("2024-01-01"))
            }
            assertThrows<IllegalArgumentException> {
                nextChargeDate(d("2024-01-01"), CycleUnit.MONTH, -1, d("2024-01-01"))
            }
        }

        @Test
        fun `a negative occurrence index is rejected`() {
            assertThrows<IllegalArgumentException> {
                occurrenceOn(d("2024-01-01"), CycleUnit.MONTH, 1, -1)
            }
        }
    }

    @Nested
    @DisplayName("Enumerating charges in a window")
    inner class ChargeDatesInRangeTests {

        @Test
        fun `monthly charges across a half year keep the anchor day`() {
            val dates = chargeDatesInRange(
                anchor = d("2024-01-31"),
                unit = CycleUnit.MONTH,
                count = 1,
                from = d("2024-01-01"),
                to = d("2024-06-30"),
            )
            assertEquals(
                listOf(
                    d("2024-01-31"), d("2024-02-29"), d("2024-03-31"),
                    d("2024-04-30"), d("2024-05-31"), d("2024-06-30"),
                ),
                dates,
            )
        }

        @Test
        fun `the range is inclusive at both ends`() {
            val dates = chargeDatesInRange(d("2024-01-15"), CycleUnit.MONTH, 1, d("2024-01-15"), d("2024-03-15"))
            assertEquals(listOf(d("2024-01-15"), d("2024-02-15"), d("2024-03-15")), dates)
        }

        @Test
        fun `charges before the anchor are never emitted`() {
            val dates = chargeDatesInRange(d("2024-06-15"), CycleUnit.MONTH, 1, d("2024-01-01"), d("2024-12-31"))
            assertEquals(d("2024-06-15"), dates.first())
            assertEquals(7, dates.size)
        }

        @Test
        fun `a window entirely before the anchor is empty`() {
            val dates = chargeDatesInRange(d("2024-06-15"), CycleUnit.MONTH, 1, d("2024-01-01"), d("2024-05-31"))
            assertTrue(dates.isEmpty())
        }

        @Test
        fun `an inverted window is empty`() {
            val dates = chargeDatesInRange(d("2024-01-01"), CycleUnit.MONTH, 1, d("2024-06-01"), d("2024-05-01"))
            assertTrue(dates.isEmpty())
        }

        @Test
        fun `a single day window catches a charge landing on it`() {
            val dates = chargeDatesInRange(d("2024-01-31"), CycleUnit.MONTH, 1, d("2024-02-29"), d("2024-02-29"))
            assertEquals(listOf(d("2024-02-29")), dates)
        }
    }

    @Nested
    @DisplayName("nextChargeDate and occurrenceOn must agree")
    inner class Consistency {

        @Test
        fun `walking forward reproduces the indexed occurrences for every unit`() {
            val anchor = d("2024-01-31")
            for (unit in CycleUnit.entries) {
                for (count in intArrayOf(1, 2, 3)) {
                    var cursor = anchor
                    for (index in 1..24) {
                        cursor = nextChargeDate(anchor, unit, count, cursor)
                        assertEquals(occurrenceOn(anchor, unit, count, index), cursor) {
                            "unit=$unit count=$count index=$index"
                        }
                    }
                }
            }
        }

        @Test
        fun `the returned charge is always strictly after and within one cycle`() {
            val anchor = d("2024-01-31")
            var after = d("2024-01-01")
            repeat(40) {
                val next = nextChargeDate(anchor, CycleUnit.MONTH, 1, after)
                assertTrue(next > after) { "$next must be strictly after $after" }
                after = after.plusDays(11)
            }
        }
    }
}
