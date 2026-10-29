package com.family.kidtv.data

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 后台抽帧封面队列。对齐 v1.5.1 ThumbnailService：
 * covers: episodeKey → file://...jpg
 * schedule() after rescan; preloadFromDb on start.
 */
class ThumbnailService(
    private val context: Context,
    private val dao: KidTvDao,
    private val scope: CoroutineScope,
) {
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers: StateFlow<Map<String, String>> = _covers.asStateFlow()

    private val queue = ArrayDeque<Episode>()
    private val queuedKeys = mutableSetOf<String>()
    private val mutex = Mutex()
    private var drainJob: Job? = null

    fun preloadFromDb() {
        scope.launch(Dispatchers.IO) {
            val rows = runCatching { dao.allThumbnailCache() }.getOrDefault(emptyList())
            val map = LinkedHashMap<String, String>()
            for (row in rows) {
                val f = File(row.cacheFilePath)
                if (f.isFile && f.length() > 500) {
                    map[row.episodeKey] = Uri.fromFile(f).toString()
                }
            }
            if (map.isNotEmpty()) {
                _covers.value = _covers.value + map
            }
        }
    }

    fun schedule(library: Library) {
        scope.launch {
            mutex.withLock {
                for (ep in library.series.flatMap { it.episodes }) {
                    if (ep.key in _covers.value) continue
                    if (ep.key in queuedKeys) continue
                    queuedKeys += ep.key
                    queue.addLast(ep)
                }
            }
            ensureDrain()
        }
    }

    fun enqueue(episode: Episode) {
        scope.launch {
            mutex.withLock {
                if (episode.key in _covers.value || episode.key in queuedKeys) return@withLock
                queuedKeys += episode.key
                queue.addFirst(episode)
            }
            ensureDrain()
        }
    }

    private fun ensureDrain() {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch(Dispatchers.IO) { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val ep = mutex.withLock {
                val next = queue.removeFirstOrNull()
                if (next != null) queuedKeys.remove(next.key)
                next
            } ?: break
            try {
                val modified = ThumbnailGenerator.resolveModifiedMs(context, ep)
                val existing = dao.thumbnailCache(ep.key)
                if (existing != null &&
                    existing.sizeBytes == ep.sizeBytes &&
                    existing.modifiedMs == modified &&
                    File(existing.cacheFilePath).isFile
                ) {
                    publish(ep.key, existing.cacheFilePath)
                    continue
                }
                val uriStr = withContext(Dispatchers.IO) {
                    ThumbnailGenerator.generate(context, ep, modified)
                } ?: continue
                val path = Uri.parse(uriStr).path ?: continue
                dao.putThumbnailCache(
                    EpisodeThumbnailCache(
                        episodeKey = ep.key,
                        videoUri = ep.uri,
                        sizeBytes = ep.sizeBytes,
                        modifiedMs = modified,
                        cacheFilePath = path,
                    )
                )
                publish(ep.key, path)
            } catch (_: Exception) {
                // skip bad files; continue queue
            }
        }
    }

    private fun publish(episodeKey: String, path: String) {
        val uri = Uri.fromFile(File(path)).toString()
        _covers.value = _covers.value + (episodeKey to uri)
    }
}
