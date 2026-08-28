package com.toolbill.android.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.IbmPlexMono
import com.toolbill.android.core.design.W450
import com.toolbill.android.core.design.IbmPlexSans
import com.toolbill.android.core.design.Radius
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.SortSelector
import com.toolbill.android.core.design.component.ToolbillActionButton
import com.toolbill.android.core.design.component.ToolbillActionTextButton
import com.toolbill.android.core.design.component.ToolbillFilterChip

// Onboarding is drawn on a 24dp gutter with 36dp of head room — wider than the 16dp the list
// screens use. The action bar sits at padding:16px 24px 12px.
private val Gutter = 24.dp
private val HeadRoom = 36.dp

private val StepLabel = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = W450,
    fontSize = 11.sp,
    lineHeight = 11.sp,
    letterSpacing = 1.32.sp, // .12em
)

private val Heading = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp,
)

private val Body = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

private val Caption = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

private val SmallProse = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
)

private val ChoiceText = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

private val CurrencyRowText = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

private val CurrencyCode = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 20.sp,
)

private val FieldCode = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 13.sp,
)

private val SectionEyebrow = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = W450,
    fontSize = 10.5.sp,
    lineHeight = 11.sp,
    letterSpacing = 1.05.sp,
)

private data class CurrencyOption(val name: String, val code: String)

private val currencies = listOf(
    CurrencyOption("Indian rupee", "INR"),
    CurrencyOption("US dollar", "USD"),
    CurrencyOption("Euro", "EUR"),
    CurrencyOption("Pound sterling", "GBP"),
    CurrencyOption("Canadian dollar", "CAD"),
    CurrencyOption("Australian dollar", "AUD"),
)

private val startingPoints = listOf(
    "Adobe CC", "AWS", "Figma", "Google Workspace",
    "Shopify", "Claude", "Vercel", "Notion",
)

@Composable
private fun Rule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Toolbill.stateColors.dividerDense),
    )
}

/**
 * Three screens, no account, no paywall. Skip lands you in an empty Home.
 *
 * The chosen home currency is a filled field, not a checked radio: it is the answer already
 * given, and the list beneath it is the set of alternatives rather than a poll.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinish: () -> Unit = {},
    onAddManually: () -> Unit = {},
) {
    var step by remember { mutableIntStateOf(0) }
    var currencyIndex by remember { mutableIntStateOf(0) }
    var splitIndex by remember { mutableIntStateOf(0) }
    var defaultBusiness by remember { mutableStateOf(true) }
    var trackFx by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Gutter, end = Gutter, top = HeadRoom),
        ) {
            Text(
                text = "Step ${step + 1} of 3".uppercase(),
                style = StepLabel,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(14.dp))

            when (step) {
                0 -> CurrencyStep(currencyIndex) { currencyIndex = it }
                1 -> SplitStep(
                    splitIndex = splitIndex,
                    defaultBusiness = defaultBusiness,
                    trackFx = trackFx,
                    onSplit = { splitIndex = it },
                    onDefaultBusiness = { defaultBusiness = it },
                    onTrackFx = { trackFx = it },
                )

                else -> FirstEntryStep()
            }
            Spacer(Modifier.height(Gutter))
        }

        // The action bar: a full-width primary, with at most one text action to its left.
        // Step three offers Skip / Add manually — there is no Back once you are at the end.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (step) {
                0 -> ToolbillActionButton(
                    text = "Continue",
                    onClick = { step++ },
                    modifier = Modifier.weight(1f),
                )

                1 -> {
                    ToolbillActionTextButton(text = "Back", onClick = { step-- })
                    ToolbillActionButton(
                        text = "Continue",
                        onClick = { step++ },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    ToolbillActionTextButton(text = "Skip for now", onClick = onFinish)
                    ToolbillActionButton(
                        text = "Add manually",
                        onClick = onAddManually,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, body: String, bodyBottom: Int) {
    Text(text = title, style = Heading, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(text = body, style = Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(bodyBottom.dp))
}

@Composable
private fun CurrencyStep(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    StepHeading(
        title = "What currency do you think in?",
        body = "Every total in Toolbill is converted to this. You can still enter " +
            "subscriptions in the currency they're billed in.",
        bodyBottom = 24,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outline, Radius.sm)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Home currency", style = ToolbillText.fieldLabel, color = scheme.onSurfaceVariant)
            Text(
                text = currencies[selected].name,
                style = ToolbillText.fieldValue,
                color = scheme.onSurface,
            )
        }
        Text(text = currencies[selected].code, style = FieldCode, color = scheme.primary)
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "Detected from your device region. Change it any time in Settings.",
        style = Caption,
        color = scheme.outline,
    )

    Spacer(Modifier.height(24.dp))
    val alternatives = currencies.indices.filter { it != selected }
    Rule()
    alternatives.forEachIndexed { position, index ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(index) }
                .padding(horizontal = 2.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currencies[index].name,
                style = CurrencyRowText,
                color = if (position == alternatives.lastIndex) {
                    scheme.onSurfaceVariant
                } else {
                    scheme.onSurface
                },
            )
            Text(currencies[index].code, style = CurrencyCode, color = scheme.outline)
        }
        if (position != alternatives.lastIndex) Rule()
    }
}

@Composable
private fun SplitStep(
    splitIndex: Int,
    defaultBusiness: Boolean,
    trackFx: Boolean,
    onSplit: (Int) -> Unit,
    onDefaultBusiness: (Boolean) -> Unit,
    onTrackFx: (Boolean) -> Unit,
) {
    StepHeading(
        title = "Do you need to separate business spend?",
        body = "If yes, every subscription gets a business or personal tag and the CSV " +
            "export splits them for filing.",
        bodyBottom = 22,
    )
    SortSelector(
        options = listOf("Both, tagged", "Business only", "Personal"),
        selectedIndex = splitIndex,
        horizontalPadding = 0.dp,
        height = 40.dp,
        textStyle = ChoiceText,
        onSelect = onSplit,
    )
    Spacer(Modifier.height(20.dp))
    ToggleRow(
        title = "Default new entries to business",
        body = "Most of your tools are probably deductible.",
        checked = defaultBusiness,
        onCheckedChange = onDefaultBusiness,
    )
    ToggleRow(
        title = "Track FX drift",
        body = "Records what each foreign charge actually cost you.",
        checked = trackFx,
        onCheckedChange = onTrackFx,
    )

    // The privacy promise is a bordered card, not loose copy — it is the claim the whole
    // product rests on.
    Spacer(Modifier.height(26.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.md)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "No account, ever",
            style = ChoiceText,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Data is stored on this device. Backup is optional and goes to your own " +
                "Google Drive. Toolbill never asks for bank credentials.",
            style = SmallProse,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FirstEntryStep() {
    StepHeading(
        title = "Add your most expensive tool first.",
        body = "One entry is enough to see how this works. Add the rest whenever — " +
            "there's no cap on the free tier.",
        bodyBottom = 20,
    )
    Text(
        text = "COMMON STARTING POINTS",
        style = SectionEyebrow,
        color = MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.height(10.dp))
    ChipFlow(startingPoints)
    Spacer(Modifier.height(26.dp))
    Rule()
    Spacer(Modifier.height(18.dp))
    Text(
        text = "Picking a chip pre-fills name, category and the usual billing cycle. " +
            "Everything stays editable — nothing is fetched from a server.",
        style = SmallProse,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * Wrapping chip flow.
 *
 * A real flow layout — estimating each chip's width from its character count packed the rows
 * unevenly and left ragged gaps at the right edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(labels: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { ToolbillFilterChip(label = it, selected = false) }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Rule()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = ToolbillText.rowName,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolbillSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 44×26 with a 20dp knob — Material's switch is 52×32 and reads much heavier than the design. */
@Composable
private fun ToolbillSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .background(
                color = if (checked) scheme.primary else scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(13.dp),
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .background(
                    color = if (checked) scheme.onPrimary else scheme.outline,
                    shape = RoundedCornerShape(10.dp),
                ),
        )
    }
}
