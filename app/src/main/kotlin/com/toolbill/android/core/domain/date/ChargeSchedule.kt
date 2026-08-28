package com.toolbill.android.core.domain.date

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The unit a billing cycle is measured in.
 *
 * Stored alongside a count, never collapsed into a day count. "Monthly on the 31st" is not
 * "every 30.44 days" — it has to land on real calendar dates.
 */
enum class CycleUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

/**
 * The [index]-th charge of a subscription, counting the anchor itself as index 0.
 *
 * Every occurrence is computed from the original [anchor] rather than by stepping forward from
 * the previous one. That single choice is what implements the return-to-anchor rule:
 * `2024-01-31` monthly gives `2024-02-29`, then `2024-03-31` — because [LocalDate.plusMonths]
 * clamps the day of month to the target month's length but never mutates the anchor.
 *
 * Stepping iteratively (`current = current.plusMonths(1)`) is the classic bug in this category:
 * it clamps to 29 in February and every later charge stays on the 29th forever.
 */
fun occurrenceOn(
    anchor: LocalDate,
    unit: CycleUnit,
    count: Int,
    index: Int,
): LocalDate {
    requireValidCount(count)
    require(index >= 0) { "occurrence index must be non-negative, was $index" }

    val step = count.toLong() * index
    return when (unit) {
        CycleUnit.DAY -> anchor.plusDays(step)
        CycleUnit.WEEK -> anchor.plusWeeks(step)
        CycleUnit.MONTH -> anchor.plusMonths(step)
        CycleUnit.YEAR -> anchor.plusYears(step)
    }
}

/**
 * The earliest charge date strictly after [after].
 *
 * [after] is exclusive, so passing a charge date returns the following one. Pass
 * `today.minusDays(1)` to include a charge falling today.
 *
 * Dates only — no time, no zone. Conversion to an instant belongs at the alarm-scheduling
 * boundary, which is what keeps DST and timezone changes out of this function entirely.
 */
fun nextChargeDate(
    anchor: LocalDate,
    unit: CycleUnit,
    count: Int,
    after: LocalDate,
): LocalDate {
    requireValidCount(count)
    return occurrenceOn(anchor, unit, count, occurrenceIndexAfter(anchor, unit, count, after))
}

/**
 * Every charge date falling within [from]..[to], both ends inclusive.
 *
 * Charges before the anchor never exist, so a window entirely before it yields an empty list,
 * as does an inverted window. Backs the calendar grid and charge materialisation.
 */
fun chargeDatesInRange(
    anchor: LocalDate,
    unit: CycleUnit,
    count: Int,
    from: LocalDate,
    to: LocalDate,
): List<LocalDate> {
    requireValidCount(count)
    if (to < from || to < anchor) return emptyList()

    var index = if (from <= anchor) 0 else occurrenceIndexAfter(anchor, unit, count, from.minusDays(1))
    val dates = mutableListOf<LocalDate>()
    while (true) {
        val date = occurrenceOn(anchor, unit, count, index)
        if (date > to) break
        dates += date
        index++
    }
    return dates
}

/**
 * Smallest occurrence index whose date is strictly after [after].
 *
 * Estimates the index by whole elapsed units, then corrects. The estimate is off by at most one
 * or two — month-length clamping is the only source of error — so both loops run a bounded
 * number of times regardless of how far [after] is from the anchor.
 */
private fun occurrenceIndexAfter(
    anchor: LocalDate,
    unit: CycleUnit,
    count: Int,
    after: LocalDate,
): Int {
    if (after < anchor) return 0

    val elapsedUnits = when (unit) {
        CycleUnit.DAY -> ChronoUnit.DAYS.between(anchor, after)
        CycleUnit.WEEK -> ChronoUnit.WEEKS.between(anchor, after)
        CycleUnit.MONTH -> ChronoUnit.MONTHS.between(anchor, after)
        CycleUnit.YEAR -> ChronoUnit.YEARS.between(anchor, after)
    }

    var index = (elapsedUnits / count).coerceAtLeast(0L).toInt()
    while (occurrenceOn(anchor, unit, count, index) <= after) index++
    while (index > 0 && occurrenceOn(anchor, unit, count, index - 1) > after) index--
    return index
}

private fun requireValidCount(count: Int) {
    require(count >= 1) { "cycle count must be at least 1, was $count" }
}
