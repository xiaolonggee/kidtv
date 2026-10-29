package com.family.kidtv.storage

/**
 * 媒体库目录形态（相对「启蒙视频」根）。
 * LanguageRoot: 根下直接是「英语/法语」等语言文件夹。
 * Nested: 根本身是某语言文件夹（其父级才是库根）。
 * Unknown: 尚未识别。
 */
sealed class LibraryLayout {
    data class LanguageRoot(val root: java.io.File) : LibraryLayout()
    data class Nested(val languageDir: java.io.File, val libraryRoot: java.io.File) : LibraryLayout()
    data object Unknown : LibraryLayout()
}
