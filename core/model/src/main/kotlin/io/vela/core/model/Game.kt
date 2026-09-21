package io.vela.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameKind { ROM, ANDROID_APP, PC_SHORTCUT }

@Serializable
enum class CompletionStatus { NONE, BACKLOG, PLAYING, COMPLETED, ABANDONED }

/** Where the launchable thing lives. */
@Serializable
sealed interface GameLocation {
    /** Absolute path on a filesystem we can read with java.io.File ("All files access"). */
    @Serializable
    data class File(val path: String) : GameLocation

    /** SAF document URI (content://...), readable through ContentResolver. */
    @Serializable
    data class Document(val uri: String) : GameLocation

    /** Installed Android application, optionally a specific activity. */
    @Serializable
    data class AndroidApp(val packageName: String, val activity: String? = null) : GameLocation

    /** Opaque target handled by a PC player (Winlator shortcut path, Moonlight app id...). */
    @Serializable
    data class External(val target: String) : GameLocation
}

/** Full game record: the entry the detail screen and the launcher work with. */
data class Game(
    val id: GameId,
    val platformId: PlatformId,
    val kind: GameKind,
    val title: String,
    val sortTitle: String,
    val location: GameLocation,
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
    val completion: CompletionStatus = CompletionStatus.NONE,
    val userRating: Int? = null,
    val playerOverride: PlayerId? = null,
    val coreOverride: String? = null,
    val metadata: GameMetadata? = null,
    val artwork: Map<ArtworkType, String> = emptyMap(),
) {
    val displayTitle: String get() = metadata?.title?.takeIf { it.isNotBlank() } ?: title
}

/**
 * Lightweight projection used by grids and rails. Thousands of these can live in memory,
 * so it only carries what a card needs.
 */
data class GameSummary(
    val id: GameId,
    val platformId: PlatformId,
    val kind: GameKind,
    val title: String,
    val boxArt: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val favorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val totalPlayTimeMs: Long = 0,
    val packageName: String? = null,
)

/** Scraped or hand-edited descriptive data. */
data class GameMetadata(
    val title: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    /** ISO-8601 date or just a year, as provided by the source. */
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val players: String? = null,
    /** Normalised 0..1 rating. */
    val rating: Float? = null,
    val region: String? = null,
    /** Series the game belongs to ("Pokemon", "Zelda"). */
    val franchise: String? = null,
    /** ESRB/PEGI/ELSPA label as the source gives it ("E", "T", "12"). */
    val ageRating: String? = null,
    /** Where the description came from, when it is a web page. */
    val sourceUrl: String? = null,
    val providerId: String? = null,
    val providerGameId: String? = null,
    val scrapedAt: Long? = null,
) {
    val releaseYear: Int? get() = releaseDate?.take(4)?.toIntOrNull()
}

@Serializable
enum class ArtworkType {
    BOX_FRONT, BOX_BACK, LOGO, BACKGROUND, HERO, SCREENSHOT, TITLE_SCREEN, MARQUEE, ICON, VIDEO;

    val isVideo: Boolean get() = this == VIDEO
}

/** A stored piece of artwork for a game. */
data class Artwork(
    val gameId: GameId,
    val type: ArtworkType,
    /** Local file path (preferred) or URI. */
    val localPath: String,
    val sourceUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val providerId: String? = null,
)

data class PlaySession(
    val id: Long,
    val gameId: GameId,
    val startedAt: Long,
    val endedAt: Long?,
) {
    val durationMs: Long get() = (endedAt ?: startedAt) - startedAt
}
