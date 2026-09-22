package io.vela.core.model

import kotlinx.serialization.Serializable

/** Storage access technique for a [LibrarySource]. */
@Serializable
enum class SourceAccess {
    /** java.io.File access ("All files access" or app-owned directory). Fast; supports path launching. */
    FILE,

    /** Storage Access Framework tree URI. Slower listing; content URIs only. */
    DOCUMENT_TREE,
}

/**
 * A directory the scanner walks. When [platformId] is null the scanner auto-detects the platform
 * of each immediate sub-folder from its name (ES-DE style `ROMs/snes`, `ROMs/ps2`...) and falls
 * back to extension matching.
 */
data class LibrarySource(
    val id: LibrarySourceId,
    /** `file:///storage/emulated/0/ROMs` or `content://.../tree/...`. */
    val uri: String,
    val displayName: String,
    val access: SourceAccess,
    val platformId: PlatformId? = null,
    val recursive: Boolean = true,
    val enabled: Boolean = true,
    val lastScanAt: Long? = null,
    val lastScanGameCount: Int = 0,
)

sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val source: String, val filesSeen: Int, val gamesFound: Int, val message: String = "") : ScanProgress
    data class Finished(val result: ScanResult) : ScanProgress
    data class Failed(val message: String) : ScanProgress
}

data class ScanResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val skipped: Int,
    val durationMs: Long,
    val errors: List<String> = emptyList(),
)

@Serializable
enum class CollectionKind {
    /** User-curated list of games. */
    MANUAL,

    /** Computed from game state; not editable. */
    FAVORITES, RECENT, PLAYING, COMPLETED, BACKLOG,
}

data class GameCollection(
    val id: CollectionId,
    val name: String,
    val kind: CollectionKind = CollectionKind.MANUAL,
    /** Icon key resolved by the UI theme (e.g. `star`, `sword`, `heart`). */
    val icon: String = "collection",
    val accentColor: Long? = null,
    val sortOrder: Int = 0,
    val gameCount: Int = 0,
    val coverArt: String? = null,
)

/** Sort options shared by grids and search. */
enum class GameSort { TITLE, LAST_PLAYED, MOST_PLAYED, RECENTLY_ADDED, RELEASE_YEAR, RATING, USER_RATING }
