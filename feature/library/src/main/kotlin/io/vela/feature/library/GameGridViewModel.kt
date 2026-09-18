package io.vela.feature.library

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
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSort
import io.vela.core.model.GameSummary
import io.vela.core.model.LaunchOption
import io.vela.core.model.PlatformId
import io.vela.core.settings.SettingsRepository
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
)

@HiltViewModel
class GameGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val library: LibraryRepository,
    private val collections: CollectionRepository,
    settings: SettingsRepository,
    private val actions: GameActions,
    val menu: GameMenuController,
) : ViewModel() {

    val route: GameGridRoute = savedStateHandle.toRoute()
    private val platformId = route.platformId?.let(::PlatformId)
    private val collectionId = route.collectionId?.let(::CollectionId)

    private val sort = MutableStateFlow(if (route.favorites || collectionId != null) GameSort.TITLE else GameSort.TITLE)
    val focused = MutableStateFlow<GameSummary?>(null)

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
        val count = collection?.gameCount ?: entry?.gameCount ?: 0
        GameGridHeader(
            title = route.title ?: collection?.name ?: entry?.displayName ?: if (route.favorites) "Favorites" else "All games",
            subtitle = listOfNotNull(entry?.platform?.manufacturer, if (count == 1) "1 game" else "$count games").joinToString("   "),
            accent = collection?.accentColor ?: entry?.platform?.accentColor ?: 0xFF7FD7FF,
            sort = s,
            count = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameGridHeader(route.title ?: "", "", 0xFF7FD7FF, GameSort.TITLE, 0))

    val menuState: StateFlow<GameMenuState> = menu.state

    fun setSort(s: GameSort) { sort.value = s }
    fun cycleSort() {
        val all = GameSort.entries
        sort.value = all[(all.indexOf(sort.value) + 1) % all.size]
    }

    fun launch(game: GameSummary) = viewModelScope.launch { actions.launch(game.id) }
    fun openMenu(game: GameSummary) = viewModelScope.launch { menu.open(game) }
    fun openMenuForFocused() { focused.value?.let { openMenu(it) } }

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
}

fun GameSort.label(): String = when (this) {
    GameSort.TITLE -> "Title"
    GameSort.LAST_PLAYED -> "Last played"
    GameSort.MOST_PLAYED -> "Most played"
    GameSort.RECENTLY_ADDED -> "Recently added"
    GameSort.RELEASE_YEAR -> "Release year"
    GameSort.RATING -> "Rating"
}
