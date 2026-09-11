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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.toolbill.android.core.design.ToolbillIcons
import com.toolbill.android.core.domain.subscription.PricedSubscription
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
import com.toolbill.android.core.design.component.ToolbillOutlinedButton
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.RowState
import com.toolbill.android.core.design.component.Stat
import com.toolbill.android.core.design.component.StatBadgeRow
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val chargeDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val sinceFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)

/** How many charges the history shows before it offers the rest. */
private const val HISTORY_PREVIEW = 6

@Composable
fun SubscriptionDetailScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    priced: PricedSubscription,
    charges: List<Charge> = emptyList(),
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMarkCancelled: () -> Unit = {},
) {
    val home = homeCurrency
    val subscription = priced.subscription
    var confirmingDelete by remember { mutableStateOf(false) }
    var showAllCharges by remember { mutableStateOf(false) }

    // Only charges carrying a captured home figure can be summed or plotted. A row without one
    // is still shown -- it happened -- but as a dash, never converted at a rate that was not in
    // force when it was taken.
    //
    // A skipped charge is excluded as well. It carries a real, known home figure of zero, so it
    // would sum and plot happily -- as a total dragged below what was actually paid and a drift
    // line dropping to the floor on a month nothing was billed.
    val captured = remember(charges) {
        charges.filter { it.captured && it.countsTowardSpend }
    }
    val capturedTotal = remember(captured) { captured.sumOf { it.homeAmountMinor } }
    val firstCharge = remember(charges) { charges.minByOrNull { it.dueDate }?.dueDate }
    // Drift needs at least two different captured figures. With one, the chart would be a flat
    // line implying a stability nothing has measured.
    val driftPoints = remember(captured) {
        captured.sortedBy { it.dueDate }.map { it.homeAmountMinor }
    }
    val hasDrift = driftPoints.distinct().size >= 2

    val row = subscription.toRowUi(
        today = today,
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

    val visibleCharges = if (showAllCharges) charges else charges.take(HISTORY_PREVIEW)

    val listState = rememberLazyListState()
    // The bar shows actions only until the 24sp name scrolls under it, then adopts the name.
    val headerScroll = rememberHeaderScroll(listState, titleRevealPx = 92)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = subscription.name,
            onBack = onBack,
            scroll = headerScroll,
        ) {
            ToolbillTextButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.offset(x = 10.dp)
            )
            ToolbillIconButton(
                icon = ToolbillIcons.More,
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
                // Code, figure, minor unit and qualifier all share one baseline; only the
                // figure is 36sp, so the row reads as a single number with annotations rather
                // than as one long run of digits.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = detailAmount.code,
                        style = Toolbill.money.heroCode,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                    // Nested so the minor unit sits hard against the figure: the 4dp the outer
                    // row spaces its annotations by would read as a gap inside the number.
                    Row(modifier = Modifier.alignByBaseline()) {
                        Text(
                            text = detailAmount.integer,
                            style = Toolbill.money.detailHeader,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline(),
                        )
                        if (detailAmount.fraction != null) {
                            Text(
                                text = detailAmount.decimalSeparator + detailAmount.fraction,
                                style = Toolbill.money.detailFraction,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    Text(
                        text = "/ month" + if (foreignBilled) " \u00B7 $originalAmount" else "",
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
                    Stat(
                        "PAID · CAPTURED",
                        if (captured.isEmpty()) "—" else MoneyFormat.format(capturedTotal, home).plain,
                    ),
                    Stat("CHARGES", charges.size.toString()),
                    Stat("SINCE", firstCharge?.format(sinceFormat) ?: "—"),
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
                    if (hasDrift) {
                        Text(
                            text = driftLabel(driftPoints),
                            style = Toolbill.money.code,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (hasDrift) {
                    val first = captured.minByOrNull { it.dueDate }!!
                    val last = captured.maxByOrNull { it.dueDate }!!
                    FxSparkline(
                        points = driftPoints,
                        spoken = "Cost rose from " +
                            "${MoneyFormat.symbol(first.homeAmountMinor, home)} in " +
                            "${first.dueDate.format(sinceFormat)} to " +
                            "${MoneyFormat.symbol(last.homeAmountMinor, home)} in " +
                            last.dueDate.format(sinceFormat),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = "${MoneyFormat.symbol(first.homeAmountMinor, home)} \u00B7 " +
                                first.dueDate.format(sinceFormat),
                            style = Toolbill.money.code.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${MoneyFormat.symbol(last.homeAmountMinor, home)} \u00B7 " +
                                last.dueDate.format(sinceFormat),
                            style = Toolbill.money.code.copy(fontSize = 9.5.sp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                } else {
                    Text(
                        text = "No movement recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(Space.s2))
            Text(
                // The spoken version of the chart lives on FxSparkline's semantics, not here.
                text = if (hasDrift) {
                    "Starts at the first rate Toolbill captured itself. Nothing before that is " +
                        "drawn, because nothing before that was measured."
                } else {
                    "A line appears once Toolbill has recorded this charge at two different " +
                        "rates. Nothing before the first capture is drawn, because nothing " +
                        "before it was measured."
                },
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

        if (charges.isEmpty()) {
            item {
                Text(
                    text = "No charges recorded yet. Toolbill writes one down each time this " +
                        "subscription falls due — the next is " +
                        "${row.supportLine.lowercase(Locale.ENGLISH)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s3),
                )
            }
        }

        itemsIndexed(visibleCharges, key = { _, charge -> charge.id }) { index, charge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4, vertical = Space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = charge.dueDate.format(chargeDateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (charge.captured) {
                            MoneyFormat.format(charge.amountMinor, charge.currency)
                                .let { "${it.code} ${it.plain}" }
                        } else {
                            "not captured"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (charge.captured) {
                        MoneyFormat.format(charge.homeAmountMinor, home).plain
                    } else {
                        "\u2014"
                    },
                    style = Toolbill.money.row,
                    color = if (charge.captured) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
            if (index < visibleCharges.lastIndex) {
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }
        }

        if (charges.size > HISTORY_PREVIEW) {
            item {
                ToolbillTextButton(
                    text = if (showAllCharges) {
                        "Show fewer"
                    } else {
                        "Show all ${charges.size} charges"
                    },
                    onClick = { showAllCharges = !showAllCharges },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(Space.s6))
            }
        } else {
            item { Spacer(Modifier.height(Space.s6)) }
        }

        item {
            Spacer(Modifier.height(Space.s8))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s2),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                // Only offered while there is something to cancel.
                if (subscription.status != SubscriptionStatus.CANCELLED) {
                    ToolbillOutlinedButton(text = "Mark cancelled", onClick = onMarkCancelled)
                }
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
            chargeCount = charges.size,
            capturedTotal = capturedTotal,
            homeCurrency = home,
            onDismiss = { confirmingDelete = false },
            onConfirm = { confirmingDelete = false; onDelete() },
            onMarkCancelled = { confirmingDelete = false; onMarkCancelled() },
        )
    }
}

/** "+3.0%" between the first and last captured figures. */
private fun driftLabel(points: List<Long>): String {
    val first = points.first()
    val last = points.last()
    if (first == 0L) return ""
    val tenths = (last - first) * 1000 / first
    val sign = if (tenths >= 0) "+" else "\u2212"
    val magnitude = kotlin.math.abs(tenths)
    return "$sign${magnitude / 10}.${magnitude % 10}%"
}

/**
 * Delete is the last of three actions and the only red one.
 *
 * The middle action offers the safer thing the user probably wanted — no "are you sure"
 * theatre, just the cheaper alternative stated plainly, with the cost of the destructive
 * option spelled out in the figures it would destroy.
 */
@Composable
private fun DeleteConfirmation(
    name: String,
    chargeCount: Int,
    capturedTotal: Long,
    homeCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onMarkCancelled: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = Radius.lg,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Delete $name?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    // The cost of the destructive option, in the figures it would destroy.
                    text = if (chargeCount == 0) {
                        "This removes the subscription. Nothing has been charged against it " +
                            "yet, so no history is lost. Marking it cancelled instead keeps it " +
                            "in your records for tax purposes."
                    } else {
                        "This removes the subscription and all $chargeCount recorded " +
                            (if (chargeCount == 1) "charge" else "charges") + " \u2014 " +
                            "${MoneyFormat.symbolWhole(capturedTotal, homeCurrency)} of history " +
                            "that your CSV export uses. Marking it cancelled instead keeps the " +
                            "record for tax purposes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ToolbillTextButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onMarkCancelled,
                        shape = Radius.md,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = "Mark\ncancelled",
                            style = ToolbillText.button,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    ToolbillDestructiveTextButton(
                        text = "Delete",
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun FxSparkline(points: List<Long>, spoken: String) {
    // Normalised to the observed range, so the shape shows the movement rather than the
    // distance from zero -- a 3% rise plotted from zero is a flat line.
    val low = points.min()
    val high = points.max()
    val span = (high - low).coerceAtLeast(1L).toFloat()
    val normalised = points.map { (it - low) / span }
    val line = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .height(56.dp)
            .semantics { contentDescription = spoken },
    ) {
        if (normalised.size < 2) return@Canvas
        val stepX = size.width / (normalised.size - 1)
        val firstY = size.height * (1f - normalised[0]) * 0.9f
        
        // Vertical dashed line at the start
        drawLine(
            color = outline,
            start = Offset(1.dp.toPx(), 0f),
            end = Offset(1.dp.toPx(), firstY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )

        normalised.forEachIndexed { index, value ->
            if (index == 0) return@forEachIndexed
            drawLine(
                color = line,
                start = Offset((index - 1) * stepX, size.height * (1f - normalised[index - 1]) * 0.9f),
                end = Offset(index * stepX, size.height * (1f - value) * 0.9f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        
        // Dot at the end
        val lastY = size.height * (1f - points.last()) * 0.9f
        drawCircle(
            color = line,
            radius = 3.dp.toPx(),
            center = Offset(size.width - 3.dp.toPx(), lastY)
        )
    }
}
