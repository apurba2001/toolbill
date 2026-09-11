package com.toolbill.android.feature.`import`

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.domain.importing.ImportField

/**
 * Step two: say which column is which.
 *
 * Every field can be pointed at any column, or at nothing. The guess from the headings is a
 * starting point — it is right often enough to be worth making and wrong often enough that it
 * must never be the only say the user gets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMapScreen(
    modifier: Modifier = Modifier,
    state: ImportState = ImportState(),
    onBack: () -> Unit = {},
    onAssign: (ImportField, Int?) -> Unit = { _, _ -> },
    onNext: () -> Unit = {},
) {
    val table = state.table
    val scrollState = rememberScrollState()
    val headerScroll = rememberHeaderScroll({ scrollState.value }, titleRevealPx = 40)
    var choosingFor by remember { mutableStateOf<ImportField?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Map columns", onBack = onBack, scroll = headerScroll)
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Space.s4),
            ) {
                Spacer(Modifier.height(Space.s2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldSectionLabel("STEP 2 OF 4")
                    Text(
                        text = listOfNotNull(
                            state.fileName,
                            table?.let { "${it.rows.size} rows" },
                        ).joinToString(" · "),
                        style = Toolbill.money.rowSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Space.s4))
                Text(
                    text = "We guessed from your headings. Fix anything we got wrong — " +
                        "Service name and Amount are the only two we can't do without.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Space.s6))
                ImportField.entries.forEach { field ->
                    val columnIndex = state.mapping.columnFor(field)
                    MappingRow(
                        field = field,
                        columnName = columnIndex?.let { table?.headers?.getOrNull(it) },
                        sample = columnIndex?.let { index ->
                            table?.rows?.firstOrNull { it.getOrNull(index)?.isNotBlank() == true }
                                ?.getOrNull(index)
                        },
                        onClick = { choosingFor = field },
                    )
                    HorizontalDivider(color = Toolbill.stateColors.dividerDense)
                }

                Spacer(Modifier.height(Space.s12))
            }
        }

        Column(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.s4),
        ) {
            val missing = state.mapping.missingRequired
            if (missing.isNotEmpty()) {
                Text(
                    text = "Still needed: " + missing.joinToString(" and ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = Toolbill.stateColors.overdue.content,
                )
                Spacer(Modifier.height(Space.s2))
            }
            ToolbillButton(
                text = "Review ${state.preview?.candidates?.size ?: table?.rows?.size ?: 0} rows",
                onClick = onNext,
                enabled = state.mapping.isUsable && state.preview != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val field = choosingFor
    if (field != null && table != null) {
        ModalBottomSheet(
            onDismissRequest = { choosingFor = null },
            shape = Radius.lg,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(bottom = Space.s8)) {
                Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s2)) {
                    Text(
                        text = field.label,
                        style = ToolbillText.rowName,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (field.required) {
                            "Required — pick the column that holds it"
                        } else {
                            "Optional — leave it out and Toolbill fills in a default"
                        },
                        style = ToolbillText.rowSupport,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.s2))

                if (!field.required) {
                    ColumnChoice(
                        label = "Don't import this",
                        sample = null,
                        selected = state.mapping.columnFor(field) == null,
                        onClick = { onAssign(field, null); choosingFor = null },
                    )
                }
                table.headers.forEachIndexed { index, header ->
                    ColumnChoice(
                        label = header.ifBlank { "Column ${index + 1}" },
                        sample = table.rows.firstOrNull { it.getOrNull(index)?.isNotBlank() == true }
                            ?.getOrNull(index),
                        selected = state.mapping.columnFor(field) == index,
                        onClick = { onAssign(field, index); choosingFor = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun MappingRow(
    field: ImportField,
    columnName: String?,
    sample: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = field.label + if (field.required) " *" else "",
                style = ToolbillText.settingsTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sample?.let { "e.g. $it" }
                    ?: if (columnName == null) "Not imported" else "No values in this column",
                style = ToolbillText.settingsBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .border(
                    BorderStroke(
                        1.dp,
                        if (columnName == null && field.required) {
                            Toolbill.stateColors.overdue.content
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    Radius.sm,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = columnName ?: "—",
                style = Toolbill.money.rowSecondary,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ColumnChoice(
    label: String,
    sample: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s4, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = ToolbillText.settingsTitle,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            sample?.let {
                Text(
                    text = it,
                    style = ToolbillText.settingsBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                style = ToolbillText.settingsTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
