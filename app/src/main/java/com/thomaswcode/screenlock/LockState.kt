package com.thomaswcode.screenlock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

// Whether the lock was on, kept across the service's reconnects: `uiautomator dump` (and anything else
// that rebinds accessibility services) ends the service for a moment, and the lock must come back by
// itself. It never comes back after a reboot or a long gap, so a lock can't outlive the session that set it
object LockState {

    // The longest gap between the service ending and connecting again that still restores the lock
    const val RESTORE_WINDOW_MS = 60_000L

    private const val PREFS = "lock_state"
    private const val KEY_LOCKED = "locked"
    private const val KEY_BOOT = "boot_count"
    private const val KEY_UNBOUND_AT = "unbound_at"

    // Pure decision, for the tests: times are SystemClock.elapsedRealtime() values, -1 for none
    fun shouldRestore(
        savedLocked: Boolean,
        savedBootCount: Int,
        bootCount: Int,
        unboundAtMs: Long,
        nowMs: Long
    ): Boolean =
        savedLocked && savedBootCount == bootCount && unboundAtMs >= 0 &&
            nowMs >= unboundAtMs && nowMs - unboundAtMs <= RESTORE_WINDOW_MS

    // Called as the service connects: whether to lock again, clearing the record of the gap either way
    fun restoreOnConnect(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val restore = shouldRestore(
            prefs.getBoolean(KEY_LOCKED, false),
            prefs.getInt(KEY_BOOT, -1),
            bootCount(context),
            prefs.getLong(KEY_UNBOUND_AT, -1L),
            SystemClock.elapsedRealtime()
        )
        prefs.edit().putLong(KEY_UNBOUND_AT, -1L).apply()
        return restore
    }

    fun save(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOCKED, locked)
            .putInt(KEY_BOOT, bootCount(context))
            .apply()
    }

    // The service is ending: note when, so a quick reconnect can restore the lock
    fun markUnbound(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNBOUND_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
