package com.family.kidtv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 一次“看完”事件（本地真相源；synced=false 的会排队上传服务器）。 */
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey val id: String,
    val type: String = "play_complete",
    val language: String,
    val series: String,
    val episodeKey: String,
    val episodeNum: Int,
    val title: String,
    val watchedSec: Int,
    val durationSec: Int,
    val counted: Boolean,
    val localDate: String,
    val occurredAtMs: Long,
    val synced: Boolean = false,
)

@Entity(tableName = "duration_cache")
data class DurationCache(
    @PrimaryKey val episodeKey: String,
    val sizeBytes: Long,
    val durationMs: Long,
)

@Entity(tableName = "synced_files")
data class SyncedFile(
    @PrimaryKey val contentId: String,
    val sha256: String,
    val reported: Boolean = false,
)

/** 单集播放进度（中途退出后续播）。对齐 v1.5.1。 */
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val episodeKey: String,
    val positionMs: Long,
    val passWatchedMs: Long,
    val updatedAtMs: Long,
)

/** 抽帧封面缓存。 */
@Entity(tableName = "episode_thumbnail_cache")
data class EpisodeThumbnailCache(
    @PrimaryKey val episodeKey: String,
    val videoUri: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val cacheFilePath: String,
)

data class EpisodeCount(val episodeKey: String, val n: Int)
data class DayStat(val localDate: String, val eps: Int, val sec: Int)

@Dao
interface KidTvDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(e: PlayEvent)

    @Query("SELECT episodeKey, COUNT(*) AS n FROM play_events WHERE counted GROUP BY episodeKey")
    fun playCounts(): Flow<List<EpisodeCount>>

    @Query("SELECT episodeKey, COUNT(*) AS n FROM play_events WHERE counted GROUP BY episodeKey")
    suspend fun playCountsOnce(): List<EpisodeCount>

    @Query("SELECT localDate, COUNT(*) AS eps, COALESCE(SUM(watchedSec),0) AS sec FROM play_events WHERE counted GROUP BY localDate")
    fun dailyStats(): Flow<List<DayStat>>

    @Query("SELECT * FROM play_events WHERE NOT synced ORDER BY occurredAtMs LIMIT 50")
    suspend fun unsyncedEvents(): List<PlayEvent>

    @Query("UPDATE play_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM duration_cache")
    suspend fun durationCache(): List<DurationCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDuration(d: DurationCache)

    @Query("SELECT * FROM synced_files")
    suspend fun syncedFiles(): List<SyncedFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSyncedFile(f: SyncedFile)

    @Query("UPDATE synced_files SET reported = 1 WHERE contentId IN (:ids)")
    suspend fun markReported(ids: List<String>)

    @Query("DELETE FROM synced_files WHERE contentId = :id")
    suspend fun removeSyncedFile(id: String)

    @Query("SELECT * FROM playback_progress WHERE episodeKey = :key LIMIT 1")
    suspend fun playbackProgress(key: String): PlaybackProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlaybackProgress(p: PlaybackProgress)

    @Query("DELETE FROM playback_progress WHERE episodeKey = :key")
    suspend fun clearPlaybackProgress(key: String)

    @Query("SELECT * FROM episode_thumbnail_cache WHERE episodeKey = :key LIMIT 1")
    suspend fun thumbnailCache(key: String): EpisodeThumbnailCache?

    @Query("SELECT * FROM episode_thumbnail_cache")
    suspend fun allThumbnailCache(): List<EpisodeThumbnailCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putThumbnailCache(row: EpisodeThumbnailCache)
}

@Database(
    entities = [
        PlayEvent::class, DurationCache::class, SyncedFile::class,
        PlaybackProgress::class, EpisodeThumbnailCache::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class KidTvDb : RoomDatabase() {
    abstract fun dao(): KidTvDao

    companion object {
        @Volatile private var instance: KidTvDb? = null
        fun get(context: Context): KidTvDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, KidTvDb::class.java, "kidtv.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
