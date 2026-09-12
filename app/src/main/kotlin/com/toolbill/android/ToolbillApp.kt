package com.toolbill.android

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.toolbill.android.core.design.ToolbillIcons
import com.toolbill.android.core.design.component.ToolbillIconButton
import com.toolbill.android.core.design.component.ToolbillExtendedFab
import com.toolbill.android.core.design.component.ToolbillTextButton
import com.toolbill.android.core.design.Radius
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolbill.android.core.billing.EntitlementSource
import com.toolbill.android.core.data.repository.ChargeRepository
import com.toolbill.android.core.data.repository.SubscriptionRepository
import com.toolbill.android.core.domain.subscription.Subscription
import com.toolbill.android.core.domain.subscription.SubscriptionStatus
import com.toolbill.android.core.reminder.ReminderNotifier
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import com.toolbill.android.core.design.Toolbill
import com.toolbill.android.core.design.ToolbillText
import com.toolbill.android.feature.backup.BackupDialogs
import com.toolbill.android.feature.backup.BackupViewModel
import com.toolbill.android.feature.calendar.CalendarScreen
import com.toolbill.android.feature.diagnostics.ReminderDiagnosticScreen
import com.toolbill.android.feature.export.ExportScreen
import com.toolbill.android.feature.import.ImportScreen
import com.toolbill.android.feature.import.ImportViewModel
import com.toolbill.android.feature.import.ImportMapScreen
import com.toolbill.android.feature.import.ImportReviewScreen
import com.toolbill.android.feature.import.ImportResultScreen
import com.toolbill.android.feature.home.HomeScreen
import com.toolbill.android.feature.insights.InsightsScreen
import com.toolbill.android.feature.notifications.NotificationRationaleSheet
import com.toolbill.android.feature.onboarding.OnboardingScreen
import com.toolbill.android.feature.paywall.PaywallScreen
import com.toolbill.android.feature.settings.SettingsScreen
import com.toolbill.android.feature.subscriptions.AddEditSubscriptionSheet
import com.toolbill.android.feature.subscriptions.AllSubscriptionsScreen
import com.toolbill.android.feature.subscriptions.SubscriptionDetailScreen
import com.toolbill.android.feature.subscriptions.SubscriptionsViewModel

/** The four top-level destinations, with the design's own glyphs. */
enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("Home", ToolbillIcons.Home),
    ALL("All", ToolbillIcons.List),
    CALENDAR("Calendar", ToolbillIcons.Calendar),
    INSIGHTS("Insights", ToolbillIcons.Insights),
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
    data object Import : Screen
    data object ImportMap : Screen
    data object ImportReview : Screen
    data object ImportResult : Screen
    data object Diagnostics : Screen
    data object Paywall : Screen
    data class Detail(val id: String) : Screen

    /**
     * A string the whole stack can be written to a Bundle as.
     *
     * Detail carries the subscription it was opened for, so it has to survive process death by
     * value -- the system can kill the process while the user is in another app, and coming back
     * to the list instead of the row they were reading is the kind of loss nobody reports as a
     * bug and everybody notices.
     */
    fun token(): String = when (this) {
        is Detail -> "detail:$id"
        else -> this::class.simpleName.orEmpty()
    }

    companion object {
        fun fromToken(token: String): Screen? = when {
            token.startsWith("detail:") -> Detail(token.removePrefix("detail:"))
            token == "Onboarding" -> Onboarding
            token == "Settings" -> Settings
            token == "Export" -> Export
            token == "Import" -> Import
            token == "ImportMap" -> ImportMap
            token == "ImportReview" -> ImportReview
            token == "ImportResult" -> ImportResult
            token == "Diagnostics" -> Diagnostics
            token == "Paywall" -> Paywall
            else -> null
        }
    }
}

/**
 * The back stack, restored after process death.
 *
 * The import wizard is deliberately dropped: its four steps hang off a parsed file held in a
 * view model, and restoring the user to step three of an import whose rows no longer exist would
 * be worse than putting them back on the screen they started from.
 */
private val ScreenStackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack ->
        stack.filterNot {
            it is Screen.Import || it is Screen.ImportMap ||
                it is Screen.ImportReview || it is Screen.ImportResult
        }.map { it.token() }
    },
    restore = { tokens ->
        mutableStateListOf<Screen>().apply {
            addAll(tokens.mapNotNull(Screen::fromToken))
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbillApp(
    modifier: Modifier = Modifier,
    userSettings: UserSettings,
    subscriptionRepository: SubscriptionRepository,
    chargeRepository: ChargeRepository,
    entitlements: EntitlementSource,
    pendingAction: MutableStateFlow<String?> = remember { MutableStateFlow(null) },
) {
    val viewModel: SubscriptionsViewModel =
        viewModel(
            factory = SubscriptionsViewModel.factory(
                subscriptionRepository,
                chargeRepository,
                userSettings,
            ),
        )
    // One source of state for every tab, so the Home hero, the All total, the calendar and
    // the Insights breakdown cannot quote different numbers for the same portfolio.
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val charges by viewModel.charges.collectAsStateWithLifecycle()
    val homeCurrency by viewModel.homeCurrency.collectAsStateWithLifecycle()
    // Read once per composition rather than per call site, so two sections of one screen
    // cannot land on opposite sides of midnight.
    val today = remember { LocalDate.now() }

    // Survives process death: coming back on a different tab is a small, constant annoyance.
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val onboarded = userSettings.onboardingComplete.collectAsStateWithLifecycle().value
    val stack = rememberSaveable(saver = ScreenStackSaver) {
        // Onboarding replayed on every launch until its completion was written down.
        mutableStateListOf<Screen>().apply { if (!onboarded) add(Screen.Onboarding) }
    }
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var rationaleOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHost = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    val context = LocalContext.current
    val entitlement by entitlements.entitlement.collectAsStateWithLifecycle()
    // Android grants one prompt per install. It is spent here, after the rationale sheet, and
    // never on launch. A refusal is final and the app carries on without reminders.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Granting does not itself change the portfolio, so the data-driven re-plan in
        // ToolbillApplication will not run. Anything already owed is delivered here instead.
        if (granted) refreshReminders(context)
    }

    val importViewModel: ImportViewModel =
        viewModel(
            factory = ImportViewModel.factory(
                subscriptionRepository,
                chargeRepository,
                userSettings,
            ),
        )
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    val backupViewModel: BackupViewModel =
        viewModel(
            factory = BackupViewModel.factory(
                subscriptionRepository.subscriptionDao,
                chargeRepository.chargeDao,
                userSettings,
            ),
        )
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val backupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri -> if (uri != null) backupViewModel.backUpTo(context, uri) }
    val restoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) backupViewModel.previewRestore(context, uri) }

    val top = stack.lastOrNull()

    // The commit happens in the view model, so the step that follows it is driven by the result
    // arriving rather than by the button that started it -- a failed write leaves the user on
    // the review screen instead of on a summary of an import that did not happen.
    LaunchedEffect(importState.result) {
        if (importState.result != null && stack.lastOrNull() == Screen.ImportReview) {
            stack.add(Screen.ImportResult)
        }
    }

    // Every row action reports itself here once it has been written, carrying its own inverse.
    // collectLatest so a second action supersedes the first rather than queueing behind it --
    // the superseded Undo is gone, but it was already stale the moment the next write landed.
    LaunchedEffect(viewModel) {
        viewModel.undoables.collectLatest { undoable ->
            val result = snackbarHost.showSnackbar(
                message = undoable.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undoable.undo()
        }
    }

    // A launcher shortcut or the quick-settings tile. It asks for one thing, so it takes
    // precedence over whatever screen the stack was left on.
    val action by pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(action) {
        if (action == MainActivity.ACTION_ADD_SUBSCRIPTION) {
            stack.clear()
            editing = null
            sheetOpen = true
            pendingAction.value = null
        }
    }

    val backupOverlay: @Composable () -> Unit = {
        BackupDialogs(
            state = backupState,
            onConfirmRestore = backupViewModel::applyPending,
            onDismiss = backupViewModel::dismiss,
        )
    }

    // Rendered on both paths below: a pushed screen returns early, and Detail's Edit action
    // has to be able to open this sheet over it.
    val overlays: @Composable () -> Unit = {
        if (sheetOpen) {
            AddEditSubscriptionSheet(
                sheetState = sheetState,
                editing = editing,
                homeCurrency = homeCurrency,
                today = today,
                onDismiss = { sheetOpen = false; editing = null },
                onSave = viewModel::save,
                onDelete = {
                    editing?.let { subscription ->
                        viewModel.deleteWithUndo(subscription)
                        // The detail screen for a deleted row has nothing left to show.
                        if ((stack.lastOrNull() as? Screen.Detail)?.id == subscription.id) {
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    sheetOpen = false
                    editing = null
                },
                onSaved = {
                    sheetOpen = false
                    editing = null
                    // The permission ask comes after the first entry, never on launch — there
                    // is nothing to be reminded about until something is tracked. Skipped
                    // outright where notifications already work, which is every device below
                    // API 33 and anyone who has already said yes.
                    if (!userSettings.notificationRationaleShown &&
                        !ReminderNotifier.canNotify(context)
                    ) {
                        userSettings.notificationRationaleShown = true
                        rationaleOpen = true
                    }
                },
            )
        }

        if (rationaleOpen) {
            NotificationRationaleSheet(
                leadDays = userSettings.leadTimeDays,
                homeCurrency = homeCurrency,
                onAllow = {
                    rationaleOpen = false
                    // Below API 33 the permission does not exist and notifications are already
                    // on, so there is nothing to ask for — refreshing is the whole action.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        refreshReminders(context)
                    }
                },
                onDismiss = { rationaleOpen = false },
            )
        }
    }

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
                initialCurrency = homeCurrency,
                onHomeCurrency = userSettings::setHomeCurrency,
                onFinish = { userSettings.setOnboardingComplete(); pop() },
                onAddManually = {
                    userSettings.setOnboardingComplete()
                    pop()
                    sheetOpen = true
                },
            )

            Screen.Settings -> SettingsScreen(
                modifier = inset,
                onBack = pop,
                onOpenDiagnostics = { stack.add(Screen.Diagnostics) },
                onOpenExport = { stack.add(Screen.Export) },
                onOpenImport = { stack.add(Screen.Import) },
                onOpenPaywall = { stack.add(Screen.Paywall) },
                onUndoImport = viewModel::undoLastImport,
                onRefreshRates = {
                    // The same call the daily worker makes; the installed table is a StateFlow,
                    // so a success re-prices every figure on every screen without this screen
                    // having to tell anything.
                    (context.applicationContext as? ToolbillApplication)
                        ?.fxRepository?.refresh() ?: false
                },
                onBackUp = { backupFile.launch(backupViewModel.suggestedFileName()) },
                onRestore = { restoreFile.launch(arrayOf("application/gzip", "application/json", "*/*")) },
                entitlement = entitlement,
                userSettings = userSettings
            )

            Screen.Export -> ExportScreen(
                modifier = inset,
                onBack = pop,
                subscriptions = subscriptions.map { it.subscription },
                charges = charges,
                homeCurrency = homeCurrency,
                today = today,
            )
            Screen.Import -> ImportScreen(
                modifier = inset,
                state = importState,
                onBack = pop,
                onPick = { importViewModel.load(context, it) },
                onNext = { stack.add(Screen.ImportMap) },
            )

            Screen.ImportMap -> ImportMapScreen(
                modifier = inset,
                state = importState,
                onBack = pop,
                onAssign = importViewModel::assign,
                onNext = { stack.add(Screen.ImportReview) },
            )

            Screen.ImportReview -> ImportReviewScreen(
                modifier = inset,
                state = importState,
                homeCurrency = homeCurrency,
                onBack = pop,
                onToggle = importViewModel::toggle,
                onNext = { importViewModel.commit() },
            )

            Screen.ImportResult -> ImportResultScreen(
                modifier = inset,
                outcome = importState.result,
                homeCurrency = homeCurrency,
                onUndo = {
                    viewModel.undoLastImport()
                    closeImport(stack)
                    importViewModel.reset()
                },
                onClose = { closeImport(stack); importViewModel.reset() },
            )
            Screen.Diagnostics ->
                ReminderDiagnosticScreen(
                    modifier = inset,
                    userSettings = userSettings,
                    onBack = pop,
                )
            Screen.Paywall -> PaywallScreen(
                modifier = inset,
                entitlement = entitlement,
                trackedCount = subscriptions.size,
                onDismiss = pop,
            )
            is Screen.Detail -> {
                val priced = subscriptions.firstOrNull { it.subscription.id == top.id }
                if (priced == null) {
                    // Deleted while its screen was open, or the list has not loaded yet.
                    LaunchedEffect(top.id, subscriptions.isEmpty()) {
                        if (subscriptions.isNotEmpty()) pop()
                    }
                } else {
                    val subscriptionCharges by viewModel
                        .chargesFor(top.id)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    SubscriptionDetailScreen(
                        modifier = inset,
                        priced = priced,
                        charges = subscriptionCharges,
                        homeCurrency = homeCurrency,
                        today = today,
                        onBack = pop,
                        onEdit = { editing = priced.subscription; sheetOpen = true },
                        onDelete = { viewModel.deleteWithUndo(priced.subscription); pop() },
                        // Keeps the row and its charge history for tax purposes; only stops
                        // it counting toward the burn.
                        onMarkCancelled = {
                            viewModel.save(
                                priced.subscription.copy(
                                    status = SubscriptionStatus.CANCELLED,
                                    cancelledDate = today,
                                ),
                            )
                        },
                    )
                }
            }
        }
        overlays()
        backupOverlay()
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
                        // The design's search bar: one rounded field spanning the row, with the
                        // way out inside it. A bare text field sharing the bar with the title
                        // read as the title having been replaced by an editable label.
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(Radius.full)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolbillIconButton(
                                icon = ToolbillIcons.Back,
                                contentDescription = "Close search",
                                onClick = { searching = false; query = "" },
                            )
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = ToolbillText.appBarTitle.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focus),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            // The real count, so the field says how much it is
                                            // searching rather than listing the fields it looks in.
                                            text = "Search ${subscriptions.size} subscription" +
                                                if (subscriptions.size == 1) "" else "s",
                                            style = ToolbillText.appBarTitle,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                    inner()
                                },
                            )
                            if (query.isNotEmpty()) {
                                ToolbillIconButton(
                                    icon = ToolbillIcons.Close,
                                    contentDescription = "Clear search",
                                    onClick = { query = "" },
                                )
                            }
                        }
                    } else {
                        Text(
                            text = titleFor(tab, subscriptions.size),
                            style = ToolbillText.appBarTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        ToolbillIconButton(
                            icon = if (tab == Tab.ALL) {
                                ToolbillIcons.Search
                            } else {
                                ToolbillIcons.Settings
                            },
                            contentDescription = if (tab == Tab.ALL) "Search" else "Settings",
                            onClick = {
                                if (tab == Tab.ALL) {
                                    searching = true
                                } else {
                                    stack.add(Screen.Settings)
                                }
                            },
                        )
                    }
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
                        icon = {
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            // Both list tabs: without it, All has no way to add a second subscription once the
            // empty state — whose primary action is the only other route — has gone.
            if (tab == Tab.HOME || tab == Tab.ALL) {
                ToolbillExtendedFab(
                    text = "Add",
                    onClick = { editing = null; sheetOpen = true },
                )
            }
        },
    ) { insets ->
        // Scaffold insets do not account for the FAB, which otherwise sits on top of the last
        // row's amount — the one figure that row exists to show. Only the tabs that have one.
        val hasFab = tab == Tab.HOME || tab == Tab.ALL
        val withFabClearance = PaddingValues(
            top = insets.calculateTopPadding(),
            bottom = insets.calculateBottomPadding() + if (hasFab) 88.dp else 0.dp,
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
                subscriptions = subscriptions,
                homeCurrency = homeCurrency,
                today = today,
                onOpenSubscription = { stack.add(Screen.Detail(it)) },
                onAdd = { editing = null; sheetOpen = true },
                onImport = { stack.add(Screen.Import) },
            )

            Tab.ALL -> AllSubscriptionsScreen(
                contentPadding = withFabClearance,
                subscriptions = subscriptions,
                homeCurrency = homeCurrency,
                today = today,
                onAdd = { editing = null; sheetOpen = true },
                onImport = { stack.add(Screen.Import) },
                onOpenSubscription = { stack.add(Screen.Detail(it)) },
                query = query,
                // Every row action is reversible and says so. Nothing in this app should
                // change a figure without offering the way back.
                onMarkPaid = { viewModel.markPaid(it.subscription, today) },
                onSkipCharge = { viewModel.skipCharge(it.subscription, today) },
                onTogglePause = { viewModel.togglePause(it.subscription) },
                onDuplicate = { viewModel.duplicate(it.subscription) },
                // Edit opens the sheet the action is named after. It used to push the detail
                // screen, which is a different thing the row already does on a plain tap.
                onEdit = { editing = it.subscription; sheetOpen = true },
                onDelete = { viewModel.deleteWithUndo(it.subscription) },
                onClearSearch = { query = "" },
            )

            Tab.CALENDAR -> CalendarScreen(
                contentPadding = insets,
                subscriptions = subscriptions,
                homeCurrency = homeCurrency,
                today = today,
            )

            Tab.INSIGHTS -> InsightsScreen(
                contentPadding = insets,
                subscriptions = subscriptions,
                charges = charges,
                homeCurrency = homeCurrency,
                today = today,
            )
        }
        }
    }

    overlays()
    backupOverlay()
}

private fun titleFor(tab: Tab, storedCount: Int): String = when (tab) {
    Tab.HOME -> "Toolbill"
    Tab.ALL -> "All · $storedCount"
    Tab.CALENDAR -> "Calendar"
    Tab.INSIGHTS -> "Insights"
}

/**
 * Re-plans reminders after something that changed whether they can be delivered, rather than
 * what is owed. The portfolio flow in [ToolbillApplication] covers every other case.
 */
private fun refreshReminders(context: android.content.Context) {
    val app = context.applicationContext as? ToolbillApplication ?: return
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { app.reminderManager.refresh() }
    }
}

/** Drops the whole four-step wizard, wherever in it the user happened to be. */
private fun closeImport(stack: MutableList<Screen>) {
    stack.removeAll {
        it is Screen.Import || it is Screen.ImportMap ||
            it is Screen.ImportReview || it is Screen.ImportResult
    }
}
