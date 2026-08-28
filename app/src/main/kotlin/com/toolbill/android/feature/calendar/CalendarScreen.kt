package com.toolbill.android.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.domain.date.chargeDatesInRange
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import com.toolbill.android.feature.subscriptions.PricedSubscription
import com.toolbill.android.feature.subscriptions.SampleData
import com.toolbill.android.feature.subscriptions.label
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayTitle = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)

private val CellShape = RoundedCornerShape(6.dp)
private val CellHeight = 48.dp

/** One day's charges, materialised from the schedule rather than stored. */
private data class DayCharges(
    val date: LocalDate,
    val charges: List<Pair<PricedSubscription, Long>>,
) {
    val total: Long get() = charges.sumOf { it.second }
}

/**
 * Magnitude as a dot count.
 *
 * One dot under ₹1k, two to ₹3k, three above — same size, same colour. A single dot that grows
 * or changes hue encodes two variables in one mark and reads as a different kind of event.
 */
private fun dotCount(totalMinor: Long): Int = when {
    totalMinor < 100_000L -> 1
    totalMinor < 300_000L -> 2
    else -> 3
}

/** How a day cell is painted. Order matters: the first match wins. */
private enum class DayTone { SELECTED, TODAY, TRIAL, OVERDUE, DUE_SOON, PLAIN, OUTSIDE }

/**
 * Builds the month's charges by running every subscription's schedule across the window.
 *
 * This is the same [chargeDatesInRange] the materialisation worker will use, so the calendar
 * and the recorded charges cannot disagree about which days a subscription lands on.
 */
private fun chargesForMonth(
    subscriptions: List<PricedSubscription>,
    month: YearMonth,
): Map<LocalDate, DayCharges> {
    val from = month.atDay(1)
    val to = month.atEndOfMonth()
    val byDate = mutableMapOf<LocalDate, MutableList<Pair<PricedSubscription, Long>>>()

    subscriptions
        .filter { it.subscription.status == SubscriptionStatus.ACTIVE }
        .forEach { priced ->
            val s = priced.subscription
            chargeDatesInRange(s.anchorDate, s.cycle.unit, s.cycle.count, from, to).forEach { date ->
                byDate.getOrPut(date) { mutableListOf() } += priced to priced.homeAmountMinor
            }
        }

    return byDate.mapValues { (date, charges) -> DayCharges(date, charges) }
}

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    subscriptions: List<PricedSubscription> = SampleData.all,
) {
    val home = SampleData.HOME_CURRENCY
    val today = SampleData.today
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.of(2026, 8, 27)) }

    val charges = remember(subscriptions, month) { chargesForMonth(subscriptions, month) }
    val monthTotal = charges.values.sumOf { it.total }
    val chargeCount = charges.values.sumOf { it.charges.size }
    val overdueDates = remember(subscriptions) {
        subscriptions.mapNotNull { it.overdueSince }.toSet()
    }
    val trialDates = remember(subscriptions) {
        subscriptions.filter { it.subscription.status == SubscriptionStatus.TRIAL }
            .mapNotNull { it.subscription.trialEndDate }
            .toSet()
    }

    // The month header is `flex:none` in the design — a fixed bar with the grid scrolling
    // beneath it. As a list item it scrolled away and let rows run under the status bar.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)) {
                Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Text(
                        text = month.format(monthTitle),
                        style = ToolbillText.monthTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToolbillIconButton(
                            icon = Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous month",
                            onClick = { month = month.minusMonths(1); selected = null },
                        )
                        ToolbillIconButton(
                            icon = Icons.Rounded.ChevronRight,
                            contentDescription = "Next month",
                            onClick = { month = month.plusMonths(1); selected = null },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            Text(
                text = "${MoneyFormat.symbol(monthTotal, home)} charged this month · " +
                    "$chargeCount charges",
                style = Toolbill.money.code,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
        item {
            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                charges = charges,
                overdueDates = overdueDates,
                trialDates = trialDates,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(Space.s3))
            IntensityLegend()
            Spacer(Modifier.height(Space.s4))
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        val day = selected?.let { charges[it] }
        if (day != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.s4, vertical = Space.s3),
                ) {
                    Text(
                        text = day.date.format(dayTitle),
                        style = ToolbillText.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = MoneyFormat.symbol(day.total, home),
                        style = Toolbill.money.row,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            items(day.charges, key = { it.first.subscription.id }) { charge ->
                val s = charge.first.subscription
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.s4, vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = s.name,
                            style = ToolbillText.rowName,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${s.cycle.label()} · " +
                                if (s.isBusiness) "business" else "personal",
                            style = ToolbillText.rowSupport,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = MoneyFormat.format(charge.second, home).plain,
                        style = Toolbill.money.row,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.s12)) }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    charges: Map<LocalDate, DayCharges>,
    overdueDates: Set<LocalDate>,
    trialDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    // Week starts Monday, as the design's M T W T F S S header does.
    val firstOfMonth = month.atDay(1)
    val leading = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(leading.toLong())
    val weeks = 6

    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    style = ToolbillText.weekday,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(weeks) { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(7) { dayOfWeek ->
                    val date = gridStart.plusDays((week * 7 + dayOfWeek).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val total = charges[date]?.total ?: 0L
                    val tone = when {
                        !inMonth -> DayTone.OUTSIDE
                        date == selected -> DayTone.SELECTED
                        date == today -> DayTone.TODAY
                        date in trialDates -> DayTone.TRIAL
                        date in overdueDates -> DayTone.OVERDUE
                        total > 0 && !date.isBefore(today) &&
                            date.isBefore(today.plusDays(8)) -> DayTone.DUE_SOON

                        else -> DayTone.PLAIN
                    }
                    DayCell(
                        date = date,
                        tone = tone,
                        totalMinor = total,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    tone: DayTone,
    totalMinor: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val states = Toolbill.stateColors

    val content = when (tone) {
        DayTone.OUTSIDE -> scheme.outlineVariant
        DayTone.SELECTED -> scheme.onPrimaryContainer
        DayTone.TODAY -> scheme.onSurface
        DayTone.TRIAL -> states.trial.content
        DayTone.OVERDUE -> states.overdue.content
        DayTone.DUE_SOON -> states.dueSoon.content
        DayTone.PLAIN -> scheme.onSurfaceVariant
    }
    val fill = when (tone) {
        DayTone.SELECTED -> scheme.primaryContainer
        DayTone.DUE_SOON -> states.dueSoon.container
        else -> Color.Transparent
    }
    // Only today is outlined — it is a position marker, not a spend signal.
    val outlined = tone == DayTone.TODAY

    Box(
        modifier = modifier
            .height(CellHeight)
            .background(fill, CellShape)
            .then(if (outlined) Modifier.border(1.dp, scheme.primary, CellShape) else Modifier)
            .clickable(enabled = totalMinor > 0 || tone == DayTone.TODAY, onClick = onClick)
            .padding(top = 4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = date.dayOfMonth.toString(), style = ToolbillText.dayNumber, color = content)
            when {
                tone == DayTone.TODAY -> {
                    Spacer(Modifier.height(5.dp))
                    Text(text = "today", style = ToolbillText.dayTag, color = scheme.outline)
                }

                tone == DayTone.TRIAL -> {
                    Spacer(Modifier.height(6.dp))
                    Text(text = "trial", style = ToolbillText.dayTag, color = content)
                }

                totalMinor > 0 -> {
                    Spacer(Modifier.height(9.dp))
                    ChargeDots(
                        count = dotCount(totalMinor),
                        color = if (tone == DayTone.PLAIN) scheme.outline else content,
                    )
                }
            }
        }
    }
}

/** 4dp dots, 3dp apart — one per magnitude step. */
@Composable
private fun ChargeDots(count: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) {
            Box(Modifier.size(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun IntensityLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        listOf(1 to "<₹1k", 2 to "₹1–3k", 3 to "₹3k+").forEach { (dots, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChargeDots(dots, MaterialTheme.colorScheme.outline)
                Spacer(Modifier.size(Space.s2))
                Text(
                    text = label,
                    style = Toolbill.money.code.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
