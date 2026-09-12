package com.toolbill.android.feature.subscriptions

import com.toolbill.android.core.domain.subscription.PricedSubscription
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.component.RowActionSheet
import com.toolbill.android.core.design.component.defaultRowActions
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.AmountColumnHeader
import com.toolbill.android.core.design.component.DenseSubscriptionRow
import androidx.compose.foundation.layout.Column
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.component.HeroAmount
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.EmptyState
import com.toolbill.android.core.design.component.Filter
import com.toolbill.android.core.design.component.FilterChipRow
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.TotalFooter
import com.toolbill.android.core.domain.date.nextChargeDate
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val sortOptions = listOf("Cost", "Renewal", "Name")
private val sortCaptions = listOf("high → low · /mo", "soonest first", "A → Z")

@Composable
fun AllSubscriptionsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    subscriptions: List<PricedSubscription> = emptyList(),
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
    onOpenSubscription: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onImport: () -> Unit = {},
    onMarkPaid: (PricedSubscription) -> Unit = {},
    onSkipCharge: (PricedSubscription) -> Unit = {},
    onTogglePause: (PricedSubscription) -> Unit = {},
    onDuplicate: (PricedSubscription) -> Unit = {},
    onEdit: (PricedSubscription) -> Unit = {},
    onDelete: (PricedSubscription) -> Unit = {},
    onClearSearch: () -> Unit = {},
    query: String = "",
) {
    var sheetFor by remember { mutableStateOf<PricedSubscription?>(null) }
    val home = homeCurrency

    var filterIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }

    val businessCount = subscriptions.count { it.subscription.isBusiness }
    val personalCount = subscriptions.size - businessCount
    val filters = listOf(
        Filter("All"),
        Filter("Business"),
        Filter("Personal"),
        Filter("Cancelled"),
        Filter("Paused"),
    )

    // Search spans name, category and notes — the design's three fields. A query narrows
    // whatever the chips already selected rather than replacing it.
    val searched = remember(query, subscriptions) {
        if (query.isBlank()) subscriptions else subscriptions.filter { priced ->
            val s = priced.subscription
            val haystack = listOfNotNull(s.name, s.categoryLabel, s.notes).joinToString(" ")
            haystack.contains(query.trim(), ignoreCase = true)
        }
    }

    val filtered = remember(filterIndex, searched) {
        searched.filter { priced ->
            when (filterIndex) {
                1 -> priced.subscription.isBusiness
                2 -> !priced.subscription.isBusiness
                3 -> priced.subscription.status == SubscriptionStatus.CANCELLED
                4 -> priced.subscription.status == SubscriptionStatus.PAUSED
                else -> true
            }
        }
    }

    val sorted = remember(filtered, sortIndex) {
        // Live rows always rank above paused and cancelled ones, whatever the chosen sort. The
        // footer totals "across N active", so the rows it counts have to be the ones on top.
        val byRank = compareBy<PricedSubscription> { it.subscription.status.listRank }
        when (sortIndex) {
            1 -> filtered.sortedWith(
                byRank.thenBy { priced ->
                    val s = priced.subscription
                    nextChargeDate(s.anchorDate, s.cycle.unit, s.cycle.count, today.minusDays(1))
                },
            )

            2 -> filtered.sortedWith(byRank.thenBy { it.subscription.name.lowercase() })
            // Cost: every list in the app ranks on the normalized monthly figure, which is what
            // lets an annual ₹4,662 plan sort correctly against a ₹388 monthly one.
            else -> filtered.sortedWith(
                byRank.thenByDescending {
                    normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle)
                },
            )
        }
    }

    // Only when there is genuinely nothing tracked. A search or a filter that matches nothing is
    // a different situation with a different answer, and showing "No subscriptions yet" over a
    // portfolio of thirty was both untrue and alarming -- it took the filter row away with it,
    // so the control that emptied the list was the one control the user could no longer reach.
    if (subscriptions.isEmpty()) {
        EmptyState(
            amountMinor = 0,
            currency = home,
            headline = "No subscriptions yet.",
            body = "Add them as you notice the charges — you don't have to do all thirty in " +
                "one sitting. Unlimited entries, free, forever.",
            primaryAction = "Add subscription",
            secondaryAction = "Import from CSV",
            modifier = modifier.padding(contentPadding),
            onPrimary = onAdd,
            onSecondary = onImport,
        )
        return
    }

    val active = sorted.filter { it.subscription.status == SubscriptionStatus.ACTIVE }
    val activeTotal = active.sumOf {
        normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle)
    }
    val trials = sorted.filter { it.subscription.status == SubscriptionStatus.TRIAL }
    val paused = sorted.filter { it.subscription.status == SubscriptionStatus.PAUSED }
    val cancelled = sorted.filter { it.subscription.status == SubscriptionStatus.CANCELLED }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Spacer(Modifier.height(Space.s2))
            FilterChipRow(filters, filterIndex, onSelect = { filterIndex = it })
            Spacer(Modifier.height(Space.s3))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortSelector(
                    options = sortOptions,
                    selectedIndex = sortIndex,
                    horizontalPadding = 0.dp,
                    // Shares its row with the sort caption, so the segments size to their
                    // labels rather than pushing the caption off the right edge.
                    fillWidth = false,
                    onSelect = { sortIndex = it },
                )
                Text(
                    text = sortCaptions[sortIndex],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.s2))
            // The currency is declared once here and never repeated on 35 rows, which keeps
            // the digits in one tabular block.
            // The home currency's own symbol. This column header was a hardcoded rupee sign,
            // so every figure beneath it was labelled in a currency the user may never have
            // chosen -- on a GBP install it read "Rs / MONTH" over pounds.
            AmountColumnHeader(
                leading = "SERVICE",
                trailing = "${MoneyFormat.format(0L, home).symbol} / MONTH",
            )
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        if (sorted.isEmpty()) {
            item {
                NoMatches(
                    query = query,
                    filterLabel = filters.getOrNull(filterIndex)?.label.takeIf { filterIndex != 0 },
                    hiddenCount = subscriptions.size,
                    // What the filter is hiding, on the same normalized basis every other total
                    // in the app uses.
                    hiddenMonthlyMinor = subscriptions
                        .filter { it.subscription.status.countsTowardBurn }
                        .sumOf { normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle) },
                    homeCurrency = home,
                    onClearFilter = { filterIndex = 0 },
                    onClearSearch = onClearSearch,
                )
            }
        }

        items(sorted, key = { it.subscription.id }) { priced ->
            DenseSubscriptionRow(
                row = priced.subscription.toRowUi(
                    today = today,
                    homeCurrency = home,
                    homeAmountMinor = priced.homeAmountMinor,
                    overdueSince = priced.overdueSince,
                ),
                onClick = { onOpenSubscription(priced.subscription.id) },
                onLongClick = { sheetFor = priced },
            )
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        if (sorted.isNotEmpty()) item {
            TotalFooter(
                primary = "${MoneyFormat.symbol(activeTotal, home)} / month across " +
                    "${active.size} active",
                caption = buildExclusionCaption(trials, paused, cancelled, home),
                endInset = 96.dp,
            )
        }
    }

    sheetFor?.let { priced ->
        val name = priced.subscription.name
        RowActionSheet(
            title = name,
            subtitle = MoneyFormat.symbol(priced.homeAmountMinor, home) + " · " +
                priced.subscription.cycle.label(),
            actions = defaultRowActions(
                onMarkPaid = { onMarkPaid(priced) },
                onSkip = { onSkipCharge(priced) },
                onPause = { onTogglePause(priced) },
                onDuplicate = { onDuplicate(priced) },
                onEdit = { onEdit(priced) },
                onDelete = { onDelete(priced) },
                paused = priced.subscription.status == SubscriptionStatus.PAUSED,
            ),
            onDismiss = { sheetFor = null },
        )
    }
}

/**
 * Names what the total leaves out, and what it counts at a monthly share.
 *
 * Stated rather than implied: a total that silently excludes three rows the user can see is
 * the fastest way to lose their trust in every other figure in the app.
 */
private fun buildExclusionCaption(
    trials: List<PricedSubscription>,
    paused: List<PricedSubscription>,
    cancelled: List<PricedSubscription>,
    home: String,
): String {
    val parts = mutableListOf<String>()
    if (trials.isNotEmpty()) {
        val detail = trials.joinToString(", ") { priced ->
            val monthly = MoneyFormat.symbolWhole(
                normalizedMonthlyMinor(priced.homeAmountMinor, priced.subscription.cycle),
                home,
            )
            priced.subscription.trialEndDate
                ?.let { "$monthly from ${it.format(dayMonthFormat)}" }
                ?: monthly
        }
        parts += "${trials.size} trial ($detail)"
    }
    if (paused.isNotEmpty()) {
        val detail = paused.joinToString(", ") { MoneyFormat.symbolWhole(it.homeAmountMinor, home) }
        parts += "${paused.size} paused ($detail)"
    }
    if (cancelled.isNotEmpty()) {
        val detail =
            cancelled.joinToString(", ") { MoneyFormat.symbolWhole(it.homeAmountMinor, home) }
        parts += "${cancelled.size} cancelled ($detail)"
    }
    if (parts.isEmpty()) return "annual plans counted at their monthly share"
    val joined = when (parts.size) {
        1 -> parts.first()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
    return "excludes $joined · annual plans counted at their monthly share"
}

/**
 * What to show when the list is empty because of the controls above it, not because of the data.
 *
 * Built like the real empty state rather than as a line of centred text: the same dimmed hero,
 * the same left-aligned headline and body, the same button. A sentence floating in the middle of
 * a blank screen reads as something having gone wrong, which is the opposite of what has
 * happened — the portfolio is intact and one filter is hiding it.
 *
 * The hero shows the burn the filter is currently hiding, so the figure on screen is the answer
 * to "where did everything go".
 */
@Composable
private fun NoMatches(
    query: String,
    filterLabel: String?,
    hiddenCount: Int,
    hiddenMonthlyMinor: Long,
    homeCurrency: String,
    onClearFilter: () -> Unit,
    onClearSearch: () -> Unit,
) {
    // ROOT, not the device locale. These labels are fixed English tokens defined in this
    // file, so there is nothing to localise -- and reading the device locale inside a composable
    // is not observable, so a locale change would not recompose this text anyway.
    val lowerFilter = filterLabel?.lowercase(Locale.ROOT)
    val trimmed = query.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4, vertical = Space.s8),
    ) {
        HeroAmount(
            amountMinor = hiddenMonthlyMinor,
            currency = homeCurrency,
            alpha = 0.5f,
            modifier = Modifier.padding(vertical = Space.s2),
        )
        Spacer(Modifier.height(Space.s4))
        Text(
            text = when {
                trimmed.isNotEmpty() && lowerFilter != null ->
                    "Nothing $lowerFilter matches \"$trimmed\"."

                trimmed.isNotEmpty() -> "Nothing matches \"$trimmed\"."
                lowerFilter != null -> "Nothing is $lowerFilter right now."
                else -> "Nothing to show."
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            text = when {
                trimmed.isNotEmpty() ->
                    "Search looks at names, categories and notes. " +
                        "You track ${plural(hiddenCount, "subscription")} in total."

                else -> "Your ${plural(hiddenCount, "subscription")} " +
                    (if (hiddenCount == 1) "is" else "are") + " still here. None of them " +
                    (if (hiddenCount == 1) "is" else "are") + " $lowerFilter."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.s6))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            if (filterLabel != null) {
                ToolbillButton(
                    text = "Show all $hiddenCount",
                    onClick = onClearFilter,
                )
            }
            if (trimmed.isNotEmpty()) {
                if (filterLabel != null) {
                    ToolbillTextButton(text = "Clear search", onClick = onClearSearch)
                } else {
                    ToolbillButton(text = "Clear search", onClick = onClearSearch)
                }
            }
        }
    }
}

/** "1 subscription", "5 subscriptions". */
private fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"
