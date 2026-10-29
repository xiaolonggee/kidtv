package com.family.kidtv.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.family.kidtv.data.KidTvDao
import com.family.kidtv.data.MediaLibrary
import com.family.kidtv.data.Settings
import com.family.kidtv.data.SettingsRepo
import com.family.kidtv.data.SyncedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest

data class SyncStatus(
    val configured: Boolean = false,
    val online: Boolean = false,
    val message: String = "未配置服务器",
    val pendingEvents: Int = 0,
    val downloading: String? = null,     // 当前下载中的文件
    val downloadPct: Int = 0,
    val lastError: String? = null,
)

/**
 * 同步循环：
 *  - 事件实时上传（失败指数退避重试，幂等 eventId）
 *  - 定期拉取服务器设置（版本号比较）
 *  - 拉取内容清单，下载新/变更文件（断点续传 + sha256 校验）写入 U 盘
 */
class SyncManager(
    private val context: Context,
    private val dao: KidTvDao,
    private val settingsRepo: SettingsRepo,
    private val library: MediaLibrary,
) {
    val status = MutableStateFlow(SyncStatus())
    val libraryChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private var backoffSec = 5L

    fun requestSync() { kick.trySend(Unit) }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            var contentCooldown = 0L
            while (true) {
                withTimeoutOrNull(30_000) { kick.receive() }
                val s = settingsRepo.flow.first()
                if (s.serverUrl.isEmpty() || s.deviceToken.isEmpty()) {
                    status.value = SyncStatus(configured = false, message = "未配置服务器")
                    continue
                }
                val api = ApiClient(s.serverUrl, s.deviceToken)
                try {
                    pushEvents(api)
                    pullSettings(api, s)
                    val now = System.currentTimeMillis()
                    if (now > contentCooldown) {
                        contentCooldown = now + 5 * 60_000
                        syncContent(api, s)
                    }
                    backoffSec = 5
                    status.value = status.value.copy(
                        configured = true, online = true, lastError = null,
                        message = "已连接 ${s.serverUrl}",
                        pendingEvents = dao.unsyncedEvents().size,
                        downloading = null,
                    )
                } catch (e: Exception) {
                    status.value = status.value.copy(
                        configured = true, online = false,
                        message = "连接失败，${backoffSec}s 后重试",
                        lastError = e.message, downloading = null,
                    )
                    kotlinx.coroutines.delay(backoffSec * 1000)
                    backoffSec = (backoffSec * 2).coerceAtMost(300)
                    requestSync()
                }
            }
        }
    }

    private suspend fun pushEvents(api: ApiClient) {
        while (true) {
            val batch = dao.unsyncedEvents()
            if (batch.isEmpty()) return
            val res = withContext(Dispatchers.IO) {
                api.postEvents(batch.map {
                    EventDto(
                        eventId = it.id, type = it.type, language = it.language,
                        series = it.series, episodeKey = it.episodeKey,
                        episodeNum = it.episodeNum, title = it.title,
                        watchedSec = it.watchedSec, durationSec = it.durationSec,
                        counted = it.counted, localDate = it.localDate,
                        occurredAtMs = it.occurredAtMs,
                    )
                })
            }
            dao.markSynced(res.accepted + res.duplicates)
            if (batch.size < 50) return
        }
    }

    private suspend fun pullSettings(api: ApiClient, local: Settings) {
        val remote = withContext(Dispatchers.IO) { api.getSettings() }
        if (remote.version > local.settingsVersion) {
            settingsRepo.update {
                val mode = remote.settings.playbackMode
                val base = it.copy(
                    dailyLimit = remote.settings.dailyLimit,
                    threshold = remote.settings.threshold,
                    masterStars = remote.settings.masterStars,
                    playlistSize = remote.settings.playlistSize,
                    wifiOnly = remote.settings.wifiOnly,
                    allowDelete = remote.settings.allowDelete,
                    settingsVersion = remote.version,
                )
                if (!mode.isNullOrBlank()) {
                    base.withPlaybackMode(com.family.kidtv.data.PlaybackMode.fromStorage(mode))
                } else {
                    base.withPlaybackMode(com.family.kidtv.data.PlaybackMode.fromAutoplay(remote.settings.autoplay))
                }
            }
        }
    }

    private fun networkAllowed(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun syncContent(api: ApiClient, s: Settings) {
        val manifest = withContext(Dispatchers.IO) { api.getManifest() }
        val synced = dao.syncedFiles().associateBy { it.contentId }
        var changed = false
        val reports = mutableListOf<SyncStateItem>()

        for (item in manifest.items) {
            if (item.deleted) {
                if (s.allowDelete && synced.containsKey(item.id)) {
                    library.findExistingFile(s.libraryUri, item.language, item.series, item.filename)
                        ?.let { library.deleteFile(it) }
                    dao.removeSyncedFile(item.id)
                    reports += SyncStateItem(item.id, "deleted")
                    changed = true
                }
                continue
            }
            val existing = library.findExistingFile(s.libraryUri, item.language, item.series, item.filename)
            val rec = synced[item.id]
            if (existing != null && rec != null && rec.sha256 == item.sha256) {
                if (!rec.reported) reports += SyncStateItem(item.id, "synced")
                continue
            }
            if (existing != null && rec == null) {
                // 文件已由用户手动拷贝到 U 盘：直接登记，不重复下载
                dao.putSyncedFile(SyncedFile(item.id, item.sha256))
                reports += SyncStateItem(item.id, "synced")
                continue
            }
            if (!networkAllowed(s.wifiOnly)) {
                status.value = status.value.copy(message = "等待 Wi-Fi 后继续下载")
                continue
            }
            if (downloadItem(api, s, item)) {
                dao.putSyncedFile(SyncedFile(item.id, item.sha256))
                reports += SyncStateItem(item.id, "synced")
                changed = true
            }
        }
        if (reports.isNotEmpty()) {
            withContext(Dispatchers.IO) { api.postSyncState(reports) }
            dao.markReported(reports.filter { it.status == "synced" }.map { it.contentId })
        }
        if (changed) libraryChanged.tryEmit(Unit)
    }

    /** 下载到缓存（断点续传 + 边下边算 sha256），校验后经 SAF 写入媒体库。 */
    private suspend fun downloadItem(api: ApiClient, s: Settings, item: ManifestItem): Boolean {
        val tmp = File(context.cacheDir, "dl-${item.id}.part")
        try {
            var offset = tmp.length()
            if (offset > item.size) { tmp.delete(); offset = 0 }
            if (offset < item.size) {
                withContext(Dispatchers.IO) {
                    api.downloadFile(item.id, offset).use { res ->
                        if (res.code != 206 && offset > 0) { tmp.delete(); offset = 0 }
                        val body = res.body ?: throw ApiException(500, "empty body")
                        java.io.FileOutputStream(tmp, offset > 0).use { out ->
                            val buf = ByteArray(256 * 1024)
                            var done = offset
                            val input = body.byteStream()
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                status.value = status.value.copy(
                                    downloading = "${item.series}/${item.filename}",
                                    downloadPct = if (item.size > 0) (done * 100 / item.size).toInt() else 0,
                                    message = "正在下载新内容…",
                                )
                            }
                        }
                    }
                }
            }
            if (item.size > 0 && tmp.length() != item.size) return false
            if (item.sha256.isNotEmpty() && sha256(tmp) != item.sha256) { tmp.delete(); return false }

            val target = library.createTargetFile(s.libraryUri, item.language, item.series, item.filename)
                ?: run {
                    status.value = status.value.copy(lastError = "无法写入媒体库（请在家长设置中选择 U 盘目录）")
                    return false
                }
            withContext(Dispatchers.IO) {
                val out = if (target.scheme == "file") java.io.FileOutputStream(target.path!!)
                else context.contentResolver.openOutputStream(target)!!
                out.use { o -> tmp.inputStream().use { it.copyTo(o, 256 * 1024) } }
            }
            tmp.delete()
            return true
        } catch (e: Exception) {
            status.value = status.value.copy(lastError = "下载失败：${e.message}")
            return false
        } finally {
            status.value = status.value.copy(downloading = null, downloadPct = 0)
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
