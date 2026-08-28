package com.toolbill.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.toolbill.android.core.domain.money.MoneyFormat
import com.toolbill.android.feature.subscriptions.SampleData
import java.time.format.DateTimeFormatter
import java.util.Locale

// The three sizes the design draws. Glance picks the largest that fits.
private val SmallSize = DpSize(140.dp, 60.dp)
private val WideSize = DpSize(280.dp, 60.dp)
private val TallSize = DpSize(280.dp, 140.dp)

private val updatedFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * The home-screen widget.
 *
 * Glance composables only — Column, Row, Text, Spacer and a GlanceTheme background — so dynamic
 * colour comes free on Android 12+. It reads the same local store the app does and never blocks
 * on a refresh: a widget that shows a spinner where a number should be is worse than a stale
 * number, and the diagnostic screen is where staleness gets explained.
 */
class ToolbillWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SmallSize, WideSize, TallSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetBody()
            }
        }
    }
}

class ToolbillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ToolbillWidget()
}

private object WidgetType {
    val label
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )

    val figure
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
        )

    val symbol
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )

    val renewalName
        @Composable get() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp)

    val renewalAccent
        @Composable get() = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
}

@Composable
private fun WidgetBody() {
    val size = LocalSize.current
    val home = SampleData.HOME_CURRENCY
    val burn = MoneyFormat.format(SampleData.MONTHLY_BURN_MINOR, home).withoutFraction()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(18.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when {
            size.height >= TallSize.height -> TallLayout(burn.integer, burn.symbol, home)
            size.width >= WideSize.width -> WideLayout(burn.integer, burn.symbol)
            else -> BurnBlock(label = "MONTHLY", figure = burn.integer, symbol = burn.symbol)
        }
    }
}

/** The 2×1: the burn figure alone. Nothing else fits without shrinking the number. */
@Composable
private fun BurnBlock(label: String, figure: String, symbol: String) {
    Column {
        Text(text = label, style = WidgetType.label)
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(text = symbol, style = WidgetType.symbol)
            Spacer(GlanceModifier.width(4.dp))
            Text(text = figure, style = WidgetType.figure)
        }
    }
}

/** The 4×1: burn on the left, the single next renewal on the right. */
@Composable
private fun WideLayout(figure: String, symbol: String) {
    val next = SampleData.nextSevenDays.first()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            BurnBlock(label = "MONTHLY BURN", figure = figure, symbol = symbol)
        }
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(text = "NEXT", style = WidgetType.label)
            Spacer(GlanceModifier.height(5.dp))
            Text(text = next.first.subscription.name, style = WidgetType.renewalName)
            Text(
                text = "tomorrow · " +
                    MoneyFormat.symbolWhole(next.first.homeAmountMinor, SampleData.HOME_CURRENCY),
                style = WidgetType.renewalAccent,
            )
        }
    }
}

/** The 4×2: burn, three renewals, and the week's total. */
@Composable
private fun TallLayout(figure: String, symbol: String, home: String) {
    val upcoming = SampleData.nextSevenDays.take(3)
    val weekTotal = SampleData.nextSevenDays.sumOf { it.first.homeAmountMinor }

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = "MONTHLY BURN",
            style = WidgetType.label,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = "UPD " + SampleData.today.atStartOfDay().format(updatedFormat),
            style = WidgetType.label,
        )
    }
    Spacer(GlanceModifier.height(6.dp))
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(text = symbol, style = WidgetType.symbol)
        Spacer(GlanceModifier.width(4.dp))
        Text(text = figure, style = WidgetType.figure)
    }
    Spacer(GlanceModifier.height(8.dp))
    upcoming.forEach { (priced, _) ->
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(
                text = priced.subscription.name,
                style = WidgetType.renewalName,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = MoneyFormat.format(priced.homeAmountMinor, home).withoutFraction().plain,
                style = WidgetType.renewalAccent,
            )
        }
    }
    Spacer(GlanceModifier.height(6.dp))
    Text(
        text = "NEXT 7 DAYS · " + MoneyFormat.symbolWhole(weekTotal, home),
        style = WidgetType.label,
    )
}
