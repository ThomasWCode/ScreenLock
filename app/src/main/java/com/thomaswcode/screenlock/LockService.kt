package com.thomaswcode.screenlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

// The lock itself. While locked, the service takes in every event from the touchscreen, so Android hands
// them to onMotionEvent instead of to the apps: a finger on the glass reaches nothing. Input that is
// injected (adb's `input`, other services' gestures such as Phone Relay's) joins the pipeline after that
// point, so it still reaches the apps. Holding two fingers still for 3 seconds, then lifting them, unlocks
class LockService : AccessibilityService() {

    companion object {
        // The connected service, or null when it is off: the widget and the shell command act through it
        @Volatile
        var instance: LockService? = null
            private set

        val isLocked: Boolean
            get() = instance?.locked == true

        // The shortest gap between two "how to unlock" hints
        private const val HINT_GAP_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hold = TwoFingerHold()
    private var lastHintMs = -HINT_GAP_MS

    var locked = false
        private set

    private val checkHold = Runnable {
        if (hold.check(SystemClock.uptimeMillis())) armedFeedback()
    }

    // Touch exploration (TalkBack and the like) stops Android sending the touchscreen to onMotionEvent, so the lock
    // would do nothing: it is refused while that is on, and lets go if it comes on while locked
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
        if (enabled && locked) {
            setLocked(false)
            Toast.makeText(this, R.string.touch_exploration_on, Toast.LENGTH_LONG).show()
        }
    }

    override fun onServiceConnected() {
        instance = this
        getSystemService(AccessibilityManager::class.java)
            ?.addTouchExplorationStateChangeListener(touchExplorationListener)
        setLocked(LockState.restoreOnConnect(this), feedback = false)
    }

    // Returns whether the lock is now on: a lock is refused while touch exploration is on
    fun setLocked(requested: Boolean, feedback: Boolean = true): Boolean {
        val on = requested && !touchExplorationOn()
        if (requested && !on) {
            Toast.makeText(this, R.string.touch_exploration_on, Toast.LENGTH_LONG).show()
        }
        locked = on
        handler.removeCallbacks(checkHold)
        hold.onAllUp()
        handler.removeCallbacks(endPause)
        paused = false
        applySources()
        LockState.save(this, on)
        LockWidget.update(this)
        if (feedback && on == requested) buzz(if (on) 40L else 120L)
        return on
    }

    fun toggle() = setLocked(!locked)

    // Phone Relay's gestures (dispatchGesture) enter Android's input chain before the lock, so they would be taken
    // in with a finger's. Relay pauses the lock around each gesture (PauseProvider): for up to ms the touchscreen
    // goes back to the apps, then resume() takes it again. A finger can get through only in that moment
    fun pause(ms: Long) {
        handler.removeCallbacks(endPause)
        handler.postDelayed(endPause, ms)
        if (!paused) {
            paused = true
            applySources()
        }
    }

    fun resume() {
        handler.removeCallbacks(endPause)
        if (paused) {
            paused = false
            hold.onAllUp()
            applySources()
        }
    }

    private var paused = false
    private val endPause = Runnable { resume() }

    // Taking in the touchscreen's events is the lock; 0 gives them back to the apps
    private fun applySources() {
        serviceInfo = serviceInfo.apply {
            motionEventSources = if (locked && !paused) InputDevice.SOURCE_TOUCHSCREEN else 0
        }
    }

    private fun touchExplorationOn(): Boolean =
        getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    override fun onMotionEvent(event: MotionEvent) {
        if (!locked) return
        // A gesture that another accessibility service dispatched outside a pause comes from no device, unlike a
        // finger: it is taken in all the same, but never counts towards the unlock hold or shows the hint
        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(checkHold)
                if (hold.onAllUp()) setLocked(false)
                return
            }
            MotionEvent.ACTION_DOWN -> hint(event.eventTime)
        }
        // The fingers still on the screen: a lifting finger is in the event until its ACTION_POINTER_UP is done
        val lifting = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        val count = event.pointerCount - if (lifting >= 0) 1 else 0
        val ids = IntArray(count)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        var n = 0
        for (i in 0 until event.pointerCount) {
            if (i == lifting) continue
            ids[n] = event.getPointerId(i)
            xs[n] = event.getX(i)
            ys[n] = event.getY(i)
            n++
        }
        if (hold.onFingers(ids, xs, ys, event.eventTime)) {
            handler.postDelayed(checkHold, TwoFingerHold.HOLD_MS)
        }
        if (hold.check(event.eventTime)) armedFeedback()
    }

    // A touch while locked: say how to unlock, at most once every HINT_GAP_MS
    private fun hint(timeMs: Long) {
        if (timeMs - lastHintMs < HINT_GAP_MS) return
        lastHintMs = timeMs
        Toast.makeText(this, R.string.hint_locked, Toast.LENGTH_SHORT).show()
    }

    // The hold has lasted long enough: two short pulses say "lift your fingers to unlock"
    private fun armedFeedback() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 80, 30), -1))
    }

    private fun buzz(ms: Long) {
        vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrate(effect: VibrationEffect) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        disconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnected()
        super.onDestroy()
    }

    // The service is ending (turned off, or rebound by `uiautomator dump`): Android stops sending it the
    // touchscreen, so the lock is off; a quick reconnect puts it back (LockState)
    private fun disconnected() {
        handler.removeCallbacks(checkHold)
        getSystemService(AccessibilityManager::class.java)
            ?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        if (instance === this) {
            LockState.markUnbound(this)
            instance = null
            LockWidget.update(this)
        }
    }
}
