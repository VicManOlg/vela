package io.vela.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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

/**
 * Where the face buttons sit on the device, and how they are drawn in the hint bar. The Odin 3
 * ships Nintendo-style buttons (A on the right, B at the bottom); Xbox-style pads and PlayStation
 * pads put the confirm button at the bottom.
 */
@Serializable
enum class ControllerLayout(val label: String, val description: String) {
    ODIN3("Odin 3 / Nintendo", "A right, B bottom, X top, Y left"),
    XBOX("Xbox", "A bottom, B right, X left, Y top, coloured"),
    PLAYSTATION("PlayStation", "Cross bottom, Circle right, Square left, Triangle top"),
    ;

    /** Physical position of the button that reports [label] (A, B, X or Y). */
    fun positionOf(label: String): String = when (this) {
        ODIN3 -> when (label) { "A" -> "right"; "B" -> "bottom"; "X" -> "top"; else -> "left" }
        XBOX, PLAYSTATION -> when (label) { "A" -> "bottom"; "B" -> "right"; "X" -> "left"; else -> "top" }
    }

    /** What is printed on the button that reports [label]. */
    fun glyphOf(label: String): String = when (this) {
        PLAYSTATION -> when (label) { "A" -> "✕"; "B" -> "○"; "X" -> "□"; else -> "△" }
        else -> label
    }
}

/** How the Library shows systems. */
@Serializable
enum class LibraryLayout(val label: String, val description: String) {
    THEME("Theme default", "Whatever the active theme suggests"),
    STAGE("Stage", "One system at a time, with a dial of consoles"),
    SHOWCASE("Showcase", "Poster cards with the console and your covers"),
    GRID("Grid", "Compact colour tiles"),
    WHEEL("Wheel", "Systems stacked on the left; the selected one's covers and scene on the right"),
    MOSAIC("Mosaic", "Square tiles built from your own covers, the console on top"),
    COLUMNS("Columns", "Tall panels side by side, each with its system's scene; the focused one opens up"),
    BOOK("Book", "Art-book carousel: wide scene cards, the centre one in front, like the pages of a catalogue"),
    ;

    companion object {
        /** Theme JSON key (`layout.libraryLayout`) to a concrete layout; unknown or missing means Stage. */
        fun fromKey(key: String?): LibraryLayout = entries.firstOrNull { it != THEME && it.name.equals(key, ignoreCase = true) } ?: STAGE
    }
}

/** Home screen arrangement. */
@Serializable
enum class HomeLayout(val label: String, val description: String) {
    THEME("Theme default", "Whatever the active theme suggests"),
    RAILS("Rails", "Several rows: continue playing, recent, favourites, systems…"),
    SPOTLIGHT("Spotlight", "The focused game fills the screen; one row of covers and one of systems"),
    TILES("Tiles", "One row of big square tiles with round buttons below; no tab bar"),
    STRIP("Strip", "Small tiles along the top, the focused game large underneath"),
    DASHBOARD("Dashboard", "Blocks: one big tile plus grids of squares"),
    CAROUSEL("Carousel", "One big shelf of covers in the middle; the focused one stands centred, systems as chips below"),
}

/** Whether the section tabs are shown at the top of the shell. */
@Serializable
enum class TabBarMode(val label: String, val description: String) {
    THEME("Theme default", "Some themes hide the tabs on their Home"),
    ALWAYS("Always", "Tabs on every screen"),
    HIDDEN("Hidden", "Never; switch sections with L1 and R1 or the hint bar"),
}

/** How a game list (platform, collection, favourites, all) is laid out. */
@Serializable
enum class LibraryView(val label: String, val description: String) {
    THEME("Theme default", "Whatever the active theme suggests"),
    GRID("Grid", "Box art cards"),
    COMPACT("Compact grid", "Smaller cards, more per row"),
    LIST("List", "Titles on the left, preview on the right"),
    SHOWCASE("Showcase", "One row of large art"),
    HERO("Hero", "The focused game's scene fills the top; one row of covers below"),
    WALL("Wall", "Edge-to-edge mosaic of square covers, no gaps, no labels"),
    DETAILS("Details", "Dense table: title, system, last played, play time, stars"),
    BOOK("Book", "Titles on the left; cover, description and fact chips on the right, like an art book"),
    ;

    companion object {
        /** Theme JSON key (`layout.libraryView`) to a concrete view; unknown or missing means Grid. */
        fun fromKey(key: String?): LibraryView = entries.firstOrNull { it != THEME && it.name.equals(key, ignoreCase = true) } ?: GRID
    }
}

@Serializable
enum class HomeRail(val label: String, val description: String) {
    CONTINUE_PLAYING("Continue playing", "Games marked Playing, or the last ones with play time"),
    RECENT("Recent", "Most recently played"),
    FAVORITES("Favorites", "Your favourites"),
    PLATFORMS("Systems", "One tile per system"),
    COLLECTIONS("Collections", "Your collections"),
    ANDROID("Android games", "Games installed on the device"),
    RECOMMENDED("Because you play", "Unplayed games from the systems you use most"),
    RECENTLY_ADDED("Recently added", "Newest in the library"),
    APPS("Quick apps", "Apps you pinned"),
    TOP_RATED("Your top rated", "Games you gave the most stars"),
}

/** Rails are stored by name; names this build does not know (newer builds, downgrades) are skipped, not fatal. */
object HomeRailListSerializer : KSerializer<List<HomeRail>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<HomeRail>) = delegate.serialize(encoder, value.map { it.name })
    override fun deserialize(decoder: Decoder): List<HomeRail> =
        delegate.deserialize(decoder).mapNotNull { name -> HomeRail.entries.firstOrNull { it.name == name } }.distinct()
}

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
    @Serializable(with = HomeRailListSerializer::class)
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
    /** Sections the user switched off; kept separate so new sections still appear once. */
    @Serializable(with = HomeRailListSerializer::class)
    val hiddenHomeRails: List<HomeRail> = emptyList(),
    // Controller
    val confirmButton: ConfirmButton = ConfirmButton.A,
    val controllerLayout: ControllerLayout = ControllerLayout.ODIN3,
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
    /** Superseded by [gameListView]; kept so old documents still decode. Every old install stored GRID here, so it cannot mean "theme". */
    val libraryView: LibraryView = LibraryView.GRID,
    /** Game list view; THEME follows `layout.libraryView` of the active theme. */
    val gameListView: LibraryView = LibraryView.THEME,
    val homeLayout: HomeLayout = HomeLayout.THEME,
    val libraryLayout: LibraryLayout = LibraryLayout.THEME,
    val tabBar: TabBarMode = TabBarMode.THEME,
    val reduceMotion: Boolean = false,
    val uiScale: Float = 1f,
    val uiSounds: Boolean = true,
    val uiSoundVolume: Float = 0.5f,
    /** The user's tweaks on top of the active theme; see [ThemeSpec.applying]. */
    val appearance: AppearanceOverrides = AppearanceOverrides(),
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
    /** Theme written by the in-app editor (id "custom"); null until the user customizes one. */
    val customThemeJson: String? = null,
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
