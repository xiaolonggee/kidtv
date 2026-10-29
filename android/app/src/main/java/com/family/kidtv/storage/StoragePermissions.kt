package com.family.kidtv.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

object StoragePermissions {
    fun readPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun status(context: Context): PermissionStatus {
        val hasVideo = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasLegacy = Build.VERSION.SDK_INT >= 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val manager = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else true
        val summary = buildString {
            append(if (hasVideo || hasLegacy) "可读视频 ✓" else "可读视频 ✗")
            append(" · ")
            append(if (manager) "所有文件 ✓" else "所有文件 ✗")
        }
        return PermissionStatus(hasVideo, hasLegacy, manager, summary)
    }

    fun canManageAllFilesIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 30 &&
            manageAllFilesIntent(context).resolveActivity(context.packageManager) != null

    fun manageAllFilesIntent(context: Context): Intent {
        val pkg = context.packageName
        return if (Build.VERSION.SDK_INT >= 30) {
            Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$pkg")
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            }
        }
    }
}
