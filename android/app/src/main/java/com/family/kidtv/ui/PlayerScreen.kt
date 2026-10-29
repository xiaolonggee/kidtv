package com.family.kidtv.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.family.kidtv.AppViewModel
import com.family.kidtv.data.PROGRESS_SAVE_INTERVAL_MS
import com.family.kidtv.data.shouldClearResumePosition
import com.family.kidtv.data.AfterEpisodeAction
import com.family.kidtv.PlayResult
import com.family.kidtv.Screen
import com.family.kidtv.data.Episode
import kotlinx.coroutines.launch

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private sealed class Phase {
    data object Playing : Phase()
    data class Celebrating(val result: PlayResult, val ended: Boolean) : Phase()
    data class Countdown(val next: Episode) : Phase()
}

@Composable
fun PlayerScreen(vm: AppViewModel, episodeKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val daily by vm.dailyStats.collectAsState()
    val today by vm.todayDate.collectAsState()
    val library by vm.library.collectAsState()
    val counts by vm.playCounts.collectAsState()

    val ep = library.episodeByKey[episodeKey] ?: run { vm.goHome(); return }

    // 进入时已到上限 → 温柔拦截
    LaunchedEffect(Unit) { if (vm.limitReached()) vm.replaceTop(Screen.Done) }

    var phase by remember(episodeKey) { mutableStateOf<Phase>(Phase.Playing) }
    var watchedMs by remember(episodeKey) { mutableLongStateOf(0L) }
    var positionMs by remember(episodeKey) { mutableLongStateOf(0L) }
    var durationMs by remember(episodeKey) { mutableLongStateOf(ep.durationMs) }
    var playing by remember(episodeKey) { mutableStateOf(true) }
    var finished by remember(episodeKey) { mutableStateOf(false) }
    var ended by remember(episodeKey) { mutableStateOf(false) }

    var resumeApplied by remember(episodeKey) { mutableStateOf(false) }
    val player = remember(episodeKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(ep.uri)))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(episodeKey) {
        val prog = vm.loadPlaybackProgress(episodeKey)
        if (prog != null && prog.positionMs > 1_500L && !resumeApplied) {
            player.seekTo(prog.positionMs)
            watchedMs = prog.passWatchedMs.coerceAtLeast(0L)
            positionMs = prog.positionMs
            resumeApplied = true
        } else {
            resumeApplied = true
        }
    }
    DisposableEffect(episodeKey) {
        val act = context as? Activity
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !finished) ended = true
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose {
            // Persist progress on leave (unless already finished / cleared)
            if (!finished) {
                val pos = player.currentPosition
                val dur = if (player.duration > 0) player.duration else durationMs
                if (shouldClearResumePosition(pos, dur)) {
                    vm.clearPlaybackProgress(episodeKey)
                } else {
                    vm.savePlaybackProgress(episodeKey, pos, watchedMs, dur)
                }
            }
            player.release()
            act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun finishPlay(reachedEnd: Boolean) {
        if (finished) return
        finished = true
        player.pause()
        val dur = if (durationMs > 0) durationMs else 1L
        val pos = player.currentPosition
        val pct = watchedMs * 100 / dur
        val th = settings.threshold
        if (reachedEnd || shouldClearResumePosition(pos, dur) || pct >= th) {
            vm.clearPlaybackProgress(episodeKey)
        } else {
            vm.savePlaybackProgress(episodeKey, pos, watchedMs, dur)
        }
        if (pct >= th) {
            scope.launch {
                val result = vm.recordPlay(ep, (watchedMs / 1000).toInt(), (dur / 1000).toInt())
                phase = Phase.Celebrating(result, reachedEnd)
            }
        } else {
            Toast.makeText(context, "看了 ${pct}%（未到 ${th}%），这次不算哦", Toast.LENGTH_SHORT).show()
            vm.pop()
        }
    }

    // 实际播放计时（只在真正播放时累计，快进不算）+ 进度刷新
    LaunchedEffect(episodeKey) {
        var sinceSave = 0L
        while (!finished) {
            if (player.isPlaying) {
                watchedMs += 250
                positionMs = player.currentPosition
                if (player.duration > 0) durationMs = player.duration
                sinceSave += 250
                if (sinceSave >= PROGRESS_SAVE_INTERVAL_MS) {
                    sinceSave = 0L
                    vm.savePlaybackProgress(episodeKey, positionMs, watchedMs, durationMs)
                }
            }
            kotlinx.coroutines.delay(250)
        }
    }
    // 自然播完
    LaunchedEffect(ended) { if (ended) finishPlay(true) }

    BackHandler(enabled = phase is Phase.Playing) { finishPlay(false) }

    val playerFocus = remember { FocusRequester() }
    LaunchedEffect(phase) { if (phase is Phase.Playing) runCatching { playerFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocus)
            .focusable()
            .onKeyEvent { e ->
                if (phase !is Phase.Playing || finished) return@onKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
                    Key.MediaPlayPause,
                    -> { if (player.isPlaying) player.pause() else player.play(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ------- 顶部信息条 -------
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent)))
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${ep.seriesFolder} · 第%02d集 %s".format(ep.num, ep.title),
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black,
            )
            if (ep.cnTitle.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(ep.cnTitle, color = Color(0xFFDDDDDD), fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "📺 今天 ${daily[today.toString()]?.eps ?: 0}/${settings.dailyLimit} · ",
                color = Color.White, fontSize = 14.sp,
            )
            Stars(counts[ep.key] ?: 0, settings.masterStars, 14.sp)
        }

        // ------- 暂停大图标 -------
        if (!playing && phase is Phase.Playing && !finished) {
            Box(
                Modifier.align(Alignment.Center).size(110.dp)
                    .background(Color.Black.copy(alpha = .6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 46.sp) }
        }

        // ------- 底部进度条（幼儿简洁：大进度 + 播放/暂停，无说明/文件名/阈值线） -------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f))))
                .padding(horizontal = 32.dp, vertical = 22.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp)) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .25f), RoundedCornerShape(7.dp)))
                val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth(frac).height(14.dp)
                        .background(C.BarBrush, RoundedCornerShape(7.dp)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Text(
                    "${mmss(positionMs)} / ${mmss(durationMs)}",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        // ------- 庆祝 / 倒计时 覆盖层 -------
        when (val p = phase) {
            is Phase.Celebrating -> CelebrationOverlay(
                ep = ep, result = p.result, max = settings.masterStars,
                onDone = {
                    if (!p.ended) { vm.pop(); return@CelebrationOverlay }
                    if (vm.limitReached()) { vm.goHome(); vm.push(Screen.Done); return@CelebrationOverlay }
                    when (val action = vm.afterEpisodeAction(ep.key)) {
                        is AfterEpisodeAction.NextCountdown -> phase = Phase.Countdown(action.next)
                        is AfterEpisodeAction.Replay -> vm.replaceTop(Screen.Player(ep.key))
                        is AfterEpisodeAction.Stop -> vm.pop()
                    }
                },
            )
            is Phase.Countdown -> CountdownOverlay(
                next = p.next, plays = counts[p.next.key] ?: 0, max = settings.masterStars,
                onPlay = { vm.replaceTop(Screen.Player(p.next.key)) },
                onCancel = { vm.pop() },
            )
            else -> {}
        }
    }
}

@Composable
private fun CelebrationOverlay(ep: Episode, result: PlayResult, max: Int, onDone: () -> Unit) {
    var fired by remember { mutableStateOf(false) }
    val done = { if (!fired) { fired = true; onDone() } }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(3200); done() }
    BackHandler { done() }
    Box(
        Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF2A3F8F), C.Panel)), RoundedCornerShape(30.dp))
                .padding(horizontal = 44.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(seriesEmoji(ep.seriesFolder, ""), fontSize = 64.sp)
            Text("看完啦 +1⭐", color = C.Focus, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Stars(result.after, max, 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${ep.seriesFolder} 第%02d集 · 已经看了 ${result.after} 次".format(ep.num),
                color = Color(0xFFE5EBFF), fontSize = 16.sp,
            )
            if (result.mastered) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🎉 这一集「熟了」！移到复习，新的一集加入本周片单",
                    color = C.Good, fontSize = 15.sp, fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(16.dp))
            TvButton("好的 OK", primary = true, requestInitialFocus = true, onClick = done)
        }
    }
}

@Composable
private fun CountdownOverlay(next: Episode, plays: Int, max: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    var n by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (n > 0) {
            kotlinx.coroutines.delay(1000)
            n--
        }
        onPlay()
    }
    BackHandler { onCancel() }
    Box(
        Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF2A3F8F), C.Panel)), RoundedCornerShape(30.dp))
                .padding(horizontal = 44.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("下一集马上开始 · Next", color = Color(0xFFE5EBFF), fontSize = 17.sp)
            Text("$n", color = C.Focus, fontSize = 76.sp, fontWeight = FontWeight.Black)
            Row(
                Modifier.background(Color.Black.copy(alpha = .25f), RoundedCornerShape(18.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(next.seriesFolder, next.cnTitle, null, Modifier.size(96.dp, 68.dp), emojiSize = 36.sp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("%02d · %s".format(next.num, next.title), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    if (next.cnTitle.isNotEmpty()) Text(next.cnTitle, color = C.Muted, fontSize = 14.sp)
                    Stars(plays, max, 14.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("▶ 现在播放", primary = true, requestInitialFocus = true, onClick = onPlay)
                TvButton("✋ 不看了", onClick = onCancel)
            }
        }
    }
}
