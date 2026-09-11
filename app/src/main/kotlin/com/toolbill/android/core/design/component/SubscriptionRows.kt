package com.toolbill.android.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Sizes
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.feature.subscriptions.SubscriptionRowUi

/**
 * The dense list row.
 *
 * 52–56dp: name at titleMedium, one 11sp support line, amount right-aligned in tabular mono.
 * No leading monogram — dropping it buys three extra rows per screen and keeps the amount
 * column at a fixed x, which is the whole point of a 35-row list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DenseSubscriptionRow(
    row: SubscriptionRowUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dimmed = row.state == RowState.CANCELLED || row.state == RowState.PAUSED
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        // A long press that opens a sheet should confirm itself before the
                        // sheet animates in, or the gesture feels like it missed.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            )
            .defaultMinSize(minHeight = Sizes.rowDense)
            .padding(horizontal = Space.s4, vertical = Space.s2)
            .semantics(mergeDescendants = true) { contentDescription = row.spokenDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = ToolbillText.rowName,
                color = if (dimmed) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.badgeLabel != null) {
                    StateBadge(
                        state = row.state,
                        label = row.badgeLabel,
                        spokenLabel = row.badgeSpoken.orEmpty(),
                    )
                    Spacer(Modifier.width(Space.s1))
                }
                Text(
                    text = row.supportLine,
                    style = ToolbillText.rowSupport,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.showsBusinessGlyph) {
                    Spacer(Modifier.width(Space.s1))
                    Text(text = "·", style = ToolbillText.rowSupport, color = muted)
                    Spacer(Modifier.width(Space.s1))
                    BusinessGlyph(row.isBusiness, tint = muted)
                }
            }
        }

        Spacer(Modifier.width(Space.s2))

        Column(horizontalAlignment = Alignment.End) {
            val amountColor = when {
                dimmed -> muted
                row.state == RowState.OVERDUE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            val secondaryColor = when {
                row.state == RowState.TRIAL -> Toolbill.stateColors.trial.content
                else -> muted
            }
            ColumnAmount(
                amountMinor = row.normalizedMonthlyMinor,
                currency = row.homeCurrency,
                suffix = row.amountSuffix,
                color = amountColor,
            )
            if (row.secondaryLine != null) {
                Text(
                    text = row.secondaryLine,
                    style = Toolbill.money.rowSecondary,
                    color = secondaryColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The comfortable row with a monogram — used where there are at most a handful of rows.
 */
@Composable
fun SubscriptionListRow(
    row: SubscriptionRowUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Sizes.rowComfortable)
            .padding(horizontal = Space.s4, vertical = Space.s3)
            .semantics(mergeDescendants = true) { contentDescription = row.spokenDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(row.monogram)
        Spacer(Modifier.width(Space.s3))
        RowBody(row, Modifier.weight(1f))
        Spacer(Modifier.width(Space.s2))
        RowAmounts(row)
    }
}

/**
 * Home's next-7-days row: a date block instead of a monogram, because in that list the date is
 * what the reader is scanning for.
 */
@Composable
fun UpcomingChargeRow(
    row: SubscriptionRowUi,
    dayOfMonth: String,
    month: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Sizes.rowComfortable)
            .padding(horizontal = Space.s4, vertical = Space.s3)
            .semantics(mergeDescendants = true) {
                contentDescription = "$dayOfMonth $month. ${row.spokenDescription}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The day is amber: in this list the date is what the eye is scanning for.
        Column(
            modifier = Modifier.width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dayOfMonth,
                style = Toolbill.money.code.copy(fontSize = 14.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = month,
                style = Toolbill.money.code.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(Space.s3))
        RowBody(row, Modifier.weight(1f))
        Spacer(Modifier.width(Space.s2))
        RowAmounts(row)
    }
}

@Composable
private fun RowBody(row: SubscriptionRowUi, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Text(
            text = row.name,
            style = ToolbillText.rowName,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.badgeLabel != null) {
                StateBadge(row.state, row.badgeLabel, row.badgeSpoken.orEmpty())
                Spacer(Modifier.width(Space.s1))
            }
            Text(
                text = row.supportLine,
                style = ToolbillText.rowSupport,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.showsBusinessGlyph) {
                Spacer(Modifier.width(Space.s1))
                Text("·", style = ToolbillText.rowSupport, color = muted)
                Spacer(Modifier.width(Space.s1))
                BusinessGlyph(row.isBusiness, tint = muted)
            }
        }
    }
}

@Composable
private fun RowAmounts(row: SubscriptionRowUi) {
    Column(horizontalAlignment = Alignment.End) {
        val amountColor = when {
            row.state == RowState.CANCELLED || row.state == RowState.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
            row.state == RowState.OVERDUE -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
        val secondaryColor = when {
            row.state == RowState.TRIAL -> Toolbill.stateColors.trial.content
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        ColumnAmount(
            amountMinor = row.normalizedMonthlyMinor,
            currency = row.homeCurrency,
            suffix = row.amountSuffix,
            color = amountColor,
        )
        if (row.secondaryLine != null) {
            Text(
                text = row.secondaryLine,
                style = Toolbill.money.rowSecondary,
                color = secondaryColor,
                maxLines = 1,
            )
        }
    }
}

/** The 32dp two-letter monogram. No logos — nothing is fetched from a server. */
@Composable
fun Monogram(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.monogram)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, Radius.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = ToolbillText.monogram,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The column head that declares the currency once for the whole list. */
@Composable
fun AmountColumnHeader(
    leading: String,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4, vertical = Space.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = leading,
            style = ToolbillText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = trailing,
            style = ToolbillText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
