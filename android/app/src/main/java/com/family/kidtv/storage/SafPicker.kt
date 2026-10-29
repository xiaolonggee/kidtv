package com.family.kidtv.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher

object SafPicker {
    fun isDocumentTreeAvailable(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addCategory(Intent.CATEGORY_DEFAULT)
        return intent.resolveActivity(context.packageManager) != null
    }

    fun launchTree(
        context: Context,
        launcher: ActivityResultLauncher<Uri?>,
        onUnavailable: () -> Unit,
    ) {
        if (!isDocumentTreeAvailable(context)) {
            onUnavailable(); return
        }
        try {
            launcher.launch(null)
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        } catch (_: IllegalStateException) {
            onUnavailable()
        } catch (_: Exception) {
            onUnavailable()
        }
    }
}
