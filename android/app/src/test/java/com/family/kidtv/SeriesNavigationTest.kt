package com.family.kidtv

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.family.kidtv.data.*
import com.family.kidtv.ui.SeriesScreen
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeriesNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: AppViewModel
    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AppViewModel(app)
        repeat(30) { compose.waitForIdle(); Thread.sleep(20) }
        val episodes = (1..25).map { n -> Episode(
            key = "English/Test/$n.mp4", language = "English", seriesFolder = "Test",
            num = n, title = "Episode $n", cnTitle = "", filename = "$n.mp4",
            uri = "file:///tmp/$n.mp4", sizeBytes = 100, durationMs = 10000L) }
        vm.library.value = Library("/test", listOf("English"), listOf(
            Series("English", "Test", "Test", "", null, false, episodes)))
        compose.setContent { SeriesScreen(vm, "English", "Test") }
        compose.waitForIdle()
    }
    @Test fun reviewIsPermanentRightHandTabAndEmptyStateWorks() {
        compose.onNodeWithText("📖 学习中 · 25 集").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("📖 学习中 · 25 集").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("🔁 复习中 · 0 集").assertIsFocused()
        compose.onNodeWithText("还没有熟了的集数，学会的视频会自动出现在这里。").assertIsDisplayed()
        compose.onNodeWithText("🔁 复习中 · 0 集").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText("Episode 1").assertIsDisplayed()
    }
    @Test fun episodeSixteenDoesNotJumpBackToFirst() {
        compose.onNodeWithText("📖 学习中 · 25 集").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(3) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; compose.waitForIdle() }
        compose.onNodeWithTag("episode-English/Test/16.mp4").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("episode-English/Test/21.mp4").assertIsFocused()
    }
    @Test fun masteredEpisodeMovesToReviewWithoutDeletingIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            repeat(5) { n -> KidTvDb.get(app).dao().insertEvent(PlayEvent(
                id = "review-navigation-$n", language = "English", series = "Test",
                episodeKey = "English/Test/7.mp4", episodeNum = 7, title = "Episode 7",
                watchedSec = 10, durationSec = 10, counted = true,
                localDate = "2026-01-01", occurredAtMs = 0, synced = true)) }
        }
        compose.waitUntil(5000) { vm.plays("English/Test/7.mp4") >= 5 }
        compose.onNodeWithText("🔁 复习中 · 1 集").performClick()
        compose.onNodeWithText("Episode 7").assertIsDisplayed()
        org.junit.Assert.assertEquals(25, vm.library.value.series.first().episodes.size)
    }

}
