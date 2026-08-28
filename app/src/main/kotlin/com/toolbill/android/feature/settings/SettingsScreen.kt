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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

private val moneyRows = listOf(
    SettingRow("Home currency", "Everything converts to this", "INR"),
    SettingRow("Exchange rates", "Refreshed on open when online", "24 Aug manual"),
    SettingRow(
        "Tell me about FX drift over",
        "Both must be crossed before you hear from us",
        "3% · ₹500",
    ),
    SettingRow("Categories", "11 fixed, plus Other with your own label", chevron = true),
    SettingRow(
        "Paused subscriptions",
        "Out of burn, still listed, resume date shown",
        "excluded",
    ),
)

private val reminderRows = listOf(
    SettingRow("Lead time", "How early to warn you", "3 days"),
    SettingRow("Reminders not working?", chevron = true),
)

private val dataRows = listOf(
    SettingRow("Google Drive backup", "Synced 2 hours ago", "74 KB"),
    SettingRow("Export CSV", chevron = true),
    SettingRow("Import CSV or JSON", chevron = true),
    SettingRow("Undo last import", "27 subscriptions, 2 hours ago · here for 24 hours", "↺"),
    SettingRow("Restore purchases", chevron = true),
    SettingRow("About", "1.4.0 · Pro (lifetime)", chevron = true),
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenDiagnostics: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var themeIndex by remember { mutableIntStateOf(0) }
    var dynamicColor by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val headerScroll = rememberHeaderScroll(listState, titleRevealPx = 40)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings", onBack = onBack, scroll = headerScroll)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
        item { GroupHeader("MONEY") }
        items(moneyRows, key = { it.title }) { SettingItem(it) }

        item { GroupHeader("REMINDERS") }
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
                onSelect = { themeIndex = it },
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
                Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        item { GroupHeader("DATA") }
        items(dataRows, key = { it.title }) { row ->
            SettingItem(
                row,
                onClick = {
                    when (row.title) {
                        "Export CSV" -> onOpenExport()
                        "Restore purchases", "About" -> onOpenPaywall()
                        else -> Unit
                    }
                },
            )
        }

        item { Spacer(Modifier.height(Space.s12)) }
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
private fun SettingItem(row: SettingRow, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                text = "›",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = Toolbill.stateColors.dividerDense)
}
