package com.family.kidtv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("kidtv_settings")

/** 应用设置（含服务器同步下发的部分）。对齐 v1.5.1：playbackMode + libraryPath。 */
data class Settings(
    val dailyLimit: Int = 2,
    val threshold: Int = 75,
    val masterStars: Int = 5,
    val playlistSize: Int = 4,
    val autoplay: Boolean = true,              // 兼容旧字段；与 playbackMode 同步
    val playbackMode: String = PlaybackMode.SEQUENTIAL.storageKey,
    val wifiOnly: Boolean = true,
    val allowDelete: Boolean = false,
    val serverUrl: String = "",
    val deviceId: String = "",
    val deviceToken: String = "",
    val deviceName: String = "",
    val settingsVersion: Int = 0,
    val libraryUri: String = "",               // SAF 树 URI
    val libraryPath: String = "",              // 直接文件系统路径（FolderBrowser / 所有文件访问）
    val playlistJson: String = "[]",
    /** 今日额外允许集数（家长「再看一集」一次性；按日期重置，不改 dailyLimit）。 */
    val extraEpisodesDate: String = "",
    val extraEpisodes: Int = 0,
) {
    fun resolvedPlaybackMode(): PlaybackMode =
        if (playbackMode.isNotBlank()) PlaybackMode.fromStorage(playbackMode)
        else PlaybackMode.fromAutoplay(autoplay)

    fun withPlaybackMode(mode: PlaybackMode): Settings = copy(
        playbackMode = mode.storageKey,
        autoplay = mode == PlaybackMode.SEQUENTIAL,
    )
}

class SettingsRepo(private val context: Context) {
    private object K {
        val dailyLimit = intPreferencesKey("dailyLimit")
        val threshold = intPreferencesKey("threshold")
        val masterStars = intPreferencesKey("masterStars")
        val playlistSize = intPreferencesKey("playlistSize")
        val autoplay = booleanPreferencesKey("autoplay")
        val playbackMode = stringPreferencesKey("playbackMode")
        val wifiOnly = booleanPreferencesKey("wifiOnly")
        val allowDelete = booleanPreferencesKey("allowDelete")
        val serverUrl = stringPreferencesKey("serverUrl")
        val deviceId = stringPreferencesKey("deviceId")
        val deviceToken = stringPreferencesKey("deviceToken")
        val deviceName = stringPreferencesKey("deviceName")
        val settingsVersion = intPreferencesKey("settingsVersion")
        val libraryUri = stringPreferencesKey("libraryUri")
        val libraryPath = stringPreferencesKey("libraryPath")
        val playlistJson = stringPreferencesKey("playlistJson")
        val extraEpisodesDate = stringPreferencesKey("extraEpisodesDate")
        val extraEpisodes = intPreferencesKey("extraEpisodes")
    }

    private fun fromPrefs(p: androidx.datastore.preferences.core.Preferences): Settings {
        val autoplay = p[K.autoplay] ?: true
        val modeKey = p[K.playbackMode]
        val mode = if (modeKey.isNullOrBlank()) {
            PlaybackMode.fromAutoplay(autoplay).storageKey
        } else modeKey
        return Settings(
            dailyLimit = p[K.dailyLimit] ?: 2,
            threshold = p[K.threshold] ?: 75,
            masterStars = p[K.masterStars] ?: 5,
            playlistSize = p[K.playlistSize] ?: 4,
            autoplay = autoplay,
            playbackMode = mode,
            wifiOnly = p[K.wifiOnly] ?: true,
            allowDelete = p[K.allowDelete] ?: false,
            serverUrl = p[K.serverUrl] ?: "",
            deviceId = p[K.deviceId] ?: "",
            deviceToken = p[K.deviceToken] ?: "",
            deviceName = p[K.deviceName] ?: "",
            settingsVersion = p[K.settingsVersion] ?: 0,
            libraryUri = p[K.libraryUri] ?: "",
            libraryPath = p[K.libraryPath] ?: "",
            playlistJson = p[K.playlistJson] ?: "[]",
            extraEpisodesDate = p[K.extraEpisodesDate] ?: "",
            extraEpisodes = p[K.extraEpisodes] ?: 0,
        )
    }

    val flow: Flow<Settings> = context.store.data.map { fromPrefs(it) }

    suspend fun update(block: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = block(fromPrefs(p))
            p[K.dailyLimit] = s.dailyLimit
            p[K.threshold] = s.threshold
            p[K.masterStars] = s.masterStars
            p[K.playlistSize] = s.playlistSize
            p[K.autoplay] = s.autoplay
            p[K.playbackMode] = s.playbackMode
            p[K.wifiOnly] = s.wifiOnly
            p[K.allowDelete] = s.allowDelete
            p[K.serverUrl] = s.serverUrl
            p[K.deviceId] = s.deviceId
            p[K.deviceToken] = s.deviceToken
            p[K.deviceName] = s.deviceName
            p[K.settingsVersion] = s.settingsVersion
            p[K.libraryUri] = s.libraryUri
            p[K.libraryPath] = s.libraryPath
            p[K.playlistJson] = s.playlistJson
            p[K.extraEpisodesDate] = s.extraEpisodesDate
            p[K.extraEpisodes] = s.extraEpisodes
        }
    }
}
