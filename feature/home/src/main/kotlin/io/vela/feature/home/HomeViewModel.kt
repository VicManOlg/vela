package io.vela.feature.home

import kotlinx.coroutines.flow.Flow
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuActions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.GameMenuController
import io.vela.core.model.GameCollection
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.HomeRail
import io.vela.core.model.PlatformId
import io.vela.core.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.vela.core.model.HomeLayout

/** What the focused card tells the header and the background. */
data class Spotlight(
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val accent: Long,
    val gameId: Long? = null,
)

data class HomeUiState(
    val layout: HomeLayout = HomeLayout.RAILS,
    val rails: List<HomeRail> = emptyList(),
    val continuePlaying: List<GameSummary> = emptyList(),
    val recent: List<GameSummary> = emptyList(),
    val favorites: List<GameSummary> = emptyList(),
    val platforms: List<PlatformEntry> = emptyList(),
    val collections: List<GameCollection> = emptyList(),
    val android: List<GameSummary> = emptyList(),
    val quickApps: List<GameSummary> = emptyList(),
    val recommended: List<GameSummary> = emptyList(),
    val recentlyAdded: List<GameSummary> = emptyList(),
    val topRated: List<GameSummary> = emptyList(),
    val totalGames: Int = 0,
    val isEmpty: Boolean = false,
) {
    private val platformById: Map<PlatformId, PlatformEntry> by lazy { platforms.associateBy { it.id } }
    fun platformOf(game: GameSummary): PlatformEntry? = platformById[game.platformId]

    /** The games behind one section (systems and collections have none). */
    fun gamesFor(rail: HomeRail): List<GameSummary> = when (rail) {
        HomeRail.CONTINUE_PLAYING -> continuePlaying
        HomeRail.RECENT -> recent
        HomeRail.FAVORITES -> favorites
        HomeRail.TOP_RATED -> topRated
        HomeRail.RECENTLY_ADDED -> recentlyAdded
        HomeRail.RECOMMENDED -> recommended
        HomeRail.ANDROID -> android
        HomeRail.APPS -> quickApps
        HomeRail.PLATFORMS, HomeRail.COLLECTIONS -> emptyList()
    }

    /** Every visible section's games in the user's order, once each: what the tile-based Homes show. */
    fun gamesInOrder(limit: Int = 40): List<GameSummary> = rails.flatMap(::gamesFor).distinctBy { it.id }.take(limit)

    val showsPlatforms: Boolean get() = HomeRail.PLATFORMS in rails
    val showsCollections: Boolean get() = HomeRail.COLLECTIONS in rails
    val showsQuickApps: Boolean get() = HomeRail.APPS in rails
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    games: GameRepository,
    private val library: LibraryRepository,
    collections: CollectionRepository,
    apps: AppsRepository,
    settings: AppSettingsRepository,
    private val actions: GameActions,
    private val menuController: GameMenuController,
) : ViewModel() {

    private val lists = combine(
        combine(games.observePlaying(12), games.observeRecentlyPlayed(20), games.observeFavorites(30)) { p, r, f -> Triple(p, r, f) },
        games.observeRecommendations(20),
        games.observeRecentlyAdded(20),
        games.observeTopRated(20),
    ) { (playing, recent, favs), recommended, added, topRated -> Lists(playing, recent, favs, recommended, added, topRated) }

    val state: StateFlow<HomeUiState> = combine(
        lists,
        library.observeAllPlatforms(),
        collections.observeCollections(),
        combine(apps.observeAndroidGames(40), apps.observeApps(20)) { games, pinned -> games to pinned },
        settings.settings,
    ) { l, platforms, cols, (android, pinned), prefs ->
        val total = platforms.sumOf { it.gameCount }
        // "Continue playing": games marked Playing, else the most recent ones with play time.
        val continuePlaying = l.playing.ifEmpty { l.recent.filter { it.totalPlayTimeMs > 0 }.take(8) }
        HomeUiState(
            layout = prefs.homeLayout,
            // User order first, sections this build added at the end, hidden ones out; never empty.
            rails = (prefs.homeRails + HomeRail.entries.filter { it !in prefs.homeRails })
                .filter { it !in prefs.hiddenHomeRails }
                .ifEmpty { listOf(HomeRail.PLATFORMS) },
            continuePlaying = continuePlaying,
            recent = l.recent,
            favorites = l.favorites,
            platforms = platforms.filter { it.settings.enabled && it.gameCount > 0 && it.platform.kind == io.vela.core.model.PlatformKind.EMULATED },
            collections = cols,
            android = android,
            quickApps = pinned,
            recommended = l.recommended,
            recentlyAdded = l.added,
            topRated = l.topRated,
            totalGames = total,
            isEmpty = total == 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(layout = settings.loaded.homeLayout))

    private val _spotlight = MutableStateFlow<Spotlight?>(null)
    val spotlight: StateFlow<Spotlight?> = _spotlight

    val menu: GameMenuActions = menuController.attach(viewModelScope)
    val menuState: StateFlow<GameMenuState> = menuController.state
    val menuEvents: Flow<GameMenuEvent> = menuController.events

    fun spotlightGame(game: GameSummary) {
        val platform = state.value.platformOf(game)?.platform
        val parts = listOfNotNull(
            platform?.shortName ?: if (game.kind == io.vela.core.model.GameKind.ANDROID_APP) "Android" else null,
            formatLastPlayedShort(game.lastPlayedAt),
            game.totalPlayTimeMs.takeIf { it > 0 }?.let { io.vela.core.ui.components.formatPlayTime(it) + " played" },
        )
        _spotlight.value = Spotlight(
            title = game.title,
            subtitle = parts.joinToString("   "),
            artwork = game.background ?: game.boxArt,
            accent = platform?.accentColor ?: 0xFF3DDC84,
            gameId = game.id.value,
        )
    }

    fun spotlightPlatform(entry: PlatformEntry) {
        _spotlight.value = Spotlight(
            title = entry.displayName,
            subtitle = if (entry.gameCount == 1) "1 game" else "${entry.gameCount} games",
            artwork = null,
            accent = entry.platform.accentColor,
        )
    }

    fun spotlightCollection(collection: GameCollection) {
        _spotlight.value = Spotlight(
            title = collection.name,
            subtitle = if (collection.gameCount == 1) "1 game" else "${collection.gameCount} games",
            artwork = collection.coverArt,
            accent = collection.accentColor ?: 0xFF7FD7FF,
        )
    }

    fun launch(game: GameSummary) {
        viewModelScope.launch { actions.launch(game.id) }
    }

    fun openMenu(game: GameSummary) = menuController.open(game)



    fun rescan() = library.scanInBackground()

    private data class Lists(
        val playing: List<GameSummary>,
        val recent: List<GameSummary>,
        val favorites: List<GameSummary>,
        val recommended: List<GameSummary>,
        val added: List<GameSummary>,
        val topRated: List<GameSummary>,
    )

    private fun formatLastPlayedShort(at: Long?): String? = io.vela.core.ui.components.formatLastPlayed(at)
}
