package com.family.kidtv.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.GatePurpose
import com.family.kidtv.Screen
import com.family.kidtv.data.Episode
import com.family.kidtv.data.Series
import com.family.kidtv.storage.SafPicker

private val WEEK_CN = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun HomeScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val counts by vm.playCounts.collectAsState()
    val daily by vm.dailyStats.collectAsState()
    val library by vm.library.collectAsState()
    val covers by vm.episodeCovers.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val today by vm.todayDate.collectAsState()

    val todayEps = daily[today.toString()]?.eps ?: 0
    val dayLimit = vm.effectiveDailyLimit()
    var tab by rememberSaveable { mutableStateOf("") }
    val languages = library.languages
    val curTab = if (tab in languages) tab else languages.firstOrNull() ?: ""

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
        // ---------------- 顶栏 ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧸", fontSize = 36.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("宝宝动画屋", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(
                    "Little Ears · ${today.monthValue}月${today.dayOfMonth}日 星期${WEEK_CN[today.dayOfWeek.value - 1]}",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Pill("📺 今天 {} 集", " $todayEps/$dayLimit ", done = todayEps >= dayLimit)
            Spacer(Modifier.width(10.dp))
            Pill("🔥 连续 {} 天", " ${vm.streak()} ")
            Spacer(Modifier.width(10.dp))
            TvButton("📅 打卡日历", onClick = { vm.push(Screen.Calendar) })
            Spacer(Modifier.width(10.dp))
            TvButton("🔒 家长", onClick = { vm.openGate(GatePurpose.ParentSettings) })
        }
        Spacer(Modifier.height(10.dp))

        if (library.series.isEmpty()) {
            EmptyLibrary(vm, scanning)
            return@Column
        }

        // ---------------- 本周片单 ----------------
        Row(verticalAlignment = Alignment.Bottom) {
            Text("⭐ 本周片单", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text(
                "This week's playlist · 每集看满 ${settings.masterStars} 次就“熟了”",
                color = C.Muted, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        val playlist = vm.playlist
        if (playlist.isEmpty()) {
            Text("片单空空的～ 所有动画都熟了！", color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                items(playlist, key = { it.key }) { ep ->
                    val series = library.series.firstOrNull { it.language == ep.language && it.folder == ep.seriesFolder }
                    val cover = covers[ep.key] ?: series?.coverUri
                    PlaylistCard(
                        ep = ep,
                        plays = counts[ep.key] ?: 0,
                        max = settings.masterStars,
                        coverUri = cover,
                        cnName = series?.cnName ?: "",
                        first = ep == playlist.first(),
                        onClick = { vm.push(Screen.EpisodeDetail(ep.key)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---------------- 语言标签页 ----------------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            languages.forEach { lang ->
                LangTab(lang, selected = lang == curTab, onSelect = { tab = lang })
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---------------- 系列卡片 ----------------
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            items(library.series.filter { it.language == curTab }, key = { it.language + "/" + it.folder }) { s ->
                val thumb = s.episodes.firstNotNullOfOrNull { covers[it.key] } ?: s.coverUri
                SeriesCard(s, counts, settings.masterStars, coverUri = thumb) {
                    vm.push(Screen.SeriesPage(s.language, s.folder))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "⬆⬇⬅➡ 移动 · OK 确认 · 返回键 返回",
            color = C.Muted.copy(alpha = 0.8f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun EmptyLibrary(vm: AppViewModel, scanning: Boolean) {
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setLibraryUri(uri.toString())
            Toast.makeText(context, "已选择文件夹，正在找动画片…", Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (scanning) "🔎" else "📺", fontSize = 88.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            if (scanning) "正在找动画片…" else "还没有动画片哦",
            color = C.Text, fontSize = 32.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (scanning) "请稍等一下下"
            else "请爸爸妈妈帮帮忙：\n① 插上 U 盘 ② 选好动画片文件夹",
            color = C.Muted, fontSize = 18.sp, lineHeight = 28.sp,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton("🔌 重新扫描 U 盘", onClick = { vm.rescan() }, primary = true, requestInitialFocus = true)
            TvButton("📂 选择文件夹", onClick = {
                SafPicker.launchTree(context, pickFolder) {
                    Toast.makeText(context, "请用「浏览路径」或家长设置", Toast.LENGTH_LONG).show()
                    vm.openGate(GatePurpose.ParentSettings)
                }
            })
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton("📁 浏览路径", onClick = { vm.push(Screen.FolderBrowser("")) })
            TvButton("🔒 家长设置", onClick = { vm.openGate(GatePurpose.ParentSettings) })
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "文件夹示例：启蒙视频 / 英语 / 小鼠波波 / 01.mp4",
            color = C.PathText, fontSize = 13.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PlaylistCard(
    ep: Episode, plays: Int, max: Int, coverUri: String?, cnName: String,
    first: Boolean, onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.width(200.dp).height(158.dp),
        shape = RoundedCornerShape(20.dp),
        requestInitialFocus = first,
    ) {
        Column(Modifier.clip(RoundedCornerShape(20.dp))) {
            Box(Modifier.fillMaxWidth().height(88.dp)) {
                Art(ep.seriesFolder, cnName, coverUri, Modifier.fillMaxSize(), emojiSize = 40.sp)
                Text(
                    languageTag(ep.language), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                if (plays == 0) Text(
                    "新", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Color(0xFFFF4D7E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.background(C.Panel).fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    "%02d · %s".format(ep.num, ep.title),
                    color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cnName.ifEmpty { ep.seriesFolder }, color = C.Muted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Stars(plays, max, 16.sp)
                }
            }
        }
    }
}

@Composable
private fun LangTab(lang: String, selected: Boolean, onSelect: () -> Unit) {
    Focusable(
        onClick = onSelect,
        modifier = Modifier
            .height(46.dp)
            .onFocusChanged { if (it.isFocused) onSelect() },
        shape = RoundedCornerShape(23.dp),
        background = if (selected) Color.White else Color.White.copy(alpha = 0.08f),
        focusScale = 1.04f,
    ) {
        Row(
            Modifier.height(46.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(languageEmoji(lang), fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                lang, fontSize = 19.sp, fontWeight = FontWeight.Black,
                color = if (selected) C.Bg2 else C.Text,
            )
            val sub = languageSub(lang)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(sub, fontSize = 13.sp, color = if (selected) C.Bg2.copy(alpha = .75f) else C.Muted)
            }
        }
    }
}

@Composable
private fun SeriesCard(
    s: Series,
    counts: Map<String, Int>,
    masterStars: Int,
    coverUri: String?,
    onClick: () -> Unit,
) {
    val total = s.episodes.size
    val started = s.episodes.count { (counts[it.key] ?: 0) > 0 }
    val mastered = s.episodes.count { (counts[it.key] ?: 0) >= masterStars }
    Focusable(
        onClick = onClick,
        modifier = Modifier.width(300.dp).height(150.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.clip(RoundedCornerShape(22.dp))) {
            Art(s.displayName, s.cnName, coverUri, Modifier.width(130.dp).fillMaxHeight(), emojiSize = 56.sp)
            Column(Modifier.background(C.Panel).weight(1f).fillMaxHeight().padding(12.dp)) {
                Text(
                    s.displayName, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (s.cnName.isNotEmpty()) Text(s.cnName, color = C.Muted, fontSize = 14.sp, maxLines = 1)
                if (s.fav) Text("❤️ 宝宝最爱", color = C.Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("已看 $started/$total · 熟了 $mastered", color = C.Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                MiniBar(if (total > 0) started.toFloat() / total else 0f, Modifier.fillMaxWidth())
            }
        }
    }
}
