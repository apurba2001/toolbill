package com.toolbill.android.feature.`import`

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.rememberHeaderScroll

/**
 * Step one: pick a file.
 *
 * The example below is what a readable file looks like, not what one has to look like — the
 * headings are mapped by hand on the next screen precisely so nobody has to rename columns in a
 * spreadsheet before they can use this.
 */
@Composable
fun ImportScreen(
    modifier: Modifier = Modifier,
    state: ImportState = ImportState(),
    onBack: () -> Unit = {},
    onPick: (android.net.Uri) -> Unit = {},
    onNext: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val headerScroll = rememberHeaderScroll({ scrollState.value }, titleRevealPx = 40)

    // Any text type, because providers disagree about what a CSV is: Drive reports text/comma-
    // separated-values, a file manager reports text/csv, and some report nothing at all.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onPick(uri) }

    val mimeTypes = arrayOf(
        "text/csv", "text/comma-separated-values", "text/tab-separated-values",
        "text/plain", "application/json", "application/csv", "*/*",
    )

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Import", onBack = onBack, scroll = headerScroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Space.s4),
        ) {
            Spacer(Modifier.height(Space.s2))
            FieldSectionLabel("STEP 1 OF 4")

            Spacer(Modifier.height(Space.s4))
            Text(
                text = "Bring the spreadsheet you already keep.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Space.s3))
            Text(
                text = "CSV, TSV or a JSON export from another tracker. You'll map the columns " +
                    "yourself on the next screen, so the headings don't have to match ours.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.s6))
            ToolbillButton(
                text = if (state.table == null) "Choose a file" else "Choose a different file",
                onClick = { picker.launch(mimeTypes) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { message ->
                Spacer(Modifier.height(Space.s3))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Toolbill.stateColors.overdue.content,
                )
            }

            state.table?.let { table ->
                Spacer(Modifier.height(Space.s4))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.sm)
                        .padding(Space.s3),
                ) {
                    Text(
                        text = state.fileName ?: "Selected file",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${table.rows.size} rows · ${table.headers.size} columns",
                        style = Toolbill.money.rowSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s3))
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            text = table.headers.joinToString(" · "),
                            style = Toolbill.money.rowSecondary,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                        )
                        HorizontalDivider(
                            color = Toolbill.stateColors.dividerDense,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        table.rows.take(3).forEach { row ->
                            Text(
                                text = row.joinToString(" · "),
                                style = Toolbill.money.rowSecondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Space.s4))
                ToolbillButton(
                    text = "Map the columns",
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Space.s8))
            FieldSectionLabel("A FILE WE CAN READ")
            Spacer(Modifier.height(Space.s2))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.sm)
                    .horizontalScroll(rememberScrollState())
                    .padding(Space.s3),
            ) {
                Text(
                    text = "Service,Price,Currency,Billed,Next charge",
                    style = Toolbill.money.rowSecondary,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
                HorizontalDivider(
                    color = Toolbill.stateColors.dividerDense,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                listOf(
                    "Claude Pro,20.00,USD,Monthly,27/08/2026",
                    "Vercel Pro,20.00,USD,Monthly,25/08/2026",
                    "iCloud+,749,INR,Monthly,16/08/2026",
                ).forEach { line ->
                    Text(
                        text = line,
                        style = Toolbill.money.rowSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "Only the service name and the amount are essential. Anything else " +
                    "missing gets a sensible default that you can see before it lands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s12))
        }
    }
}
