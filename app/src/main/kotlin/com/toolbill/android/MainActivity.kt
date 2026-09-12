package com.toolbill.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.toolbill.android.core.design.ToolbillTheme
import com.toolbill.android.feature.lock.LockedScreen
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether the app content may be shown.
 *
 * [Locked] is the resting state whenever the lock is on — the app opens into it, and returns
 * to it every time it leaves the foreground. Only [AuthOutcome.Success] leaves it.
 */
private sealed interface LockState {
    data object Unlocked : LockState
    data class Locked(val message: String?) : LockState
}

class MainActivity : FragmentActivity() {

    private lateinit var settings: UserSettings

    private var lockState by mutableStateOf<LockState>(LockState.Unlocked)

    /**
     * True from the moment the prompt is asked for until it resolves.
     *
     * A device-credential prompt is a separate system activity, so it sends this one through
     * onStop / onStart. Without this flag that round trip would re-lock and re-prompt forever.
     */
    private var authInFlight = false

    /**
     * When the lock went on, on the monotonic clock, or 0 when it must not be waived.
     *
     * elapsedRealtime rather than wall time: a timezone change or an NTP correction must not
     * hand someone a free unlock, or take one away mid-errand.
     */
    private var lockedAt = 0L

    /**
     * True between this app launching another screen and the user coming back from it.
     *
     * Set by the [startActivity] overrides rather than by each caller, so nothing has to
     * remember. Cleared on the way back in, and by the screen going dark.
     */
    private var leavingOnPurpose = false

    /** A launcher shortcut or quick-settings tap, held until the composition can act on it. */
    private val pendingAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The Application owns the database, the repositories and the settings store, so the
        // widget and the activity read exactly one of each.
        val app = application as ToolbillApplication
        settings = app.settings
        if (settings.appLockEnabled.value) lockState = LockState.Locked(null)
        readShortcutAction(intent)

        // Keeps the content out of the recents thumbnail while the lock is on, which is what
        // makes drawing the lock as an overlay safe rather than unmounting the app.
        //
        // From API 33 there is an API for exactly this, and it leaves ordinary screenshots
        // alone -- FLAG_SECURE would also stop the user photographing their own burn figure to
        // send to an accountant, which is a real thing to want and no threat to anybody. Older
        // devices get FLAG_SECURE, because there the choice is that or nothing.
        lifecycleScope.launch {
            settings.appLockEnabled.collect { enabled ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!enabled)
                } else if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val themeMode by settings.themeMode.collectAsState()
            val dynamicColor by settings.dynamicColor.collectAsState()

            ToolbillTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                // The app stays composed and the lock is drawn over it.
                //
                // Swapping one for the other tore ToolbillApp out of the composition every time
                // the lock came on, taking its remembered navigation with it -- so returning
                // from a file picker landed on Home instead of the screen you left. What keeps
                // the content out of the recents thumbnail is FLAG_SECURE, not unmounting it.
                Box(Modifier.fillMaxSize()) {
                    ToolbillApp(
                        userSettings = settings,
                        subscriptionRepository = app.subscriptionRepository,
                        chargeRepository = app.chargeRepository,
                        entitlements = app.entitlements,
                        pendingAction = pendingAction,
                    )

                    (lockState as? LockState.Locked)?.let { locked ->
                        LockedScreen(
                            message = locked.message,
                            onUnlock = ::promptIfLocked,
                            // Opaque and swallowing every tap, so nothing underneath can be
                            // read or reached while it is up.
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShortcutAction(intent)
    }

    /**
     * Every activity this app starts passes through here.
     *
     * That is the point: a file picker, a share sheet, a settings page and a permission dialog
     * all leave through one of these two methods, so one latch catches them all without each
     * call site having to remember to set it. Elapsed time was the first attempt and it was
     * wrong -- a trip into a manufacturer's battery menu takes as long as it takes.
     */
    override fun startActivity(intent: Intent) {
        leavingOnPurpose = true
        super.startActivity(intent)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        leavingOnPurpose = true
        super.startActivity(intent, options)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        leavingOnPurpose = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStart() {
        super.onStart()
        // Back from something this app put on screen itself. The lock still went on, so the
        // recents thumbnail never showed the user's spending -- but asking them to authenticate
        // again for an errand they are in the middle of is a toll, not security.
        if (lockState is LockState.Locked && returnIsExpected) {
            lockState = LockState.Unlocked
        }
        clearReturnExpectation()
        promptIfLocked()
    }

    override fun onStop() {
        super.onStop()
        // Re-lock on every real departure from the foreground. A configuration change is not a
        // departure, and neither is the credential screen the prompt itself puts up.
        if (!isChangingConfigurations && !authInFlight && settings.appLockEnabled.value) {
            // Why we stopped, decided while we still can tell. A screen that went dark is the
            // phone being pocketed or put down, and that always costs a fresh unlock however we
            // got there -- a settings page left open on a desk is not a reason to stay unlocked.
            val screenOn = getSystemService<PowerManager>()?.isInteractive == true
            if (screenOn) {
                lockedAt = SystemClock.elapsedRealtime()
            } else {
                lockedAt = 0L
                leavingOnPurpose = false
            }
            lockState = LockState.Locked(null)
        }
    }

    /**
     * Whether coming back now should skip the prompt.
     *
     * Two ways to qualify, and the screen going dark disqualifies both. Either this app sent the
     * user to the other screen and is expecting them back, or they were gone briefly enough that
     * it was plainly the same sitting -- which covers the odd route out that does not go through
     * [startActivity], such as a notification tapped from the shade.
     */
    private val returnIsExpected: Boolean
        get() = lockedAt != 0L &&
            (leavingOnPurpose || SystemClock.elapsedRealtime() - lockedAt < RETURN_GRACE_MILLIS)

    private fun clearReturnExpectation() {
        leavingOnPurpose = false
        lockedAt = 0L
    }

    private fun promptIfLocked() {
        if (lockState !is LockState.Locked || authInFlight) return
        authInFlight = true
        BiometricAuthManager.authenticate(this) { outcome ->
            authInFlight = false
            lockState = when (outcome) {
                AuthOutcome.Success -> LockState.Unlocked
                // Nothing to authenticate against on this device, so the lock cannot hold
                // anything shut that is not already open. See AuthOutcome.Unavailable.
                is AuthOutcome.Unavailable -> LockState.Unlocked
                is AuthOutcome.Failed -> LockState.Locked(outcome.message)
            }
        }
    }

    /** Reads and clears the extra, so a rotation does not replay the shortcut. */
    private fun readShortcutAction(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_SHORTCUT_ACTION) ?: return
        intent.removeExtra(EXTRA_SHORTCUT_ACTION)
        pendingAction.value = action
    }

    companion object {
        /** Shared with res/xml/shortcuts.xml and [AddSubscriptionTileService]. */
        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
        const val ACTION_ADD_SUBSCRIPTION = "add_subscription"

        /** See [withinReturnGrace]. */
        private const val RETURN_GRACE_MILLIS = 60_000L
    }
}
