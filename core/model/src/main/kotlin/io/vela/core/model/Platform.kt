package io.vela.core.model

import kotlinx.serialization.Serializable

/** How games of this platform are launched. */
@Serializable
enum class PlatformKind {
    /** ROM files launched through an emulator [PlayerDefinition]. */
    EMULATED,

    /** Installed Android packages launched directly. */
    ANDROID,

    /** PC games launched through a compatibility layer or a streaming client. */
    PC,
}

@Serializable
enum class PlatformFamily { NINTENDO, SONY, SEGA, MICROSOFT, ATARI, SNK, NEC, ARCADE, COMPUTER, ANDROID, PC, OTHER }

/**
 * A gaming system. Built-in definitions ship in `:core:catalog` as JSON; the user can enable/disable
 * them, add ROM directories and choose the emulator. Everything here is data - nothing is hardcoded
 * in the UI or launcher.
 */
@Serializable
data class Platform(
    val id: PlatformId,
    val name: String,
    val shortName: String,
    val manufacturer: String,
    val releaseYear: Int? = null,
    val family: PlatformFamily = PlatformFamily.OTHER,
    val kind: PlatformKind = PlatformKind.EMULATED,
    /** Lower-case file extensions without the dot, e.g. `sfc`, `smc`, `zip`. */
    val extensions: Set<String> = emptySet(),
    /** Lower-case folder names that map to this platform during auto-detection, e.g. `snes`, `sfc`. */
    val folderAliases: Set<String> = emptySet(),
    /** Ordered candidate players; the first installed one is used unless the user picked another. */
    val defaultPlayers: List<PlayerId> = emptyList(),
    /** ScreenScraper system id, used by the ScreenScraper provider. */
    val screenScraperId: Int? = null,
    /** IGDB platform id. */
    val igdbId: Int? = null,
    /** libretro-thumbnails system directory name. */
    val libretroName: String? = null,
    /** RetroArch assets icon name when it differs from [libretroName] (or there is none). */
    val iconName: String? = null,
    /** Ids of this system in the generated emulator catalogue (Daijishō); empty means the same id. */
    val catalogIds: List<String> = emptyList(),
    /** Accent colour (ARGB) used by the theme for platform tiles. */
    @Serializable(with = ArgbHexSerializer::class)
    val accentColor: Long = 0xFF3D7BFF,
    val sortOrder: Int = 1000,
) {
    fun accepts(extension: String): Boolean = extension.lowercase() in extensions
}

/** Per-platform user preferences persisted in the database. */
data class PlatformSettings(
    val platformId: PlatformId,
    val enabled: Boolean = true,
    val playerId: PlayerId? = null,
    /** Libretro core id (for RetroArch-like players) or null for the player's default. */
    val coreId: String? = null,
    /** Free-form overrides merged into the launch template (`{key}` placeholders). */
    val launchOverrides: Map<String, String> = emptyMap(),
    val customName: String? = null,
)
