package com.toolbill.android.feature.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.FieldMessage
import com.toolbill.android.core.design.component.FieldSectionLabel
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.ToolbillActionButton
import com.toolbill.android.core.design.component.ToolbillFilterChip
import com.toolbill.android.core.design.component.ToolbillOutlinedButton
import com.toolbill.android.core.design.component.ToolbillSelectField
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.component.ToolbillTextField
import com.toolbill.android.core.domain.subscription.BillingCycle
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.toolbill.android.core.domain.subscription.Subscription

/** Which step the sheet is showing. The sheet itself never stacks. */
private enum class SheetStep { Form, Cycle, Currency }

/** A bundled catalogue entry. Never carries an amount — see [AddEditSubscriptionSheet]. */
data class CatalogueEntry(
    val name: String,
    val currency: String,
    val cycle: BillingCycle,
    val category: String,
)

private val catalogue = listOf(
    CatalogueEntry("Claude Pro", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Claude Max", "USD", BillingCycle.MONTHLY, "AI tools"),
    CatalogueEntry("Adobe Creative Cloud", "USD", BillingCycle.ANNUAL, "Design"),
    CatalogueEntry("AWS", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Figma Professional", "USD", BillingCycle.MONTHLY, "Design"),
    CatalogueEntry("Google Workspace", "INR", BillingCycle.MONTHLY, "Productivity"),
    CatalogueEntry("Shopify Basic", "INR", BillingCycle.MONTHLY, "Other"),
    CatalogueEntry("Vercel Pro", "USD", BillingCycle.MONTHLY, "Hosting & infra"),
    CatalogueEntry("Notion Plus", "USD", BillingCycle.MONTHLY, "Productivity"),
)

private val cyclePresets = listOf(
    "Monthly" to BillingCycle.MONTHLY,
    "Annual" to BillingCycle.ANNUAL,
    "Quarterly" to BillingCycle.QUARTERLY,
    "Weekly" to BillingCycle.WEEKLY,
)

// One segmented control with a trailing "Custom" segment, not five loose chips. Named
// rather than an ellipsis: it opens a step, and an ellipsis promises a menu.
private val cycleSegments = cyclePresets.map { it.first } + "Custom"

private val firstChargeOptions = listOf("Today", "1 Sep", "Pick…")
private val chargeDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * The add / edit sheet.
 *
 * A sheet rather than a full screen because adding is a repeated, low-commitment act: someone
 * entering their fifth tool in a row should never lose sight of the list.
 *
 * The catalogue pre-fills name, currency, cycle and category — but **never the amount**.
 * Bundled prices go stale within months, and a silently wrong amount corrupts the burn total
 * permanently, because nobody re-checks a field that was already filled. Focus lands on the
 * empty amount instead: the one number only the user's invoice knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubscriptionSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = onDismiss,
    editing: Subscription? = null,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(editing?.currency ?: SampleData.HOME_CURRENCY) }
    var cycleIndex by remember { mutableIntStateOf(0) }
    var chargeIndex by remember { mutableIntStateOf(0) }
    var isBusiness by remember { mutableStateOf(editing?.isBusiness ?: true) }
    var amountTouched by remember { mutableStateOf(false) }
    var customCycle by remember { mutableStateOf<BillingCycle?>(null) }
    var step by remember { mutableStateOf(SheetStep.Form) }
    var pickingDate by remember { mutableStateOf(false) }
    var firstCharge by remember { mutableStateOf(SampleData.today) }

    BackHandler(enabled = step != SheetStep.Form) { step = SheetStep.Form }

    val amountFocus = remember { FocusRequester() }
    val suggestions = remember(name) {
        if (name.isBlank()) emptyList()
        else catalogue.filter { it.name.startsWith(name, ignoreCase = true) }.take(3)
    }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    // Only the amount holds Save. Validation appears on blur, never while typing.
    val amountError = amountTouched && amount.isNotEmpty() && amountValue <= 0.0
    val canSave = name.isNotBlank() && amountValue > 0.0

    // The amount and currency values are 20sp mono in the design — larger than a normal field.
    val fieldMoney = Toolbill.money.fieldValue.copy(fontSize = 20.sp, lineHeight = 26.sp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = Radius.lg,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // The step swaps inside this one sheet. Stacking a second sheet doubles the drag
        // handles and scrims; growing this one pushes the required fields under the keyboard.
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState == SheetStep.Form) {
                    slideInHorizontally(tween(220)) { -it / 6 } + fadeIn(tween(160)) togetherWith
                        slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(140))
                } else {
                    slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(160)) togetherWith
                        slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(140))
                }
            },
            label = "addSheetStep",
        ) { current ->
            when (current) {
                SheetStep.Cycle -> CustomCycleContent(
                    initial = customCycle ?: cyclePresets[cycleIndex.coerceAtMost(3)].second,
                    onBack = { step = SheetStep.Form },
                    onConfirm = {
                        customCycle = it
                        cycleIndex = cycleSegments.lastIndex
                        step = SheetStep.Form
                    },
                )

                SheetStep.Currency -> CurrencyPickerContent(
                    selected = currency,
                    onBack = { step = SheetStep.Form },
                    onPick = { currency = it; step = SheetStep.Form },
                )

                SheetStep.Form ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (editing == null) "Add subscription" else "Edit subscription",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (editing == null) "Cancel" else "Delete",
                    style = ToolbillText.button,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                ToolbillTextField(
                    label = "Service",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Search or type a name",
                    modifier = Modifier.fillMaxWidth(),
                )

                suggestions.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = entry.name
                                currency = entry.currency
                                cycleIndex = cyclePresets
                                    .indexOfFirst { it.second == entry.cycle }
                                    .coerceAtLeast(0)
                                amountFocus.requestFocus()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name,
                            style = ToolbillText.rowName,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${entry.category} · ${entry.currency}",
                            style = ToolbillText.rowSupport,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Amount and Currency are the same field box, so their heights agree by
                // construction rather than by coincidence. The currency slot is a fixed 104dp.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ToolbillTextField(
                        label = "Amount",
                        value = amount,
                        onValueChange = { amount = it; amountTouched = true },
                        placeholder = "0.00",
                        isError = amountError,
                        valueStyle = fieldMoney,
                        keyboardType = KeyboardType.Decimal,
                        focusRequester = amountFocus,
                        modifier = Modifier.weight(1f),
                    )
                    ToolbillSelectField(
                        label = "Currency",
                        value = currency,
                        width = 104.dp,
                        valueStyle = fieldMoney,
                        onClick = { step = SheetStep.Currency },
                    )
                }

                if (amountError) FieldMessage("Amount must be more than zero", isError = true)

                Spacer(Modifier.height(16.dp))
                FieldSectionLabel("Billing cycle")
                Spacer(Modifier.height(8.dp))
                SortSelector(
                    options = cycleSegments,
                    selectedIndex = cycleIndex,
                    horizontalPadding = 0.dp,
                    // The last segment is the custom cycle; it opens a nested sheet rather than
                    // growing this one, so the required fields stay above the keyboard.
                    onSelect = { index ->
                        if (index == cycleSegments.lastIndex) step = SheetStep.Cycle
                        else { cycleIndex = index; customCycle = null }
                    },
                )
                if (customCycle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = customCycle!!.label().replaceFirstChar { it.uppercase() },
                        style = ToolbillText.rowSupport,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(14.dp))
                FieldSectionLabel("First charge")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    firstChargeOptions.forEachIndexed { index, label ->
                        ToolbillFilterChip(
                            label = if (index == 2 && chargeIndex == 2) {
                                firstCharge.format(chargeDateFormat)
                            } else {
                                label
                            },
                            selected = index == chargeIndex,
                            onClick = {
                                if (index == 2) pickingDate = true else chargeIndex = index
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                ToolbillFilterChip(
                    label = "Business expense",
                    selected = isBusiness,
                    onClick = { isBusiness = !isBusiness },
                )

                // Category, notes and trial date stay behind text actions so the required path
                // is only four fields long.
                Spacer(Modifier.height(6.dp))
                Row {
                    ToolbillTextButton(text = "+ Notes", onClick = {})
                    ToolbillTextButton(text = "+ Trial end date", onClick = {})
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ToolbillActionButton(
                        text = if (editing == null) "Save" else "Save changes",
                        onClick = onSaved,
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    )
                    if (editing == null) {
                        // Keeps the sheet open and resets only name and amount — the pattern
                        // that gets someone from zero to thirty entries in one sitting.
                        ToolbillOutlinedButton(
                            text = "＋ Another",
                            onClick = { name = ""; amount = ""; amountTouched = false },
                            enabled = canSave,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Save enables once name and amount are set. Nothing leaves the device.",
                    style = ToolbillText.sectionCaption,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
            }
        }
    }

    if (pickingDate) {
        FirstChargeDatePicker(
            initial = firstCharge,
            onDismiss = { pickingDate = false },
            onPick = { firstCharge = it; chargeIndex = 2; pickingDate = false },
        )
    }
}
