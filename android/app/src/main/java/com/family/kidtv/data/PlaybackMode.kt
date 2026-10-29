package com.family.kidtv.data

/**
 * 播完后默认行为（与服务器 playbackMode 字段对齐）。
 * storageKey 写入 DataStore / 服务器 JSON。
 */
enum class PlaybackMode(val storageKey: String, val labelZh: String, val labelEn: String) {
    SEQUENTIAL("sequential", "顺序连播", "Next episode"),
    LOOP("loop", "单集循环", "Replay"),
    STOP("stop", "播完停止", "Stop");

    companion object {
        fun fromStorage(key: String?): PlaybackMode =
            entries.firstOrNull { it.storageKey == key } ?: SEQUENTIAL

        /** 旧版只有 autoplay 布尔时的兼容映射。 */
        fun fromAutoplay(autoplay: Boolean): PlaybackMode =
            if (autoplay) SEQUENTIAL else STOP
    }
}

/** 单集播完后的下一步（PlayerScreen 庆祝层之后）。 */
sealed class AfterEpisodeAction {
    data class NextCountdown(val next: Episode) : AfterEpisodeAction()
    data object Replay : AfterEpisodeAction()
    data object Stop : AfterEpisodeAction()
}
