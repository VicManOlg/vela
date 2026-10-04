package io.vela.feature.library

import kotlinx.coroutines.flow.asStateFlow
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuActions
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.GameQuery
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.GameMenuController
import io.vela.core.model.CollectionId
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSort
import io.vela.core.model.Game
import io.vela.core.model.GameSummary
import io.vela.core.model.LibraryView
import io.vela.core.model.PlatformId
import io.vela.core.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameGridHeader(
    val title: String,
    val subtitle: String,
    val accent: Long,
    val sort: GameSort,
    val count: Int,
    /** The system's box shape, for a system's own grid; null in shared lists (all, favourites, collections). */
    val boxArtAspect: Float? = null,
)

@HiltViewModel
class GameGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val library: LibraryRepository,
    private val collections: CollectionRepository,
    private val settings: AppSettingsRepository,
    private val actions: GameActions,
    private val menuController: GameMenuController,
) : ViewModel() {

    val route: GameGridRoute = savedStateHandle.toRoute()
    private val platformId = route.platformId?.let(::PlatformId)
    private val collectionId = route.collectionId?.let(::CollectionId)

    private val sort = MutableStateFlow(GameSort.TITLE)
    private val _focused = MutableStateFlow<GameSummary?>(null)
    val focused: StateFlow<GameSummary?> = _focused.asStateFlow()
    fun setFocused(value: GameSummary?) { _focused.value = value }

    val platform: StateFlow<PlatformEntry?> = (platformId?.let(library::observePlatform) ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Paged for platforms/all; collections are small and come as a plain list through paging too. */
    val paged: Flow<PagingData<GameSummary>> = combine(sort, settings.settings) { s, prefs ->
        GameQuery(
            platformId = platformId,
            favoritesOnly = route.favorites,
            sort = s,
            hideDuplicateRegions = prefs.hideDuplicateRegions,
            showHidden = prefs.showHiddenGames,
        )
    }.flatMapLatest { q ->
        if (collectionId != null) {
            collections.observeGames(collectionId).map { list -> PagingData.from(list) }
        } else {
            games.pagedGames(q)
        }
    }.cachedIn(viewModelScope)

    val header: StateFlow<GameGridHeader> = combine(platform, sort, collections.observeCollections()) { entry, s, cols ->
        val collection = cols.firstOrNull { it.id == collectionId }
        // Known up front for a system or a collection; "All games" and "Favorites" learn it from the list.
        val count = collection?.gameCount ?: entry?.gameCount ?: 0
        GameGridHeader(
            title = route.title ?: collection?.name ?: entry?.displayName ?: if (route.favorites) "Favorites" else "All games",
            subtitle = entry?.platform?.manufacturer ?: "",
            accent = collection?.accentColor ?: entry?.platform?.accentColor ?: 0xFF7FD7FF,
            sort = s,
            count = count,
            boxArtAspect = entry?.platform?.boxArtAspect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameGridHeader(route.title ?: "", "", 0xFF7FD7FF, GameSort.TITLE, 0))

    val menu: GameMenuActions = menuController.attach(viewModelScope)
    val menuState: StateFlow<GameMenuState> = menuController.state
    val menuEvents: Flow<GameMenuEvent> = menuController.events

    /** Full record of the focused game (description, developer, year...) for views with a facts panel. */
    val focusedDetails: StateFlow<Game?> = focused
        .flatMapLatest { g -> if (g == null) flowOf(null) else games.observeGame(g.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Persisted globally so every list opens the way the user last chose. */
    val view: StateFlow<LibraryView> = settings.settings.map { it.gameListView }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.loaded.gameListView)

    fun setView(v: LibraryView) = viewModelScope.launch { settings.update { it.copy(gameListView = v) } }

    /** Favourites, collections and "All games" mix systems, so rows name the platform. */
    val showsSeveralPlatforms: Boolean get() = platformId == null

    fun platformLabel(game: GameSummary): String? = library.platform(game.platformId)?.shortName

    fun setSort(s: GameSort) { sort.value = s }
    fun cycleSort() {
        val all = GameSort.entries
        sort.value = all[(all.indexOf(sort.value) + 1) % all.size]
    }

    fun launch(game: GameSummary) = viewModelScope.launch { actions.launch(game.id) }
    fun openMenu(game: GameSummary) = menuController.open(game)
    fun openMenuForFocused() { _focused.value?.let { openMenu(it) } }

}

fun GameSort.label(): String = when (this) {
    GameSort.TITLE -> "Title"
    GameSort.LAST_PLAYED -> "Last played"
    GameSort.MOST_PLAYED -> "Most played"
    GameSort.RECENTLY_ADDED -> "Recently added"
    GameSort.RELEASE_YEAR -> "Release year"
    GameSort.RATING -> "Rating"
    GameSort.USER_RATING -> "Your rating"
}
