package com.toolbill.android.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText

// Measurements taken from the drawn Buttons block of the design document:
//   filled / outlined : padding 10 × 22, radius 12
//   text / destructive text : padding 10 × 14, radius 12
//   destructive confirm : padding 10 × 20, radius 12
//   extended FAB : padding 14 × 20, radius 16, 8dp gap
private val FilledPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
private val TextPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
private val DestructivePadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
private val FabPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

private val NoElevation
    @Composable get() = ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        disabledElevation = 0.dp,
    )

/** The filled button. 40dp tall, 12dp corners, no shadow. */
@Composable
fun ToolbillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.md,
        elevation = NoElevation,
        contentPadding = FilledPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/** The outlined button. Same box as the filled one, a 1dp outline instead of a fill. */
@Composable
fun ToolbillOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.md,
        contentPadding = FilledPadding,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/** The text button. Tighter horizontal padding than the filled and outlined pair. */
@Composable
fun ToolbillTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.md,
        contentPadding = TextPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/**
 * The destructive confirm.
 *
 * An error *container* fill rather than a solid error fill: it is the last of three actions in
 * a dialog, and a saturated red button reads as the default one.
 */
@Composable
fun ToolbillDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.md,
        elevation = NoElevation,
        contentPadding = DestructivePadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/** The destructive text action. */
@Composable
fun ToolbillDestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ToolbillTextButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    color = MaterialTheme.colorScheme.error,
)

/**
 * The extended FAB — 48dp tall, not Material's 56dp, with 16dp corners and no shadow.
 *
 * Built from a Surface because ExtendedFloatingActionButton fixes its own height and content
 * padding, and neither matches the drawn component.
 */
@Composable
fun ToolbillExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.lg,
        color = if (pressed) container.copy(alpha = 0.88f) else container,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(FabPadding),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text, style = ToolbillText.button)
        }
    }
}

// The onboarding / sheet action bar uses a taller, full-width primary: `padding:13px 0`
// (46dp) stretched across the row, with its text-button siblings at `padding:13px 16px`.
private val ActionBarPrimaryPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
private val ActionBarTextPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)

/** The full-width primary action that closes an onboarding step. 46dp tall, centred label. */
@Composable
fun ToolbillActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Radius.md,
        elevation = NoElevation,
        contentPadding = ActionBarPrimaryPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/** The text action that sits beside [ToolbillActionButton] in an action bar. */
@Composable
fun ToolbillActionTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.md,
        contentPadding = ActionBarTextPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text, style = ToolbillText.button)
    }
}

/** An icon-only action, sized to the design's 40dp touch box. */
@Composable
fun ToolbillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * How far a screen has scrolled, reduced to the two facts a header needs.
 *
 * [lifted] once anything has moved under the bar — the bar then needs a fill so rows do not
 * show through it. [titleVisible] once the screen's own headline has scrolled past, which is
 * when the bar has to say where you are.
 */
@Immutable
data class HeaderScroll(val lifted: Boolean, val titleVisible: Boolean)

/** Derives [HeaderScroll] from a raw pixel offset. */
@Composable
fun rememberHeaderScroll(offsetPx: () -> Int, titleRevealPx: Int = 96): HeaderScroll {
    val density = LocalDensity.current
    val threshold = with(density) { titleRevealPx.dp.roundToPx() }
    val state by remember(threshold) {
        derivedStateOf {
            val offset = offsetPx()
            HeaderScroll(lifted = offset > 2, titleVisible = offset > threshold)
        }
    }
    return state
}

/** Derives [HeaderScroll] from a [LazyListState]. */
@Composable
fun rememberHeaderScroll(listState: LazyListState, titleRevealPx: Int = 96): HeaderScroll =
    rememberHeaderScroll(
        offsetPx = {
            if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE
            else listState.firstVisibleItemScrollOffset
        },
        titleRevealPx = titleRevealPx,
    )

/**
 * The header every pushed screen shares: a pinned 52dp bar, a back arrow, a 17sp title.
 *
 * Pinned rather than hide-on-scroll. On these screens the bar holds the only non-gesture way
 * out, and reclaiming 52dp is not worth hiding the exit. Instead it earns its keep by staying
 * transparent at rest and taking a fill and a hairline only once content is behind it — and by
 * promoting the title in only after the screen's own headline has scrolled away, so the bar is
 * never repeating what is already on screen.
 */
@Composable
fun ScreenHeader(
    title: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Close rather than back where the screen is a dismissible overlay, like the paywall. */
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    backDescription: String = "Back",
    scroll: HeaderScroll = HeaderScroll(lifted = false, titleVisible = true),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (scroll.lifted) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "headerFill",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (title.isNotEmpty() && scroll.titleVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "headerTitle",
    )

    Column(modifier = modifier.fillMaxWidth().background(background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = backIcon,
                contentDescription = backDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .size(20.dp),
            )
            if (title.isNotEmpty()) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = ToolbillText.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).alpha(titleAlpha),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        if (scroll.lifted) {
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }
    }
}
