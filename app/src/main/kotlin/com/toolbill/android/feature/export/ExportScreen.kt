package com.toolbill.android.feature.export

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.ToolbillFilterChip
import com.toolbill.android.core.design.component.ToolbillOutlinedButton
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.domain.export.CSV_HEADER
import com.toolbill.android.core.domain.export.ExportFilters
import com.toolbill.android.core.domain.export.ExportPeriod
import com.toolbill.android.core.domain.export.chargesForExport
import com.toolbill.android.core.domain.export.csvLine
import com.toolbill.android.core.domain.export.summarize
import com.toolbill.android.core.domain.export.toCsv
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.feature.subscriptions.FirstChargeDatePicker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** How many CSV lines the preview box shows before it says how many more there are. */
private const val PREVIEW_ROWS = 7

/**
 * The CSV export.
 *
 * Every figure on this screen comes from the charges table. It used to render a fixed preview
 * of seven invented rows and a summary reading "112 charges · 32 services" on an install that
 * had none — so the one screen whose whole job is to say exactly what it will hand an
 * accountant was the least accurate screen in the app.
 */
@Composable
fun ExportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    subscriptions: List<Subscription> = emptyList(),
    charges: List<Charge> = emptyList(),
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
) {
    val context = LocalContext.current

    var period by remember { mutableStateOf(ExportPeriod.FINANCIAL_YEAR) }
    var customRange by remember {
        mutableStateOf(ExportPeriod.CUSTOM.defaultRange(today))
    }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    var filters by remember { mutableStateOf(ExportFilters()) }
    var status by remember { mutableStateOf<String?>(null) }

    val range = if (period == ExportPeriod.CUSTOM) customRange else period.defaultRange(today)
    val rows = remember(charges, subscriptions, range, filters) {
        chargesForExport(charges, subscriptions, range, filters)
    }
    val summary = remember(rows, homeCurrency) { summarize(rows, homeCurrency) }
    val csv = remember(rows) { toCsv(rows) }

    // The storage picker. Drive, Dropbox and a USB stick are all destinations here, which is
    // how the file reaches any of them without this app knowing they exist.
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = ExportWriter.writeTo(context, uri, csv).fold(
            onSuccess = { "Saved ${summary.chargeCount} rows." },
            onFailure = { "Could not write the file. Nothing was saved." },
        )
    }

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
                    text = "One row per charge, in your home currency, with the rate Toolbill " +
                        "captured on that date. Charges recorded before any rate was captured " +
                        "export with an empty rate rather than a guessed one. Eleven fixed " +
                        "categories plus other_label keep the columns stable year to year.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Space.s6))
                FieldSectionLabel("PERIOD")
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    ExportPeriod.entries.forEach { option ->
                        ToolbillFilterChip(
                            label = option.label(option.defaultRange(today)),
                            selected = option == period,
                            onClick = { period = option },
                        )
                    }
                }

                Spacer(Modifier.height(Space.s4))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s6)) {
                    LabelledValue(
                        label = "FROM",
                        value = range.start.format(fullDate),
                        modifier = Modifier.weight(1f),
                        // Only the custom range is the user's to move. Tapping a date on a
                        // fixed period would silently turn it into a custom one.
                        onClick = if (period == ExportPeriod.CUSTOM) {
                            { pickingFrom = true }
                        } else {
                            null
                        },
                    )
                    LabelledValue(
                        label = "TO",
                        value = range.endInclusive.format(fullDate),
                        modifier = Modifier.weight(1f),
                        onClick = if (period == ExportPeriod.CUSTOM) {
                            { pickingTo = true }
                        } else {
                            null
                        },
                    )
                }

                Spacer(Modifier.height(Space.s4))
                FieldSectionLabel("INCLUDE")
                Spacer(Modifier.height(Space.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    ToolbillFilterChip(
                        label = "Business",
                        selected = filters.business,
                        onClick = { filters = filters.copy(business = !filters.business) },
                    )
                    ToolbillFilterChip(
                        label = "Personal",
                        selected = filters.personal,
                        onClick = { filters = filters.copy(personal = !filters.personal) },
                    )
                    ToolbillFilterChip(
                        label = "Cancelled",
                        selected = filters.cancelled,
                        onClick = { filters = filters.copy(cancelled = !filters.cancelled) },
                    )
                }

                Spacer(Modifier.height(Space.s4))
                Text(
                    text = "${plural(summary.chargeCount, "charge")} · " +
                        "${plural(summary.serviceCount, "service")} · " +
                        MoneyFormat.symbol(summary.totalHomeMinor, homeCurrency) + " total",
                    style = Toolbill.money.code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Skipped charges are listed and excluded from the total.",
                    style = Toolbill.money.rowSecondary,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(Modifier.height(Space.s6))
                FieldSectionLabel("PREVIEW")
                Spacer(Modifier.height(Space.s2))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, Radius.sm)
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.s3),
                ) {
                    Text(
                        text = CSV_HEADER,
                        style = Toolbill.money.rowSecondary,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                    if (rows.isEmpty()) {
                        Text(
                            text = "No charges in this period.",
                            style = Toolbill.money.rowSecondary,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    rows.take(PREVIEW_ROWS).forEach { (charge, subscription) ->
                        Text(
                            text = csvLine(charge, subscription),
                            style = Toolbill.money.rowSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (rows.size > PREVIEW_ROWS) {
                    Spacer(Modifier.height(Space.s2))
                    Text(
                        text = "+ ${rows.size - PREVIEW_ROWS} more rows · empty rate = no " +
                            "rate captured · status = paid or skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Spacer(Modifier.height(Space.s6))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    ToolbillButton(
                        text = "Share CSV",
                        enabled = rows.isNotEmpty(),
                        onClick = {
                            ExportWriter.shareIntent(context, csv, range).fold(
                                onSuccess = {
                                    context.startActivity(
                                        android.content.Intent.createChooser(it, "Export CSV"),
                                    )
                                },
                                onFailure = { status = "Could not prepare the file to share." },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ToolbillOutlinedButton(
                        text = "Save as file",
                        enabled = rows.isNotEmpty(),
                        onClick = { saveFile.launch(ExportWriter.suggestedName(range)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                status?.let {
                    Spacer(Modifier.height(Space.s2))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(Space.s2))
                Text(
                    text = "Generated on device. The share sheet and the storage picker decide " +
                        "where it goes — Toolbill never uploads it anywhere itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.s12))
            }
        }
    }

    if (pickingFrom) {
        FirstChargeDatePicker(
            initial = range.start,
            onDismiss = { pickingFrom = false },
            onPick = { picked ->
                // A range that ends before it starts exports nothing and reads as a bug. Push
                // the far end rather than refusing the date the user just chose.
                customRange = picked..maxOf(picked, customRange.endInclusive)
                period = ExportPeriod.CUSTOM
                pickingFrom = false
            },
        )
    }
    if (pickingTo) {
        FirstChargeDatePicker(
            initial = range.endInclusive,
            onDismiss = { pickingTo = false },
            onPick = { picked ->
                customRange = minOf(picked, customRange.start)..picked
                period = ExportPeriod.CUSTOM
                pickingTo = false
            },
        )
    }
}

@Composable
private fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, Radius.sm)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = ToolbillText.fieldLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = ToolbillText.fieldValue,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "1 charge", "2 charges" — a summary that reads as a bug undermines the figure beside it. */
private fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"
