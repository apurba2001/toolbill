package com.toolbill.android.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.IbmPlexSans
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.ToolbillActionButton
import com.toolbill.android.core.design.component.ToolbillActionTextButton

private val promises = listOf(
    "Scheduled locally. No server sees your renewals.",
    "One notification per renewal. Never a digest, never marketing.",
    "Turn it off in Settings and nothing else changes.",
)

private val Body = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
)

private val Promise = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
)

/**
 * Shown after the first entry, before the system dialog — never on launch.
 *
 * A sheet rather than a full screen: it interrupts a flow the user was already in, and a sheet
 * says "one more thing" where a screen would say "you are somewhere else now".
 *
 * Android gives exactly one chance at the permission prompt, so it is spent only once there is
 * something worth being reminded about, and only after saying what the permission is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRationaleSheet(
    onAllow: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Radius.lg,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp)) {
            Text(
                text = "ONE THING BEFORE YOU GO",
                style = ToolbillText.microLabel.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Want a nudge three days before a charge?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "That's the only reason Toolbill asks for notifications. It's the " +
                    "window where cancelling still saves you money.",
                style = Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            NotificationPreview()

            // The three promises sit under a rule — they are the terms of the ask.
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(Modifier.height(12.dp))
            promises.forEachIndexed { index, promise ->
                if (index > 0) Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "·",
                        style = Toolbill.money.code,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = promise,
                        style = Promise,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbillActionTextButton(text = "Not now", onClick = onDismiss)
                ToolbillActionButton(
                    text = "Allow notifications",
                    onClick = onAllow,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Next you'll see Android's own permission dialog. We don't ask twice " +
                    "if you decline.",
                style = ToolbillText.sectionCaption,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * A rendering of the notification itself — the clearest possible statement of the ask, and the
 * reason the sheet does not need to describe what a reminder looks like.
 */
@Composable
private fun NotificationPreview() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outlineVariant, Radius.md)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Toolbill.stateColors.dueSoon.container, Radius.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "T",
                    style = Toolbill.money.code.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                    color = scheme.primary,
                )
            }
            Text(
                text = "Toolbill · now",
                style = ToolbillText.detailCardTitle.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = scheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Vercel Pro renews in 3 days — ₹1,742",
            style = TextStyle(
                fontFamily = IbmPlexSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Tap to review, pause, or mark cancelled.",
            style = ToolbillText.settingsBody,
            color = scheme.onSurfaceVariant,
        )
    }
}
