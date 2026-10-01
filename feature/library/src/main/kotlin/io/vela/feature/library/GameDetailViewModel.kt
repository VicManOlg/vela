package io.vela.feature.library

import io.vela.core.model.toSummary
import kotlinx.coroutines.flow.Flow
import io.vela.core.model.GameMenuAction
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuActions
import io.vela.core.model.PlayerResolution
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.GameMenuController
import io.vela.core.data.usecase.PlayGame
import io.vela.core.model.CompletionStatus
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.Platform
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameDetailUiState(
    val game: Game? = null,
    val platform: Platform? = null,
    val playerName: String? = null,
    val playerMissing: Boolean = false,
    val otherVersions: List<GameSummary> = emptyList(),
    val franchise: List<GameSummary> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val games: GameRepository,
    private val library: LibraryRepository,
    private val play: PlayGame,
    private val actions: GameActions,
    private val menuController: GameMenuController,
) : ViewModel() {

    private val route: GameDetailRoute = savedStateHandle.toRoute()
    val gameId = GameId(route.gameId)

    val state: StateFlow<GameDetailUiState> = games.observeGame(gameId).flatMapLatest { game ->
        if (game == null) return@flatMapLatest flowOf(GameDetailUiState(loading = false))
        val platform = library.platform(game.platformId)
        combine(flowOf(game), library.observePlatform(game.platformId)) { g, _ -> g }.map { g ->
            val resolution = if (g.kind == GameKind.ROM) play.currentPlayer(g) else null
            GameDetailUiState(
                game = g,
                platform = platform,
                playerName = when (resolution) {
                    is PlayerResolution.Ready -> resolution.player.definition.name + (resolution.player.core?.let { " · ${it.name}" } ?: "")
                    is PlayerResolution.NotInstalled -> resolution.player.name
                    PlayerResolution.NoCandidate -> null
                    null -> "Android"
                },
                playerMissing = resolution is PlayerResolution.NotInstalled || resolution is PlayerResolution.NoCandidate,
                otherVersions = games.duplicatesOf(g),
                franchise = games.sameFranchise(g),
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameDetailUiState())

    val menu: GameMenuActions = menuController.attach(viewModelScope)
    val menuState: StateFlow<GameMenuState> = menuController.state
    val menuEvents: Flow<GameMenuEvent> = menuController.events

    fun launch() = viewModelScope.launch { actions.launch(gameId) }
    fun toggleFavorite() = viewModelScope.launch { actions.toggleFavorite(gameId) }
    fun refreshMetadata() = viewModelScope.launch { actions.refreshMetadata(gameId) }
    fun setCompletion(status: CompletionStatus) = viewModelScope.launch { actions.setCompletion(gameId, status); menuController.dismiss() }
    fun setUserRating(rating: Int?) = viewModelScope.launch { actions.setUserRating(gameId, rating) }

    fun openMenu() { state.value.game?.let { menuController.open(it.toSummary()) } }
    fun openLaunchWith() { state.value.game?.let { menuController.openAt(it.toSummary(), GameMenuAction.LAUNCH_WITH) } }
    fun openCollections() { state.value.game?.let { menuController.openAt(it.toSummary(), GameMenuAction.COLLECTIONS) } }
    fun openCompletion() { state.value.game?.let { menuController.openAt(it.toSummary(), GameMenuAction.COMPLETION) } }


}
