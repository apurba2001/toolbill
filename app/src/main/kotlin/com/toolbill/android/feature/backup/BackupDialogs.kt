package com.toolbill.android.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.component.ToolbillActionTextButton

/**
 * The confirmation a restore has to pass, and the line that reports what happened.
 *
 * The plan's risk register calls restore the one path that can destroy data unrecoverably, and
 * the way that happens is a single tap that looks routine. So the plan is stated in full before
 * anything is written — including, explicitly, what will be *left alone*, because "will my own
 * entries survive this?" is the question the user actually has and no generic warning answers it.
 */
@Composable
fun BackupDialogs(
    state: BackupUiState,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val plan = state.pending
    if (plan != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Restore this backup?") },
            text = {
                Column {
                    Text(
                        text = plan.summary() + ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = buildString {
                            append("Nothing on this device is deleted. ")
                            if (plan.subscriptionsOnlyLocal.isNotEmpty()) {
                                append(
                                    "${plan.subscriptionsOnlyLocal.size} subscription" +
                                        (if (plan.subscriptionsOnlyLocal.size == 1) "" else "s") +
                                        " that only exist here are kept. ",
                                )
                            }
                            if (plan.subscriptionsKeptLocal.isNotEmpty()) {
                                append(
                                    "${plan.subscriptionsKeptLocal.size} you have edited more " +
                                        "recently than the backup are left as they are.",
                                )
                            }
                        }.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                ToolbillActionTextButton(text = "Restore", onClick = onConfirmRestore)
            },
            dismissButton = {
                ToolbillActionTextButton(text = "Cancel", onClick = onDismiss)
            },
        )
        return
    }

    val message = state.message
    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (state.isError) "That didn't work" else "Done") },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) {
                        Toolbill.stateColors.overdue.content
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            confirmButton = { ToolbillActionTextButton(text = "OK", onClick = onDismiss) },
        )
    }
}
