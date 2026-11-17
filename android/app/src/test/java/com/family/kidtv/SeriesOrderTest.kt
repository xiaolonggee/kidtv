package com.family.kidtv

import com.family.kidtv.data.Episode
import com.family.kidtv.data.Series
import com.family.kidtv.data.Settings
import com.family.kidtv.data.seriesByPopularity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesOrderTest {
    private fun show(name: String, lang: String = "法语", cn: String = "") = Series(
        lang, name, name, cn, null, false, (1..2).map { n ->
            Episode("$lang/$name/$n", lang, name, n, "$n", "", "$n.mp4", "", 0, 0)
        })

    @Test fun familyPreferencesApplyWithoutHistoryAndAliasesWork() {
        val items = listOf(show("Other"), show("TROTRO"), show("古力小超人"), show("小鼠波波"))
        assertEquals(listOf("小鼠波波", "古力小超人", "TROTRO", "Other"),
            seriesByPopularity(items, "法语", emptyMap()).map { it.folder })
        assertEquals("English title", seriesByPopularity(
            listOf(show("Other"), show("English title", cn = "小鼠啵啵")), "法语", emptyMap()).first().folder)
    }

    @Test fun allEpisodeCountsRankAheadOfInitialPreferences() {
        val items = listOf(show("小鼠波波"), show("Other"), show("古力小超人"))
        val counts = mapOf("法语/Other/1" to 3, "法语/Other/2" to 4, "法语/小鼠波波/1" to 6)
        assertEquals(listOf("Other", "小鼠波波", "古力小超人"),
            seriesByPopularity(items, "法语", counts).map { it.folder })
    }

    @Test fun languagesStaySeparateAndOrdinaryTiesKeepScanOrder() {
        val items = listOf(show("Zebra"), show("Alpha"), show("小鼠波波", "英语"), show("Trotro"))
        assertEquals(listOf("Trotro", "Zebra", "Alpha"),
            seriesByPopularity(items, "法语", mapOf("英语/小鼠波波/1" to 100)).map { it.folder })
        assertEquals(listOf("小鼠波波"), seriesByPopularity(items, "英语", emptyMap()).map { it.folder })
    }

    @Test fun totalsDoNotOverflowAndBadNegativeCountsDoNotWin() {
        val items = listOf(show("A"), show("B"))
        val counts = mapOf("法语/A/1" to Int.MAX_VALUE, "法语/A/2" to Int.MAX_VALUE,
            "法语/B/1" to Int.MAX_VALUE, "法语/B/2" to -1)
        assertEquals("A", seriesByPopularity(items, "法语", counts).first().folder)
    }

    @Test fun newDefaultIsSixButExplicitPreferenceAndDailyLimitAreUnchanged() {
        assertEquals(6, Settings().playlistSize)
        assertEquals(4, Settings(playlistSize = 4).copy(masterStars = 6).playlistSize)
        assertEquals(2, Settings().dailyLimit)
    }
}
