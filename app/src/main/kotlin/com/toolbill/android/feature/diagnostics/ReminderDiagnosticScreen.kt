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

import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.toolbill.android.UserSettings
import com.toolbill.android.core.reminder.ReminderNotifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    userSettings: UserSettings,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val deviceName = remember { detectedDevice() }
    // Re-read on every resume: the whole point of the screen is to send the user into system
    // settings and back, and a cached "restricted" after they fixed it is its own small lie.
    val lifecycleOwner = LocalLifecycleOwner.current
    var checks by remember { mutableStateOf(selfChecks(context, userSettings)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checks = selfChecks(context, userSettings)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsAllowed = checks.firstOrNull { it.label == NOTIFICATION_CHECK }?.ok == true
    val batteryUnrestricted = checks.firstOrNull { it.label == BATTERY_CHECK }?.ok == true
    val fixes = buildList {
        if (!batteryUnrestricted) add("Set battery saver for Toolbill to No restrictions.")
        if (!notificationsAllowed) add("Allow Toolbill to send notifications.")
        // Autostart has no API to read, so it is only ever advice -- and only worth giving
        // alongside a real problem, never on its own.
        if (!batteryUnrestricted) add("Turn on Autostart so reminders survive a reboot.")
    }
    val needsAttention = fixes.isNotEmpty()

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
                    text = if (needsAttention) {
                        "Your phone is probably putting Toolbill to sleep."
                    } else {
                        "Reminders should arrive on this phone."
                    },
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
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Toolbill.stateColors.dividerDense),
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Stacked, not a label/value row. Real device names run to "Google
                    // sdk_gphone64_x86_64 - Android 17", which took the whole width and left
                    // the eyebrow wrapping one letter per line down the side of the card.
                    Column {
                        Text(
                            text = "DETECTED DEVICE",
                            style = EyebrowStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = deviceName,
                            style = Toolbill.money.code.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Only shown when there is something to change. With both permissions
                    // already in order, a numbered list of fixes and two buttons into system
                    // settings is an instruction to solve a problem the user does not have --
                    // and it makes a healthy screen read like a broken one.
                    if (needsAttention) {
                        Spacer(Modifier.height(Space.s6))
                        Text(
                            text = if (fixes.size == 1) "One setting to change" else "Two settings to change",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Space.s2))
                        fixes.forEachIndexed { index, fix ->
                            NumberedStep("${index + 1}", fix)
                        }

                        Spacer(Modifier.height(Space.s4))
                        // Stacked: the two labels do not fit on one line at 400dp, and the
                        // outlined one broke mid-word.
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            if (!batteryUnrestricted) {
                                ToolbillButton(
                                    text = "Open battery settings",
                                    onClick = { openBatterySettings(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (!notificationsAllowed) {
                                ToolbillOutlinedButton(
                                    text = "Notification settings",
                                    onClick = { openNotificationSettings(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.s4))
                        Text(
                            text = "Opens this app's own page in system settings. Manufacturer " +
                                "menus differ, so the autostart toggle may sit a level deeper.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(Space.s4))
                        Text(
                            text = "Nothing to change. This phone is letting Toolbill wake up " +
                                "when it needs to — send yourself a test below if you want " +
                                "to see it arrive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.s8))
            Text(
                text = "SELF-CHECK",
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s2))
            checks.forEach { check ->
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
                            Toolbill.stateColors.trial.content
                        } else {
                            Toolbill.stateColors.overdue.content
                        },
                    )
                }
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }

            Spacer(Modifier.height(Space.s6))
            Column {
                Text(
                    text = "Send yourself a test reminder",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Arrives immediately, down the same path a renewal reminder takes. " +
                        "If it does not appear, the settings above are blocking delivery " +
                        "rather than scheduling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.s3))
                ToolbillOutlinedButton(
                    text = "Send test",
                    onClick = {
                        // Recorded only if it actually reached the notification manager, and
                        // marked as a test so the row cannot pass for a real renewal firing.
                        if (ReminderNotifier.notifyTest(context)) {
                            userSettings.recordReminderFired(wasTest = true)
                        }
                        checks = selfChecks(context, userSettings)
                    },
                    // Nothing to prove while notifications are off, and the row above already
                    // says so. Offering the button anyway would send a test that silently
                    // vanishes -- the exact failure this screen exists to catch.
                    enabled = ReminderNotifier.canNotify(context),
                )
            }

            Spacer(Modifier.height(Space.s6))
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Toolbill.stateColors.dividerDense),
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth()
            ) {
                val widgetAge = checks.lastOrNull()?.detail ?: "never"
                val annotatedText = buildAnnotatedString {
                    append(
                        "The widget is frozen by the same policy. Glance refreshes through " +
                            "WorkManager, so a restricted app shows a stale burn figure with no " +
                            "warning — ",
                    )
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Toolbill.stateColors.overdue.content,
                        ),
                    ) {
                        append(widgetAge)
                    }
                    append(" above is the tell. Fixing battery access fixes both.")
                }
                Text(
                    text = annotatedText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.s4))
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
