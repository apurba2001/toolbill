package com.toolbill.android.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.SectionHeader
import com.toolbill.android.core.domain.money.MoneyFormat

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val home = "INR"
    val total = sampleCategorySpend.sumOf { it.amountMinor }
    val max = sampleCategorySpend.maxOf { it.amountMinor }

    // Fixed header, scrolling body — `flex:none` over `flex:1` in the design.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Text(
                text = "Insights",
                style = ToolbillText.monthTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Financial year 2026–27 · Apr → Aug",
                style = Toolbill.money.code,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
        item { SectionHeader("Spend by category · normalized monthly") }

        items(sampleCategorySpend, key = { it.label }) { entry ->
            CategoryRow(entry, max, home)
        }

        item {
            Text(
                text = "sums to ${MoneyFormat.symbol(total, home)} — the same figure as the " +
                    "Home hero",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
            )
        }

        item {
            SectionHeader(
                title = "Business vs personal",
                trailing = "$businessSharePercent / $personalSharePercent",
            )
            SplitBar(BUSINESS_MINOR, PERSONAL_MINOR)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4, vertical = Space.s2),
            ) {
                Text(
                    text = "${MoneyFormat.symbol(BUSINESS_MINOR, home)} deductible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${MoneyFormat.symbol(PERSONAL_MINOR, home)} personal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("Cheaper billed annually") }

        items(sampleAnnualSavings, key = { it.service }) { saving ->
            Column(Modifier.padding(horizontal = Space.s4, vertical = Space.s2)) {
                Text(
                    text = saving.service,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${MoneyFormat.symbolWhole(saving.monthlyTotalMinor, home)} → " +
                            "${MoneyFormat.symbolWhole(saving.annualPriceMinor, home)} a year",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = MoneyFormat.signedSymbol(-saving.savingMinor, home)
                            .removePrefix("−").let { "−$it" },
                        style = Toolbill.money.rowSecondary,
                        color = Toolbill.stateColors.trial.content,
                    )
                }
            }
        }

        item {
            val totalSaving = sampleAnnualSavings.sumOf { it.savingMinor }
            val percent = totalSaving * 1000 / ANNUAL_TOOLING_MINOR
            Text(
                text = "Switching all three saves ${MoneyFormat.symbolWhole(totalSaving, home)} " +
                    "a year — ${percent / 10}.${percent % 10}% of your " +
                    "${MoneyFormat.symbolWhole(ANNUAL_TOOLING_MINOR, home)} annual tooling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
            )
        }

        item {
            SectionHeader("FX impact · since 12 Apr capture", trailing = "+₹2,902")
            FxDriftChart()
            Text(
                text = "Dotted line is what you'd have paid at April's captured rate. You are " +
                    "paying 3.0% more for the same tools — ₹1,254 a month on ₹40,438 of " +
                    "USD-billed burn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
            )
            Spacer(Modifier.height(Space.s12))
        }
        }
    }
}

@Composable
private fun CategoryRow(entry: CategorySpend, maxMinor: Long, home: String) {
    Column(Modifier.padding(horizontal = Space.s4, vertical = 4.5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = entry.label,
                style = ToolbillText.insightsRow,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MoneyFormat.format(entry.amountMinor, home).plain,
                style = Toolbill.money.code.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(5.dp))
        // A full-width track with an amber fill — the empty remainder is what makes the
        // proportion legible. A bare bar leaves the reader guessing what 100% would be.
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(entry.amountMinor.toFloat() / maxMinor)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SplitBar(businessMinor: Long, personalMinor: Long) {
    val fraction = businessMinor.toFloat() / (businessMinor + personalMinor)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .height(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

/**
 * The FX drift chart: what you actually paid, against what April's captured rate would have
 * cost. Nothing is drawn before the first rate Toolbill captured itself.
 */
@Composable
private fun FxDriftChart() {
    val actual = listOf(0.00f, 0.22f, 0.45f, 0.71f, 1.00f)
    val line = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .height(72.dp),
    ) {
        val stepX = size.width / (actual.size - 1)
        val baselineY = size.height * 0.85f

        drawLine(
            color = baseline,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        actual.forEachIndexed { index, value ->
            if (index == 0) return@forEachIndexed
            val previous = actual[index - 1]
            drawLine(
                color = line,
                start = Offset((index - 1) * stepX, baselineY - previous * baselineY * 0.7f),
                end = Offset(index * stepX, baselineY - value * baselineY * 0.7f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s4),
    ) {
        listOf("Apr", "May", "Jun", "Jul", "Aug").forEach { month ->
            Text(
                text = month,
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
