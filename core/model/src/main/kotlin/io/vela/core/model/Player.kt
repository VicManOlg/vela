package io.vela.core.model

import kotlinx.serialization.Serializable

/**
 * How the ROM is handed to the emulator.
 *
 * Template placeholders available everywhere (`data`, extras, component):
 * `{rom.uri}` content:// URI (SAF document or our FileProvider), `{rom.path}` absolute path,
 * `{rom.name}` file name with extension, `{rom.stem}` file name without extension,
 * `{rom.dir}` parent directory path, `{core.path}` resolved libretro core path, `{core.id}`,
 * `{platform.id}`, `{package}` the resolved package name, plus any [PlatformSettings.launchOverrides].
 */
@Serializable
enum class RomDelivery {
    /** Nothing about the ROM is passed (launch the emulator to its menu). */
    NONE,

    /** `intent.data = content://` URI + FLAG_GRANT_READ_URI_PERMISSION. */
    CONTENT_URI_DATA,

    /** `intent.data = file://` URI (legacy emulators). */
    FILE_URI_DATA,

    /** ROM referenced only through extras (e.g. RetroArch `ROM` path extra). */
    EXTRAS_ONLY,
}

@Serializable
enum class ExtraType { STRING, BOOLEAN, INT, LONG, FLOAT, URI, STRING_ARRAY }

@Serializable
data class IntentExtra(
    val key: String,
    val value: String,
    val type: ExtraType = ExtraType.STRING,
    /** When false the extra is skipped if its template resolves to an empty string. */
    val allowEmpty: Boolean = false,
)

/** A libretro core option for RetroArch-like players. */
@Serializable
data class CoreOption(
    val id: String,
    val name: String,
    /** e.g. `snes9x_libretro_android.so`; resolved into `{core.path}`. */
    val fileName: String,
)

/**
 * Declarative recipe for launching one emulator/app family. Shipped as JSON in `:core:catalog`,
 * user-editable, and the only place that knows about intents. Conceptually similar to Daijishō
 * "players" but fully data-driven.
 */
@Serializable
data class PlayerDefinition(
    val id: PlayerId,
    val name: String,
    /** Any of these packages satisfies the player (Play Store vs GitHub builds, 32/64-bit). */
    val packages: List<String>,
    /** Fully qualified activity, `.Relative` to the package, or null to use the launcher activity. */
    val activity: String? = null,
    val action: String = "android.intent.action.VIEW",
    val categories: List<String> = emptyList(),
    val delivery: RomDelivery = RomDelivery.CONTENT_URI_DATA,
    val mimeType: String? = null,
    val extras: List<IntentExtra> = emptyList(),
    /** Intent flag names, e.g. `FLAG_ACTIVITY_NEW_TASK`. */
    val flags: List<String> = listOf("FLAG_ACTIVITY_NEW_TASK", "FLAG_ACTIVITY_CLEAR_TOP"),
    /** Platforms this player can run. Empty = generic (any). */
    val platforms: List<PlatformId> = emptyList(),
    /** Per-platform libretro cores; key is platform id. Only for libretro-style players. */
    val cores: Map<String, List<CoreOption>> = emptyMap(),
    /** Template for `{core.path}`, e.g. `/data/data/{package}/cores/{core.file}`. */
    val corePathTemplate: String? = null,
    /** Requires a real file path (cannot work with SAF-only libraries). */
    val requiresFilePath: Boolean = false,
    val website: String? = null,
    val notes: String? = null,
) {
    fun supports(platformId: PlatformId): Boolean = platforms.isEmpty() || platformId in platforms

    fun coresFor(platformId: PlatformId): List<CoreOption> = cores[platformId.value].orEmpty()
}

/** Resolution result: which player + core will run a given game. */
data class ResolvedPlayer(
    val definition: PlayerDefinition,
    val installedPackage: String,
    val core: CoreOption?,
)
