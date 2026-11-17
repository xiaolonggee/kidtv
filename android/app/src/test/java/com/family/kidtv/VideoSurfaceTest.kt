package com.family.kidtv

import android.content.Context
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoSurfaceTest {
    @Test fun compatibilityViewUsesTextureAndOriginalUsesSurface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val compatible = LayoutInflater.from(context).inflate(R.layout.kidtv_texture_player, null) as PlayerView
        assertTrue(compatible.videoSurfaceView is TextureView)
        assertFalse(compatible.useController)
        assertTrue(PlayerView(context).videoSurfaceView is SurfaceView)
    }
}
