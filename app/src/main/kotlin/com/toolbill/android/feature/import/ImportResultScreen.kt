package com.toolbill.android.feature.`import`

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillIcons
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.domain.money.MoneyFormat

/**
 * Step four: what actually landed.
 *
 * The burn figure before and after is the point of this screen. It is the number the user came
 * for, and seeing it move by an amount they did not expect is how a column mapped wrongly gets
 * caught — which is why the undo sits here rather than only in Settings.
 */
@Composable
fun ImportResultScreen(
    modifier: Modifier = Modifier,
    outcome: ImportOutcome? = null,
    homeCurrency: String = "INR",
    onUndo: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Space.s2),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    painter = painterResource(ToolbillIcons.Close),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Space.s4),
        ) {
            Spacer(Modifier.height(Space.s2))
            Text(
                text = buildAnnotatedString {
                    append("STEP 4 OF 4 · ")
                    withStyle(SpanStyle(color = Toolbill.stateColors.trial.content)) {
                        append("DONE")
                    }
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(Space.s4))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold)) {
                        append((outcome?.imported ?: 0).toString())
                    }
                    withStyle(SpanStyle(fontSize = 24.sp)) { append(" imported") }
                },
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Space.s2))
            Text(
                text = when (val skipped = outcome?.skipped ?: 0) {
                    0 -> "Every row in the file landed."
                    1 -> "1 row was left out — unticked, unusable, or a duplicate."
                    else -> "$skipped rows were left out — unticked, unusable, or duplicates."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.s8))
            if (outcome != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                    SummaryRow(
                        "Burn before",
                        MoneyFormat.symbol(outcome.burnBeforeMinor, homeCurrency),
                    )
                    SummaryRow(
                        "Imported",
                        "+ " + MoneyFormat.symbol(outcome.addedMonthlyMinor, homeCurrency),
                        Toolbill.stateColors.trial.content,
                    )
                    HorizontalDivider(color = Toolbill.stateColors.dividerDense)
                    SummaryRow(
                        "Burn now",
                        MoneyFormat.symbol(
                            outcome.burnBeforeMinor + outcome.addedMonthlyMinor,
                            homeCurrency,
                        ),
                        MaterialTheme.colorScheme.onSurface,
                        bold = true,
                    )
                }
            }

            Spacer(Modifier.height(Space.s8))
            ToolbillButton(
                text = "Done",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s3))
            Text(
                text = "Not what you expected? Undo puts every one of these rows back the way " +
                    "it was. It stays available for 24 hours, in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s2))
            com.toolbill.android.core.design.component.ToolbillOutlinedButton(
                text = "Undo this import",
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s12))
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (bold) {
                Toolbill.money.row.copy(fontWeight = FontWeight.Bold)
            } else {
                Toolbill.money.row
            },
            color = valueColor,
        )
    }
}
