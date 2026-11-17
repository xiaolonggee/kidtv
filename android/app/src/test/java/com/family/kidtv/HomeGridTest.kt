package com.family.kidtv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.family.kidtv.data.Series
import com.family.kidtv.ui.SeriesGrid
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class HomeGridTest {
    @get:Rule val compose = createComposeRule()
    private fun series(lang: String, count: Int) = (1..count).map {
        Series(lang, "Cartoon $it", "Cartoon $it", "动画 $it", null, it == 1, emptyList())
    }
    @Test fun eightSeriesFitTwoRowsAndRemoteMovesDownUpAndSelects() {
        var selected = ""
        compose.setContent {
            val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            SeriesGrid(series("法语", 8), emptyMap(), 5, emptyMap(),
                Modifier.width(896.dp).height(200.dp)) { selected = it.folder }
        }
        (1..8).forEach { compose.onNodeWithTag("series-法语/Cartoon $it").assertIsDisplayed() }
        val first = compose.onNodeWithTag("series-法语/Cartoon 1")
        first.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        first.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("series-法语/Cartoon 5").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("series-法语/Cartoon 6").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("series-法语/Cartoon 2").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        assertEquals("Cartoon 2", selected)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home-grid.png")
    }
    @Test fun longLibraryScrollsVerticallyAndLanguageChangeResetsGrid() {
        val language = mutableStateOf("英语")
        compose.setContent {
            val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            SeriesGrid(series(language.value, if (language.value == "英语") 20 else 6),
                emptyMap(), 5, emptyMap(), Modifier.width(896.dp).height(200.dp)) {}
        }
        compose.onNodeWithTag("series-英语/Cartoon 1").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        repeat(4) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; compose.waitForIdle() }
        compose.onNodeWithTag("series-英语/Cartoon 17").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { language.value = "法语" }
        compose.onNodeWithTag("series-法语/Cartoon 1").assertIsDisplayed()
        compose.onNodeWithTag("series-法语/Cartoon 6").assertIsDisplayed()
    }
}
