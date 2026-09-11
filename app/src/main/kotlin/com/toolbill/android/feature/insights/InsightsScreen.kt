package com.toolbill.android.feature.insights

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.SectionHeader
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.subscription.Charge
import com.toolbill.android.core.domain.subscription.CategorySpend
import com.toolbill.android.core.domain.subscription.PricedSubscription
import com.toolbill.android.core.domain.subscription.businessBurnMinor
import com.toolbill.android.core.domain.subscription.businessSharePercent
import com.toolbill.android.core.domain.subscription.categorySpend
import com.toolbill.android.core.domain.subscription.monthlyBurnMinor
import com.toolbill.android.core.domain.subscription.personalBurnMinor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    subscriptions: List<PricedSubscription> = emptyList(),
    charges: List<Charge> = emptyList(),
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
) {
    val home = homeCurrency
    val categories = remember(subscriptions) { subscriptions.categorySpend() }
    val burn = remember(subscriptions) { subscriptions.monthlyBurnMinor() }
    val business = remember(subscriptions) { subscriptions.businessBurnMinor() }
    val personal = remember(subscriptions) { subscriptions.personalBurnMinor() }
    val businessPercent = remember(subscriptions) { subscriptions.businessSharePercent() }
    val max = categories.maxOfOrNull { it.amountMinor } ?: 1L
    val drift = remember(charges, homeCurrency) { FxDrift.from(charges, homeCurrency) }

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
                text = financialYearLabel(today),
                style = Toolbill.money.code,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (categories.isEmpty()) {
            Text(
                text = "Nothing to break down yet. Add a subscription and this fills in — " +
                    "category split, business share, and what currency movement has cost you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s4),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item { SectionHeader("Spend by category · normalized monthly") }

            items(categories, key = { it.label }) { entry ->
                CategoryRow(entry, max, home)
            }

            item {
                Text(
                    // True by construction: every active subscription lands in exactly one
                    // bucket, so the breakdown and the headline cannot disagree.
                    text = "sums to ${MoneyFormat.symbol(burn, home)} — the same figure as " +
                        "the Home hero",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                )
            }

            item {
                SectionHeader(
                    title = "Business vs personal",
                    trailing = "$businessPercent / ${100 - businessPercent}",
                )
                SplitBar(business, personal)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.s4, vertical = Space.s2),
                ) {
                    Text(
                        text = "${MoneyFormat.symbol(business, home)} deductible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${MoneyFormat.symbol(personal, home)} personal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "FX impact",
                    trailing = drift?.let { MoneyFormat.signedSymbol(it.deltaMinor, home) },
                )
                Text(
                    text = drift?.let {
                        "Charges recorded between ${it.first.format(monthShort)} and " +
                            "${it.last.format(monthShort)} came to " +
                            "${MoneyFormat.symbolWhole(it.actualMinor, home)}. At the rate " +
                            "captured on the first of them they would have been " +
                            "${MoneyFormat.symbolWhole(it.baselineMinor, home)}."
                    } ?: "Nothing measured yet. Once Toolbill has recorded the same " +
                        "foreign-billed charge at two different rates, what the movement has " +
                        "cost you appears here — as a figure it observed, not an estimate. " +
                        "Subscriptions billed in $home never drift, so they are left out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                )
                Spacer(Modifier.height(Space.s12))
            }
        }
    }
}

/**
 * What currency movement has actually cost, from recorded charges alone.
 *
 * Compares what each charge was recorded at against what it would have been at the first
 * captured rate for that subscription. Only charges Toolbill captured itself take part — an
 * uncaptured row has no rate to compare against and would drag the figure toward a number
 * nobody measured.
 */
private data class FxDrift(
    val actualMinor: Long,
    val baselineMinor: Long,
    val first: LocalDate,
    val last: LocalDate,
) {
    val deltaMinor: Long get() = actualMinor - baselineMinor

    companion object {
        /**
         * Drift across [homeCurrency], from recorded charges alone.
         *
         * Only foreign-billed charges take part. A subscription billed in the home currency has
         * a rate of one for ever and no movement to measure, and including it would inflate both
         * sides of the comparison -- making "charges came to X" a figure mostly about spending
         * that has nothing to do with currency, under a heading that says FX.
         *
         * Only charges Toolbill captured itself take part either: an uncaptured row has no rate
         * to compare against and would drag the figure toward a number nobody measured.
         */
        fun from(charges: List<Charge>, homeCurrency: String): FxDrift? {
            // Skipped rows are excluded before anything is divided by them: they keep the
            // contracted amount but cost zero, so one would set a baseline rate of zero and
            // take the whole drift figure with it.
            val captured = charges.filter {
                it.captured &&
                    it.countsTowardSpend &&
                    it.amountMinor > 0 &&
                    !it.currency.equals(homeCurrency, ignoreCase = true)
            }
            if (captured.isEmpty()) return null

            // The first captured charge of each subscription sets that subscription's baseline.
            // The rate it was actually converted at is stored on the row, so it is read rather
            // than reconstructed by dividing one rounded figure by another.
            val baselineRate = captured
                .groupBy { it.subscriptionId }
                .mapValues { (_, rows) -> rows.minBy { it.dueDate }.effectiveRate() }

            var actual = 0L
            var baseline = 0L
            captured.forEach { charge ->
                val rate = baselineRate[charge.subscriptionId] ?: return@forEach
                actual += charge.homeAmountMinor
                baseline += convertAt(charge.amountMinor, charge.currency, rate, homeCurrency)
            }
            if (actual == baseline) return null

            return FxDrift(
                actualMinor = actual,
                baselineMinor = baseline,
                first = captured.minOf { it.dueDate },
                last = captured.maxOf { it.dueDate },
            )
        }

        /**
         * The rate this charge went through at.
         *
         * Prefers the stored one. Rows written before Toolbill recorded rates carry none, so
         * theirs is recovered from the two figures it does have -- in decimal, because the whole
         * app stores money as exact integers precisely so binary floats stay out of the maths.
         */
        private fun Charge.effectiveRate(): BigDecimal = fxRate ?: BigDecimal(homeAmountMinor)
            .movePointLeft(MoneyFormat.fractionDigits(homeCurrency))
            .divide(
                BigDecimal(amountMinor).movePointLeft(MoneyFormat.fractionDigits(currency)),
                6,
                RoundingMode.HALF_UP,
            )

        private fun convertAt(
            amountMinor: Long,
            currency: String,
            rate: BigDecimal,
            homeCurrency: String,
        ): Long = BigDecimal(amountMinor)
            .movePointLeft(MoneyFormat.fractionDigits(currency))
            .multiply(rate)
            .movePointRight(MoneyFormat.fractionDigits(homeCurrency))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}

/** India's financial year runs April to March; the label names the months covered so far. */
private fun financialYearLabel(today: LocalDate): String {
    val startYear = if (today.monthValue >= 4) today.year else today.year - 1
    val endShort = ((startYear + 1) % 100).toString().padStart(2, '0')
    return "Financial year $startYear-$endShort · Apr → ${today.format(monthShort)}"
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
                style = Toolbill.money.code.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
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
    val total = (businessMinor + personalMinor).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.s4)
            .height(3.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(businessMinor.toFloat() / total)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
