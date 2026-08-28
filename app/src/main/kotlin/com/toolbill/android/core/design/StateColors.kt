package com.toolbill.android.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.toolbill.android.core.domain.subscription.SubscriptionStatus

/**
 * A subscription state's content colour and the container it fills its badge with.
 */
@Immutable
data class StateColor(
    val content: Color,
    val container: Color,
)

/**
 * The six subscription states.
 *
 * Six, not four: [SubscriptionStatus.ACTIVE] splits into active / due soon / overdue depending
 * on how close the next charge is, which is a display concern rather than a stored one.
 *
 * Cancelled and paused are deliberately colourless — they are records, not signals.
 */
@Immutable
data class ToolbillStateColors(
    val active: StateColor,
    val dueSoon: StateColor,
    val overdue: StateColor,
    val trial: StateColor,
    val cancelled: StateColor,
    val paused: StateColor,
    /**
     * Divider colour for the dense 35-row list.
     *
     * Deliberately much darker than `outlineVariant`: at 35 rows a bright rule between every
     * one of them competes with the amounts. Warm neutrals on paper-white lose their dividers
     * first, so light gets its own tone rather than inverting this one.
     */
    val dividerDense: Color,
)

/**
 * Dark state colours, straight from the design's contrast table. Every ratio there is measured
 * against the surface the badge actually sits on.
 */
val ToolbillDarkStateColors = ToolbillStateColors(
    active = StateColor(content = Color(0xFFA9A39A), container = Color(0xFF1E1D1B)),
    dueSoon = StateColor(content = Color(0xFFF2C35C), container = Color(0xFF3B2E08)),
    overdue = StateColor(content = Color(0xFFFF6B5E), container = Color(0xFF4A1611)),
    trial = StateColor(content = Color(0xFF7FD4C1), container = Color(0xFF0F3B34)),
    cancelled = StateColor(content = Color(0xFF8F8A81), container = Color(0xFF161514)),
    paused = StateColor(content = Color(0xFF9FB4D8), container = Color(0xFF1D2530)),
    dividerDense = Color(0xFF1E1D1B),
)

/**
 * Light state colours.
 *
 * The design specifies dueSoon, trial, cancelled and dividerDense outright; the remaining
 * content/container pairs are re-picked here against paper-white rather than flipped.
 */
val ToolbillLightStateColors = ToolbillStateColors(
    active = StateColor(content = Color(0xFF55524A), container = Color(0xFFF2F0EA)),
    dueSoon = StateColor(content = Color(0xFF8A6A00), container = Color(0xFFFFE9BC)),
    overdue = StateColor(content = Color(0xFFB3261E), container = Color(0xFFFFDAD5)),
    trial = StateColor(content = Color(0xFF0B5A4E), container = Color(0xFFA8F0E0)),
    cancelled = StateColor(content = Color(0xFF6F6A62), container = Color(0xFFECE9E2)),
    paused = StateColor(content = Color(0xFF3C5170), container = Color(0xFFDDE6F2)),
    dividerDense = Color(0xFFC9C4BA),
)

val LocalToolbillStateColors = staticCompositionLocalOf { ToolbillDarkStateColors }
