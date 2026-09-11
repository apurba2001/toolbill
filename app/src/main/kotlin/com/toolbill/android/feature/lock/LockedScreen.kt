package com.toolbill.android.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.ToolbillActionButton

/**
 * The lock screen.
 *
 * It is deliberately the emptiest screen in the app: the one thing a lock must not do is leak
 * the figures it is locking. So there is no burn total, no renewal count, no service name — an
 * onlooker learns only that the app exists, which they already knew from the icon.
 *
 * The eyebrow / figure / caption stack is the same one the Home hero uses, with a dash where
 * the amount would be. That is what makes it read as Toolbill rather than as a system dialog:
 * the layout the user already knows, holding nothing.
 */
@Composable
fun LockedScreen(
    message: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = Space.s4),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MONTHLY BURN",
            style = ToolbillText.eyebrow,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(10.dp))

        // The hero's shape with nothing in it. Redacted bars rather than a fake figure: a
        // placeholder number on a money screen is a number someone will read.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "INR",
                style = Toolbill.money.heroCode,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.alignByBaseline(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                RedactedBar(width = 92.dp)
                RedactedBar(width = 56.dp)
            }
        }

        Spacer(Modifier.height(Space.s8))

        Text(
            text = "Toolbill is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.s2))
        Text(
            // The failure reason when there is one, the standing explanation otherwise. Either
            // way it says what to do next, not merely what went wrong.
            text = message?.let { "$it Nothing has been unlocked." }
                ?: "Your subscriptions stay hidden until you authenticate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.s6))
        ToolbillActionButton(
            text = "Unlock",
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.s3))
        Text(
            text = "Uses your fingerprint or screen lock · nothing leaves the device",
            style = ToolbillText.sectionCaption,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** A digit-height block where a figure would be. Same weight as the hero, no information. */
@Composable
private fun RedactedBar(width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .width(width)
            .height(30.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            ),
    )
}
