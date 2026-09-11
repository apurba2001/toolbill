package com.toolbill.android

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Biometric or device credential — a device with no fingerprint enrolled still has a PIN. */
private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * What a single authentication attempt resolved to.
 *
 * [Failed] and [Unavailable] are kept apart deliberately. Collapsing them is what turns a lock
 * into a formality: a cancelled prompt and a device with no credential at all are the same
 * callback but the opposite decision.
 */
sealed interface AuthOutcome {
    /** The user proved who they are. The only outcome that opens the app. */
    data object Success : AuthOutcome

    /** Cancelled, wrong finger, locked out, sensor busy. Recoverable — stay locked, offer retry. */
    data class Failed(val message: String) : AuthOutcome

    /**
     * No hardware and no enrolled screen lock, so there is nothing to authenticate against.
     * A lock cannot protect a device that anyone can already open, so the app proceeds.
     */
    data class Unavailable(val message: String) : AuthOutcome
}

object BiometricAuthManager {

    /**
     * Whether this device can authenticate at all — used to gate the Settings toggle, so the
     * lock is never offered on a device where it could only ever resolve to [Unavailable].
     */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, onResult: (AuthOutcome) -> Unit) {
        when (BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> prompt(activity, onResult)

            // Nothing to authenticate against on this device, now or later.
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            ->
                onResult(AuthOutcome.Unavailable("This device has no screen lock set up."))

            // Sensor busy, or the security patch that enables it is missing. Both are transient
            // or fixable, so the lock holds and the user can retry.
            else -> onResult(AuthOutcome.Failed("Authentication is unavailable right now."))
        }
    }

    private fun prompt(activity: FragmentActivity, onResult: (AuthOutcome) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(AuthOutcome.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(AuthOutcome.Failed(errString.toString()))
            }

            // Fires per rejected read, and the prompt stays up for another try — so this one
            // reports nothing. Only onAuthenticationError ends the attempt.
            override fun onAuthenticationFailed() = Unit
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Toolbill")
            .setSubtitle("Authenticate to see your subscriptions")
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(promptInfo)
    }
}
