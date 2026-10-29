package com.family.kidtv.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

@Serializable
data class PairResponse(
    val deviceId: String, val deviceToken: String, val name: String,
    val settingsVersion: Int, val settings: ServerSettings,
)

@Serializable
data class ServerSettings(
    val dailyLimit: Int = 2, val threshold: Int = 75, val masterStars: Int = 5,
    val playlistSize: Int = 4, val autoplay: Boolean = true,
    val playbackMode: String? = null,  // sequential | loop | stop；空则回退 autoplay
    val wifiOnly: Boolean = true, val allowDelete: Boolean = false,
)

@Serializable
data class SettingsResponse(val version: Int, val settings: ServerSettings)

@Serializable
data class EventDto(
    val eventId: String, val type: String, val language: String, val series: String,
    val episodeKey: String, val episodeNum: Int, val title: String,
    val watchedSec: Int, val durationSec: Int, val counted: Boolean,
    val localDate: String, val occurredAtMs: Long,
)

@Serializable
data class EventsResponse(val accepted: List<String>, val duplicates: List<String>)

@Serializable
data class ManifestItem(
    val id: String, val language: String, val series: String, val filename: String,
    val title: String = "", val size: Long = 0, val sha256: String = "",
    val deleted: Boolean = false, val updatedAt: String? = null,
)

@Serializable
data class ManifestResponse(val items: List<ManifestItem>)

@Serializable
data class SyncStateItem(val contentId: String, val status: String = "synced")

private val json = Json { ignoreUnknownKeys = true }
private val JSON_MT = "application/json; charset=utf-8".toMediaType()

class ApiClient(baseUrl: String, private val token: String = "") {
    private val base = baseUrl.trimEnd('/')
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun req(path: String) = Request.Builder()
        .url("$base$path")
        .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }

    private inline fun <reified T> call(r: Request): T {
        http.newCall(r).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, res.body?.string() ?: "")
            return json.decodeFromString(res.body!!.string())
        }
    }

    fun pair(code: String, name: String): PairResponse = call(
        req("/api/pair").post(json.encodeToString(mapOf("code" to code, "name" to name)).toRequestBody(JSON_MT)).build()
    )

    fun postEvents(events: List<EventDto>): EventsResponse = call(
        req("/api/events").post(
            json.encodeToString(mapOf("events" to events)).toRequestBody(JSON_MT)
        ).build()
    )

    fun getSettings(): SettingsResponse = call(req("/api/settings").get().build())

    fun getManifest(): ManifestResponse = call(req("/api/manifest").get().build())

    fun postSyncState(items: List<SyncStateItem>) {
        val body = json.encodeToString(mapOf("items" to items)).toRequestBody(JSON_MT)
        http.newCall(req("/api/sync-state").post(body).build()).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "")
        }
    }

    /** 断点续传下载：offset>0 时带 Range 头。调用方负责关闭 Response。 */
    fun downloadFile(contentId: String, offset: Long): Response {
        val r = req("/api/files/$contentId").get()
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        val res = http.newCall(r).execute()
        if (!res.isSuccessful) { res.close(); throw ApiException(res.code, "") }
        return res
    }
}

class ApiException(val code: Int, message: String) : Exception("HTTP $code $message")
