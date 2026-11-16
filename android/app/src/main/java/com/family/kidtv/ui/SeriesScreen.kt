package com.family.kidtv.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.Screen
import com.family.kidtv.data.Episode

private fun mmss(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)

@Composable
fun SeriesScreen(vm: AppViewModel, language: String, folder: String) {
    val library by vm.library.collectAsState()
    val counts by vm.playCounts.collectAsState()
    val settings by vm.settings.collectAsState()
    val covers by vm.episodeCovers.collectAsState()
    val s = library.series.firstOrNull { it.language == language && it.folder == folder } ?: run {
        vm.goHome(); return
    }
    val ms = settings.masterStars
    val learning = s.episodes.filter { (counts[it.key] ?: 0) < ms }
    val review = s.episodes.filter { (counts[it.key] ?: 0) >= ms }
    var selectedTab by rememberSaveable(language, folder) { mutableIntStateOf(0) }
    val learningScroll = rememberLazyGridState()
    val reviewScroll = rememberLazyGridState()
    val tabs = remember { List(2) { FocusRequester() } }
    // Initial focus belongs to the permanent tab row, never a recycled grid item.
    LaunchedEffect(language, folder) { tabs[selectedTab].requestFocus() }
    val visibleEpisodes = if (selectedTab == 0) learning else review
    val episodeFocus = remember(s.episodes) { s.episodes.associate { it.key to FocusRequester() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp)) {
        // 头部
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton("⬅ 返回", onClick = { vm.pop() })
            Spacer(Modifier.width(16.dp))
            Art(s.displayName, s.cnName, s.coverUri, Modifier.size(86.dp, 72.dp), emojiSize = 40.sp, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.displayName, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    if (s.cnName.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(s.cnName, color = C.Muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    "📁 ${library.root}/${s.language}/${s.folder}/ → ${s.episodes.size} 个视频",
                    color = C.PathText, fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "★ = 已看完的次数\n满 ${ms}★ =「熟了」→ 复习",
                color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("📖 学习中 · ${learning.size} 集", "🔁 复习中 · ${review.size} 集").forEachIndexed { index, title ->
                TvButton(title, onClick = { selectedTab = index }, primary = selectedTab == index,
                    modifier = Modifier.weight(1f).focusRequester(tabs[index])
                        .onFocusChanged { if (it.isFocused) selectedTab = index }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown && visibleEpisodes.isNotEmpty()) {
                                if (event.type == KeyEventType.KeyDown) {
                                    val scroll = if (selectedTab == 0) learningScroll else reviewScroll
                                    val ep = visibleEpisodes.getOrNull(scroll.firstVisibleItemIndex)
                                    ep?.let { episodeFocus[it.key]?.requestFocus() }
                                }
                                return@onPreviewKeyEvent true
                            }
                            val target = when (event.key) {
                                Key.DirectionLeft -> 0
                                Key.DirectionRight -> 1
                                else -> return@onPreviewKeyEvent false
                            }
                            if (event.type == KeyEventType.KeyDown) tabs[target].requestFocus()
                            true
                        })
            }
        }
        Text("左右切换分类 · 向下选择集数 · 熟了的视频在「复习中」", color = C.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
        if (visibleEpisodes.isEmpty()) {
            Text(if (selectedTab == 0) "全部都熟了，去右侧「复习中」再看吧！🎉"
                else "还没有熟了的集数，学会的视频会自动出现在这里。",
                color = C.Muted, fontSize = 17.sp, modifier = Modifier.padding(20.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = if (selectedTab == 0) learningScroll else reviewScroll,
            modifier = Modifier.weight(1f).testTag("series-episodes"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
        ) {
            items(visibleEpisodes, key = { it.key }) { ep ->
                EpisodeCard(ep, counts[ep.key] ?: 0, ms, mastered = selectedTab == 1,
                    seriesName = s.displayName, coverUri = covers[ep.key] ?: s.coverUri,
                    modifier = Modifier.focusRequester(episodeFocus.getValue(ep.key)).focusProperties {
                        if (visibleEpisodes.indexOf(ep) < 5) up = tabs[selectedTab]
                    }) { vm.push(Screen.EpisodeDetail(ep.key)) }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    ep: Episode, plays: Int, max: Int, mastered: Boolean,
    seriesName: String, coverUri: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val (c1, _) = C.seriesColors(seriesName)
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(116.dp).testTag("episode-${ep.key}"),
        shape = RoundedCornerShape(18.dp),
        background = if (mastered) C.MasteredBg else C.Panel,
        focusScale = 1.05f,
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            Art(ep.title, ep.cnTitle, coverUri,
                Modifier.align(Alignment.TopEnd).size(64.dp, 48.dp),
                emojiSize = 18.sp, shape = RoundedCornerShape(10.dp))
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text("%02d".format(ep.num), color = c1, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (plays == 0 && !mastered) {
                        Spacer(Modifier.width(4.dp))
                        Text("新", color = C.Pink, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.weight(1f))
                    if (mastered) Text(
                        "熟了 ✓", color = Color(0xFF063516), fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.background(C.Good, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    else Text(ep.filename, color = C.PathText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(60.dp))
                }
                Text(ep.title, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ep.cnTitle.isNotEmpty()) Text(ep.cnTitle, color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Stars(plays, max, 15.sp)
                Spacer(Modifier.weight(1f))
                if (ep.durationMs > 0) Text(mmss(ep.durationMs / 1000), color = C.PathText, fontSize = 10.sp)
            }
        }
    }
}
