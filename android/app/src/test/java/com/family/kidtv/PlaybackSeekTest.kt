package com.family.kidtv

import com.family.kidtv.ui.seekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSeekTest {
    @Test fun seeksTenSecondsInBothDirections() {
        assertEquals(40_000L, seekTarget(30_000L, 100_000L, 10_000L))
        assertEquals(20_000L, seekTarget(30_000L, 100_000L, -10_000L))
    }
    @Test fun clampsToBeginningAndBeforeEnd() {
        assertEquals(0L, seekTarget(5_000L, 100_000L, -10_000L))
        assertEquals(99_999L, seekTarget(95_000L, 100_000L, 10_000L))
    }
    @Test fun unknownDurationDoesNotSeek() {
        assertEquals(30_000L, seekTarget(30_000L, -1L, 10_000L))
        assertEquals(30_000L, seekTarget(30_000L, 0L, -10_000L))
    }
}
