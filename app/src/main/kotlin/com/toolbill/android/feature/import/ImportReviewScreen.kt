package com.toolbill.android.feature.`import`

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.domain.importing.ImportCandidate
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.pricedIn
import com.toolbill.android.feature.subscriptions.label

/**
 * Step three: see exactly what will land.
 *
 * Every row of the file appears, including the ones that cannot be imported and why. Dropping
 * them silently would mean importing thirty rows, getting twenty-seven, and having no way to
 * find out which three went missing.
 */
@Composable
fun ImportReviewScreen(
    modifier: Modifier = Modifier,
    state: ImportState = ImportState(),
    homeCurrency: String = "INR",
    onBack: () -> Unit = {},
    onToggle: (Int) -> Unit = {},
    onNext: () -> Unit = {},
) {
    val preview = state.preview
    val candidates = preview?.candidates.orEmpty()
    val selected = preview?.selected.orEmpty()

    val addsToBurn = selected.sumOf { candidate ->
        val subscription = candidate.subscription!!
        normalizedMonthlyMinor(subscription.pricedIn(homeCurrency).homeAmountMinor, subscription.cycle)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Review", onBack = onBack)

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = Space.s4)) {
                    Spacer(Modifier.height(Space.s2))
                    FieldSectionLabel("STEP 3 OF 4")
                    Spacer(Modifier.height(Space.s4))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Stat(
                            "READY",
                            preview?.ready?.size?.toString() ?: "0",
                            Toolbill.stateColors.trial.content,
                        )
                        Stat(
                            "NEED A LOOK",
                            preview?.attention?.size?.toString() ?: "0",
                            MaterialTheme.colorScheme.primary,
                        )
                        Stat(
                            "ADDS TO BURN",
                            MoneyFormat.symbolWhole(addsToBurn, homeCurrency),
                            MaterialTheme.colorScheme.onSurface,
                            alignEnd = true,
                        )
                    }

                    Spacer(Modifier.height(Space.s2))
                    Text(
                        text = buildString {
                            append("Untick anything you don't want. ")
                            if (preview?.dayFirstDates == false) {
                                append("Dates in this file read month first. ")
                            } else {
                                append("Dates in this file read day first. ")
                            }
                            val unusable = preview?.unusable?.size ?: 0
                            if (unusable > 0) append("$unusable row(s) can't be imported.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s4))
                }
            }

            items(candidates, key = { it.rowNumber }) { candidate ->
                CandidateRow(candidate, homeCurrency) { onToggle(candidate.rowNumber) }
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }

            item { Spacer(Modifier.height(Space.s8)) }
        }

        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.s4),
        ) {
            ToolbillButton(
                text = if (selected.isEmpty()) {
                    "Nothing selected"
                } else {
                    "Import ${selected.size} subscription" + if (selected.size == 1) "" else "s"
                },
                onClick = onNext,
                enabled = selected.isNotEmpty() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "You can undo the whole import for 24 hours afterwards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    alignEnd: Boolean = false,
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        FieldSectionLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(top = Space.s1),
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: ImportCandidate,
    homeCurrency: String,
    onToggle: () -> Unit,
) {
    val subscription = candidate.subscription
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = candidate.isUsable, onClick = onToggle)
            .padding(horizontal = Space.s4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = candidate.include && candidate.isUsable,
            onCheckedChange = { onToggle() },
            enabled = candidate.isUsable,
        )
        Spacer(Modifier.padding(horizontal = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = subscription?.name ?: "Row ${candidate.rowNumber}",
                style = ToolbillText.rowName,
                color = if (candidate.isUsable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            val detail = when {
                candidate.problems.isNotEmpty() -> candidate.problems.joinToString(" · ")
                candidate.duplicateOf != null ->
                    "Looks like one you already track" +
                        candidate.notes.takeIf { it.isNotEmpty() }
                            ?.joinToString(" · ", prefix = " · ").orEmpty()

                candidate.notes.isNotEmpty() -> candidate.notes.joinToString(" · ")
                else -> subscription?.let {
                    "${it.cycle.label()} · first charge ${it.anchorDate}"
                }.orEmpty()
            }
            Text(
                text = "Row ${candidate.rowNumber} · $detail",
                style = ToolbillText.rowSupport,
                color = when {
                    candidate.problems.isNotEmpty() -> Toolbill.stateColors.overdue.content
                    candidate.needsAttention -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (subscription != null) {
            Text(
                text = MoneyFormat.symbol(
                    subscription.pricedIn(homeCurrency).homeAmountMinor,
                    homeCurrency,
                ),
                style = Toolbill.money.row,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
