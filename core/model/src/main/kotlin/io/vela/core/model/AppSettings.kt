package io.vela.core.model

import kotlinx.serialization.Serializable

/** Preferred way of reading ROM folders. */
@Serializable
enum class StorageMode {
    /** Ask for "All files access" and use java.io.File (fast, path launching works for RetroArch). */
    ALL_FILES,

    /** Storage Access Framework only. */
    SAF,
}

/** Face-button layout: which physical button confirms. */
@Serializable
enum class ConfirmButton { A, B }

/** How the Library shows systems. */
@Serializable
enum class LibraryLayout(val label: String, val description: String) {
    STAGE("Stage", "One system at a time, with a dial of consoles"),
    SHOWCASE("Showcase", "Poster cards with the console and your covers"),
    GRID("Grid", "Compact colour tiles"),
}

/** Home screen arrangement. */
@Serializable
enum class HomeLayout(val label: String, val description: String) {
    RAILS("Rails", "Several rows: continue playing, recent, favourites, systems…"),
    SPOTLIGHT("Spotlight", "The focused game fills the screen; one row of covers and one of systems"),
}

/** How a game list (platform, collection, favourites, all) is laid out. */
@Serializable
enum class LibraryView(val label: String, val description: String) {
    GRID("Grid", "Box art cards"),
    COMPACT("Compact grid", "Smaller cards, more per row"),
    LIST("List", "Titles on the left, preview on the right"),
    SHOWCASE("Showcase", "One row of large art"),
}

@Serializable
enum class HomeRail { CONTINUE_PLAYING, RECENT, FAVORITES, PLATFORMS, COLLECTIONS, ANDROID, RECOMMENDED, RECENTLY_ADDED, APPS }

/** Everything the user can tune, persisted through DataStore. Mutations go through `SettingsRepository`. */
@Serializable
data class AppSettings(
    val setupCompleted: Boolean = false,
    val themeId: String = "vela-night",
    val storageMode: StorageMode = StorageMode.ALL_FILES,
    val scanOnStartup: Boolean = true,
    val showHiddenGames: Boolean = false,
    val hideDuplicateRegions: Boolean = true,
    val purgeMissingGames: Boolean = false,
    val homeRails: List<HomeRail> = listOf(
        HomeRail.CONTINUE_PLAYING,
        HomeRail.RECENT,
        HomeRail.FAVORITES,
        HomeRail.RECENTLY_ADDED,
        HomeRail.PLATFORMS,
        HomeRail.COLLECTIONS,
        HomeRail.ANDROID,
        HomeRail.RECOMMENDED,
    ),
    // Controller
    val confirmButton: ConfirmButton = ConfirmButton.A,
    val hapticFeedback: Boolean = true,
    val analogStickNavigation: Boolean = true,
    val stickDeadZone: Float = 0.5f,
    val repeatInitialDelayMs: Int = 400,
    val repeatIntervalMs: Int = 90,
    val repeatFastAfterMs: Int = 1500,
    val repeatFastIntervalMs: Int = 40,
    // Appearance
    val videoPreviews: Boolean = true,
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val gridColumns: Int = 0,
    val libraryView: LibraryView = LibraryView.GRID,
    val homeLayout: HomeLayout = HomeLayout.RAILS,
    val libraryLayout: LibraryLayout = LibraryLayout.STAGE,
    val reduceMotion: Boolean = false,
    val uiScale: Float = 1f,
    val uiSounds: Boolean = true,
    val uiSoundVolume: Float = 0.5f,
    // Android apps
    val showSystemApps: Boolean = false,
    val autoDetectGames: Boolean = true,
    // Scraping
    val scraping: ScrapingSettings = ScrapingSettings(),
    // Launcher
    val returnToHomeAfterGame: Boolean = false,
    val playTimeCapMinutes: Int = 600,
    /** Player JSON overrides supplied by the user (same schema as catalog/players.json). */
    val userPlayersJson: String? = null,
    val userPlatformsJson: String? = null,
)

@Serializable
data class ScrapingSettings(
    val metadataProviderId: String = "libretro",
    val artworkProviderIds: List<String> = listOf("libretro"),
    val preferredRegions: List<String> = listOf("eu", "us", "wor", "jp"),
    val language: String = "en",
    val downloadVideos: Boolean = false,
    val overwriteExisting: Boolean = false,
    val autoScrapeNewGames: Boolean = true,
    val wifiOnly: Boolean = true,
    /** Use the Wikipedia article lead as the description when the metadata source has none. */
    val wikipediaDescriptions: Boolean = true,
    val screenScraperUser: String = "",
    val screenScraperPassword: String = "",
    val steamGridDbApiKey: String = "",
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val theGamesDbApiKey: String = "",
)
