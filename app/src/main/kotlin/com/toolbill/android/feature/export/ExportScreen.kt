package com.toolbill.android.feature.export

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillFilterChip
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.ToolbillOutlinedButton
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill

private val periods = listOf("FY 2026–27", "This quarter", "Custom…")

/**
 * The CSV preview.
 *
 * A skipped renewal exports as a row like any other — `status` reads skipped and the home
 * amount is 0 — so nothing quietly vanishes between the app and an accountant's spreadsheet.
 * Rows backfilled from before install carry an empty rate rather than a guessed one.
 */
private val previewRows = listOf(
    "date,service,cat,other,type,amt,ccy,rate,home,status",
    "26-08-27,Claude,AI,,biz,20.00,USD,87.10,1742,paid",
    "26-08-25,Vercel,Host,,biz,20.00,USD,87.10,1742,paid",
    "26-08-19,Posthog,Anly,,biz,50.00,USD,86.98,4349,paid",
    "26-08-16,iCloud+,Stor,,pers,749,INR,1.00,749,paid",
    "26-08-10,Judge.me,Other,Shop,biz,1299,INR,1.00,1299,paid",
    "26-06-09,AWS,Host,,biz,0,INR,,0,skipped",
    "26-03-27,Claude,AI,,biz,20.00,USD,,1680,paid",
)

@Composable
fun ExportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var periodIndex by remember { mutableIntStateOf(0) }
    var includeBusiness by remember { mutableStateOf(true) }
    var includePersonal by remember { mutableStateOf(false) }
    var includeCancelled by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val headerScroll = rememberHeaderScroll(offsetPx = { scrollState.value }, titleRevealPx = 48)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Export for filing", onBack = onBack, scroll = headerScroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = Space.s4),
        ) {
        Spacer(Modifier.height(6.dp))
        Column(Modifier.padding(horizontal = Space.s4)) {
        Text(
            text = "One row per charge, in your home currency, with the rate Toolbill captured " +
                "on that date. Rows you backfilled from before install export with an empty " +
                "rate rather than a guessed one. Eleven fixed categories plus other_label keep " +
                "the columns stable year to year.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s6))
        Text("PERIOD", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            periods.forEachIndexed { index, label ->
                ToolbillFilterChip(
                    label = label,
                    selected = index == periodIndex,
                    onClick = { periodIndex = index },
                )
            }
        }

        Spacer(Modifier.height(Space.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
            LabelledValue("FROM", "1 Apr 2026")
            LabelledValue("TO", "31 Mar 2027")
        }

        Spacer(Modifier.height(Space.s4))
        Text("INCLUDE", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ToolbillFilterChip(
                label = "Business",
                selected = includeBusiness,
                onClick = { includeBusiness = !includeBusiness },
            )
            ToolbillFilterChip(
                label = "Personal",
                selected = includePersonal,
                onClick = { includePersonal = !includePersonal },
            )
            ToolbillFilterChip(
                label = "Cancelled",
                selected = includeCancelled,
                onClick = { includeCancelled = !includeCancelled },
            )
        }

        Spacer(Modifier.height(Space.s4))
        Text(
            text = "112 charges · 32 services · ₹2,16,955 total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "22 monthly × 5 months + 1 annual renewal + 1 trial conversion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s6))
        Text("PREVIEW", style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.s2))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, Radius.sm)
                .horizontalScroll(rememberScrollState())
                .padding(Space.s3),
        ) {
            previewRows.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = Toolbill.money.rowSecondary,
                    color = if (index == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(Space.s2))
        Text(
            text = "+ 106 more rows · empty rate = backfilled · status = paid or skipped",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s6))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ToolbillButton(text = "Share CSV", onClick = {})
            ToolbillOutlinedButton(text = "Save to Drive", onClick = {})
        }
        Spacer(Modifier.height(Space.s2))
        Text(
            text = "Generated on device. Android's share sheet decides where it goes — " +
                "Toolbill never uploads it anywhere itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.s12))
        }
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Column {
        Text(text = label, style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
