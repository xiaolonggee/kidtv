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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.kidtv.AppViewModel
import com.family.kidtv.Screen
import com.family.kidtv.data.PlaybackMode
import com.family.kidtv.storage.SafPicker
import com.family.kidtv.storage.StoragePermissions
import com.family.kidtv.data.Settings
import kotlinx.coroutines.launch

private data class SettingDef(
    val label: String, val sub: String,
    val get: (Settings) -> Int, val set: (Settings, Int) -> Settings,
    val min: Int = 0, val max: Int = 0, val step: Int = 1,
    val fmt: (Int) -> String = { "$it" },
    val bool: Boolean = false,
)

/** 常用：每日上限 / 熟了次数 / 片单数量 / 播完默认 */
private val BASIC_DEFS = listOf(
    SettingDef("每日集数上限", "Daily limit", { it.dailyLimit }, { s, v -> s.copy(dailyLimit = v) }, 1, 6, 1, { "$it 集" }),
    SettingDef("“熟了”所需次数", "Stars to master", { it.masterStars }, { s, v -> s.copy(masterStars = v) }, 3, 10, 1, { "$it 次" }),
    SettingDef("本周片单数量", "Playlist size", { it.playlistSize }, { s, v -> s.copy(playlistSize = v) }, 3, 5, 1, { "$it 集" }),
    SettingDef(
        "播完默认", "顺序 / 循环 / 停止",
        get = { PlaybackMode.entries.indexOf(it.resolvedPlaybackMode()) },
        set = { s, v -> s.withPlaybackMode(PlaybackMode.entries[v.coerceIn(0, PlaybackMode.entries.lastIndex)]) },
        min = 0, max = PlaybackMode.entries.lastIndex, step = 1,
        fmt = { PlaybackMode.entries[it.coerceIn(0, PlaybackMode.entries.lastIndex)].labelZh },
    ),
)

/** 高级：阈值、同步开关等 */
private val ADVANCED_DEFS = listOf(
    SettingDef("完成判定阈值", "看到多少算看完", { it.threshold }, { s, v -> s.copy(threshold = v) }, 50, 100, 5, { "$it%" }),
    SettingDef("仅 Wi-Fi 下载", "同步新内容时", { if (it.wifiOnly) 1 else 0 }, { s, v -> s.copy(wifiOnly = v == 1) }, bool = true, fmt = { if (it == 1) "开 ON" else "关 OFF" }),
    SettingDef("同步删除本地文件", "后台删除的内容", { if (it.allowDelete) 1 else 0 }, { s, v -> s.copy(allowDelete = v == 1) }, bool = true, fmt = { if (it == 1) "开 ON" else "关 OFF" }),
)

@Composable
fun ParentScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val library by vm.library.collectAsState()
    val scanOutcome by vm.lastScanOutcome.collectAsState()
    val syncStatus by vm.sync.status.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val daily by vm.dailyStats.collectAsState()
    val today by vm.todayDate.collectAsState()
    var advancedOpen by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setLibraryUri(uri.toString())
            Toast.makeText(context, "已选择媒体库目录，正在扫描…", Toast.LENGTH_SHORT).show()
        }
    }

    fun applySetting(def: SettingDef, newVal: Int) {
        scope.launch {
            vm.updateSetting { def.set(it, newVal) }
            vm.refreshPlaylist(null)
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp)) {
        Column(
            Modifier.weight(1.5f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton("⬅ 返回", onClick = { vm.pop() }, requestInitialFocus = true)
                Spacer(Modifier.width(12.dp))
                Text("⚙️ 家长设置", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text("⬅➡ 调整", color = C.Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(2.dp))

            Text("常用", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            BASIC_DEFS.forEach { def ->
                SettingRow(def, settings) { applySetting(def, it) }
            }

            Spacer(Modifier.height(4.dp))
            TvButton(
                if (advancedOpen) "▼ 高级（收起）" else "▶ 高级（阈值 / 路径 / 诊断 / 配对）",
                onClick = { advancedOpen = !advancedOpen },
            )

            if (advancedOpen) {
                Text("高级选项", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                ADVANCED_DEFS.forEach { def ->
                    SettingRow(def, settings) { applySetting(def, it) }
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("📂 SAF 选择媒体库", onClick = {
                        SafPicker.launchTree(context, pickFolder) {
                            Toast.makeText(context, "此设备无文档树选择器，请用「浏览路径」", Toast.LENGTH_LONG).show()
                        }
                    })
                    TvButton("📁 浏览路径", onClick = {
                        vm.push(Screen.FolderBrowser(settings.libraryPath))
                    })
                    TvButton(if (scanning) "⏳ 扫描中…" else "🔄 重新扫描", onClick = { vm.rescan() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("🛠 诊断", onClick = {
                        vm.refreshDiagnostics()
                        vm.push(Screen.Diagnostics)
                    })
                    if (StoragePermissions.canManageAllFilesIntent(context)) {
                        TvButton("所有文件权限", onClick = {
                            runCatching { context.startActivity(StoragePermissions.manageAllFilesIntent(context)) }
                        })
                    }
                }
                if (settings.libraryPath.isNotEmpty()) {
                    Text("当前路径：${settings.libraryPath}", color = C.PathText, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                Text("🌐 服务器与配对", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Black)
                var url by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
                var code by remember { mutableStateOf("") }
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                fun Modifier.dpadEscape() = this.onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down); true }
                        Key.DirectionUp -> { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); true }
                        else -> false
                    }
                }
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("服务器地址，如 http://192.168.1.10:8600") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().dpadEscape(),
                    colors = fieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("6 位配对码") },
                        singleLine = true, modifier = Modifier.width(190.dp).dpadEscape(),
                        colors = fieldColors(),
                    )
                    TvButton("🔗 配对", primary = true, onClick = {
                        if (url.isBlank() || code.length < 6) {
                            Toast.makeText(context, "请填写服务器地址和 6 位配对码", Toast.LENGTH_SHORT).show()
                        } else scope.launch {
                            val msg = vm.pairWithServer(url.trim(), code)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    })
                    TvButton("⬆ 立即同步", onClick = {
                        vm.sync.requestSync()
                        Toast.makeText(context, "已触发同步", Toast.LENGTH_SHORT).show()
                    })
                }
                if (settings.deviceId.isNotEmpty()) {
                    Text(
                        "已配对：${settings.deviceName}（ID ${settings.deviceId}）· 设置版本 v${settings.settingsVersion}",
                        color = C.Good, fontSize = 13.sp,
                    )
                }
            }

            // 轻量媒体库入口（不打开高级也能扫）
            if (!advancedOpen) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("📂 选文件夹", onClick = {
                        SafPicker.launchTree(context, pickFolder) {
                            Toast.makeText(context, "请打开「高级」用浏览路径", Toast.LENGTH_LONG).show()
                        }
                    })
                    TvButton(if (scanning) "⏳ 扫描中…" else "🔄 重新扫描", onClick = { vm.rescan() })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.width(18.dp))

        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .background(C.Panel, RoundedCornerShape(18.dp)).padding(16.dp),
        ) {
            Text("📂 媒体库", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                if (library.root.isEmpty()) "（未找到媒体库）" else library.root + "/",
                color = C.PathText, fontSize = 12.sp,
            )
            Text(scanOutcome.summary, color = C.Muted, fontSize = 11.sp)
            if (!scanOutcome.error.isNullOrBlank()) {
                Text("扫描错误：${scanOutcome.error}", color = C.Pink, fontSize = 12.sp)
            }
            library.languages.forEach { lang ->
                Text("├─ $lang/", color = C.PathText, fontSize = 12.sp)
                library.series.filter { it.language == lang }.forEach { s ->
                    Text("│  ├─ ${s.folder}/ (${s.episodes.size})", color = C.PathText, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("🔄 同步状态", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(syncStatus.message, color = if (syncStatus.online) C.Good else C.Muted, fontSize = 13.sp)
            if (syncStatus.pendingEvents > 0) {
                Text("待上传事件：${syncStatus.pendingEvents}", color = C.Warn, fontSize = 13.sp)
            }
            syncStatus.downloading?.let {
                Spacer(Modifier.height(4.dp))
                Text("下载中：$it（${syncStatus.downloadPct}%）", color = C.Text, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                MiniBar(syncStatus.downloadPct / 100f, Modifier.fillMaxWidth())
            }
            syncStatus.lastError?.let {
                Spacer(Modifier.height(4.dp))
                Text("⚠ $it", color = C.Danger, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("📊 今日", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            val eps = daily[today.toString()]?.eps ?: 0
            val limit = vm.effectiveDailyLimit()
            val bonus = vm.bonusForToday()
            Text(
                "已看 $eps/$limit 集" + (if (bonus > 0) "（含加看 $bonus）" else "") +
                    " · 连续 ${vm.streak()} 天 · 本周 ${vm.weekSeconds() / 60} 分钟",
                color = C.Text, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "规则：观看 ≥ 阈值才 +1⭐；满“熟了”次数后移入复习。阈值等在「高级」里调。",
                color = C.PathText, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.Text, unfocusedTextColor = C.Text,
    focusedBorderColor = C.Focus, unfocusedBorderColor = C.Muted.copy(alpha = 0.4f),
    focusedLabelColor = C.Focus, unfocusedLabelColor = C.Muted,
    cursorColor = C.Focus,
)

@Composable
private fun SettingRow(def: SettingDef, settings: Settings, onChange: (Int) -> Unit) {
    val value = def.get(settings)
    fun step(dir: Int) {
        val next = if (def.bool) 1 - value
        else (value + dir * def.step).coerceIn(def.min, def.max)
        if (next != value) onChange(next) else if (def.bool) onChange(next)
    }
    Focusable(
        onClick = { step(1) },
        modifier = Modifier
            .fillMaxWidth().height(52.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { step(-1); true }
                    Key.DirectionRight -> { step(1); true }
                    else -> false
                }
            },
        shape = RoundedCornerShape(16.dp),
        focusScale = 1.02f,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(def.label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(def.sub, color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("◀", color = C.Muted, fontSize = 14.sp)
            Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                Text(def.fmt(value), color = C.Focus, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Text("▶", color = C.Muted, fontSize = 14.sp)
        }
    }
}
