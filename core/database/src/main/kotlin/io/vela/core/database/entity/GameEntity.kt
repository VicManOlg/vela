package io.vela.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How [GameEntity.locationValue] must be interpreted. */
enum class LocationType { FILE, DOCUMENT, ANDROID_APP, EXTERNAL }

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["locationType", "locationValue"], unique = true),
        Index(value = ["platformId", "sortTitle"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["favorite"]),
        Index(value = ["sourceId"]),
        Index(value = ["duplicateKey"]),
        // Every list reads "present = 1 AND hidden = 0 ORDER BY sortTitle".
        Index(value = ["present", "hidden", "sortTitle"]),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    /** GameKind name. */
    val kind: String,
    val title: String,
    val sortTitle: String,
    val locationType: LocationType,
    /** Absolute path, document URI, package name or external target. */
    val locationValue: String,
    /** Activity class for Android apps, null otherwise. */
    val locationExtra: String? = null,
    val fileName: String,
    val fileSize: Long = 0,
    val lastModified: Long = 0,
    val extension: String = "",
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val addedAt: Long,
    /** CompletionStatus name. */
    val completion: String = "NONE",
    val userRating: Int? = null,
    val playerOverride: String? = null,
    val coreOverride: String? = null,
    /** Library source this file was found in; null for Android apps and manual entries. */
    val sourceId: Long? = null,
    /** Region hint derived from the file name (eu/us/jp/wor). */
    val region: String? = null,
    /** platformId + sortTitle; rows sharing it are the same game in another region/revision. */
    val duplicateKey: String,
    /** Disc number for multi-disc sets, null for single files. */
    val discNumber: Int? = null,
    /** False when the last scan could not find the file; kept so stats survive an unplugged SD card. */
    @ColumnInfo(defaultValue = "1") val present: Boolean = true,
    /** Last scan generation that saw this file; used to detect removals incrementally. */
    @ColumnInfo(defaultValue = "0") val scanGeneration: Long = 0,
)

@Entity(tableName = "game_metadata", indices = [Index("franchise")])
data class GameMetadataEntity(
    @PrimaryKey val gameId: Long,
    val title: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    /** `|`-separated genre list. */
    val genres: String? = null,
    val players: String? = null,
    val rating: Float? = null,
    val region: String? = null,
    val franchise: String? = null,
    val ageRating: String? = null,
    val sourceUrl: String? = null,
    val providerId: String? = null,
    val providerGameId: String? = null,
    val scrapedAt: Long? = null,
)

@Entity(
    tableName = "artwork",
    primaryKeys = ["gameId", "type"],
    indices = [Index("gameId")],
)
data class ArtworkEntity(
    val gameId: Long,
    /** ArtworkType name. */
    val type: String,
    val localPath: String,
    val sourceUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val providerId: String? = null,
    val updatedAt: Long,
)

@Entity(tableName = "play_sessions", indices = [Index("gameId"), Index("startedAt")])
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
)
