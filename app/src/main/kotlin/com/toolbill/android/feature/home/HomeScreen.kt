package com.toolbill.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbill.android.core.design.EyebrowStyle
import com.toolbill.android.core.design.Space
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.core.design.component.HeroAmount
import com.toolbill.android.core.design.component.InlineNotice
import com.toolbill.android.core.design.component.NoticeCard
import com.toolbill.android.core.design.component.SectionHeader
import com.toolbill.android.core.design.component.Stat
import com.toolbill.android.core.design.component.StatRow
import com.toolbill.android.core.design.component.UpcomingChargeRow
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.core.domain.money.annualizedMinor
import com.toolbill.android.core.domain.subscription.BillingCycle
import com.toolbill.android.feature.subscriptions.SampleData
import com.toolbill.android.feature.subscriptions.toRowUi
import com.toolbill.android.feature.subscriptions.toUpcomingRowUi
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenSubscription: (String) -> Unit = {},
) {
    val home = SampleData.HOME_CURRENCY
    val burn = SampleData.MONTHLY_BURN_MINOR
    val annualized = annualizedMinor(burn, BillingCycle.MONTHLY)

    val upcoming = SampleData.nextSevenDays
    val weekTotal = upcoming.sumOf { (priced, _) -> priced.homeAmountMinor }

    val needsAttention = listOfNotNull(
        SampleData.heroku.overdueSince?.let { SampleData.heroku to it },
        SampleData.linear.subscription.trialEndDate?.let { SampleData.linear to it },
    )

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
                    currency = home,
                    spokenLabel = "Monthly burn, ${MoneyFormat.symbol(burn, home)}, normalized " +
                        "across ${SampleData.ACTIVE_COUNT} active subscriptions",
                )
            }
        }

        item {
            StatRow(
                stats = listOf(
                    Stat("ANNUALIZED", MoneyFormat.format(annualized, home).withoutFraction().plain),
                    Stat("BUSINESS", "${SampleData.BUSINESS_SHARE_PERCENT}%"),
                    Stat("ACTIVE", SampleData.ACTIVE_COUNT.toString()),
                ),
                modifier = Modifier.padding(bottom = Space.s4),
            )
        }

        // A trial is an annotation on the figure above, not a separable object — so it is a
        // flat notice rather than a card, and it is stated as what it will add, not as spend.
        item {
            InlineNotice(
                eyebrow = "TRIAL",
                message = "+₹696/mo from 30 Aug, when Linear's trial ends · " +
                    "not in burn until it charges",
                eyebrowColor = Toolbill.stateColors.trial.content,
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            NoticeCard(
                eyebrow = "FX",
                headline = "This month is ₹1,254 higher on rates alone",
                body = "USD/INR moved ${SampleData.FX_MOVE_PERCENT} since July · " +
                    "${MoneyFormat.symbolWhole(SampleData.USD_BILLED_BURN_MINOR, home)} of your " +
                    "burn is USD-billed. Shown because both thresholds were crossed: 3% and ₹500.",
            )
        }

        // Overdue charges and a trial about to convert are the two things that cost money if
        // ignored, so they sit above the week's schedule rather than inside it.
        if (needsAttention.isNotEmpty()) {
            item { SectionHeader(title = "Needs attention") }
            items(needsAttention, key = { (priced, _) -> "attn-" + priced.subscription.id }) {
                (priced, date) ->
                UpcomingChargeRow(
                    row = priced.subscription.toRowUi(
                        today = SampleData.today,
                        homeCurrency = home,
                        homeAmountMinor = priced.homeAmountMinor,
                        overdueSince = priced.overdueSince,
                    ),
                    dayOfMonth = date.format(dayFormat),
                    month = date.format(monthFormat).uppercase(Locale.ENGLISH),
                    onClick = { onOpenSubscription(priced.subscription.id) },
                )
                HorizontalDivider(color = Toolbill.stateColors.dividerDense)
            }
        }

        item {
            SectionHeader(
                title = "Next 7 days",
                trailing = MoneyFormat.symbol(weekTotal, home),
            )
            Text(
                text = "All eight charges, listed in full — actual amounts, not normalized " +
                    "shares. Linear's trial converts 30 Aug (+₹696) and sits under Needs " +
                    "attention instead.",
                style = ToolbillText.sectionCaption,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = Space.s4, end = Space.s4, bottom = Space.s2),
            )
            Spacer(Modifier.height(Space.s2))
        }

        items(upcoming, key = { (priced, date) -> priced.subscription.id + date }) { (priced, date) ->
            UpcomingChargeRow(
                row = priced.subscription.toUpcomingRowUi(
                    today = SampleData.today,
                    homeCurrency = home,
                    homeAmountMinor = priced.homeAmountMinor,
                    chargeDate = date,
                ),
                dayOfMonth = date.format(dayFormat),
                month = date.format(monthFormat).uppercase(Locale.ENGLISH),
                onClick = { onOpenSubscription(priced.subscription.id) },
            )
            HorizontalDivider(color = Toolbill.stateColors.dividerDense)
        }

        // The footer repeats the total so the arithmetic closes in view.
        item {
            WeekFooter(
                left = "${upcoming.size} CHARGES · 25–31 AUG",
                right = MoneyFormat.symbol(weekTotal, home),
            )
        }
    }
}

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
