package com.toolbill.android.feature.subscriptions

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
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.component.RowActionSheet
import com.toolbill.android.core.design.component.defaultRowActions
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.AmountColumnHeader
import com.toolbill.android.core.design.component.DenseSubscriptionRow
import com.toolbill.android.core.design.component.EmptyState
import com.toolbill.android.core.design.component.Filter
import com.toolbill.android.core.design.component.FilterChipRow
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.TotalFooter
import com.toolbill.android.core.domain.date.nextChargeDate
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val sortOptions = listOf("Cost", "Renewal", "Name")
private val sortCaptions = listOf("high → low · /mo", "soonest first", "A → Z")

@Composable
fun AllSubscriptionsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    subscriptions: List<PricedSubscription> = SampleData.eightItemList,
    onOpenSubscription: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onImport: () -> Unit = {},
    onAction: (String, String) -> Unit = { _, _ -> },
    query: String = "",
) {
    var sheetFor by remember { mutableStateOf<PricedSubscription?>(null) }
    val home = SampleData.HOME_CURRENCY
    val today = SampleData.today

    var filterIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }

    val businessCount = subscriptions.count { it.subscription.isBusiness }
    val personalCount = subscriptions.size - businessCount
    val filters = listOf(
        Filter("All"),
        Filter("Business", businessCount.takeIf { it > 0 }),
        Filter("Personal", personalCount.takeIf { it > 0 }),
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

    if (sorted.isEmpty()) {
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
            SortSelector(sortOptions, sortIndex, onSelect = { sortIndex = it })
            Text(
                text = sortCaptions[sortIndex],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s1),
            )
            Spacer(Modifier.height(Space.s2))
            // The currency is declared once here and never repeated on 35 rows, which keeps
            // the digits in one tabular block.
            AmountColumnHeader(leading = "SERVICE", trailing = "₹ / MONTH")
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
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

        item {
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
                onMarkPaid = { onAction("Marked $name as paid", priced.subscription.id) },
                onSkip = { onAction("Skipped this charge for $name", priced.subscription.id) },
                onPause = { onAction("Paused $name", priced.subscription.id) },
                onDuplicate = { onAction("Duplicated $name", priced.subscription.id) },
                onEdit = { onOpenSubscription(priced.subscription.id) },
                onDelete = { onAction("Deleted $name", priced.subscription.id) },
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
