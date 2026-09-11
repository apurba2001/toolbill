package com.toolbill.android.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill

/**
 * A flat labelled notice — an eyebrow in the gutter, the message beside it.
 *
 * Flat rather than a card: cards are reserved for content that is genuinely a separable object.
 * A trial that has not started charging yet is an annotation on the burn figure above it.
 */
@Composable
fun InlineNotice(
    eyebrow: String,
    message: String,
    modifier: Modifier = Modifier,
    eyebrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = eyebrow,
            style = Toolbill.money.code.copy(lineHeight = 17.sp),
            color = eyebrowColor,
        )
        Text(
            text = message,
            style = ToolbillText.noticeBody.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The FX notice — one of only two places in the app that uses a card, because a rate movement
 * really is a separable object with its own provenance.
 */
@Composable
fun NoticeCard(
    eyebrow: String,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    // Bordered and transparent, not a filled surface: on a near-black ground a fill reads as
    // elevation, and this notice is an annotation on the figure above rather than a raised card.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Text(
            text = eyebrow,
            style = Toolbill.money.code.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = accent,
        )
        Column {
            Text(
                text = headline,
                style = ToolbillText.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = ToolbillText.noticeBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Spacer(Modifier.height(Space.s2))
                action()
            }
        }
    }
}

/** A section header with a trailing figure — `Next 7 days   ₹14,446.00`. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.s4, end = Space.s4, top = Space.s6, bottom = Space.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title.uppercase(java.util.Locale.ROOT),
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = Toolbill.money.code,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One figure in the stat row under the hero. */
data class Stat(val label: String, val value: String)

/**
 * The stat row beneath the hero — annualized, business share, active count.
 *
 * Separated by 1px dividers rather than cards; hierarchy is carried by type size.
 */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            stats.forEach { stat ->
                Column {
                    Text(
                        text = stat.label,
                        style = ToolbillText.microLabel,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stat.value,
                        style = Toolbill.money.stat,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * The empty state.
 *
 * No illustration, no mascot. The zeroed hero figure is the illustration — and it is the same
 * component the populated screen uses, so the layout does not jump when data arrives.
 */
@Composable
fun EmptyState(
    amountMinor: Long,
    currency: String,
    headline: String,
    body: String,
    primaryAction: String,
    secondaryAction: String,
    modifier: Modifier = Modifier,
    onPrimary: () -> Unit = {},
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s12),
    ) {
        // The same component the populated screen uses, dimmed rather than replaced: a
        // hardcoded "0.00" would drop the currency and jump the layout on the first entry.
        HeroAmount(
            amountMinor = amountMinor,
            currency = currency,
            alpha = 0.5f,
            modifier = Modifier.padding(vertical = Space.s2),
        )
        Spacer(Modifier.height(Space.s4))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.s6))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
            ToolbillButton(text = primaryAction, onClick = onPrimary)
            ToolbillTextButton(text = secondaryAction, onClick = onSecondary)
        }
    }
}

/** The footer that repeats a total so the arithmetic closes in view. */
@Composable
fun TotalFooter(
    primary: String,
    caption: String,
    modifier: Modifier = Modifier,
    /**
     * Extra room on the trailing edge so the text never runs under a floating action button.
     * This block exists to let the arithmetic close in view; half-covered it does the opposite.
     */
    endInset: Dp = 0.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.s4,
                end = Space.s4 + endInset,
                top = Space.s4,
                bottom = Space.s8,
            ),
    ) {
        Text(
            text = primary,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s1))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
