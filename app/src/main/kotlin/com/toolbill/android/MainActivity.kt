package com.toolbill.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
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

        setContent {
            val themeMode by settings.themeMode.collectAsState()
            val dynamicColor by settings.dynamicColor.collectAsState()

            ToolbillTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                when (val state = lockState) {
                    LockState.Unlocked -> ToolbillApp(
                        userSettings = settings,
                        subscriptionRepository = app.subscriptionRepository,
                        chargeRepository = app.chargeRepository,
                        entitlements = app.entitlements,
                        pendingAction = pendingAction,
                    )

                    is LockState.Locked -> LockedScreen(
                        message = state.message,
                        onUnlock = ::promptIfLocked,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShortcutAction(intent)
    }

    override fun onStart() {
        super.onStart()
        promptIfLocked()
    }

    override fun onStop() {
        super.onStop()
        // Re-lock on every real departure from the foreground. A configuration change is not a
        // departure, and neither is the credential screen the prompt itself puts up.
        if (!isChangingConfigurations && !authInFlight && settings.appLockEnabled.value) {
            lockState = LockState.Locked(null)
        }
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
    }
}
