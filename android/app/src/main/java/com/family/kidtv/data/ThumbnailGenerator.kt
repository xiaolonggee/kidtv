package com.family.kidtv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

/** 从视频抽帧生成 episode 封面 JPEG。对齐 v1.5.1 ThumbnailGenerator。 */
object ThumbnailGenerator {
    private const val MAX_WIDTH = 480
    private const val JPEG_QUALITY = 82

    fun cacheKey(episodeKey: String, sizeBytes: Long, modifiedMs: Long): String {
        val raw = "$episodeKey|$sizeBytes|$modifiedMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun cacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "episode_thumbs").also { it.mkdirs() }
        return File(dir, "$key.jpg")
    }

    fun generate(context: Context, episode: Episode, modifiedMs: Long): String? {
        val key = cacheKey(episode.key, episode.sizeBytes, modifiedMs)
        val out = cacheFile(context, key)
        if (out.isFile && out.length() > 500) return Uri.fromFile(out).toString()
        val frame = extractFrame(context, episode.uri, episode.durationMs) ?: return null
        val scaled = scaleDown(frame, MAX_WIDTH)
        if (scaled !== frame) frame.recycle()
        FileOutputStream(out).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        scaled.recycle()
        return Uri.fromFile(out).toString()
    }

    fun resolveModifiedMs(context: Context, episode: Episode): Long {
        val uri = Uri.parse(episode.uri)
        if (uri.scheme == "file") {
            val path = uri.path ?: return 0L
            val f = File(path)
            return if (f.isFile) f.lastModified() else 0L
        }
        return try {
            DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractFrame(context: Context, uri: String, durationMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            var dur = durationMs
            if (dur <= 0) {
                dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 60_000L
            }
            val candidates = listOf(
                (dur * 0.15).toLong(),
                (dur * 0.20).toLong(),
                min(30_000L, (dur * 0.18).toLong()),
                (dur * 0.25).toLong(),
            ).distinct().map { max(0L, it) * 1000L }
            for (t in candidates) {
                val bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                if (isMostlyBlack(bmp)) {
                    bmp.recycle()
                } else {
                    return bmp
                }
            }
            retriever.getFrameAtTime(candidates.first(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun isMostlyBlack(bmp: Bitmap): Boolean {
        val w = min(bmp.width, 32)
        val h = min(bmp.height, 32)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        var sum = 0L
        for (x in 0 until w) {
            for (y in 0 until h) {
                val p = small.getPixel(x, y)
                sum += Color.red(p) + Color.green(p) + Color.blue(p)
            }
        }
        if (small !== bmp) small.recycle()
        return sum.toDouble() / (w * h * 3) < 18.0
    }

    private fun scaleDown(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val h = max(1, (src.height * (maxWidth.toFloat() / src.width)).toInt())
        return Bitmap.createScaledBitmap(src, maxWidth, h, true)
    }
}
