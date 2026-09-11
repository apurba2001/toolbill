package com.toolbill.android.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.ToolbillText

/**
 * Chip geometry, taken from the drawn design rather than the token table.
 *
 * The token table lists "full 28 · chips", but no chip anywhere in the design — component sheet
 * or screen frame — is drawn as a pill. Every one is `border-radius: 8px` with `padding:
 * 6px 13px`. The drawing wins; the token line is stale.
 */
private val ChipShape = RoundedCornerShape(8.dp)

/** One filter chip: a label and an optional count, as in `Business 29`. */
data class Filter(val label: String, val count: Int? = null)

/**
 * A filter chip.
 *
 * The selected state fills with primaryContainer and prefixes a check — Material's FilterChip
 * puts the check in a leading icon slot and keeps the outline, which reads differently.
 */
@Composable
fun ToolbillFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheckmark: Boolean = true,
    onClick: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        selected -> scheme.primary
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> scheme.outline
        selected -> scheme.onPrimary
        else -> scheme.onSurface
    }
    val outline = when {
        !enabled -> scheme.outlineVariant
        else -> scheme.outline
    }

    Box(
        modifier = modifier
            .clip(ChipShape)
            .then(if (selected) Modifier.background(container) else Modifier.border(1.dp, outline, ChipShape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected && showCheckmark) {
                CheckMark(color = content)
                Spacer(Modifier.width(5.dp))
            }
            Text(text = label, style = ToolbillText.chip, color = content)
        }
    }
}

/**
 * The selected-chip tick, drawn rather than typed.
 *
 * IBM Plex has no U+2713, so the character falls back to whatever the system offers and lands
 * looking like a radical sign. Drawing it keeps the weight consistent with the label beside it.
 */
@Composable
private fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = Offset(0f, h * 0.55f),
            end = Offset(w * 0.36f, h * 0.92f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.36f, h * 0.92f),
            end = Offset(w, h * 0.12f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** The horizontally scrolling filter row. */
@Composable
fun FilterChipRow(
    filters: List<Filter>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        filters.forEachIndexed { index, filter ->
            ToolbillFilterChip(
                label = filter.count?.let { "${filter.label} $it" } ?: filter.label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * The sort control.
 *
 * A segmented control rather than a menu: three options, always visible, one tap. 34dp tall
 * with 8dp corners and 1dp internal dividers — and, unlike Material's version, no check on the
 * selected segment. The fill is the signal; a tick as well is one signal too many at 12sp.
 */
@Composable
fun SortSelector(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = Space.s4,
    /**
     * 34dp for the sort control (`padding:9px 0`, 12/16 text); 40dp for the onboarding choice
     * (`padding:11px 0`, 13/18 text). The design uses both.
     */
    height: androidx.compose.ui.unit.Dp = 34.dp,
    textStyle: androidx.compose.ui.text.TextStyle = ToolbillText.chip,
    /**
     * Equal segments spanning the available width — the shape everywhere the control is the
     * only thing on its row. `false` sizes each segment to its label, for the one place it
     * shares a row with a caption. It defaults to the common case: an un-weighted Row of
     * segments in an unbounded parent overflows and clips its last option.
     */
    fillWidth: Boolean = true,
    onSelect: (Int) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .padding(horizontal = horizontalPadding)
            .height(height)
            .clip(ChipShape)
            .border(1.dp, scheme.outline, ChipShape),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(height)
                        .background(scheme.outline),
                )
            }
            Box(
                modifier = Modifier
                    .height(height)
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .background(if (selected) scheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = textStyle,
                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                )
            }
        }
    }
}
