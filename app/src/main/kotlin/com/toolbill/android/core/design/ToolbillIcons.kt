package com.toolbill.android.core.design

import androidx.annotation.DrawableRes
import com.toolbill.android.R

/**
 * The app's icon set — Lucide, converted to stroke `VectorDrawable`s at 1.75 weight.
 *
 * Material Icons is frozen at 1.7.8 and its filled, tightly optically-corrected shapes read as
 * a different era than this design: the frames use thin geometric glyphs (`⌗ ⌕ ▤ ≡ ▦ ◱`), and a
 * consistent 24-grid stroke set is the closest honest translation of that.
 *
 * Stroke rather than fill for a practical reason too: at the 18–20dp these render at, a stroke
 * icon carries the same visual weight as the 11sp mono labels beside it, where a filled glyph
 * would out-shout them.
 *
 * These are resource ids rather than `Painter`s so they can be held by enums and data classes —
 * a `Painter` can only be resolved inside composition. Call sites wrap them in
 * `painterResource`.
 *
 * Lucide is ISC-licensed. Regenerate with `scratchpad/lucide.py`; do not hand-edit the XML.
 */
object ToolbillIcons {
    @DrawableRes val Home = R.drawable.ic_home
    @DrawableRes val List = R.drawable.ic_list
    @DrawableRes val Calendar = R.drawable.ic_calendar
    @DrawableRes val Insights = R.drawable.ic_insights

    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Add = R.drawable.ic_add
    @DrawableRes val Back = R.drawable.ic_back
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val More = R.drawable.ic_more

    @DrawableRes val ChevronLeft = R.drawable.ic_chevron_left
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right
    @DrawableRes val ChevronDown = R.drawable.ic_chevron_down

    @DrawableRes val MarkPaid = R.drawable.ic_mark_paid
    @DrawableRes val Skip = R.drawable.ic_skip
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val Duplicate = R.drawable.ic_duplicate
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val Delete = R.drawable.ic_delete

    @DrawableRes val Import = R.drawable.ic_import
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Return = R.drawable.ic_return
}
