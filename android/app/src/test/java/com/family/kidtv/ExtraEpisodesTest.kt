package com.family.kidtv

import com.family.kidtv.data.Settings
import com.family.kidtv.data.withExtraEpisodesToday
import org.junit.Assert.*
import org.junit.Test

class ExtraEpisodesTest {
    @Test fun batchAddsOnceAndPreservesPermanentSettings() {
        val original = Settings(dailyLimit = 6, masterStars = 5, playlistJson = "keep", extraEpisodesDate = "2026-11-16", extraEpisodes = 2)
        val updated = original.withExtraEpisodesToday("2026-11-16", 5)
        assertEquals(7, updated.extraEpisodes)
        assertEquals(original.copy(extraEpisodes = 7), updated)
        assertEquals(2, original.extraEpisodes)
    }
    @Test fun oldDayAllowanceDoesNotCarryOver() {
        val updated = Settings(extraEpisodesDate = "2026-11-15", extraEpisodes = 9).withExtraEpisodesToday("2026-11-16", 3)
        assertEquals(3, updated.extraEpisodes)
        assertEquals("2026-11-16", updated.extraEpisodesDate)
    }
    @Test fun acceptsBoundaryBatches() {
        assertEquals(1, Settings().withExtraEpisodesToday("today", 1).extraEpisodes)
        assertEquals(20, Settings().withExtraEpisodesToday("today", 20).extraEpisodes)
    }
    @Test fun rejectsInvalidBatches() {
        for (amount in listOf(-1, 0, 21, Int.MAX_VALUE)) {
            try { Settings().withExtraEpisodesToday("today", amount); fail("Accepted $amount") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun handlesNegativeStoredBonusAndRejectsOverflow() {
        assertEquals(3, Settings(extraEpisodesDate = "today", extraEpisodes = -3).withExtraEpisodesToday("today", 3).extraEpisodes)
        try {
            Settings(dailyLimit = 6, extraEpisodesDate = "today", extraEpisodes = Int.MAX_VALUE).withExtraEpisodesToday("today", 1)
            fail("Accepted overflow")
        } catch (_: IllegalArgumentException) { }
    }
}
