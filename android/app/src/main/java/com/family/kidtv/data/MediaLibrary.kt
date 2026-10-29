package com.family.kidtv.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 库中的一集。key = 语言/系列文件夹/文件名（跨扫描方式稳定）。 */
data class Episode(
    val key: String,
    val language: String,
    val seriesFolder: String,
    val num: Int,
    val title: String,
    val cnTitle: String,
    val filename: String,
    val uri: String,
    val sizeBytes: Long,
    val durationMs: Long,
)

data class Series(
    val language: String,
    val folder: String,
    val displayName: String,
    val cnName: String,
    val coverUri: String?,
    val fav: Boolean,
    val episodes: List<Episode>,
)

data class Library(
    val root: String,                 // 展示用根路径
    val languages: List<String>,
    val series: List<Series>,
) {
    val episodeByKey: Map<String, Episode> by lazy {
        series.flatMap { it.episodes }.associateBy { it.key }
    }
    companion object { val EMPTY = Library("", emptyList(), emptyList()) }
}

private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "m4v", "mov", "avi", "ts", "3gp")
private val EP_RE = Regex("""^(\d{1,4})[\s._\-]*(.*)$""")
private val LANG_ALIAS = mapOf("english" to "英语", "french" to "法语", "英文" to "英语", "法文" to "法语")
const val LIB_DIR_NAME = "启蒙视频"

private fun canonicalLanguage(folder: String) = LANG_ALIAS[folder.lowercase()] ?: folder

/** info.json（可选）：{"name":"Mouse Boo","cn":"小鼠波波","fav":true,"episodes":{"1":"认颜色"}} */
private data class InfoJson(val name: String?, val cn: String?, val fav: Boolean, val epCn: Map<Int, String>)

private fun parseInfoJson(text: String): InfoJson = try {
    val o = Json.parseToJsonElement(text).jsonObject
    InfoJson(
        name = o["name"]?.jsonPrimitive?.content,
        cn = o["cn"]?.jsonPrimitive?.content,
        fav = o["fav"]?.jsonPrimitive?.content == "true",
        epCn = o["episodes"]?.jsonObject?.mapNotNull { (k, v) ->
            k.trimStart('0').toIntOrNull()?.let { it to v.jsonPrimitive.content }
        }?.toMap() ?: emptyMap(),
    )
} catch (e: Exception) { InfoJson(null, null, false, emptyMap()) }

/** 统一抽象 SAF 文档与直接文件，两种扫描共用一套逻辑。 */
private interface Node {
    val name: String
    val isDirectory: Boolean
    val size: Long
    val uri: String
    fun children(): List<Node>
    fun readText(context: Context): String?
}

private class SafNode(val doc: DocumentFile) : Node {
    override val name get() = doc.name ?: ""
    override val isDirectory get() = doc.isDirectory
    override val size get() = doc.length()
    override val uri get() = doc.uri.toString()
    override fun children() = doc.listFiles().map { SafNode(it) }
    override fun readText(context: Context) = try {
        context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().decodeToString() }
    } catch (e: Exception) { null }
}

private class FileNode(val file: File) : Node {
    override val name get() = file.name
    override val isDirectory get() = file.isDirectory
    override val size get() = file.length()
    override val uri get() = Uri.fromFile(file).toString()
    override fun children() = (file.listFiles() ?: emptyArray()).map { FileNode(it) }
    override fun readText(context: Context) = try { file.readText() } catch (e: Exception) { null }
}

class MediaLibrary(private val context: Context, private val dao: KidTvDao) {

    /** 扫描：优先 libraryPath（直接路径），其次 SAF libraryUri，否则常见回退路径。 */
    suspend fun scan(libraryUri: String, libraryPath: String = ""): ScanOutcome {
        val durations = dao.durationCache().associateBy { it.episodeKey }
        val tried = mutableListOf<String>()
        try {
            if (libraryPath.isNotEmpty()) {
                tried += "path:$libraryPath"
                val f = File(libraryPath)
                if (f.isDirectory) {
                    val root = descendToLibRoot(FileNode(f))
                    val lib = scanRoot(root, f.absolutePath, durations)
                    return ScanOutcome(lib, "path", tried)
                }
                return ScanOutcome(Library.EMPTY, "error", tried, error = "路径不是目录: $libraryPath")
            }
            if (libraryUri.isNotEmpty()) {
                tried += "saf:$libraryUri"
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(libraryUri))
                if (doc == null) {
                    return ScanOutcome(Library.EMPTY, "error", tried, error = "SAF URI 无效")
                }
                val root = descendToLibRoot(SafNode(doc))
                val lib = scanRoot(root, displayRoot(doc), durations)
                return ScanOutcome(lib, "saf", tried)
            }
            val candidates = directCandidates()
            for (f in candidates) {
                tried += f.absolutePath
                if (f.isDirectory) {
                    val lib = scanRoot(FileNode(f), f.absolutePath, durations)
                    return ScanOutcome(lib, "auto", tried, autoDetectedPath = f.absolutePath)
                }
            }
            return ScanOutcome(Library.EMPTY, "empty", tried)
        } catch (t: Throwable) {
            return ScanOutcome(Library.EMPTY, "error", tried, error = t.message ?: t::class.java.simpleName)
        }
    }

    private fun displayRoot(doc: DocumentFile): String =
        doc.uri.lastPathSegment?.substringAfterLast(':')?.let { "/$it" } ?: doc.uri.toString()

    /** 用户可能选了 U 盘根目录，也可能直接选了「启蒙视频」文件夹。 */
    private fun descendToLibRoot(root: Node): Node =
        if (root.name == LIB_DIR_NAME) root
        else root.children().firstOrNull { it.isDirectory && it.name == LIB_DIR_NAME } ?: root

    private fun directCandidates(): List<File> {
        val out = mutableListOf(
            File("/storage/emulated/0/$LIB_DIR_NAME"),
            File("/sdcard/$LIB_DIR_NAME"),
        )
        File("/storage").listFiles()?.forEach { vol ->
            if (vol.isDirectory && vol.name !in setOf("emulated", "self")) {
                out += File(vol, LIB_DIR_NAME)
            }
        }
        return out
    }

    private suspend fun scanRoot(root: Node, rootPath: String, durations: Map<String, DurationCache>): Library {
        val seriesList = mutableListOf<Series>()
        val languages = mutableListOf<String>()
        val langDirs = root.children().filter { it.isDirectory }
            .sortedBy { langOrder(canonicalLanguage(it.name)) }
        for (langDir in langDirs) {
            val lang = canonicalLanguage(langDir.name)
            if (lang !in languages) languages += lang
            for (seriesDir in langDir.children().filter { it.isDirectory }.sortedBy { it.name }) {
                val kids = seriesDir.children()
                val info = kids.firstOrNull { it.name.equals("info.json", true) }
                    ?.readText(context)?.let { parseInfoJson(it) }
                val cover = kids.firstOrNull {
                    it.name.equals("cover.jpg", true) || it.name.equals("cover.png", true)
                }
                val eps = kids.filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in VIDEO_EXT }
                    .mapNotNull { f -> parseEpisode(f, lang, seriesDir.name, info?.epCn ?: emptyMap(), durations) }
                    .sortedWith(compareBy({ it.num }, { it.filename }))
                if (eps.isNotEmpty()) {
                    seriesList += Series(
                        language = lang,
                        folder = seriesDir.name,
                        displayName = info?.name ?: seriesDir.name,
                        cnName = info?.cn ?: "",
                        coverUri = cover?.uri,
                        fav = info?.fav == true,
                        episodes = eps,
                    )
                }
            }
        }
        return Library(rootPath, languages, seriesList)
    }

    private fun langOrder(lang: String) = when (lang) { "英语" -> 0; "法语" -> 1; else -> 2 }

    private suspend fun parseEpisode(
        f: Node, lang: String, seriesFolder: String,
        epCn: Map<Int, String>, durations: Map<String, DurationCache>,
    ): Episode? {
        val base = f.name.substringBeforeLast('.')
        val m = EP_RE.find(base) ?: return null       // 需要集号前缀
        val num = m.groupValues[1].toIntOrNull() ?: return null
        val title = m.groupValues[2].trim().ifEmpty { "第${num}集" }
        val key = "$lang/$seriesFolder/${f.name}"
        val cached = durations[key]
        val durationMs = if (cached != null && cached.sizeBytes == f.size) cached.durationMs
        else readDuration(f.uri).also {
            if (it > 0) dao.putDuration(DurationCache(key, f.size, it))
        }
        return Episode(key, lang, seriesFolder, num, title, epCn[num] ?: "", f.name, f.uri, f.size, durationMs)
    }

    private fun readDuration(uri: String): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L } finally {
            try { r.release() } catch (e: Exception) {}
        }
    }

    // ------------------------------------------------ 同步下载的写入目标
    /** 找到/创建 语言/系列 目录并返回要写入的目标；仅 SAF 模式支持可靠写入。 */
    fun createTargetFile(libraryUri: String, language: String, series: String, filename: String): Uri? {
        if (libraryUri.isEmpty()) {
            // 直接路径回退：尝试内置存储（Android 10+ 对任意目录的直写可能失败）
            val root = directCandidates().firstOrNull { it.isDirectory }
                ?: File("/storage/emulated/0/$LIB_DIR_NAME").also { it.mkdirs() }
            val dir = File(root, "$language/$series").also { it.mkdirs() }
            return if (dir.isDirectory) Uri.fromFile(File(dir, filename)) else null
        }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(libraryUri)) ?: return null
        var dir = if (tree.name == LIB_DIR_NAME) tree
        else tree.findFile(LIB_DIR_NAME)?.takeIf { it.isDirectory } ?: tree.createDirectory(LIB_DIR_NAME) ?: return null
        for (seg in listOf(language, series)) {
            dir = dir.findFile(seg)?.takeIf { it.isDirectory } ?: dir.createDirectory(seg) ?: return null
        }
        dir.findFile(filename)?.delete()
        return dir.createFile("video/mp4", filename)?.uri
    }

    fun findExistingFile(libraryUri: String, language: String, series: String, filename: String): Uri? {
        if (libraryUri.isEmpty()) {
            val f = directCandidates().firstOrNull { it.isDirectory }?.let { File(it, "$language/$series/$filename") }
            return if (f?.isFile == true) Uri.fromFile(f) else null
        }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(libraryUri)) ?: return null
        var dir: DocumentFile? = if (tree.name == LIB_DIR_NAME) tree else tree.findFile(LIB_DIR_NAME) ?: tree
        for (seg in listOf(language, series)) dir = dir?.findFile(seg)
        return dir?.findFile(filename)?.uri
    }

    fun deleteFile(uri: Uri): Boolean = try {
        if (uri.scheme == "file") File(uri.path!!).delete()
        else DocumentFile.fromSingleUri(context, uri)?.delete() == true
    } catch (e: Exception) { false }
}
