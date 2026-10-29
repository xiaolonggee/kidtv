package com.family.kidtv.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.family.kidtv.AppViewModel
import com.family.kidtv.Screen
import com.family.kidtv.data.PlaybackProgress

private fun mmssDetail(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
fun EpisodeDetailScreen(vm: AppViewModel, episodeKey: String) {
    val context = LocalContext.current
    val library by vm.library.collectAsState()
    val settings by vm.settings.collectAsState()
    val counts by vm.playCounts.collectAsState()
    val covers by vm.episodeCovers.collectAsState()
    val ep = library.episodeByKey[episodeKey] ?: run { vm.pop(); return }
    val plays = counts[ep.key] ?: 0
    val mastered = plays >= settings.masterStars
    val durSec = (ep.durationMs / 1000).coerceAtLeast(0)
    val cover = covers[ep.key]

    var resumeMs by remember(episodeKey) { mutableLongStateOf(0L) }

    LaunchedEffect(episodeKey) {
        val p: PlaybackProgress? = vm.loadPlaybackProgress(episodeKey)
        resumeMs = p?.positionMs ?: 0L
    }

    val preview = remember(episodeKey) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.parse(ep.uri)))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(resumeMs, episodeKey) {
        if (resumeMs > 1_500L) {
            preview.seekTo(resumeMs)
        }
    }
    DisposableEffect(episodeKey) {
        onDispose {
            preview.release()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("⬅ 返回", onClick = { vm.pop() })
            Spacer(Modifier.width(16.dp))
            Text("集详情", color = C.Muted, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(260.dp, 160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            player = preview
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Fallback art if preview not ready — show cover underneath briefly via Art when no player surface yet
                if (cover != null) {
                    // cover used by Art elsewhere; preview is primary
                }
            }
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "%s · %s · 第 %02d 集".format(ep.language, ep.seriesFolder, ep.num),
                    color = C.Muted, fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(ep.title, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Black)
                if (ep.cnTitle.isNotEmpty()) {
                    Text(ep.cnTitle, color = C.Muted, fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stars(plays, settings.masterStars, 18.sp)
                    Spacer(Modifier.width(12.dp))
                    if (mastered) {
                        Text(
                            "熟了 ✓",
                            color = Color(0xFF0B2A16),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(C.Good, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    } else {
                        Text(
                            "距「熟了」还差 ${settings.masterStars - plays} 次",
                            color = C.Muted, fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "时长 %02d:%02d · 文件 %s".format(durSec / 60, durSec % 60, ep.filename),
                    color = C.PathText, fontSize = 12.sp,
                )
                if (resumeMs > 1_500L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "上次看到 ${mmssDetail(resumeMs)} · 将从这里继续",
                        color = C.Focus, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val playLabel = if (resumeMs > 1_500L) "▶ 继续播放" else "▶ 全屏播放"
                    TvButton(playLabel, primary = true, requestInitialFocus = true, onClick = {
                        preview.pause()
                        vm.push(Screen.Player(ep.key))
                    })
                    if (resumeMs > 1_500L) {
                        TvButton("从头看", onClick = {
                            vm.clearPlaybackProgress(ep.key)
                            resumeMs = 0L
                            preview.seekTo(0L)
                            preview.pause()
                            vm.push(Screen.Player(ep.key))
                        })
                    }
                    TvButton("⬅ 返回", onClick = { vm.pop() })
                }
            }
        }
        Text(
            "静音预览 · 按「全屏播放」进入正式观看（算次数）",
            color = C.Muted, fontSize = 12.sp,
        )
    }
}
