package com.toolbill.android.feature.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill

private data class ProFeature(val number: String, val title: String, val body: String)

private val proFeatures = listOf(
    ProFeature(
        "01", "Renewal notifications",
        "Choose your lead time. Fires offline via AlarmManager.",
    ),
    ProFeature(
        "02", "CSV export",
        "Per-charge, with the rate captured that day where we have one.",
    ),
    ProFeature(
        "03", "Insights & FX history",
        "Savings, drift and category splits — from install day forward.",
    ),
    ProFeature(
        "04", "Google Drive backup",
        "Your Drive, your file. Restores on a new phone.",
    ),
)

private data class Plan(val name: String, val price: String, val caption: String)

private val plans = listOf(
    Plan("Yearly", "₹1,499", "≈ \$19 · renews yearly, cancel in Play Store"),
    Plan("Lifetime", "₹3,099", "≈ \$39 · one payment, no renewal"),
)

/**
 * The paywall. No timer, no pre-selection, no strike-through anchor price.
 *
 * Neither plan is pre-selected: pre-selecting one is a nudge, and this audience reads nudges
 * as a reason to distrust the rest of the screen.
 */
@Composable
fun PaywallScreen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<Int?>(null) }

    val scrollState = rememberScrollState()
    val headerScroll = rememberHeaderScroll(offsetPx = { scrollState.value }, titleRevealPx = 56)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Toolbill Pro",
            onBack = onDismiss,
            backIcon = Icons.Rounded.Close,
            backDescription = "Close",
            scroll = headerScroll,
        ) {
            Text(
                text = "Restore purchase",
                style = ToolbillText.restoreAction,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onRestore),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = Space.s4),
        ) {
        Spacer(Modifier.height(6.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Toolbill Pro",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            text = "Tracking stays free and unlimited — all 31 of your subscriptions, forever, " +
                "with no entry cap. Pro pays for the four things that need building and " +
                "maintaining.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // A ruled list, not four loose blocks: four promises the price rests on read as a
        // contract when they are enumerated.
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        proFeatures.forEach { feature ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = feature.number,
                    style = Toolbill.money.code.copy(fontSize = 12.sp, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = feature.title,
                        style = ToolbillText.button,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = feature.body,
                        style = ToolbillText.noticeBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        Spacer(Modifier.height(Space.s6))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            plans.forEachIndexed { index, plan ->
                PlanCard(
                    plan = plan,
                    selected = selected == index,
                    onSelect = { selected = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(Space.s3))
        Text(
            text = "Neither option is pre-selected. If you file taxes as a business, both are " +
                "deductible — the yearly is roughly what one of your mid-sized tools costs a " +
                "month.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s4))
        Text(
            text = "If Pro lapses, nothing is deleted or hidden. You keep every entry and the " +
                "app keeps working — you just stop getting reminders and exports.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s6))
        ToolbillButton(
            text = if (selected == null) "Pick a plan to continue" else "Continue",
            onClick = {},
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ToolbillTextButton(text = "Terms", onClick = {})
            ToolbillTextButton(text = "Privacy", onClick = {})
            Spacer(Modifier.weight(1f))
            ToolbillTextButton(text = "Not now", onClick = onDismiss)
        }
        Spacer(Modifier.height(Space.s8))
        }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.selectable(selected = selected, onClick = onSelect),
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(Modifier.padding(Space.s4)) {
            Text(
                text = plan.name,
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                text = plan.price,
                style = Toolbill.money.heroFraction,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s1))
            Text(
                text = plan.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
