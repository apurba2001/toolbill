package com.toolbill.android.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.ToolbillText

/** One action offered by the long-press sheet. */
data class RowAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The long-press action sheet.
 *
 * The design puts mark-paid, skip, pause, duplicate, edit and delete behind a long press rather
 * than behind per-row buttons: at 35 rows, six affordances per row is 210 targets competing
 * with the amounts the list exists to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowActionSheet(
    title: String,
    subtitle: String,
    actions: List<RowAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Radius.lg,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(bottom = Space.s8)) {
            Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s2)) {
                Text(
                    text = title,
                    style = ToolbillText.rowName,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = ToolbillText.rowSupport,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.s2))
            actions.forEach { action ->
                val tint = if (action.destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action.onClick(); onDismiss() }
                        // 48dp minimum touch target.
                        .padding(horizontal = Space.s4, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Space.s3))
                    Text(text = action.label, style = ToolbillText.settingsTitle, color = tint)
                }
            }
        }
    }
}

/** The six actions the design lists, in its order. */
fun defaultRowActions(
    onMarkPaid: () -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
): List<RowAction> = listOf(
    RowAction("Mark paid", Icons.Rounded.TaskAlt, onClick = onMarkPaid),
    RowAction("Skip this charge", Icons.Rounded.SkipNext, onClick = onSkip),
    RowAction("Pause", Icons.Rounded.PauseCircleOutline, onClick = onPause),
    RowAction("Duplicate", Icons.Rounded.ContentCopy, onClick = onDuplicate),
    RowAction("Edit", Icons.Rounded.Edit, onClick = onEdit),
    RowAction("Delete", Icons.Rounded.DeleteOutline, destructive = true, onClick = onDelete),
)
