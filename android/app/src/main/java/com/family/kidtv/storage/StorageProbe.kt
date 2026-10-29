package com.family.kidtv.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * 探测内置盘 / U 盘上的「启蒙视频」库根与可读子目录。
 * 行为对齐 v1.5.1（jadx StorageProbe），用干净 Kotlin 重写。
 */
object StorageProbe {
    private val LANG_FOLDER = setOf("英语", "法语", "english", "french", "英文", "法文")
    private val SKIP_DIR = setOf(".", "..", "Android", "LOST.DIR", "System Volume Information")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "m4v", "mov", "avi", "ts", "3gp")
    private const val LIB_NAME = "启蒙视频"

    fun listVolumes(context: Context): List<VolumeInfo> {
        val map = linkedMapOf<String, VolumeInfo>()
        fun add(id: String, path: String, label: String, removable: Boolean, state: String = "mounted") {
            if (path.isBlank()) return
            val prev = map[id]
            if (prev == null || (removable && !prev.removable)) {
                map[id] = VolumeInfo(id, path, label, removable, state)
            }
        }
        runCatching {
            val primary = Environment.getExternalStorageDirectory().absolutePath
            add("primary", primary, "内置存储", false)
        }
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.name !in setOf("emulated", "self")) {
                add("storage-${f.name}", f.absolutePath, f.name, removable = f.name != "emulated")
            }
        }
        for (p in listOf("/mnt/usb_storage", "/mnt/usb", "/storage/usbotg", "/storage/usb0", "/storage/usb1")) {
            val f = File(p)
            if (f.isDirectory) add("mnt-$p", f.absolutePath, f.name, true)
        }
        File("/mnt").listFiles()?.forEach { f ->
            if (!f.isDirectory) return@forEach
            when (f.name) {
                "usb_storage", "sdcard", "usbhost", "usb" ->
                    add("mnt-${f.name}", f.absolutePath, f.name, true)
                "media_rw" -> f.listFiles()?.filter { it.isDirectory }?.forEach { u ->
                    add("uuid-${u.name}", u.absolutePath, "USB ${u.name.take(8)}", true)
                }
            }
        }
        context.getExternalFilesDirs(null)?.forEachIndexed { i, dir ->
            if (dir == null) return@forEachIndexed
            volumeRootFromAppDir(dir)?.let { root ->
                add("extfiles-$i", root.absolutePath, root.name.ifBlank { "外置$i" }, removable = i > 0)
            }
        }
        return map.values.toList()
    }

    fun withTopLevelFolderNames(volumes: List<VolumeInfo>): List<VolumeInfo> =
        volumes.map { v ->
            val names = readTopLevelFolderNames(File(v.path))
            v.copy(topFolders = names)
        }

    fun readTopLevelFolderNames(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && it.name !in SKIP_DIR && !it.name.startsWith('.') }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    fun findLibraryRootFolders(context: Context, maxDepth: Int = 4): List<File> {
        val out = linkedSetOf<String>()
        for (vol in listVolumes(context)) {
            searchLibraryRoots(File(vol.path), 0, maxDepth, out)
        }
        return out.map { File(it) }.sortedBy { it.absolutePath }
    }

    fun findEnlightenmentFolders(context: Context, maxDepth: Int = 4): List<File> =
        findLibraryRootFolders(context, maxDepth).filter { it.name == LIB_NAME }

    private fun searchLibraryRoots(dir: File, depth: Int, maxDepth: Int, out: MutableSet<String>) {
        if (depth > maxDepth || !dir.isDirectory) return
        if (dir.name in SKIP_DIR || dir.name.startsWith('.')) return
        if (dir.name == LIB_NAME || looksLikeLanguageDir(dir)) {
            out += dir.absolutePath
        }
        if (depth == maxDepth) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) searchLibraryRoots(child, depth + 1, maxDepth, out)
        }
    }

    fun classifyLibraryDir(dir: File): LibraryLayout {
        if (!dir.isDirectory) return LibraryLayout.Unknown
        if (dir.name == LIB_NAME) return LibraryLayout.LanguageRoot(dir)
        if (looksLikeLanguageDir(dir)) {
            val parent = dir.parentFile
            return if (parent != null) LibraryLayout.Nested(dir, parent) else LibraryLayout.LanguageRoot(dir)
        }
        // 根下是否直接含语言文件夹
        val kids = dir.listFiles()?.filter { it.isDirectory }.orEmpty()
        if (kids.any { looksLikeLanguageDir(it) || it.name in LANG_FOLDER }) {
            return LibraryLayout.LanguageRoot(dir)
        }
        return LibraryLayout.Unknown
    }

    fun looksLikeLanguageDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (dir.name.lowercase() in LANG_FOLDER.map { it.lowercase() }) return true
        // 目录内有视频或系列子目录
        val kids = dir.listFiles() ?: return false
        val hasVideo = kids.any { it.isFile && it.extension.lowercase() in VIDEO_EXT }
        val hasSeries = kids.any { it.isDirectory && (it.listFiles()?.any { f ->
            f.isFile && f.extension.lowercase() in VIDEO_EXT
        } == true) }
        return hasVideo || hasSeries
    }

    fun browseStartPaths(context: Context): List<File> {
        val roots = mutableListOf<File>()
        findEnlightenmentFolders(context, 3).forEach { roots += it }
        listVolumes(context).forEach { roots += File(it.path) }
        return roots.distinctBy { it.absolutePath }.filter { it.exists() }
    }

    fun listReadableChildren(dir: File): List<File> {
        if (!dir.isDirectory || !dir.canRead()) return emptyList()
        return dir.listFiles()
            ?.filter { it.canRead() && it.name !in SKIP_DIR && !it.name.startsWith('.') }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
    }

    private fun volumeRootFromAppDir(appFiles: File): File? {
        // .../Android/data/<pkg>/files → climb to volume root
        var f: File? = appFiles
        while (f != null) {
            if (f.name == "Android" && f.parentFile != null) return f.parentFile
            f = f.parentFile
        }
        return null
    }
}
