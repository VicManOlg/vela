package io.vela.core.data.usecase

import io.vela.core.common.Outcome
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.GameRepository
import io.vela.core.launcher.LaunchedGame
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.LaunchOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Drives the shared game context menu. One instance per ViewModel; every screen that shows
 * game cards owns one and renders its state with `GameMenuHost`.
 */
class GameMenuController @Inject constructor(
    private val games: GameRepository,
    private val collections: CollectionRepository,
    private val actions: GameActions,
    private val play: PlayGame,
) {
    private val _state = MutableStateFlow<GameMenuState>(GameMenuState.Hidden)
    val state: StateFlow<GameMenuState> = _state

    val isOpen: Boolean get() = _state.value != GameMenuState.Hidden

    suspend fun open(game: GameSummary) {
        val full = games.game(game.id)
        _state.value = GameMenuState.Context(game, full?.completion ?: CompletionStatus.NONE)
    }

    fun dismiss() {
        _state.value = GameMenuState.Hidden
    }

    /** @return true when the caller should navigate to details. */
    suspend fun onAction(action: String): MenuResult {
        val game = currentGame() ?: return MenuResult.None
        // "RATE:n" comes back from the star picker (n = 0 clears), so every screen's plain
        // string action channel is enough and no extra callback is needed.
        if (action.startsWith("RATE:")) {
            dismiss()
            actions.setUserRating(game.id, action.substringAfter(':').toIntOrNull()?.takeIf { it > 0 })
            return MenuResult.None
        }
        return when (action) {
            "PLAY" -> { dismiss(); actions.launch(game.id); MenuResult.Launched }
            "DETAILS" -> { dismiss(); MenuResult.OpenDetails(game) }
            "FAVORITE" -> { dismiss(); actions.toggleFavorite(game.id); MenuResult.None }
            "COLLECTIONS" -> { showCollections(game); MenuResult.None }
            "LAUNCH_WITH" -> { showLaunchWith(game); MenuResult.None }
            "COMPLETION" -> {
                val full = games.game(game.id)
                _state.value = GameMenuState.Completion(game, full?.completion ?: CompletionStatus.NONE)
                MenuResult.None
            }
            "REFRESH_METADATA" -> { dismiss(); actions.refreshMetadata(game.id); MenuResult.None }
            "RATE" -> { _state.value = GameMenuState.Rate(game, game.userRating); MenuResult.None }
            "HIDE" -> { _state.value = GameMenuState.ConfirmHide(game); MenuResult.None }
            else -> MenuResult.None
        }
    }

    suspend fun toggleCollection(id: CollectionId) {
        val s = _state.value as? GameMenuState.Collections ?: return
        actions.toggleCollection(id, s.game.id, id in s.memberOf)
        showCollections(s.game)
    }

    fun startNewCollection() {
        val game = currentGame() ?: return
        _state.value = GameMenuState.NewCollection(game)
    }

    suspend fun createCollection(name: String) {
        val game = currentGame() ?: return
        actions.createCollectionWith(name, game.id)
        showCollections(game)
    }

    suspend fun launchWith(option: LaunchOption, remember: Boolean): Outcome<LaunchedGame>? {
        val game = currentGame() ?: return null
        dismiss()
        return actions.launchWith(game.id, option.playerId, option.coreId, remember)
    }

    suspend fun setCompletion(status: CompletionStatus) {
        val game = currentGame() ?: return
        actions.setCompletion(game.id, status)
        dismiss()
    }

    suspend fun confirmHide() {
        val game = currentGame() ?: return
        dismiss()
        actions.hide(game.id)
    }

    private fun currentGame(): GameSummary? = when (val s = _state.value) {
        GameMenuState.Hidden -> null
        is GameMenuState.Context -> s.game
        is GameMenuState.Collections -> s.game
        is GameMenuState.NewCollection -> s.game
        is GameMenuState.LaunchWith -> s.game
        is GameMenuState.Completion -> s.game
        is GameMenuState.Rate -> s.game
        is GameMenuState.ConfirmHide -> s.game
    }

    private suspend fun showCollections(game: GameSummary) {
        val all = collections.observeCollections().first()
        val member = collections.observeCollectionsOfGame(game.id).first()
        _state.value = GameMenuState.Collections(game, all, member)
    }

    private suspend fun showLaunchWith(game: GameSummary) {
        val full = games.game(game.id) ?: return
        val options = play.optionsFor(full).flatMap { opt ->
            if (opt.cores.isEmpty()) {
                listOf(LaunchOption(opt.definition.id, opt.definition.name, null, null, opt.isInstalled, opt.isCurrent))
            } else {
                opt.cores.map { core ->
                    LaunchOption(
                        opt.definition.id, opt.definition.name, core.id, core.name, opt.isInstalled,
                        isCurrent = opt.isCurrent && (full.coreOverride ?: opt.cores.first().id) == core.id,
                    )
                }
            }
        }
        _state.value = GameMenuState.LaunchWith(game, options)
    }

    sealed interface MenuResult {
        data object None : MenuResult
        data object Launched : MenuResult
        data class OpenDetails(val game: GameSummary) : MenuResult
    }
}
