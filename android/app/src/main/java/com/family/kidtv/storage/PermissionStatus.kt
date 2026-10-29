package com.family.kidtv.storage

data class PermissionStatus(
    val hasReadMediaVideo: Boolean,
    val hasReadExternalStorage: Boolean,
    val isExternalStorageManager: Boolean,
    val summary: String,
) {
    val canReadVideo: Boolean get() = hasReadMediaVideo || hasReadExternalStorage
}
