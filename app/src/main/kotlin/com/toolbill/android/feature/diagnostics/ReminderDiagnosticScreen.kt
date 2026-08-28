package com.toolbill.android.feature.diagnostics

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.toolbill.android.core.design.component.rememberHeaderScroll
import com.toolbill.android.core.design.component.ScreenHeader
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillButton
import com.toolbill.android.core.design.component.ToolbillOutlinedButton
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill

/** One line of the self-check. [ok] false renders in the overdue colour, never as a scare. */
data class SelfCheck(val label: String, val detail: String, val ok: Boolean)

private val selfChecks = listOf(
    SelfCheck("Notification permission", "granted", ok = true),
    SelfCheck("Exact alarms", "allowed", ok = true),
    SelfCheck("Battery optimization", "restricted", ok = false),
    SelfCheck("Last reminder fired", "11 Aug, 09:00", ok = true),
    SelfCheck("Widget last updated", "6 days ago", ok = false),
)

/**
 * The OEM diagnostic screen.
 *
 * Calm and specific rather than alarmed: the user did not do anything wrong, and the fix is two
 * settings deep in a manufacturer's menu. The self-check reports what is actually true — a
 * screen that claims reminders work while they silently do not is worse than no screen.
 */
@Composable
fun ReminderDiagnosticScreen(
    modifier: Modifier = Modifier,
    deviceName: String = "Xiaomi · HyperOS 2",
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val headerScroll = rememberHeaderScroll(offsetPx = { scrollState.value }, titleRevealPx = 72)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Reminders", onBack = onBack, scroll = headerScroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = Space.s4),
        ) {
        Spacer(Modifier.height(6.dp))
        // The header spans the full width; everything below it keeps the 16dp gutter.
        Column(Modifier.padding(horizontal = Space.s4)) {
            Text(
                    text = "Your phone is probably putting Toolbill to sleep.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "Reminders are scheduled on the device, not sent from a server. That's " +
                    "better for privacy, but it means the system has to let Toolbill wake up at " +
                    "the right moment — and some manufacturers stop apps from doing that by " +
                    "default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.s6))
            Row {
                Text(
                    text = "DETECTED DEVICE",
                    style = EyebrowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(Space.s6))
            Text(
                text = "Two settings to change",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s2))
            NumberedStep("1", "Set battery saver for Toolbill to No restrictions.")
            NumberedStep("2", "Turn on Autostart so reminders survive a reboot.")

            Spacer(Modifier.height(Space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                ToolbillButton(text = "Open battery settings", onClick = {})
                ToolbillOutlinedButton(text = "Autostart", onClick = {})
            }
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "Deep-links to the OEM screen. If it isn't available on your build, we show " +
                    "the standard Android battery page instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.s8))
            Text(
                text = "Self-check",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.s2))
            selfChecks.forEach { check ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = check.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = check.detail,
                        style = Toolbill.money.rowSecondary,
                        color = if (check.ok) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            Toolbill.stateColors.overdue.content
                        },
                    )
                }
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }

            Spacer(Modifier.height(Space.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Send yourself a test reminder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Arrives in 60 seconds. If it doesn't, the settings above are " +
                            "still blocking it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolbillOutlinedButton(text = "Send test", onClick = {})
            }

            Spacer(Modifier.height(Space.s6))
            Text(
                text = "The widget is frozen by the same policy. Glance refreshes through " +
                    "WorkManager, so a restricted app shows a stale burn figure with no warning — " +
                    "6 days ago above is the tell. Fixing battery access fixes both.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                text = "Also seen on Oppo, Vivo, OnePlus (App auto-launch) and Samsung " +
                    "(Sleeping apps). The guidance changes to match your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s12))
        }
        }
    }
}

@Composable
private fun NumberedStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s2)) {
        Text(
            text = number,
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
