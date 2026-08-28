package com.toolbill.android.feature.subscriptions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.StatRow
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillDestructiveTextButton
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.RowState
import com.toolbill.android.core.design.component.Stat
import com.toolbill.android.core.design.component.StatBadgeRow
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import java.util.Locale

/**
 * One recorded charge.
 *
 * [fxRate] is null for charges the user backfilled from before install. We do not invent a
 * rate, and those rows stay out of the drift figure entirely.
 */
data class ChargeHistoryEntry(
    val date: String,
    val amountMinor: Long,
    val fxRate: String?,
    val note: String? = null,
)

private val sampleHistory = listOf(
    ChargeHistoryEntry("27 Jul 2026", 172_840, "86.42"),
    ChargeHistoryEntry("27 Jun 2026", 171_800, "85.90"),
    ChargeHistoryEntry("27 May 2026", 170_220, "85.11"),
    ChargeHistoryEntry("27 Apr 2026", 169_200, "84.60", note = "first capture"),
    ChargeHistoryEntry("27 Mar 2026", 168_000, null, note = "backfilled"),
    ChargeHistoryEntry("27 Feb 2026", 0, null, note = "backfilled"),
)

@Composable
fun SubscriptionDetailScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    priced: PricedSubscription = SampleData.claude,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val home = SampleData.HOME_CURRENCY
    val subscription = priced.subscription
    var confirmingDelete by remember { mutableStateOf(false) }

    val row = subscription.toRowUi(
        today = SampleData.today,
        homeCurrency = home,
        homeAmountMinor = priced.homeAmountMinor,
        overdueSince = priced.overdueSince,
    )
    val detailAmount = MoneyFormat.format(
        normalizedMonthlyMinor(priced.homeAmountMinor, subscription.cycle),
        home,
    )
    val originalAmount = MoneyFormat.format(subscription.amountMinor, subscription.currency)
        .let { "${it.code} ${it.plain}" }
    val foreignBilled = subscription.currency.uppercase(Locale.ENGLISH) != home.uppercase(Locale.ENGLISH)
    val classification = buildString {
        append(if (subscription.isBusiness) "BUSINESS" else "PERSONAL")
        append(" · ")
        append(subscription.categoryLabel.uppercase(Locale.ENGLISH))
    }

    val listState = rememberLazyListState()
    // The bar shows actions only until the 24sp name scrolls under it, then adopts the name.
    val headerScroll = rememberHeaderScroll(listState, titleRevealPx = 92)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = subscription.name,
            onBack = onBack,
            scroll = headerScroll,
        ) {
            ToolbillTextButton(text = "Edit", onClick = onEdit)
            ToolbillIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = "More actions",
                onClick = {},
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {

        item {
            Column(Modifier.padding(horizontal = Space.s4)) {
                // Every value here is derived from the subscription actually being shown. A
                // header that states one service's name over another's badge is worse than no
                // header at all.
                StatBadgeRow(
                    state = row.state,
                    badge = row.badgeLabel,
                    spoken = row.badgeSpoken ?: row.supportLine,
                    trailing = classification,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                // Mono at 36sp for the figure only; the qualifier stays at body size on the
                // same baseline, or the header wraps to two lines and swamps the screen.
                // Code, figure, minor unit and qualifier all share one baseline; only the
                // figure is 36sp, so the row reads as a single number with annotations.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = detailAmount.code,
                        style = Toolbill.money.heroCode,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Row {
                        Text(
                            text = detailAmount.integer,
                            style = Toolbill.money.detailHeader,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline(),
                        )
                        detailAmount.fraction?.let {
                            Text(
                                text = detailAmount.decimalSeparator + it,
                                style = Toolbill.money.detailFraction,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    Text(
                        text = "/ month" + if (foreignBilled) " · $originalAmount" else "",
                        style = ToolbillText.detailQualifier,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            StatRow(
                stats = listOf(
                    Stat("PAID · CAPTURED", MoneyFormat.format(684_060, home).plain),
                    Stat("CHARGES", "4 + 7"),
                    Stat("SINCE", "Oct 25"),
                ),
            )
        }

        // Only a foreign-billed subscription has drift to show. For an INR plan the whole
        // block would be a flat line labelled "what ₹1,020 cost", which says nothing.
        if (foreignBilled) item {
            // The drift block is a bordered card — it has its own provenance and its own
            // time axis, so it is a separable object rather than another section.
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.md)
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "What $originalAmount actually cost",
                        style = ToolbillText.detailCardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "+3.0% since 27 Apr",
                        style = Toolbill.money.code,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FxSparkline()
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = "₹1,692.00 · 27 Apr 26",
                        style = Toolbill.money.code.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "₹1,742.00 · 27 Aug 26",
                        style = Toolbill.money.code.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "Starts at the first rate Toolbill captured itself — install day, " +
                    "12 Apr 2026. Nothing before that is drawn, because nothing before that " +
                    "was measured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4),
            )
        }

        item {
            Spacer(Modifier.height(Space.s8))
            Text(
                text = "Payment history",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
            )
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        items(sampleHistory, key = { it.date }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4, vertical = Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = listOfNotNull(
                            entry.note,
                            entry.fxRate?.let { "rate $it" } ?: "rate —",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (entry.amountMinor == 0L) {
                        "—"
                    } else {
                        MoneyFormat.format(entry.amountMinor, home).plain
                    },
                    style = Toolbill.money.row,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        item {
            ToolbillTextButton(
                text = "Show all 11 charges",
                onClick = {},
                modifier = Modifier.padding(horizontal = Space.s2),
            )
            Text(
                text = "4 captured by Toolbill · 7 backfilled by you before install. " +
                    "Backfilled rows carry no rate — we don't invent one, and they stay out " +
                    "of the drift figure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4),
            )
        }

        item {
            Spacer(Modifier.height(Space.s8))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                ToolbillTextButton(text = "Mark cancelled", onClick = {})
                ToolbillDestructiveTextButton(
                    text = "Delete",
                    onClick = { confirmingDelete = true },
                )
            }
            Spacer(Modifier.height(Space.s12))
        }
        }
    }

    if (confirmingDelete) {
        DeleteConfirmation(
            name = subscription.name,
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * Delete is the last of three actions and the only red one.
 *
 * The middle action offers the safer thing the user probably wanted — no "are you sure"
 * theatre, just the cheaper alternative stated plainly, with the cost of the destructive
 * option spelled out in the figures it would destroy.
 */
@Composable
private fun DeleteConfirmation(name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = {
            Text(
                "This removes the subscription and all 11 recorded charges — ₹19,004 of " +
                    "history that your CSV export uses. Marking it cancelled instead keeps " +
                    "the record for tax purposes.",
            )
        },
        dismissButton = {
            Row {
                ToolbillTextButton(text = "Cancel", onClick = onDismiss)
                ToolbillTextButton(text = "Mark cancelled", onClick = onDismiss)
            }
        },
        confirmButton = {
            ToolbillDestructiveTextButton(text = "Delete", onClick = onDismiss)
        },
    )
}

@Composable
private fun FxSparkline() {
    val points = listOf(0.0f, 0.22f, 0.55f, 0.78f, 1.0f)
    val line = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .height(56.dp)
            .semantics {
                contentDescription = "Rate rose from ₹1,692 in April to ₹1,742 in August, " +
                    "up 3.0 percent"
            },
    ) {
        val stepX = size.width / (points.size - 1)
        points.forEachIndexed { index, value ->
            if (index == 0) return@forEachIndexed
            drawLine(
                color = line,
                start = Offset((index - 1) * stepX, size.height * (1f - points[index - 1]) * 0.9f),
                end = Offset(index * stepX, size.height * (1f - value) * 0.9f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
