package com.family.kidtv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.family.kidtv.data.DayStat
import com.family.kidtv.data.Episode
import com.family.kidtv.data.KidTvDb
import com.family.kidtv.data.Library
import com.family.kidtv.data.MediaLibrary
import com.family.kidtv.data.PlayEvent
import com.family.kidtv.data.Series
import com.family.kidtv.data.AfterEpisodeAction
import com.family.kidtv.data.PlaybackMode
import com.family.kidtv.data.shouldClearResumePosition
import com.family.kidtv.data.ThumbnailService
import com.family.kidtv.data.ScanOutcome
import com.family.kidtv.data.PlaybackProgress
import com.family.kidtv.data.Settings
import com.family.kidtv.storage.StoragePermissions
import com.family.kidtv.storage.StorageProbe
import com.family.kidtv.util.CrashReporter
import com.family.kidtv.data.SettingsRepo
import com.family.kidtv.sync.ApiClient
import com.family.kidtv.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

val TZ: ZoneId = ZoneId.of("Asia/Shanghai")

enum class GatePurpose { ParentSettings, OneMoreEpisode }

sealed class Screen {
    data object Home : Screen()
    data class SeriesPage(val language: String, val folder: String) : Screen()
    data class EpisodeDetail(val episodeKey: String) : Screen()
    data class Player(val episodeKey: String) : Screen()
    data object Calendar : Screen()
    data object Gate : Screen()
    data object Parent : Screen()
    data object Done : Screen()
    data class FolderBrowser(val startPath: String = "") : Screen()
    data object Diagnostics : Screen()
}

data class PlayResult(val before: Int, val after: Int, val mastered: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = KidTvDb.get(app)
    val dao = db.dao()
    val settingsRepo = SettingsRepo(app)
    val mediaLibrary = MediaLibrary(app, dao)
    val sync = SyncManager(app, dao, settingsRepo, mediaLibrary)
    val thumbnailService = ThumbnailService(app, dao, viewModelScope)
    val episodeCovers = thumbnailService.covers

    val settings = settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val playCounts = dao.playCounts()
        .map { list -> list.associate { it.episodeKey to it.n } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val dailyStats = dao.dailyStats()
        .map { list -> list.associateBy { it.localDate } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap<String, DayStat>())

    val library = MutableStateFlow(Library.EMPTY)
    val lastScanOutcome = MutableStateFlow(ScanOutcome.EMPTY)
    val scanning = MutableStateFlow(false)
    val diagnosticsText = MutableStateFlow("（尚未刷新）")
    val todayDate = MutableStateFlow(LocalDate.now(TZ))

    /** 导航栈（返回键 pop）。 */
    val backstack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    /** 家长门解锁后的去向（设置页 / 再看一集）。 */
    var gatePurpose: GatePurpose = GatePurpose.ParentSettings

    init {
        sync.start(viewModelScope)
        thumbnailService.preloadFromDb()
        rescan()
        viewModelScope.launch {
            sync.libraryChanged.collect { rescan() }
        }
        // 跨零点自动刷新“今天”（本地 Asia/Shanghai 午夜重置）
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val d = LocalDate.now(TZ)
                if (d != todayDate.value) todayDate.value = d
            }
        }
    }

    // ---------------------------------------------------------- navigation
    fun push(s: Screen) { backstack.value = backstack.value + s }
    fun replaceTop(s: Screen) { backstack.value = backstack.value.dropLast(1) + s }
    fun goHome() { backstack.value = listOf(Screen.Home) }
    fun pop(): Boolean {
        if (backstack.value.size <= 1) return false
        backstack.value = backstack.value.dropLast(1)
        return true
    }

    // ---------------------------------------------------------- library
    fun rescan() {
        viewModelScope.launch {
            scanning.value = true
            val st = settingsRepo.flow.first()
            val outcome = withContext(Dispatchers.IO) {
                mediaLibrary.scan(st.libraryUri, st.libraryPath)
            }
            lastScanOutcome.value = outcome
            library.value = outcome.library
            scanning.value = false
            thumbnailService.schedule(outcome.library)
            refreshPlaylist(null)
        }
    }

    fun setLibraryUri(uri: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(libraryUri = uri, libraryPath = "") }
            rescan()
        }
    }

    fun setLibraryPath(path: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(libraryPath = path, libraryUri = "") }
            rescan()
        }
    }

    fun afterEpisodeAction(currentKey: String): AfterEpisodeAction {
        return when (settings.value.resolvedPlaybackMode()) {
            PlaybackMode.SEQUENTIAL -> {
                val next = nextEpisode(currentKey)
                if (next != null) AfterEpisodeAction.NextCountdown(next) else AfterEpisodeAction.Stop
            }
            PlaybackMode.LOOP -> AfterEpisodeAction.Replay
            PlaybackMode.STOP -> AfterEpisodeAction.Stop
        }
    }

    fun refreshDiagnostics(extra: String? = null) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val st = settingsRepo.flow.first()
            val text = withContext(Dispatchers.IO) {
                val perm = StoragePermissions.status(ctx)
                val vols = StorageProbe.withTopLevelFolderNames(StorageProbe.listVolumes(ctx))
                val libs = StorageProbe.findEnlightenmentFolders(ctx, 4)
                buildString {
                    appendLine("== KidTV 诊断 ==")
                    appendLine("权限：${perm.summary}")
                    appendLine("libraryUri=${st.libraryUri.ifBlank { "(空)" }}")
                    appendLine("libraryPath=${st.libraryPath.ifBlank { "(空)" }}")
                    appendLine("playbackMode=${st.resolvedPlaybackMode().storageKey} (autoplay=${st.autoplay})")
                    appendLine("server=${st.serverUrl.ifBlank { "(未配对)" }} device=${st.deviceName.ifBlank { "-" }}")
                    appendLine("媒体库系列数=${library.value.series.size} 集数=${library.value.episodeByKey.size}")
                    appendLine("上次扫描：${lastScanOutcome.value.summary}")
                    appendLine()
                    appendLine("-- 卷 --")
                    vols.forEach { v ->
                        val tag = if (v.removable) "[可移动]" else ""
                        appendLine("${v.label} $tag ${v.path}")
                        if (v.topFolders.isNotEmpty()) {
                            appendLine("  顶层: ${v.topFolders.take(12).joinToString()}")
                        }
                    }
                    appendLine()
                    appendLine("-- 启蒙视频候选 --")
                    if (libs.isEmpty()) appendLine("(未找到)")
                    else libs.forEach { appendLine(it.absolutePath) }
                    appendLine()
                    appendLine("-- 最近崩溃 --")
                    appendLine(CrashReporter.latestCrashText(ctx, 4000))
                    if (!extra.isNullOrBlank()) {
                        appendLine()
                        appendLine("-- 附加 --")
                        appendLine(extra)
                    }
                }
            }
            diagnosticsText.value = text
        }
    }

    // ---------------------------------------------------------- derived stats
    fun todayStr(): String = todayDate.value.toString()
    fun plays(key: String) = playCounts.value[key] ?: 0
    fun isMastered(key: String) = plays(key) >= settings.value.masterStars
    fun todayEps() = dailyStats.value[todayStr()]?.eps ?: 0
    fun bonusForToday(): Int {
        val s = settings.value
        return if (s.extraEpisodesDate == todayStr()) s.extraEpisodes.coerceAtLeast(0) else 0
    }
    fun effectiveDailyLimit(): Int = settings.value.dailyLimit + bonusForToday()
    fun limitReached() = todayEps() >= effectiveDailyLimit()

    fun openGate(purpose: GatePurpose = GatePurpose.ParentSettings) {
        gatePurpose = purpose
        push(Screen.Gate)
    }

    fun onGateUnlocked() {
        when (gatePurpose) {
            GatePurpose.ParentSettings -> replaceTop(Screen.Parent)
            GatePurpose.OneMoreEpisode -> {
                grantOneMoreToday()
                goHome()
            }
        }
        gatePurpose = GatePurpose.ParentSettings
    }

    fun grantOneMoreToday() {
        viewModelScope.launch {
            val today = todayStr()
            settingsRepo.update {
                val cur = if (it.extraEpisodesDate == today) it.extraEpisodes else 0
                it.copy(extraEpisodesDate = today, extraEpisodes = cur + 1)
            }
        }
    }

    /** 连续打卡天数：今天没看则从昨天起算，与原型一致。 */
    fun streak(): Int {
        val dates = dailyStats.value.filterValues { it.eps > 0 }.keys
        var d = todayDate.value
        if (d.toString() !in dates) d = d.minusDays(1)
        var n = 0
        while (d.toString() in dates) { n++; d = d.minusDays(1) }
        return n
    }

    fun weekSeconds(): Int {
        val mon = todayDate.value.with(DayOfWeek.MONDAY)
        return (0..6).sumOf { dailyStats.value[mon.plusDays(it.toLong()).toString()]?.sec ?: 0 }
    }

    fun last7Seconds(): Int =
        (0..6).sumOf { dailyStats.value[todayDate.value.minusDays(it.toLong()).toString()]?.sec ?: 0 }

    // ---------------------------------------------------------- weekly playlist
    val playlist: List<Episode>
        get() = decodePlaylist(settings.value.playlistJson)
            .mapNotNull { library.value.episodeByKey[it] }

    private fun decodePlaylist(jsonStr: String): List<String> = try {
        Json.decodeFromString(ListSerializer(String.serializer()), jsonStr)
    } catch (e: Exception) { emptyList() }

    /** 片单填充顺序：最爱优先，然后英语/法语交替，与原型 PLAYLIST_ORDER 等价。 */
    private fun playlistOrder(lib: Library): List<Series> {
        val favs = lib.series.filter { it.fav }
        val rest = lib.series.filterNot { it.fav }.groupBy { it.language }
        val langs = lib.languages.ifEmpty { rest.keys.toList() }
        val queues = langs.map { rest[it].orEmpty().toMutableList() }.filter { it.isNotEmpty() }
        val inter = mutableListOf<Series>()
        var remaining = queues.sumOf { it.size }
        var i = 0
        while (remaining > 0) {
            val q = queues[i % queues.size]
            if (q.isNotEmpty()) { inter += q.removeAt(0); remaining-- }
            i++
        }
        return favs + inter
    }

    private fun nextCandidate(s: Series, pl: List<String>, counts: Map<String, Int>, masterStars: Int): String? =
        s.episodes.firstOrNull { (counts[it.key] ?: 0) < masterStars && it.key !in pl }?.key

    /** 与原型 refreshPlaylist 一致：熟了的集子被同系列下一新集替换。 */
    fun refreshPlaylist(masteredKey: String?) {
        viewModelScope.launch {
            val lib = library.value
            if (lib.series.isEmpty()) return@launch
            val st = settingsRepo.flow.first()
            val ms = st.masterStars
            val counts = dao.playCountsOnce().associate { it.episodeKey to it.n }
            var pl = decodePlaylist(st.playlistJson).filter { lib.episodeByKey.containsKey(it) }.toMutableList()
            if (masteredKey != null) {
                val i = pl.indexOf(masteredKey)
                val series = lib.episodeByKey[masteredKey]?.let { ep ->
                    lib.series.first { it.language == ep.language && it.folder == ep.seriesFolder }
                }
                if (i >= 0) {
                    val nxt = series?.let { nextCandidate(it, pl, counts, ms) }
                    if (nxt != null) pl[i] = nxt else pl.removeAt(i)
                }
            }
            pl = pl.filter { (counts[it] ?: 0) < ms }.toMutableList()
            val order = playlistOrder(lib)
            var pass = 0
            while (pass < 20 && pl.size < st.playlistSize) {
                var added = false
                for (s in order) {
                    if (pl.size >= st.playlistSize) break
                    nextCandidate(s, pl, counts, ms)?.let { pl.add(it); added = true }
                }
                if (!added) break
                pass++
            }
            val fin = pl.take(st.playlistSize)
            settingsRepo.update { it.copy(playlistJson = Json.encodeToString(ListSerializer(String.serializer()), fin)) }
        }
    }

    // ---------------------------------------------------------- record play
    /** 播放结束/退出时调用；达阈值 → 记一次（本地 + 排队上传）。 */
    suspend fun recordPlay(ep: Episode, watchedSec: Int, durationSec: Int): PlayResult {
        val before = plays(ep.key)
        val ms = settings.value.masterStars
        dao.insertEvent(
            PlayEvent(
                id = UUID.randomUUID().toString(),
                language = ep.language, series = ep.seriesFolder, episodeKey = ep.key,
                episodeNum = ep.num, title = ep.title,
                watchedSec = watchedSec, durationSec = durationSec,
                counted = true, localDate = todayStr(),
                occurredAtMs = System.currentTimeMillis(),
            )
        )
        val after = before + 1
        val mastered = before < ms && after >= ms
        refreshPlaylist(if (mastered) ep.key else null)
        sync.requestSync()
        return PlayResult(before, after, mastered)
    }

    fun nextEpisode(key: String): Episode? {
        val ep = library.value.episodeByKey[key] ?: return null
        val series = library.value.series.firstOrNull {
            it.language == ep.language && it.folder == ep.seriesFolder
        } ?: return null
        val idx = series.episodes.indexOfFirst { it.key == key }
        return series.episodes.drop(idx + 1).firstOrNull { !isMastered(it.key) }
    }


    // ---------------------------------------------------------- playback progress / covers
    suspend fun loadPlaybackProgress(key: String): PlaybackProgress? =
        withContext(Dispatchers.IO) { dao.playbackProgress(key) }

    fun savePlaybackProgress(key: String, positionMs: Long, passWatchedMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (shouldClearResumePosition(positionMs, durationMs)) {
                dao.clearPlaybackProgress(key)
            } else if (positionMs > 1_500L) {
                dao.putPlaybackProgress(
                    PlaybackProgress(
                        episodeKey = key,
                        positionMs = positionMs,
                        passWatchedMs = passWatchedMs,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun clearPlaybackProgress(key: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.clearPlaybackProgress(key) }
    }

    fun coverForEpisode(key: String): String? = episodeCovers.value[key]

    // ---------------------------------------------------------- pairing
    suspend fun pairWithServer(url: String, code: String): String {
        return try {
            val res = withContext(Dispatchers.IO) { ApiClient(url).pair(code, android.os.Build.MODEL ?: "电视") }
            settingsRepo.update {
                val mode = res.settings.playbackMode
                val base = it.copy(
                    serverUrl = url.trimEnd('/'), deviceId = res.deviceId,
                    deviceToken = res.deviceToken, deviceName = res.name,
                    settingsVersion = res.settingsVersion,
                    dailyLimit = res.settings.dailyLimit, threshold = res.settings.threshold,
                    masterStars = res.settings.masterStars, playlistSize = res.settings.playlistSize,
                    wifiOnly = res.settings.wifiOnly,
                    allowDelete = res.settings.allowDelete,
                )
                if (!mode.isNullOrBlank()) base.withPlaybackMode(PlaybackMode.fromStorage(mode))
                else base.withPlaybackMode(PlaybackMode.fromAutoplay(res.settings.autoplay))
            }
            sync.requestSync()
            "配对成功！设备「${res.name}」已连接"
        } catch (e: Exception) {
            "配对失败：${e.message}"
        }
    }

    suspend fun updateSetting(block: (Settings) -> Settings) { settingsRepo.update(block) }
}
