package io.vela.feature.library

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
import io.vela.core.launcher.PlayerResolver
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.LaunchOption
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
    val menu: GameMenuController,
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
                    is PlayerResolver.Resolution.Ready -> resolution.player.definition.name + (resolution.player.core?.let { " · ${it.name}" } ?: "")
                    is PlayerResolver.Resolution.NotInstalled -> resolution.player.name
                    PlayerResolver.Resolution.NoCandidate -> null
                    null -> "Android"
                },
                playerMissing = resolution is PlayerResolver.Resolution.NotInstalled || resolution is PlayerResolver.Resolution.NoCandidate,
                otherVersions = games.duplicatesOf(g),
                franchise = games.sameFranchise(g),
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameDetailUiState())

    val menuState: StateFlow<GameMenuState> = menu.state

    fun launch() = viewModelScope.launch { actions.launch(gameId) }
    fun toggleFavorite() = viewModelScope.launch { actions.toggleFavorite(gameId) }
    fun refreshMetadata() = viewModelScope.launch { actions.refreshMetadata(gameId) }
    fun setCompletion(status: CompletionStatus) = viewModelScope.launch { actions.setCompletion(gameId, status); menu.dismiss() }
    fun setUserRating(rating: Int?) = viewModelScope.launch { actions.setUserRating(gameId, rating) }

    fun openMenu() = viewModelScope.launch { state.value.game?.let { menu.open(it.toSummary()) } }
    fun openLaunchWith() = viewModelScope.launch { state.value.game?.let { menu.open(it.toSummary()); menu.onAction("LAUNCH_WITH") } }
    fun openCollections() = viewModelScope.launch { state.value.game?.let { menu.open(it.toSummary()); menu.onAction("COLLECTIONS") } }
    fun openCompletion() = viewModelScope.launch { state.value.game?.let { menu.open(it.toSummary()); menu.onAction("COMPLETION") } }

    fun onMenuAction(action: String) = viewModelScope.launch { menu.onAction(action) }
    fun toggleCollection(id: CollectionId) = viewModelScope.launch { menu.toggleCollection(id) }
    fun startNewCollection() = menu.startNewCollection()
    fun createCollection(name: String) = viewModelScope.launch { menu.createCollection(name) }
    fun launchWith(option: LaunchOption, remember: Boolean) = viewModelScope.launch { menu.launchWith(option, remember) }
    fun confirmHide(onHidden: () -> Unit) = viewModelScope.launch { menu.confirmHide(); onHidden() }
    fun dismissMenu() = menu.dismiss()

    private fun Game.toSummary() = GameSummary(
        id = id, platformId = platformId, kind = kind, title = displayTitle,
        boxArt = artwork[io.vela.core.model.ArtworkType.BOX_FRONT], logo = artwork[io.vela.core.model.ArtworkType.LOGO],
        background = artwork[io.vela.core.model.ArtworkType.BACKGROUND] ?: artwork[io.vela.core.model.ArtworkType.SCREENSHOT],
        favorite = favorite, lastPlayedAt = lastPlayedAt, playCount = playCount, totalPlayTimeMs = totalPlayTimeMs,
        packageName = (location as? io.vela.core.model.GameLocation.AndroidApp)?.packageName,
        userRating = userRating,
    )
}
