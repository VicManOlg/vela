package io.vela.core.database.entity

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "platform_settings")
data class PlatformSettingsEntity(
    @PrimaryKey val platformId: String,
    val enabled: Boolean = true,
    val playerId: String? = null,
    val coreId: String? = null,
    /** JSON object of `{key}` template overrides. */
    val launchOverrides: String? = null,
    val customName: String? = null,
)

@Entity(tableName = "library_sources", indices = [Index(value = ["uri"], unique = true)])
data class LibrarySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    /** SourceAccess name. */
    val access: String,
    val platformId: String? = null,
    val recursive: Boolean = true,
    val enabled: Boolean = true,
    val lastScanAt: Long? = null,
    val lastScanGameCount: Int = 0,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CollectionKind name. */
    val kind: String = "MANUAL",
    val icon: String = "collection",
    val accentColor: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_games",
    primaryKeys = ["collectionId", "gameId"],
    indices = [Index("gameId"), Index("collectionId")],
)
data class CollectionGameEntity(
    val collectionId: Long,
    val gameId: Long,
    val position: Int = 0,
    val addedAt: Long,
)

/** Full-text index over titles for instant global search. Kept in sync by triggers Room generates. */
@Fts4(contentEntity = GameEntity::class)
@Entity(tableName = "games_fts")
data class GameFtsEntity(
    val title: String,
    val sortTitle: String,
)

/**
 * Card-sized projection joined once in SQL so grids never touch the metadata/artwork tables
 * per item. Everything rails, grids and search render comes from this view.
 */
@DatabaseView(
    viewName = "game_summaries",
    value = """
        SELECT g.id AS id,
               g.platformId AS platformId,
               g.kind AS kind,
               COALESCE(NULLIF(m.title, ''), g.title) AS title,
               g.sortTitle AS sortTitle,
               g.favorite AS favorite,
               g.hidden AS hidden,
               g.present AS present,
               g.lastPlayedAt AS lastPlayedAt,
               g.playCount AS playCount,
               g.totalPlayTimeMs AS totalPlayTimeMs,
               g.addedAt AS addedAt,
               g.completion AS completion,
               g.duplicateKey AS duplicateKey,
               g.discNumber AS discNumber,
               g.userRating AS userRating,
               CASE WHEN g.locationType = 'ANDROID_APP' THEN g.locationValue END AS packageName,
               m.genres AS genres,
               m.releaseDate AS releaseDate,
               m.rating AS rating,
               box.localPath AS boxArt,
               logo.localPath AS logo,
               COALESCE(bg.localPath, hero.localPath, shot.localPath) AS background
        FROM games g
        LEFT JOIN game_metadata m ON m.gameId = g.id
        LEFT JOIN artwork box ON box.gameId = g.id AND box.type = 'BOX_FRONT'
        LEFT JOIN artwork logo ON logo.gameId = g.id AND logo.type = 'LOGO'
        LEFT JOIN artwork bg ON bg.gameId = g.id AND bg.type = 'BACKGROUND'
        LEFT JOIN artwork hero ON hero.gameId = g.id AND hero.type = 'HERO'
        LEFT JOIN artwork shot ON shot.gameId = g.id AND shot.type = 'SCREENSHOT'
    """,
)
data class GameSummaryView(
    val id: Long,
    val platformId: String,
    val kind: String,
    val title: String,
    val sortTitle: String,
    val favorite: Boolean,
    val hidden: Boolean,
    val present: Boolean,
    val lastPlayedAt: Long?,
    val playCount: Int,
    val totalPlayTimeMs: Long,
    val addedAt: Long,
    val completion: String,
    val duplicateKey: String,
    val discNumber: Int?,
    val userRating: Int?,
    val packageName: String?,
    val genres: String?,
    val releaseDate: String?,
    val rating: Float?,
    val boxArt: String?,
    val logo: String?,
    val background: String?,
)

/** Row of the per-platform counters shown on platform tiles. */
data class PlatformCount(val platformId: String, val count: Int)
