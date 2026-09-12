package com.toolbill.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.toolbill.android.BiometricAuthManager
import com.toolbill.android.BuildConfig
import com.toolbill.android.core.billing.Entitlement
import com.toolbill.android.core.billing.EntitlementReason
import com.toolbill.android.core.domain.money.FxRates
import com.toolbill.android.core.domain.reminder.LEAD_TIME_CHOICES
import com.toolbill.android.feature.subscriptions.CurrencyPickerContent
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.toolbill.android.UserSettings
import com.toolbill.android.core.design.ThemeMode
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.design.component.ScreenHeader
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill

/** A settings row. No cards — flat ListItem rows separated by 1px dividers. */
data class SettingRow(
    val title: String,
    val body: String? = null,
    val value: String? = null,
    val chevron: Boolean = false,
)

/**
 * The money rows that do not yet describe live state.
 *
 * Home currency and Exchange rates were in this list too, stating "INR" and a fixed date
 * whatever the app was actually set to. Both are real settings now, so they are rendered
 * from the settings store instead — see the MONEY group below.
 */
private val moneyRows = listOf(
    // "Tell me about FX drift over 3% - Rs 500" used to sit here. It promised a
    // notification that nothing sends, and quoted a rupee figure whatever the home
    // currency was. It returns when drift alerts exist; until then the Insights FX
    // section is where drift is read.
    // No chevron: the categories are fixed by design so the export columns stay stable year to
    // year, so there is nothing behind this row to open. The arrow promised a screen that has
    // never existed.
    SettingRow("Categories", "11 fixed, plus Other with your own label"),
    // "resume date shown" was the original copy, but pausing never asks for one and nothing
    // sets it -- the row would have shown a date that can never arrive. The list already says
    // "not in burn" under a paused row, which is what actually happens.
    SettingRow(
        "Paused subscriptions",
        "Out of burn, still listed, nothing deleted",
        "excluded",
    ),
)

private val rateCaptureFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Lead time is rendered separately -- it is a live control, not a label. */
private val reminderRows = listOf(
    SettingRow("Reminders not working?", chevron = true),
)

/**
 * "Undo last import" is rendered separately because it exists only when there is an import to
 * undo. It used to sit in this list describing one -- "27 subscriptions, 2 hours ago" -- on an
 * install that had never imported anything; now it reports a real import or is not there at all.
 *
 * About is rendered separately too, from BuildConfig, for the same reason.
 */
private val dataRows = listOf(
    SettingRow("Export CSV", chevron = true),
    SettingRow("Import CSV or JSON", chevron = true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenDiagnostics: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onUndoImport: () -> Unit = {},
    entitlement: Entitlement = Entitlement.Free,
    onBackUp: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRefreshRates: suspend () -> Boolean = { false },
    onBack: () -> Unit = {},
    userSettings: UserSettings
) {
    val homeCurrency by userSettings.homeCurrency.collectAsState()
    var pickingCurrency by remember { mutableStateOf(false) }
    val themeMode by userSettings.themeMode.collectAsState()
    val dynamicColor by userSettings.dynamicColor.collectAsState()
    val appLockEnabled by userSettings.appLockEnabled.collectAsState()
    val leadTimeDays by userSettings.leadTimeDaysFlow.collectAsState()
    val fxTable by FxRates.table.collectAsState()
    val scope = rememberCoroutineScope()
    var ratesBusy by remember { mutableStateOf(false) }
    var ratesMessage by remember { mutableStateOf<String?>(null) }
    // Read once per open: the window closes on a clock, not on a state change.
    val importUndoCount = remember { userSettings.lastImportIds.size }
    val themeIndex = themeMode.ordinal

    // Offered only where the device can actually authenticate. On a phone with no screen lock
    // the switch could be turned on and would then have nothing to check against, which is a
    // worse outcome than not offering it: it would read as protection that is not there.
    val context = LocalContext.current
    val canLock = remember(context) { BiometricAuthManager.canAuthenticate(context) }

    val listState = rememberLazyListState()
    val headerScroll = rememberHeaderScroll(listState, titleRevealPx = 40)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings", onBack = onBack, scroll = headerScroll)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
        item {
            GroupHeader("MONEY")
            SettingItem(
                SettingRow("Home currency", "Everything converts to this", homeCurrency),
                onClick = { pickingCurrency = true },
            )
            SettingItem(
                SettingRow(
                    "Exchange rates",
                    // States the source and the publication date, and does not claim live
                    // figures on an install that has never reached the network. The ECB
                    // publishes on business days, so the date is routinely not today.
                    body = when {
                        ratesBusy -> "Checking for newer rates…"
                        ratesMessage != null -> ratesMessage
                        fxTable.isLive -> "European Central Bank, published " +
                            fxTable.capturedOn.format(rateCaptureFormat) + " · tap to check again"

                        else -> "Bundled table from " +
                            "${fxTable.capturedOn.format(rateCaptureFormat)} — tap to fetch"
                    },
                    value = if (fxTable.isLive) "live" else "bundled",
                ),
                onClick = if (ratesBusy) {
                    null
                } else {
                    {
                        scope.launch {
                            ratesBusy = true
                            ratesMessage = null
                            val refreshed = onRefreshRates()
                            ratesBusy = false
                            // Says which happened. A refresh that silently changed nothing is
                            // indistinguishable from one that never ran.
                            ratesMessage = if (refreshed) {
                                "Updated just now"
                            } else {
                                "Could not reach the rate source — keeping what is on file"
                            }
                        }
                    }
                },
            )
        }
        items(moneyRows, key = { it.title }) { SettingItem(it) }

        item {
            GroupHeader("REMINDERS")
            Column(Modifier.padding(vertical = Space.s2)) {
                Row(Modifier.padding(horizontal = Space.s4)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Lead time",
                            style = ToolbillText.settingsTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "How early to warn you, at 9am on the day",
                            style = ToolbillText.settingsBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Space.s2))
                SortSelector(
                    options = LEAD_TIME_CHOICES.map { if (it == 1) "1 day" else "$it days" },
                    selectedIndex = LEAD_TIME_CHOICES.indexOf(leadTimeDays).coerceAtLeast(0),
                    // Changing this re-plans every armed reminder: a longer lead can make one
                    // owed that was not owed a moment ago. ToolbillApplication observes it.
                    onSelect = { userSettings.setLeadTimeDays(LEAD_TIME_CHOICES[it]) },
                )
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }
        items(reminderRows, key = { it.title }) { row ->
            SettingItem(
                row,
                onClick = { if (row.title.startsWith("Reminders")) onOpenDiagnostics() },
            )
        }

        item {
            GroupHeader("APPEARANCE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            SortSelector(
                options = listOf("System", "Light", "Dark"),
                selectedIndex = themeIndex,
                modifier = Modifier.fillMaxWidth(),
                onSelect = { userSettings.setThemeMode(ThemeMode.entries[it]) },
            )
            Spacer(Modifier.height(Space.s2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dynamic color",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Take the accent from your wallpaper",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = { userSettings.setDynamicColor(it) })
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        if (canLock) {
            item {
                GroupHeader("PRIVACY")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { userSettings.setAppLockEnabled(!appLockEnabled) }
                        .padding(horizontal = Space.s4, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "App lock",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            // Says what it actually does. "Every time it comes back" was true
                            // of the old behaviour, which also re-asked after a file picker --
                            // an errand the user was in the middle of.
                            text = "Ask for your fingerprint or screen lock when you come back " +
                                "to Toolbill. Stepping out to pick a file won't re-ask; a dark " +
                                "screen always will.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { userSettings.setAppLockEnabled(it) },
                    )
                }
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }
        }

        item {
            GroupHeader("DATA")
            SettingItem(
                SettingRow(
                    "Back up to a file",
                    "Everything, in one file you keep. Google Drive comes later.",
                    chevron = true,
                ),
                onClick = onBackUp,
            )
            SettingItem(
                SettingRow(
                    "Restore from a file",
                    "Shows you exactly what will change before anything does",
                    chevron = true,
                ),
                onClick = onRestore,
            )
        }

        // Google Drive backup used to sit here reporting "Synced 2 hours ago - 74 KB" with its
        // switch on, on an install that has never backed anything up anywhere. It returns when
        // there is a backup to describe; a switch that claims your data is safe and is wired to
        // nothing is the single most costly placeholder this screen could carry.

        items(dataRows, key = { it.title }) { row ->
            SettingItem(
                row,
                onClick = {
                    when (row.title) {
                        "Export CSV" -> onOpenExport()
                        "Import CSV or JSON" -> onOpenImport()
                        "Restore purchases" -> onOpenPaywall()
                        else -> Unit
                    }
                },
            )
        }

        // Present only while a real import is still inside its 24-hour window.
        if (importUndoCount > 0) {
            item {
                SettingItem(
                    SettingRow(
                        title = "Undo last import",
                        body = buildString {
                            append("$importUndoCount subscription")
                            if (importUndoCount != 1) append("s")
                            val hours = userSettings.importUndoHoursLeft ?: 0L
                            append(
                                when {
                                    hours <= 0L -> " · less than an hour left"
                                    hours == 1L -> " · 1 hour left"
                                    else -> " · $hours hours left"
                                },
                            )
                        },
                        value = "↺",
                    ),
                    onClick = onUndoImport,
                )
            }
        }

        item {
            SettingItem(
                SettingRow(
                    title = "About",
                    // The running build, and the tier actually held -- read from the one
                    // entitlement source rather than asserted.
                    body = "${BuildConfig.VERSION_NAME} · " + when (entitlement.reason) {
                        EntitlementReason.PURCHASED -> "Pro"
                        EntitlementReason.EARLY_ACCESS -> "Pro · early access"
                        EntitlementReason.NONE -> "Free"
                    },
                    chevron = true,
                ),
                onClick = onOpenPaywall,
            )
        }


        item { Spacer(Modifier.height(Space.s12)) }
        }
    }

    if (pickingCurrency) {
        // The same list the add sheet and onboarding use, so "billed in" and "converts to"
        // cannot drift apart into two different sets of currencies.
        ModalBottomSheet(
            onDismissRequest = { pickingCurrency = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            CurrencyPickerContent(
                title = "Home currency",
                selected = homeCurrency,
                onBack = { pickingCurrency = false },
                onPick = { userSettings.setHomeCurrency(it); pickingCurrency = false },
            )
        }
    }
}

/** Group headings are amber 12sp Sans in the design, not a grey mono eyebrow. */
@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label,
        style = ToolbillText.groupHeading,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.s4, end = Space.s4, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingItem(row: SettingRow, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Only rows that do something are clickable. With a no-op default every
            // informational row still rippled under a finger and then sat there, which reads as
            // a broken button rather than as a line of text that was never meant to be tapped.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.s4, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = ToolbillText.settingsTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.body != null) {
                Text(
                    text = row.body,
                    style = ToolbillText.settingsBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.value != null) {
            Text(
                text = row.value,
                style = Toolbill.money.code.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.chevron) {
            Spacer(Modifier.padding(horizontal = Space.s1))
            Text(
                text = "\u203A",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = Toolbill.stateColors.dividerDense)
}
