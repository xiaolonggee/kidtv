package com.family.kidtv

import com.family.kidtv.ui.shouldCountVideoTime
import com.family.kidtv.data.Settings
import org.junit.Assert.*
import org.junit.Test

class VideoHealthTest {
    @Test fun audioWithoutFirstFrameDoesNotCount() {
        assertFalse(shouldCountVideoTime(true, false, false))
    }
    @Test fun renderedVideoCountsOnlyWhilePlayingWithoutError() {
        assertTrue(shouldCountVideoTime(true, true, false))
        assertFalse(shouldCountVideoTime(false, true, false))
        assertFalse(shouldCountVideoTime(true, true, true))
    }
    @Test fun compatibilityDefaultAndOriginalFallback() {
        assertTrue(Settings().textureVideo)
        assertFalse(Settings().copy(textureVideo = false).textureVideo)
    }
}
