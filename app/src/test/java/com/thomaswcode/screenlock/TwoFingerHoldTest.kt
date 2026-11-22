package com.thomaswcode.screenlock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFingerHoldTest {

    private val hold = TwoFingerHold(holdMs = 3000, slopPx = 100f)

    private fun one(t: Long, x: Float = 100f) =
        hold.onFingers(intArrayOf(0), floatArrayOf(x), floatArrayOf(500f), t)

    private fun two(t: Long, dx: Float = 0f) =
        hold.onFingers(intArrayOf(0, 1), floatArrayOf(100f + dx, 400f), floatArrayOf(500f, 500f), t)

    @Test
    fun holdingTwoFingersStillForTheHoldTimeUnlocksOnLift() {
        one(0)
        assertTrue(two(50)) // the hold starts with the second finger
        assertFalse(hold.check(3049))
        assertTrue(hold.check(3050))
        assertFalse(hold.check(3100)) // reports arming once
        one(3200) // the first finger lifts after arming: still armed
        assertTrue(hold.onAllUp())
    }

    @Test
    fun liftingBeforeTheHoldTimeDoesNotUnlock() {
        one(0)
        two(0)
        assertFalse(hold.check(2999))
        assertFalse(hold.onAllUp())
    }

    @Test
    fun oneFingerLiftingEarlySpoilsTheHoldUntilAllAreUp() {
        two(0)
        one(1000) // one finger lifts early
        two(1100) // and goes down again: does not start a new hold
        assertFalse(hold.check(5000))
        assertFalse(hold.onAllUp())
        // a fresh gesture works again
        assertTrue(two(6000))
        assertTrue(hold.check(9000))
        assertTrue(hold.onAllUp())
    }

    @Test
    fun aThirdFingerSpoilsTheHold() {
        two(0)
        hold.onFingers(intArrayOf(0, 1, 2), floatArrayOf(100f, 400f, 700f), floatArrayOf(500f, 500f, 500f), 500)
        assertFalse(hold.check(4000))
        assertFalse(hold.onAllUp())
    }

    @Test
    fun smallDriftIsAllowedButSlidingSpoilsIt() {
        two(0)
        two(1000, dx = 60f) // within 100 px
        assertTrue(hold.check(3000))
        hold.onAllUp()

        two(10_000)
        two(11_000, dx = 150f) // beyond 100 px
        assertFalse(hold.check(14_000))
        assertFalse(hold.onAllUp())
    }

    @Test
    fun aDifferentPairOfFingersSpoilsIt() {
        two(0)
        hold.onFingers(intArrayOf(0, 5), floatArrayOf(100f, 400f), floatArrayOf(500f, 500f), 100)
        assertFalse(hold.check(4000))
    }

    @Test
    fun oneFingerAloneNeverUnlocks() {
        assertFalse(one(0))
        assertFalse(hold.check(10_000))
        assertFalse(hold.onAllUp())
    }
}
