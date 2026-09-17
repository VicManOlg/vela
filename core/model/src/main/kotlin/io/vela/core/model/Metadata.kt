package io.vela.core.model

/** What we know about a game before asking a metadata provider. */
data class MetadataQuery(
    val gameId: GameId,
    val platformId: PlatformId,
    /** Cleaned title (region tags and dump markers removed). */
    val title: String,
    val fileName: String,
    val fileSize: Long? = null,
    val crc32: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
    val preferredRegions: List<String> = listOf("eu", "us", "wor", "jp"),
    val language: String = "en",
)

/** One artwork the provider can deliver; downloaded lazily by the artwork store. */
data class ArtworkCandidate(
    val type: ArtworkType,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val region: String? = null,
    val format: String? = null,
)

/** A provider result ranked by [score] (0..1). */
data class MetadataMatch(
    val providerId: String,
    val providerGameId: String,
    val title: String,
    val score: Float,
    val metadata: GameMetadata,
    val artwork: List<ArtworkCandidate> = emptyList(),
)

/** Static description of a provider for the Settings UI. */
data class MetadataProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val requiresCredentials: Boolean,
    val supportsHashLookup: Boolean,
    val artworkTypes: Set<ArtworkType>,
    val website: String,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Launcher activity class name, when resolvable. */
    val activity: String? = null,
    /** True if Android flags it as a game (category GAME / FLAG_IS_GAME). */
    val isGame: Boolean,
    val isSystem: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val versionName: String? = null,
)
