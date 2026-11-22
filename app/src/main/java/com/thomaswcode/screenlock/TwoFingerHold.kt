package com.thomaswcode.screenlock

// Spots the unlock gesture among the touches the lock holds back: exactly two fingers held still on
// the screen for holdMs. Once held that long it is armed, and it unlocks when the last finger lifts,
// so the end of the gesture never reaches the app underneath. Anything else (a third finger, one
// finger lifting early, the fingers sliding) spoils it until every finger is off the screen.
// Pure logic (no MotionEvent), so the unit tests drive it directly.
class TwoFingerHold(
    private val holdMs: Long = HOLD_MS,
    private val slopPx: Float = SLOP_PX
) {

    companion object {
        const val HOLD_MS = 3000L
        // How far (in screen pixels) a finger may drift and still count as held still: about 7 mm on the S24
        const val SLOP_PX = 120f
    }

    private var startMs = -1L
    private val startX = HashMap<Int, Float>()
    private val startY = HashMap<Int, Float>()
    private var spoiled = false

    var armed = false
        private set

    // The fingers now on the screen (pointer ids and positions) at timeMs: called on every down, move and
    // finger lift. Returns true the moment a hold starts, so the caller can check back after holdMs
    fun onFingers(ids: IntArray, xs: FloatArray, ys: FloatArray, timeMs: Long): Boolean {
        if (armed || spoiled) return false
        when {
            ids.size == 2 && startMs < 0 -> {
                startMs = timeMs
                for (i in ids.indices) {
                    startX[ids[i]] = xs[i]
                    startY[ids[i]] = ys[i]
                }
                return true
            }
            ids.size == 2 -> if (moved(ids, xs, ys)) spoil()
            ids.size > 2 -> spoil()
            startMs >= 0 -> spoil() // one of the two fingers lifted before the hold was long enough
        }
        return false
    }

    // Called with the current time (by a timer, and on each event): true the moment the hold has lasted holdMs
    fun check(timeMs: Long): Boolean {
        if (armed || spoiled || startMs < 0 || timeMs - startMs < holdMs) return false
        armed = true
        return true
    }

    // Every finger has lifted (or Android cancelled the gesture): true if that should unlock
    fun onAllUp(): Boolean {
        val unlock = armed
        startMs = -1L
        startX.clear()
        startY.clear()
        spoiled = false
        armed = false
        return unlock
    }

    private fun moved(ids: IntArray, xs: FloatArray, ys: FloatArray): Boolean {
        for (i in ids.indices) {
            val x0 = startX[ids[i]] ?: return true // a different finger from the two that started it
            val y0 = startY[ids[i]] ?: return true
            val dx = xs[i] - x0
            val dy = ys[i] - y0
            if (dx * dx + dy * dy > slopPx * slopPx) return true
        }
        return false
    }

    private fun spoil() {
        spoiled = true
        startMs = -1L
    }
}
