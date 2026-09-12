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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.ToolbillIcons
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
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.core.domain.subscription.CatalogueEntry
import com.toolbill.android.core.domain.subscription.Category
import com.toolbill.android.core.domain.subscription.catalogueCategoryFor
import com.toolbill.android.core.domain.subscription.suggestServices
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Which step the sheet is showing. The sheet itself never stacks. */
private enum class SheetStep { Form, Cycle, Currency, Category }

private val cyclePresets = listOf(
    "Monthly" to BillingCycle.MONTHLY,
    "Annual" to BillingCycle.ANNUAL,
    "Quarterly" to BillingCycle.QUARTERLY,
    "Weekly" to BillingCycle.WEEKLY,
)

// One segmented control with a trailing custom segment, not five loose chips. The ellipsis
// is the point: the segment opens a step rather than committing a value.
private val cycleSegments = cyclePresets.map { it.first } + "\u2026"

/**
 * The first-charge presets.
 *
 * The middle one used to be a literal "1 Sep", which is only a sensible default in August: after
 * the 1st of September it resolved to the *next* year, so a subscription added in October was
 * anchored eleven months out and showed up on no calendar the user would ever look at. The 1st
 * of next month is the common billing anchor and is always within a month.
 */
private val firstChargeOptions = listOf("Today", "", "Pick…")
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
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
    onSave: (Subscription) -> Unit = {},
    onSaved: () -> Unit = onDismiss,
    onDelete: (() -> Unit)? = null,
    editing: Subscription? = null,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    // Prefilled when editing: the amount is what holds Save, so leaving it blank would disable
    // "Save changes" until the figure had been retyped off the invoice.
    var amount by remember {
        mutableStateOf(editing?.let { majorText(it.amountMinor, it.currency) }.orEmpty())
    }
    var currency by remember { mutableStateOf(editing?.currency ?: homeCurrency) }
    var cycleIndex by remember {
        mutableIntStateOf(
            cyclePresets.indexOfFirst { it.second == editing?.cycle }
                .takeIf { it >= 0 } ?: if (editing == null) 0 else cycleSegments.lastIndex,
        )
    }
    var isBusiness by remember { mutableStateOf(editing?.isBusiness ?: true) }
    var amountTouched by remember { mutableStateOf(false) }
    var customCycle by remember { mutableStateOf(editing?.cycle?.takeUnless { it.isPreset }) }
    var step by remember { mutableStateOf(SheetStep.Form) }
    var pickingDate by remember { mutableStateOf(false) }
    var firstCharge by remember { mutableStateOf(editing?.anchorDate ?: today) }
    // null means "follow the name", which is what the row has always claimed to do. An edited
    // subscription starts pinned to whatever it was saved with.
    var chosenCategory by remember { mutableStateOf(editing?.category) }

    BackHandler(enabled = step != SheetStep.Form) { step = SheetStep.Form }

    val amountFocus = remember { FocusRequester() }
    val suggestions = remember(name) {
        suggestServices(name)
    }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    // Only the amount holds Save. Validation appears on blur, never while typing.
    val amountError = amountTouched && amount.isNotEmpty() && amountValue <= 0.0
    val canSave = name.isNotBlank() && amountValue > 0.0

    // Everything the form knows, as the domain object the repository stores. Fields this sheet
    // does not offer yet are carried over from the row being edited rather than reset to null.
    fun composed(): Subscription = Subscription(
        id = editing?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        amountMinor = minorUnits(amount, currency),
        currency = currency,
        cycle = customCycle ?: cyclePresets[cycleIndex.coerceIn(cyclePresets.indices)].second,
        anchorDate = firstCharge,
        category = chosenCategory ?: categoryFor(name) ?: Category.OTHER,
        otherLabel = editing?.otherLabel,
        isBusiness = isBusiness,
        status = editing?.status ?: SubscriptionStatus.ACTIVE,
        trialEndDate = editing?.trialEndDate,
        resumeDate = editing?.resumeDate,
        cancelledDate = editing?.cancelledDate,
        notes = editing?.notes,
    )

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

                SheetStep.Category -> CategoryPickerContent(
                    selected = chosenCategory,
                    autoCategory = categoryFor(name),
                    onBack = { step = SheetStep.Form },
                    onPick = { chosenCategory = it; step = SheetStep.Form },
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
                    color = if (editing == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.clickable(
                        onClick = { if (editing == null) onDismiss() else onDelete?.invoke() },
                    ),
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
                        val matchIdx = entry.name.indexOf(name, ignoreCase = true)
                        val annotated = buildAnnotatedString {
                            if (matchIdx >= 0) {
                                append(entry.name.substring(0, matchIdx))
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(entry.name.substring(matchIdx, matchIdx + name.length))
                                }
                                append(entry.name.substring(matchIdx + name.length))
                            } else {
                                append(entry.name)
                            }
                        }
                        Text(
                            text = annotated,
                            style = ToolbillText.rowName,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${entry.category} \u00B7 ${entry.cycle.label()} \u00B7 ${entry.currency}",
                            style = ToolbillText.rowSupport,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (suggestions.isNotEmpty() && suggestions.none { it.name.equals(name, ignoreCase = true) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { amountFocus.requestFocus() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Use \"${name}\" as typed",
                            style = ToolbillText.rowSupport,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(ToolbillIcons.Return),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
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
                    // Segments size to their own labels and the row scrolls if the five of them
                    // do not fit, rather than each being squeezed to a fifth of the width.
                    fillWidth = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                // Which chip is lit is derived from the date rather than tracked beside it.
                // Held separately, editing a 1-September subscription selected the custom chip
                // and rendered it as "1 Sep" next to a preset reading the same thing -- two
                // chips, same label, one of them lit, for a single date.
                val nextMonth = firstOfNextMonth(today)
                val presetDates = listOf(today, nextMonth)
                val selectedChip = presetDates.indexOf(firstCharge).takeIf { it >= 0 } ?: 2

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    firstChargeOptions.forEachIndexed { index, label ->
                        ToolbillFilterChip(
                            label = when {
                                index == 1 -> nextMonth.format(chargeDateFormat)
                                index == 2 && selectedChip == 2 ->
                                    firstCharge.format(chargeDateFormat)

                                else -> label
                            },
                            selected = index == selectedChip,
                            showCheckmark = false,
                            onClick = {
                                when (index) {
                                    2 -> pickingDate = true
                                    else -> firstCharge = presetDates[index]
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isBusiness = !isBusiness },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Business expense",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = isBusiness,
                        onCheckedChange = { isBusiness = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { step = SheetStep.Category },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // Names the category that will actually be saved. "Auto · from name"
                        // was true of the mechanism and told the user nothing about the result.
                        text = (chosenCategory ?: categoryFor(name))?.displayName?.let {
                            if (chosenCategory == null) "Auto · $it ▾" else "$it ▾"
                        } ?: "Auto · from name ▾",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Category, notes and trial date stay behind text actions so the required path
                // is only four fields long.
                Spacer(Modifier.height(16.dp))
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
                        onClick = { onSave(composed()); onSaved() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    )
                    if (editing == null) {
                        // Saves, then keeps the sheet open and resets only name and amount —
                        // the pattern that gets someone from zero to thirty entries in one
                        // sitting. Currency and cycle stay, because the next tool usually
                        // shares them.
                        ToolbillOutlinedButton(
                            text = "Save & add another",
                            onClick = {
                                onSave(composed())
                                name = ""
                                amount = ""
                                amountTouched = false
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f),
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
            onPick = { firstCharge = it; pickingDate = false },
        )
    }
}


/** The amount field's text, in the currency's own minor units. */
private fun minorUnits(amount: String, currency: String): Long =
    BigDecimal(amount.trim())
        .movePointRight(MoneyFormat.fractionDigits(currency))
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()

/** The inverse, for prefilling the field when editing. */
private fun majorText(amountMinor: Long, currency: String): String =
    BigDecimal(amountMinor)
        .movePointLeft(MoneyFormat.fractionDigits(currency))
        .toPlainString()

/**
 * The category the sheet's "Auto, from name" row promises: taken from the bundled
 * catalogue when the name matches one, and left to the caller's fallback when it does not.
 */
private fun categoryFor(name: String): Category? = catalogueCategoryFor(name)

/** The "1 Sep" chip means the next one, which is this year until September has passed. */
/** The 1st of next month — the most common billing anchor, and never more than 31 days out. */
private fun firstOfNextMonth(today: LocalDate): LocalDate =
    today.withDayOfMonth(1).plusMonths(1)
