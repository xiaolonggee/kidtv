package com.family.kidtv.storage

data class VolumeInfo(
    val id: String,
    val path: String,
    val label: String,
    val removable: Boolean,
    val state: String = "mounted",
    val topFolders: List<String> = emptyList(),
)
