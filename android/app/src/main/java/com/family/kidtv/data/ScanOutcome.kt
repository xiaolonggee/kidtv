package com.family.kidtv.data

/**
 * 一次媒体库扫描结果（给家长设置 / 诊断展示）。
 * source: path | saf | auto | empty | error
 */
data class ScanOutcome(
    val library: Library,
    val source: String,
    val triedPaths: List<String> = emptyList(),
    val error: String? = null,
    val autoDetectedPath: String? = null,
) {
    val summary: String
        get() = buildString {
            append("来源=$source")
            append(" · 系列=${library.series.size}")
            append(" · 集数=${library.episodeByKey.size}")
            if (!autoDetectedPath.isNullOrBlank()) append(" · 自动发现=$autoDetectedPath")
            if (!error.isNullOrBlank()) append(" · 错误=$error")
            if (triedPaths.isNotEmpty() && library.series.isEmpty()) {
                append(" · 尝试=${triedPaths.take(4).joinToString(" | ")}")
            }
        }

    companion object {
        val EMPTY = ScanOutcome(Library.EMPTY, "empty")
    }
}
