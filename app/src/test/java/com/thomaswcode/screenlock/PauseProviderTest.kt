package com.thomaswcode.screenlock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseProviderTest {

    @Test
    fun decodesACertificateDigest() {
        assertArrayEquals(byteArrayOf(0xe6.toByte(), 0x0b, 0x7f, 0x00), PauseProvider.decodeHex("e60b7f00"))
        assertEquals(32, PauseProvider.decodeHex("e6b2e97a6cdb7834b0f303db31829aa7de4438ae0e6a01acac1f05e298e08293").size)
    }

    @Test
    fun pausesAreKeptWithinBounds() {
        assertEquals(1L, PauseProvider.clampMs(0))
        assertEquals(1L, PauseProvider.clampMs(-5))
        assertEquals(550L, PauseProvider.clampMs(550))
        assertEquals(PauseProvider.MAX_PAUSE_MS, PauseProvider.clampMs(10 * 60_000L))
    }
}
