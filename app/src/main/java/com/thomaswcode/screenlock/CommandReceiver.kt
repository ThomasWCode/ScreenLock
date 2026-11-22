package com.thomaswcode.screenlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// The shell's way in (adb, or Phone Relay's `as_shell` intents): lock, unlock or ask, without touching
// the screen. Only senders holding DUMP can reach it (see the manifest): the shell has it, apps don't.
//   adb shell am broadcast -a com.thomaswcode.screenlock.LOCK -n com.thomaswcode.screenlock/.CommandReceiver
// The reply's data is the state afterwards: "locked", "unlocked" or "service off", or
// "refused: touch exploration is on" for a LOCK that explore by touch stops
class CommandReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOCK = "com.thomaswcode.screenlock.LOCK"
        const val ACTION_UNLOCK = "com.thomaswcode.screenlock.UNLOCK"
        const val ACTION_STATUS = "com.thomaswcode.screenlock.STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val service = LockService.instance
        var refused = false
        if (service != null) {
            when (intent.action) {
                ACTION_LOCK -> refused = !service.setLocked(true)
                ACTION_UNLOCK -> service.setLocked(false)
            }
        }
        resultData = when {
            service == null -> "service off"
            refused -> "refused: touch exploration is on"
            service.locked -> "locked"
            else -> "unlocked"
        }
    }
}
