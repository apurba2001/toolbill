package com.toolbill.android.feature.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.EmptyState
import com.toolbill.android.core.design.component.HeroAmount
import com.toolbill.android.core.design.component.InlineNotice
import com.toolbill.android.core.design.component.NoticeCard
import com.toolbill.android.core.design.component.SectionHeader
import com.toolbill.android.core.design.component.Stat
import com.toolbill.android.core.design.component.StatRow
import com.toolbill.android.core.design.component.UpcomingChargeRow
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.normalizedMonthlyMinor
import com.toolbill.android.core.domain.subscription.AttentionKind
import com.toolbill.android.core.domain.subscription.PricedSubscription
import com.toolbill.android.core.domain.subscription.activeCount
import com.toolbill.android.core.domain.subscription.annualizedBurnMinor
import com.toolbill.android.core.domain.subscription.businessSharePercent
import com.toolbill.android.core.domain.subscription.foreignBilledBurnMinor
import com.toolbill.android.core.domain.subscription.monthlyBurnMinor
import com.toolbill.android.core.domain.subscription.needsAttention
import com.toolbill.android.core.domain.subscription.trials
import com.toolbill.android.core.domain.subscription.upcomingCharges
import com.toolbill.android.feature.subscriptions.toRowUi
import com.toolbill.android.feature.subscriptions.toUpcomingRowUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The week the schedule covers: today plus the following six days. */
private const val WEEK_DAYS = 6L

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    subscriptions: List<PricedSubscription> = emptyList(),
    homeCurrency: String = "INR",
    today: LocalDate = LocalDate.now(),
    onOpenSubscription: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    if (subscriptions.isEmpty()) {
        EmptyState(
            amountMinor = 0,
            currency = homeCurrency,
            headline = "Nothing tracked yet.",
            body = "Add your most expensive tool first — one entry is enough to see the burn " +
                "figure work. Unlimited entries, free, forever.",
            primaryAction = "Add subscription",
            secondaryAction = "Import from CSV",
            modifier = modifier.padding(contentPadding),
            onPrimary = onAdd,
            onSecondary = onImport,
        )
        return
    }

    val burn = remember(subscriptions) { subscriptions.monthlyBurnMinor() }
    val annualized = remember(subscriptions) { subscriptions.annualizedBurnMinor() }
    val activeCount = remember(subscriptions) { subscriptions.activeCount() }
    val businessShare = remember(subscriptions) { subscriptions.businessSharePercent() }
    val weekEnd = today.plusDays(WEEK_DAYS)
    val upcoming = remember(subscriptions, today) { subscriptions.upcomingCharges(today, weekEnd) }
    val weekTotal = upcoming.sumOf { it.amountMinor }
    val attention = remember(subscriptions, today) { subscriptions.needsAttention(today) }
    val converting = remember(subscriptions, today) {
        subscriptions.trials.filter { it.subscription.trialEndDate != null }
    }
    val foreignBurn = remember(subscriptions, homeCurrency) {
        subscriptions.foreignBilledBurnMinor(homeCurrency)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                Modifier.padding(start = Space.s4, end = Space.s4, top = 26.dp, bottom = 20.dp),
            ) {
                // The label sits left, "normalized" right — they are a pair of annotations on
                // the figure, not a headline with a subtitle.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "MONTHLY BURN",
                        style = ToolbillText.microLabel.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "normalized",
                        style = Toolbill.money.code.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                HeroAmount(
                    amountMinor = burn,
                    currency = homeCurrency,
                    spokenLabel = "Monthly burn, ${MoneyFormat.symbol(burn, homeCurrency)}, " +
                        "normalized across $activeCount active " +
                        pluralise(activeCount, "subscription"),
                )
            }
        }

        item {
            StatRow(
                stats = listOf(
                    Stat(
                        "ANNUALIZED",
                        MoneyFormat.format(annualized, homeCurrency).withoutFraction().plain,
                    ),
                    Stat("BUSINESS", "$businessShare%"),
                    Stat("ACTIVE", activeCount.toString()),
                ),
                modifier = Modifier.padding(bottom = Space.s4),
            )
        }

        // A trial is an annotation on the figure above, not a separable object — so it is a
        // flat notice rather than a card, and it is stated as what it will add, not as spend.
        if (converting.isNotEmpty()) {
            item {
                InlineNotice(
                    eyebrow = "TRIAL",
                    message = trialMessage(converting, homeCurrency),
                    eyebrowColor = Toolbill.stateColors.trial.content,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // Only when something is actually billed abroad. No drift claim: the rate table is
        // static, so until a rate source lands there is no movement to report and stating one
        // would be inventing it.
        if (foreignBurn > 0) {
            item {
                NoticeCard(
                    eyebrow = "FX",
                    headline = "${MoneyFormat.symbolWhole(foreignBurn, homeCurrency)} of your " +
                        "burn is billed abroad",
                    body = "That is ${percentOf(foreignBurn, burn)} of " +
                        "${MoneyFormat.symbolWhole(burn, homeCurrency)} a month, converted at " +
                        "today's rate. Rate movement appears here once Toolbill has recorded " +
                        "charges at more than one rate.",
                )
            }
        }

        // Things that cost money if ignored sit above the week's schedule rather than inside it.
        if (attention.isNotEmpty()) {
            item { SectionHeader(title = "Needs attention") }
            items(attention, key = { "attn-" + it.priced.subscription.id }) { item ->
                UpcomingChargeRow(
                    row = item.priced.subscription.toRowUi(
                        today = today,
                        homeCurrency = homeCurrency,
                        homeAmountMinor = item.priced.homeAmountMinor,
                        overdueSince = item.priced.overdueSince,
                    ),
                    dayOfMonth = item.date.format(dayFormat),
                    month = item.date.format(monthFormat).uppercase(Locale.ENGLISH),
                    onClick = { onOpenSubscription(item.priced.subscription.id) },
                )
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }
        }

        item {
            SectionHeader(
                title = "Next 7 days",
                trailing = MoneyFormat.symbol(weekTotal, homeCurrency),
            )
            Text(
                text = weekCaption(upcoming.size, attention, homeCurrency),
                style = ToolbillText.sectionCaption,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2),
            )
            Spacer(Modifier.height(Space.s2))
        }

        items(upcoming, key = { it.priced.subscription.id + it.date }) { charge ->
            UpcomingChargeRow(
                row = charge.priced.subscription.toUpcomingRowUi(
                    today = today,
                    homeCurrency = homeCurrency,
                    homeAmountMinor = charge.priced.homeAmountMinor,
                    chargeDate = charge.date,
                ),
                dayOfMonth = charge.date.format(dayFormat),
                month = charge.date.format(monthFormat).uppercase(Locale.ENGLISH),
                onClick = { onOpenSubscription(charge.priced.subscription.id) },
            )
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        // The footer repeats the total so the arithmetic closes in view.
        item {
            WeekFooter(
                // Uppercased as one string: applying it to the date half alone left the
                // plural "s" lowercase, which read as "0 CHARGEs".
                left = ("${upcoming.size} ${pluralise(upcoming.size, "charge")} · " +
                    "${today.format(dayMonthFormat)}–${weekEnd.format(dayMonthFormat)}")
                    .uppercase(Locale.ENGLISH),
                right = MoneyFormat.symbol(weekTotal, homeCurrency),
            )
        }
    }
}

/**
 * "+₹696/mo from 30 Aug, when Linear's trial ends" — named while there is one trial to name,
 * counted once there are several, because three names in a flat notice stop being readable.
 */
private fun trialMessage(converting: List<PricedSubscription>, home: String): String {
    val added = converting.sumOf {
        normalizedMonthlyMinor(it.homeAmountMinor, it.subscription.cycle)
    }
    val soonest = converting.minByOrNull { it.subscription.trialEndDate!! }!!
    val date = soonest.subscription.trialEndDate!!.format(dayMonthFormat)
    val subject = if (converting.size == 1) {
        "${soonest.subscription.name}'s trial ends"
    } else {
        "the first of ${converting.size} trials ends"
    }
    return "+${MoneyFormat.symbolWhole(added, home)}/mo from $date, when $subject · " +
        "not in burn until it charges"
}

private fun weekCaption(
    chargeCount: Int,
    attention: List<com.toolbill.android.core.domain.subscription.AttentionItem>,
    home: String,
): String {
    if (chargeCount == 0) return "Nothing falls due this week."
    val base = "All $chargeCount ${pluralise(chargeCount, "charge")}, listed in full — actual " +
        "amounts, not normalized shares."
    val trial = attention.firstOrNull { it.kind == AttentionKind.TRIAL_ENDING } ?: return base
    val name = trial.priced.subscription.name
    val added = normalizedMonthlyMinor(
        trial.priced.homeAmountMinor,
        trial.priced.subscription.cycle,
    )
    return "$base $name's trial converts ${trial.date.format(dayMonthFormat)} " +
        "(+${MoneyFormat.symbolWhole(added, home)}) and sits under Needs attention instead."
}

private fun percentOf(part: Long, whole: Long): String {
    if (whole <= 0L) return "0%"
    val tenths = part * 1000 / whole
    return "${tenths / 10}.${tenths % 10}%"
}

private fun pluralise(count: Int, singular: String): String =
    if (count == 1) singular else singular + "s"

@Composable
private fun WeekFooter(left: String, right: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.s4, end = Space.s4, top = Space.s3, bottom = Space.s12),
    ) {
        Text(
            text = left,
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = right,
            style = Toolbill.money.row,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}
