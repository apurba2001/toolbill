package com.toolbill.android.core.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.domain.money.FormattedMoney
import com.toolbill.android.core.domain.money.MoneyFormat

/**
 * The hero money figure — Home's monthly burn and the detail header.
 *
 * Autosizes by significant digits rather than measured width, so the step is identical across
 * fonts and locales. The minor unit renders one step down and muted, so the eye lands on the
 * rupees rather than the paise, and the ISO code labels the figure — never the symbol.
 */
@Composable
fun HeroAmount(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    spokenLabel: String? = null,
    /**
     * Fades the whole figure without changing its metrics, for the empty state -- which shows
     * a real zero rather than a placeholder so the layout does not move when data arrives.
     */
    alpha: Float = 1f,
) {
    val formatted = MoneyFormat.hero(amountMinor, currency)
    val money = Toolbill.money

    // Baseline alignment, not bottom alignment: the ISO code and the paise are much smaller
    // than the figure, so aligning boxes drops them below the digits they belong to.
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spokenLabel ?: formatted.withCode()
        },
    ) {
        Text(
            text = formatted.code,
            style = money.heroCode,
            color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatted.integer,
            style = money.hero(formatted.heroStep),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.alignByBaseline(),
        )
        if (formatted.fraction != null) {
            Text(
                text = formatted.decimalSeparator + formatted.fraction,
                style = money.heroFraction(formatted.heroStep),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * A right-aligned amount in a list column.
 *
 * The currency is declared once in the column head and never repeated per row, which is what
 * keeps 35 rows of digits in a single tabular block.
 */
@Composable
fun ColumnAmount(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    color: Color = LocalContentColor.current,
    style: TextStyle? = null,
) {
    val formatted = MoneyFormat.format(amountMinor, currency)
    val money = Toolbill.money
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(text = formatted.plain, style = style ?: money.row, color = color)
        if (suffix != null) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = suffix,
                style = money.rowSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A money figure carrying its symbol inline — rows, chips, footers, captions, widgets. */
@Composable
fun InlineMoney(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    withFraction: Boolean = true,
    color: Color = LocalContentColor.current,
    style: TextStyle? = null,
) {
    val formatted: FormattedMoney = MoneyFormat.format(amountMinor, currency)
        .let { if (withFraction) it else it.withoutFraction() }
    Text(
        text = formatted.withSymbol(),
        style = style ?: Toolbill.money.row,
        color = color,
        modifier = modifier,
    )
}
