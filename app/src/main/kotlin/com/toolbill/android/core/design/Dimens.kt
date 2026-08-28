package com.toolbill.android.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing scale. Named by multiple, so `space3` is always 12dp and the names survive
 * a change of base.
 */
object Space {
    /** 4dp · icon to label */
    val s1 = 4.dp

    /** 8dp · inside a row */
    val s2 = 8.dp

    /** 12dp · row vertical */
    val s3 = 12.dp

    /** 16dp · screen gutter */
    val s4 = 16.dp

    /** 24dp · between groups */
    val s6 = 24.dp

    /** 32dp · section break */
    val s8 = 32.dp

    /** 48dp · hero padding */
    val s12 = 48.dp
}

/** Row and cell heights the design fixes by name. */
object Sizes {
    /** Home's next-7-days row, which carries a 32dp monogram. */
    val rowComfortable = 72.dp

    /** The dense list row — 35 of these have to fit comfortably. */
    val rowDense = 56.dp

    /** The monogram on Home's next-7-days list. Absent from the dense list by design. */
    val monogram = 32.dp

    /** Calendar month-grid cell. */
    val calendarCell = 48.dp

    /** The collapsed MediumTopAppBar, which carries the burn figure inline. */
    val collapsedAppBar = 64.dp
}

/**
 * Corner radii. `full` is 28dp rather than a true pill so chips keep a flat-ish top edge in a
 * dense row.
 */
val ToolbillShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Radius {
    /** 4dp · badges */
    val xs = RoundedCornerShape(4.dp)

    /** 8dp · fields */
    val sm = RoundedCornerShape(8.dp)

    /** 12dp · buttons */
    val md = RoundedCornerShape(12.dp)

    /** 16dp · FAB, sheet */
    val lg = RoundedCornerShape(16.dp)

    /** 28dp · chips */
    val full = RoundedCornerShape(28.dp)
}
