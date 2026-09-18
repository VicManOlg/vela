package io.vela.feature.home

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
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameCollection
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.HomeRail
import io.vela.core.model.LaunchOption
import io.vela.core.model.CollectionId
import io.vela.core.model.PlatformId
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the focused card tells the header and the background. */
data class Spotlight(
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val accent: Long,
    val gameId: Long? = null,
)

data class HomeUiState(
    val rails: List<HomeRail> = emptyList(),
    val continuePlaying: List<GameSummary> = emptyList(),
    val recent: List<GameSummary> = emptyList(),
    val favorites: List<GameSummary> = emptyList(),
    val platforms: List<PlatformEntry> = emptyList(),
    val collections: List<GameCollection> = emptyList(),
    val android: List<GameSummary> = emptyList(),
    val recommended: List<GameSummary> = emptyList(),
    val recentlyAdded: List<GameSummary> = emptyList(),
    val totalGames: Int = 0,
    val isEmpty: Boolean = false,
) {
    fun platformOf(game: GameSummary): PlatformEntry? = platforms.firstOrNull { it.id == game.platformId }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    games: GameRepository,
    private val library: LibraryRepository,
    collections: CollectionRepository,
    apps: AppsRepository,
    settings: SettingsRepository,
    private val actions: GameActions,
    val menu: GameMenuController,
) : ViewModel() {

    private val lists = combine(
        games.observePlaying(12),
        games.observeRecentlyPlayed(20),
        games.observeFavorites(30),
        games.observeRecommendations(20),
        games.observeRecentlyAdded(20),
    ) { playing, recent, favs, recommended, added -> Lists(playing, recent, favs, recommended, added) }

    val state: StateFlow<HomeUiState> = combine(
        lists,
        library.observeAllPlatforms(),
        collections.observeCollections(),
        apps.observeAndroidGames(40),
        settings.settings,
    ) { l, platforms, cols, android, prefs ->
        val total = platforms.sumOf { it.gameCount }
        // "Continue playing": games marked Playing, else the most recent ones with play time.
        val continuePlaying = l.playing.ifEmpty { l.recent.filter { it.totalPlayTimeMs > 0 }.take(8) }
        HomeUiState(
            rails = prefs.homeRails,
            continuePlaying = continuePlaying,
            recent = l.recent,
            favorites = l.favorites,
            platforms = platforms.filter { it.settings.enabled && it.gameCount > 0 && it.platform.kind == io.vela.core.model.PlatformKind.EMULATED },
            collections = cols,
            android = android,
            recommended = l.recommended,
            recentlyAdded = l.added,
            totalGames = total,
            isEmpty = total == 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _spotlight = MutableStateFlow<Spotlight?>(null)
    val spotlight: StateFlow<Spotlight?> = _spotlight

    val menuState: StateFlow<GameMenuState> = menu.state

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

    fun openMenu(game: GameSummary) {
        viewModelScope.launch { menu.open(game) }
    }

    fun onMenuAction(action: String, onOpenDetails: (GameSummary) -> Unit) {
        viewModelScope.launch {
            when (val r = menu.onAction(action)) {
                is GameMenuController.MenuResult.OpenDetails -> onOpenDetails(r.game)
                else -> Unit
            }
        }
    }

    fun toggleCollection(id: CollectionId) = viewModelScope.launch { menu.toggleCollection(id) }
    fun startNewCollection() = menu.startNewCollection()
    fun createCollection(name: String) = viewModelScope.launch { menu.createCollection(name) }
    fun launchWith(option: LaunchOption, remember: Boolean) = viewModelScope.launch { menu.launchWith(option, remember) }
    fun setCompletion(status: CompletionStatus) = viewModelScope.launch { menu.setCompletion(status) }
    fun confirmHide() = viewModelScope.launch { menu.confirmHide() }
    fun dismissMenu() = menu.dismiss()

    fun rescan() = library.scanInBackground()

    private data class Lists(
        val playing: List<GameSummary>,
        val recent: List<GameSummary>,
        val favorites: List<GameSummary>,
        val recommended: List<GameSummary>,
        val added: List<GameSummary>,
    )

    private fun formatLastPlayedShort(at: Long?): String? = io.vela.core.ui.components.formatLastPlayed(at)
}
