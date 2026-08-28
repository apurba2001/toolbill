package com.toolbill.android

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertChartOutlined
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.SolidColor
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillExtendedFab
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.Radius
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.feature.calendar.CalendarScreen
import com.toolbill.android.feature.diagnostics.ReminderDiagnosticScreen
import com.toolbill.android.feature.export.ExportScreen
import com.toolbill.android.feature.home.HomeScreen
import com.toolbill.android.feature.insights.InsightsScreen
import com.toolbill.android.feature.notifications.NotificationRationaleSheet
import com.toolbill.android.feature.onboarding.OnboardingScreen
import com.toolbill.android.feature.paywall.PaywallScreen
import com.toolbill.android.feature.settings.SettingsScreen
import com.toolbill.android.feature.subscriptions.AddEditSubscriptionSheet
import com.toolbill.android.feature.subscriptions.AllSubscriptionsScreen
import com.toolbill.android.feature.subscriptions.SampleData
import com.toolbill.android.feature.subscriptions.SubscriptionDetailScreen

/** The four top-level destinations, with the design's own glyphs. */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    ALL("All", Icons.AutoMirrored.Rounded.FormatListBulleted),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    INSIGHTS("Insights", Icons.Rounded.InsertChartOutlined),
}

/**
 * Screens pushed over the tabs.
 *
 * A plain stack rather than Navigation Compose: there is no saved state, deep link or process
 * death to survive yet, and swapping this for type-safe routes is a contained change once the
 * repository exists.
 */
sealed interface Screen {
    data object Onboarding : Screen
    data object Settings : Screen
    data object Export : Screen
    data object Diagnostics : Screen
    data object Paywall : Screen
    data class Detail(val id: String) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbillApp(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Onboarding) }
    var sheetOpen by remember { mutableStateOf(false) }
    var hasAskedForNotifications by remember { mutableStateOf(false) }
    var rationaleOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val top = stack.lastOrNull()

    // Predictive back: the screen follows the gesture — scaling down and sliding right in
    // proportion to progress — so a half-committed swipe can be released without a jump.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = stack.isNotEmpty()) { events ->
        try {
            events.collect { backProgress = it.progress }
            stack.removeAt(stack.lastIndex)
        } catch (cancelled: CancellationException) {
            // Released before committing — fall back to where it started.
            throw cancelled
        } finally {
            backProgress = 0f
        }
    }

    if (top != null) {
        val pop = { stack.removeAt(stack.lastIndex); Unit }
        val eased = 1f - (0.1f * backProgress)
        val pushed = Modifier
            .graphicsLayer {
                scaleX = eased
                scaleY = eased
                translationX = backProgress * size.width * 0.22f
                alpha = 1f - (backProgress * 0.25f)
            }
        // These render outside the Scaffold, so they get no insets of their own. Without this
        // the status bar clock sits on top of the first line of every one of them.
        val inset = pushed.then(modifier).fillMaxSize().safeDrawingPadding()
        when (top) {
            Screen.Onboarding -> OnboardingScreen(
                modifier = inset,
                onFinish = pop,
                onAddManually = { pop(); sheetOpen = true },
            )

            Screen.Settings -> SettingsScreen(
                modifier = inset,
                onBack = pop,
                onOpenDiagnostics = { stack.add(Screen.Diagnostics) },
                onOpenExport = { stack.add(Screen.Export) },
                onOpenPaywall = { stack.add(Screen.Paywall) },
            )

            Screen.Export -> ExportScreen(modifier = inset, onBack = pop)
            Screen.Diagnostics -> ReminderDiagnosticScreen(modifier = inset, onBack = pop)
            Screen.Paywall -> PaywallScreen(modifier = inset, onDismiss = pop)
            is Screen.Detail -> SubscriptionDetailScreen(
                modifier = inset,
                priced = SampleData.all.firstOrNull { it.subscription.id == top.id }
                    ?: SampleData.claude,
                onBack = pop,
                onEdit = { sheetOpen = true },
            )
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // The design's header is a 13sp label row at padding 6/16, not a Material app bar
            // with a 22sp title. Calendar and Insights carry their own headers, so they get none.
            if (tab == Tab.HOME || tab == Tab.ALL) {
                // Opaque, and painted above the list: the content scrolls *behind* this bar,
                // so without a fill the rows would run up under the status bar clock.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searching && tab == Tab.ALL) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = ToolbillText.appBarTitle.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search name, category or notes",
                                        style = ToolbillText.appBarTitle,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                inner()
                            },
                        )
                    } else {
                        Text(
                            text = titleFor(tab),
                            style = ToolbillText.appBarTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ToolbillIconButton(
                        icon = when {
                            tab != Tab.ALL -> Icons.Rounded.Tune
                            searching -> Icons.Rounded.Close
                            else -> Icons.Rounded.Search
                        },
                        contentDescription = when {
                            tab != Tab.ALL -> "Settings"
                            searching -> "Close search"
                            else -> "Search"
                        },
                        onClick = {
                            when {
                                tab == Tab.HOME -> stack.add(Screen.Settings)
                                searching -> { searching = false; query = "" }
                                else -> searching = true
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = {
                            if (entry != tab) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                tab = entry
                            }
                        },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.ALL) {
                ToolbillExtendedFab(text = "Add", onClick = { sheetOpen = true })
            }
        },
    ) { insets ->
        // Scaffold insets do not account for the FAB, which otherwise sits on top of the last
        // row's amount — the one figure that row exists to show.
        val withFabClearance = PaddingValues(
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + 88.dp,
        )
        // A short cross-fade so switching tabs reads as a change of view rather than a redraw.
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 24 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "tab",
        ) { current ->
        when (current) {
            Tab.HOME -> HomeScreen(
                contentPadding = withFabClearance,
                onOpenSubscription = { stack.add(Screen.Detail(it)) },
            )

            Tab.ALL -> AllSubscriptionsScreen(
                contentPadding = withFabClearance,
                onAdd = { sheetOpen = true },
                onOpenSubscription = { stack.add(Screen.Detail(it)) },
                // Every row action is reversible and says so. Nothing in this app should
                // change a figure without offering the way back.
                query = query,
                onAction = { message, _ ->
                    scope.launch {
                        snackbarHost.currentSnackbarData?.dismiss()
                        snackbarHost.showSnackbar(message = message, actionLabel = "Undo")
                    }
                },
            )

            Tab.CALENDAR -> CalendarScreen(contentPadding = insets)
            Tab.INSIGHTS -> InsightsScreen(contentPadding = insets)
        }
        }
    }

    if (sheetOpen) {
        AddEditSubscriptionSheet(
            sheetState = sheetState,
            onDismiss = { sheetOpen = false },
            onSaved = {
                sheetOpen = false
                // The permission ask comes after the first entry, never on launch — there is
                // nothing to be reminded about until something is tracked.
                if (!hasAskedForNotifications) {
                    hasAskedForNotifications = true
                    rationaleOpen = true
                }
            },
        )
    }

    if (rationaleOpen) {
        NotificationRationaleSheet(
            onAllow = { rationaleOpen = false },
            onDismiss = { rationaleOpen = false },
        )
    }
}

private fun titleFor(tab: Tab): String = when (tab) {
    Tab.HOME -> "Toolbill"
    Tab.ALL -> "All · ${SampleData.eightItemList.size}"
    Tab.CALENDAR -> "Calendar"
    Tab.INSIGHTS -> "Insights"
}
