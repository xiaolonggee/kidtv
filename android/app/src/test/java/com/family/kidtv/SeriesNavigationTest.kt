package com.family.kidtv

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.family.kidtv.data.*
import com.family.kidtv.ui.SeriesScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SeriesNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: AppViewModel
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AppViewModel(app)
        repeat(30) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val episodes = (1..25).map { n -> Episode(
            key = "English/Test/$n.mp4", language = "English", seriesFolder = "Test",
            num = n, title = "Episode $n", cnTitle = "", filename = "$n.mp4",
            uri = "file:///tmp/$n.mp4", sizeBytes = 100, durationMs = 10000L) }
        vm.library.value = Library("/test", listOf("English"), listOf(
            Series("English", "Test", "Test", "", null, false, episodes)))
        compose.setContent {
            val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
            androidx.compose.runtime.SideEffect {
                inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            }
            SeriesScreen(vm, "English", "Test")
        }
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

    @Test fun parentBatchRequiresGateAndCannotBeConfirmedTwice() = runBlocking {
        val before = vm.settingsRepo.flow.first()
        org.junit.Assert.assertFalse(vm.grantExtraEpisodesToday(3))
        vm.openGate(GatePurpose.OneMoreEpisode)
        vm.onGateUnlocked()
        org.junit.Assert.assertTrue(vm.grantExtraEpisodesToday(3))
        val saved = vm.settingsRepo.flow.first()
        val date = java.time.LocalDate.now(TZ).toString()
        val oldBonus = if (before.extraEpisodesDate == date) before.extraEpisodes.coerceAtLeast(0) else 0
        org.junit.Assert.assertEquals(oldBonus + 3, saved.extraEpisodes)
        org.junit.Assert.assertEquals(before.dailyLimit, saved.dailyLimit)
        org.junit.Assert.assertFalse(vm.grantExtraEpisodesToday(3))
        org.junit.Assert.assertEquals(saved, vm.settingsRepo.flow.first())
    }
    @Test fun leavingParentWithoutConfirmationAddsNothing() = runBlocking {
        val before = vm.settingsRepo.flow.first()
        vm.openGate(GatePurpose.OneMoreEpisode)
        vm.onGateUnlocked()
        vm.pop()
        org.junit.Assert.assertFalse(vm.grantExtraEpisodesToday(5))
        org.junit.Assert.assertEquals(before, vm.settingsRepo.flow.first())
    }

}
