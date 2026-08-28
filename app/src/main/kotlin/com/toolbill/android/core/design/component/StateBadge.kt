package com.toolbill.android.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.StateColor
import com.toolbill.android.core.design.Toolbill

/**
 * The six display states of a subscription row.
 *
 * Six rather than four: an active subscription reads as active, due-soon or overdue depending
 * on how close its next charge is. That is a display concern derived from dates, never stored.
 */
enum class RowState {
    ACTIVE,
    DUE_SOON,
    OVERDUE,
    TRIAL,
    CANCELLED,
    PAUSED,
    ;

    /** Active needs no badge — the absence of one is the signal. */
    val hasBadge: Boolean get() = this != ACTIVE
}

@Composable
fun RowState.colors(): StateColor = with(Toolbill.stateColors) {
    when (this@colors) {
        RowState.ACTIVE -> active
        RowState.DUE_SOON -> dueSoon
        RowState.OVERDUE -> overdue
        RowState.TRIAL -> trial
        RowState.CANCELLED -> cancelled
        RowState.PAUSED -> paused
    }
}

/**
 * A state badge: `DUE 4d`, `OVERDUE 2d`, `TRIAL 6d`, `PAUSED`.
 *
 * [spokenLabel] carries the unabbreviated form — "Overdue by 2 days" — because the visual label
 * is a space-constrained abbreviation that a screen reader would otherwise spell out.
 */
@Composable
fun StateBadge(
    state: RowState,
    label: String,
    spokenLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = state.colors()
    Text(
        text = label,
        style = ToolbillText.badge,
        color = colors.content,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = spokenLabel }
            .background(colors.container, Radius.xs)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * The detail header's badge line: the state on the left, the classification on the right.
 */
@Composable
fun StatBadgeRow(
    state: RowState,
    badge: String?,
    spoken: String,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A badge marks a state worth acting on. An ordinary active subscription has none, and
        // dressing its support line as a chip invents urgency that is not there.
        if (badge != null) {
            StateBadge(state = state, label = badge, spokenLabel = spoken)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = trailing,
            style = Toolbill.money.code,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * The business/personal marker: a single mono glyph in the support line.
 *
 * With 35 rows on screen, 35 coloured chips would be noise, and the filter chips above already
 * carry the colour. One glyph costs nothing and stays legible.
 */
@Composable
fun BusinessGlyph(isBusiness: Boolean, modifier: Modifier = Modifier, tint: Color? = null) {
    Text(
        text = if (isBusiness) "B" else "P",
        style = ToolbillText.rowGlyph,
        color = tint ?: Toolbill.stateColors.active.content,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (isBusiness) "Business expense" else "Personal"
        },
    )
}
