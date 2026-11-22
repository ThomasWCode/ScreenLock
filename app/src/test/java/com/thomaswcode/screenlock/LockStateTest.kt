package com.thomaswcode.screenlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockStateTest {

    @Test
    fun aQuickReconnectInTheSameBootRestoresTheLock() {
        assertTrue(LockState.shouldRestore(true, 7, 7, unboundAtMs = 10_000, nowMs = 13_000))
        assertTrue(LockState.shouldRestore(true, 7, 7, 10_000, 10_000 + LockState.RESTORE_WINDOW_MS))
    }

    @Test
    fun itIsNotRestoredWhenItWasOff() {
        assertFalse(LockState.shouldRestore(false, 7, 7, 10_000, 13_000))
    }

    @Test
    fun itIsNotRestoredAfterAReboot() {
        assertFalse(LockState.shouldRestore(true, 7, 8, 10_000, 13_000))
        // elapsed time restarts at boot, so a "now" before the gap began is another boot too
        assertFalse(LockState.shouldRestore(true, 7, 7, 10_000, 5_000))
    }

    @Test
    fun itIsNotRestoredAfterALongGapOrWithNoRecordedGap() {
        assertFalse(LockState.shouldRestore(true, 7, 7, 10_000, 10_001 + LockState.RESTORE_WINDOW_MS))
        assertFalse(LockState.shouldRestore(true, 7, 7, -1, 13_000))
    }
}
