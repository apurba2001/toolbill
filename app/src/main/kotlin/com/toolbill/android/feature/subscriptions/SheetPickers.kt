package com.toolbill.android.feature.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.ToolbillActionButton
import com.toolbill.android.core.design.component.ToolbillActionTextButton
import com.toolbill.android.core.design.component.ToolbillFilterChip
import com.toolbill.android.core.domain.date.CycleUnit
import com.toolbill.android.core.domain.subscription.BillingCycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val unitLabels = listOf("Days", "Weeks", "Months", "Years")
private val unitValues = listOf(CycleUnit.DAY, CycleUnit.WEEK, CycleUnit.MONTH, CycleUnit.YEAR)
private val commonCounts = listOf(2, 3, 6, 10, 14, 30, 45, 90)

/**
 * The custom billing cycle — a step *within* the add sheet, not a sheet on top of it.
 *
 * Stacking sheets doubles the drag handles and the scrims and leaves the parent's edge showing;
 * growing the parent pushes the required fields under the keyboard. Swapping the content avoids
 * both: one sheet, one surface, and the form is exactly where it was on the way back.
 *
 * Count and unit stay separate because "every 3 months" and "every 90 days" are different
 * schedules — the first lands on real calendar dates, the second drifts.
 */
@Composable
fun CustomCycleContent(
    initial: BillingCycle,
    onBack: () -> Unit,
    onConfirm: (BillingCycle) -> Unit,
) {
    var count by remember { mutableIntStateOf(initial.count) }
    var unitIndex by remember { mutableIntStateOf(unitValues.indexOf(initial.unit).coerceAtLeast(0)) }

    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            StepHeader(title = "Custom billing cycle", onBack = onBack)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Charged " + everyPhrase(count, unitIndex) +
                    ", counted from the first charge date.",
                style = ToolbillText.noticeBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            FieldSectionLabel("Every")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commonCounts.take(5).forEach { value ->
                    ToolbillFilterChip(
                        label = value.toString(),
                        selected = value == count,
                        onClick = { count = value },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                commonCounts.drop(5).forEach { value ->
                    ToolbillFilterChip(
                        label = value.toString(),
                        selected = value == count,
                        onClick = { count = value },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldSectionLabel("Unit")
            Spacer(Modifier.height(8.dp))
            SortSelector(
                options = unitLabels,
                selectedIndex = unitIndex,
                horizontalPadding = 0.dp,
                onSelect = { unitIndex = it },
            )

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbillActionTextButton(text = "Cancel", onClick = onBack)
                ToolbillActionButton(
                    text = "Use this cycle",
                    onClick = { onConfirm(BillingCycle(unitValues[unitIndex], count)) },
                    modifier = Modifier.weight(1f),
                )
            }
    }
}

/** The back affordance a swapped-in step needs, since the sheet's own handle only dismisses. */
@Composable
private fun StepHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back to the form",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The currency step. A plain list — the same shape onboarding uses for the same job. */
@Composable
fun CurrencyPickerContent(
    selected: String,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val options = listOf(
        "INR" to "Indian rupee",
        "USD" to "US dollar",
        "EUR" to "Euro",
        "GBP" to "Pound sterling",
        "CAD" to "Canadian dollar",
        "AUD" to "Australian dollar",
        "SGD" to "Singapore dollar",
        "AED" to "UAE dirham",
    )

    Column(Modifier.padding(bottom = 16.dp)) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                StepHeader(title = "Billed in", onBack = onBack)
            }
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            options.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(code) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = ToolbillText.settingsTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = code,
                        style = Toolbill.money.code,
                        color = if (code == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }
    }
}

/** The first-charge date picker, behind the sheet's "Pick…" chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstChargeDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ToolbillActionTextButton(
                text = "Set date",
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    } else {
                        onDismiss()
                    }
                },
            )
        },
        dismissButton = { ToolbillActionTextButton(text = "Cancel", onClick = onDismiss) },
    ) {
        DatePicker(state = state, title = null)
    }
}

/** "every month" for one, "every 3 months" for more — never "every 1 months". */
private fun everyPhrase(count: Int, unitIndex: Int): String {
    val singular = unitLabels[unitIndex].lowercase().removeSuffix("s")
    return if (count == 1) "every $singular" else "every $count ${singular}s"
}
