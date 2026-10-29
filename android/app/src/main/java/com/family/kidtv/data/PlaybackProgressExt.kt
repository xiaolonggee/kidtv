package com.family.kidtv.data

const val RESUME_CLEAR_TAIL_MS = 5_000L
const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
const val PLAYER_CHROME_HIDE_MS = 4_000L
const val SEEK_BASE_STEP_MS = 10_000L
const val SEEK_MAX_STEP_MS = 60_000L

/** 距片尾不足 5 秒则清除续播点（下次从头）。 */
fun shouldClearResumePosition(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= (durationMs - RESUME_CLEAR_TAIL_MS).coerceAtLeast(0)

fun seekStepMs(keyRepeatCount: Int): Long =
    ((keyRepeatCount.coerceAtMost(20) / 4).coerceAtMost(5) + 1) * SEEK_BASE_STEP_MS

fun clampSeekPosition(targetMs: Long, durationMs: Long): Long {
    val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return targetMs.coerceIn(0L, max)
}
