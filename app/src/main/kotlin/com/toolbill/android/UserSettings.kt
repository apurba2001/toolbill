package com.toolbill.android

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.toolbill.android.core.design.ThemeMode
import com.toolbill.android.core.domain.reminder.DEFAULT_LEAD_DAYS
import java.util.Currency
import java.util.Locale

class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("toolbill_settings", Context.MODE_PRIVATE)

    // Onboarding asks for this on step 1; before, the answer went nowhere and every screen
    // hardcoded INR. Defaults to the device region's currency, which is what the copy claims.
    private val _homeCurrency = MutableStateFlow(
        prefs.getString("home_currency", null) ?: deviceCurrency(),
    )
    val homeCurrency: StateFlow<String> = _homeCurrency

    // Onboarding replayed on every launch because completion was never written down.
    private val _onboardingComplete = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries[prefs.getInt("theme_mode", ThemeMode.SYSTEM.ordinal)]
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(
        prefs.getBoolean("dynamic_color", true)
    )
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    // Off by default: switching an existing install to a locked one without being asked would
    // read as a fault, not a feature. Settings only offers it where the device can authenticate.
    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock", false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled

    /**
     * When a reminder last actually fired, and when the widget last actually redrew.
     *
     * Epoch millis, 0 for never. The diagnostic screen reports these rather than asserting that
     * reminders work: on a device whose battery policy is silently holding the app down, "never"
     * is the single most useful thing the screen can say. Written by whatever does the firing --
     * until that exists, they read never, which is the truth.
     */
    var lastReminderFiredAt: Long
        get() = prefs.getLong("last_reminder_fired_at", 0L)
        set(value) { prefs.edit { putLong("last_reminder_fired_at", value) } }

    var lastWidgetUpdateAt: Long
        get() = prefs.getLong("last_widget_update_at", 0L)
        set(value) { prefs.edit { putLong("last_widget_update_at", value) } }

    /**
     * How many days before a renewal the reminder lands.
     *
     * A StateFlow because the Settings row has to move the moment it is chosen, and changing it
     * has to re-plan every armed reminder -- a shorter lead does not just change future
     * notifications, it can make one that was already owed no longer owed.
     */
    private val _leadTimeDays = MutableStateFlow(
        prefs.getInt("lead_time_days", DEFAULT_LEAD_DAYS),
    )
    val leadTimeDaysFlow: StateFlow<Int> = _leadTimeDays

    /** The same value, read synchronously by the scheduler off the main thread. */
    val leadTimeDays: Int get() = _leadTimeDays.value

    fun setLeadTimeDays(days: Int) {
        prefs.edit { putInt("lead_time_days", days) }
        _leadTimeDays.value = days
    }

    /**
     * Charges already announced, as `subscriptionId@date`.
     *
     * Keyed on the charge rather than the subscription so next month's renewal still gets
     * through, and pruned once a charge is behind us so the set cannot grow for the life of
     * the install.
     */
    var deliveredReminderKeys: Set<String>
        get() = prefs.getStringSet("delivered_reminders", emptySet()) ?: emptySet()
        set(value) {
            // A defensive copy: SharedPreferences does not copy the set it is handed, and
            // mutating the instance afterwards corrupts what is on disk.
            prefs.edit { putStringSet("delivered_reminders", HashSet(value)) }
        }

    /**
     * Whether the notification rationale has been shown.
     *
     * Persisted, not remembered: Android grants exactly one permission prompt, and a sheet
     * scoped to a composition re-appeared after every cold start -- spending the one prompt on
     * a user who had already declined it.
     */
    var notificationRationaleShown: Boolean
        get() = prefs.getBoolean("notification_rationale_shown", false)
        set(value) { prefs.edit { putBoolean("notification_rationale_shown", value) } }

    /**
     * The last import, for as long as it can still be taken back.
     *
     * Thirty rows landing at once is the largest single change this app can make to someone's
     * data, and the moment to notice a column was mapped wrongly is after watching the burn
     * figure move -- which is after the import, not during it.
     */
    val lastImportIds: List<String>
        get() = if (lastImportWithinUndoWindow) {
            prefs.getStringSet("last_import_ids", emptySet())?.toList().orEmpty()
        } else {
            emptyList()
        }

    val lastImportAt: Long get() = prefs.getLong("last_import_at", 0L)

    private val lastImportWithinUndoWindow: Boolean
        get() = lastImportAt > 0L &&
            System.currentTimeMillis() - lastImportAt < IMPORT_UNDO_WINDOW_MILLIS

    /** Hours left to undo, or null once the window has closed. */
    val importUndoHoursLeft: Long?
        get() = if (!lastImportWithinUndoWindow) {
            null
        } else {
            val elapsed = System.currentTimeMillis() - lastImportAt
            ((IMPORT_UNDO_WINDOW_MILLIS - elapsed) / 3_600_000L).coerceAtLeast(0L)
        }

    fun recordImport(ids: List<String>) {
        prefs.edit {
            putStringSet("last_import_ids", HashSet(ids))
            putLong("last_import_at", System.currentTimeMillis())
        }
    }

    fun clearImportRecord() {
        prefs.edit {
            remove("last_import_ids")
            remove("last_import_at")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putInt("theme_mode", mode.ordinal) }
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean("dynamic_color", enabled) }
        _dynamicColor.value = enabled
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("app_lock", enabled) }
        _appLockEnabled.value = enabled
    }

    fun setHomeCurrency(code: String) {
        prefs.edit { putString("home_currency", code) }
        _homeCurrency.value = code
    }

    fun setOnboardingComplete() {
        prefs.edit { putBoolean("onboarding_done", true) }
        _onboardingComplete.value = true
    }

    private companion object {
        /** A day, as the Settings row has always promised. */
        const val IMPORT_UNDO_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

        /** Falls back to INR, the currency the design's worked example is drawn in. */
        fun deviceCurrency(): String = runCatching {
            Currency.getInstance(Locale.getDefault()).currencyCode
        }.getOrNull() ?: "INR"
    }
}
