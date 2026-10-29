package com.family.kidtv.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 未捕获异常落盘到 filesDir/crashes/，家长诊断页可读最近一条。
 */
object CrashReporter {
    private const val TAG = "KidTvCrash"
    private const val DIR = "crashes"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { writeCrash(app, t, e) }
            prev?.uncaughtException(t, e)
        }
    }

    fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$ts.txt")
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val body = buildString {
            appendLine("time=$ts")
            appendLine("thread=${thread.name}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")
            appendLine("app=${context.packageName}")
            appendLine()
            append(sw.toString())
        }
        file.writeText(body)
        Log.e(TAG, "crash written to ${file.absolutePath}", error)
        // 只保留最近 10 个
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
    }

    fun latestCrash(context: Context): File? =
        File(context.filesDir, DIR).listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

    fun latestCrashText(context: Context, maxChars: Int = 8000): String {
        val f = latestCrash(context) ?: return "（无崩溃记录）"
        val text = runCatching { f.readText() }.getOrElse { it.message ?: "read failed" }
        return if (text.length <= maxChars) text else text.take(maxChars) + "\n…(truncated)"
    }
}
