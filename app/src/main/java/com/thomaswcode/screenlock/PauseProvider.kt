package com.thomaswcode.screenlock

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Phone Relay's way to let its own gestures through the lock (see LockService.pause). It holds no data: only
// call() is used, and it answers only Phone Relay (by its signing certificate) and the shell (adb):
//   adb shell content call --uri content://com.thomaswcode.screenlock.pause --method pause --extra ms:l:2000
//   adb shell content call --uri content://com.thomaswcode.screenlock.pause --method resume
// The reply's "locked" says whether the lock is on (paused or not)
class PauseProvider : ContentProvider() {

    companion object {
        const val METHOD_PAUSE = "pause"
        const val METHOD_RESUME = "resume"
        const val EXTRA_MS = "ms"
        const val EXTRA_LOCKED = "locked"

        // A pause ends by itself after this long if the caller never resumes (Relay asks for its gesture's time
        // plus a margin); the cap is just over Android's longest single gesture (a minute)
        const val DEFAULT_PAUSE_MS = 2_000L
        const val MAX_PAUSE_MS = 65_000L

        private const val RELAY_PACKAGE = "com.thomaswcode.phonerelay"
        // Phone Relay's signing certificate, SHA-256 (as its /package reports it)
        private const val RELAY_CERT_SHA256 = "e6b2e97a6cdb7834b0f303db31829aa7de4438ae0e6a01acac1f05e298e08293"
        private const val SHELL_UID = 2000

        fun clampMs(ms: Long): Long = ms.coerceIn(1L, MAX_PAUSE_MS)

        fun decodeHex(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_PAUSE && method != METHOD_RESUME) {
            throw IllegalArgumentException("Unknown method: $method")
        }
        if (!callerAllowed()) {
            throw SecurityException("Only Phone Relay and the shell may pause ScreenLock")
        }
        val ms = clampMs(extras?.getLong(EXTRA_MS, DEFAULT_PAUSE_MS) ?: DEFAULT_PAUSE_MS)
        val result = Bundle()
        // The service's state lives on the main thread: act there, and answer once the touchscreen has changed hands
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            val service = LockService.instance
            if (method == METHOD_PAUSE) service?.pause(ms) else service?.resume()
            result.putBoolean(EXTRA_LOCKED, service?.locked == true)
            done.countDown()
        }
        done.await(1, TimeUnit.SECONDS)
        return result
    }

    private fun callerAllowed(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == SHELL_UID) return true
        val pm = context?.packageManager ?: return false
        val packages = pm.getPackagesForUid(uid) ?: return false
        return packages.any {
            it == RELAY_PACKAGE &&
                pm.hasSigningCertificate(it, decodeHex(RELAY_CERT_SHA256), PackageManager.CERT_INPUT_SHA256)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
