package io.vela.core.data.usecase

import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.GameRepository
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameMenuAction
import io.vela.core.model.GameMenuActions
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.LaunchOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the shared game context menu. One instance per ViewModel: the owner calls [attach] with
 * its scope, hands the controller to `GameMenuHost` as [GameMenuActions], renders [state] and
 * reacts to [events] (navigation).
 */
class GameMenuController @Inject constructor(
    private val games: GameRepository,
    private val collections: CollectionRepository,
    private val actions: GameActions,
    private val play: PlayGame,
) : GameMenuActions {
    private lateinit var scope: CoroutineScope

    private val _state = MutableStateFlow<GameMenuState>(GameMenuState.Hidden)
    val state: StateFlow<GameMenuState> = _state.asStateFlow()

    private val _events = Channel<GameMenuEvent>(Channel.BUFFERED)
    val events: Flow<GameMenuEvent> = _events.receiveAsFlow()

    val isOpen: Boolean get() = _state.value != GameMenuState.Hidden

    /** Binds the menu's work to its owner's lifetime; call once, from the ViewModel. */
    fun attach(scope: CoroutineScope): GameMenuController = apply { this.scope = scope }

    fun open(game: GameSummary) {
        scope.launch { showContext(game) }
    }

    /** Opens the menu straight on one of its pages (game details: Launch with, Collections…). */
    fun openAt(game: GameSummary, action: GameMenuAction) {
        scope.launch {
            showContext(game)
            perform(action)
        }
    }

    override fun onAction(action: GameMenuAction) {
        scope.launch { perform(action) }
    }

    override fun onRate(stars: Int?) {
        val game = currentGame() ?: return
        dismiss()
        scope.launch { actions.setUserRating(game.id, stars?.takeIf { it > 0 }) }
    }

    override fun onDismiss() = dismiss()

    override fun onToggleCollection(id: CollectionId) {
        val s = _state.value as? GameMenuState.Collections ?: return
        scope.launch {
            actions.toggleCollection(id, s.game.id, id in s.memberOf)
            showCollections(s.game)
        }
    }

    override fun onStartNewCollection() {
        val game = currentGame() ?: return
        _state.value = GameMenuState.NewCollection(game)
    }

    override fun onCreateCollection(name: String) {
        val game = currentGame() ?: return
        scope.launch {
            actions.createCollectionWith(name, game.id)
            showCollections(game)
        }
    }

    override fun onLaunchWith(option: LaunchOption, remember: Boolean) {
        val game = currentGame() ?: return
        dismiss()
        scope.launch { actions.launchWith(game.id, option.playerId, option.coreId, remember) }
    }

    override fun onSetCompletion(status: CompletionStatus) {
        val game = currentGame() ?: return
        dismiss()
        scope.launch { actions.setCompletion(game.id, status) }
    }

    override fun onConfirmHide() {
        val game = currentGame() ?: return
        dismiss()
        scope.launch {
            actions.hide(game.id)
            _events.send(GameMenuEvent.Hidden(game))
        }
    }

    fun dismiss() {
        _state.value = GameMenuState.Hidden
    }

    private suspend fun showContext(game: GameSummary) {
        val full = games.game(game.id)
        _state.value = GameMenuState.Context(game, full?.completion ?: CompletionStatus.NONE)
    }

    private suspend fun perform(action: GameMenuAction) {
        val game = currentGame() ?: return
        when (action) {
            GameMenuAction.PLAY -> { dismiss(); actions.launch(game.id) }
            GameMenuAction.DETAILS -> { dismiss(); _events.send(GameMenuEvent.OpenDetails(game)) }
            GameMenuAction.FAVORITE -> { dismiss(); actions.toggleFavorite(game.id) }
            GameMenuAction.COLLECTIONS -> showCollections(game)
            GameMenuAction.LAUNCH_WITH -> showLaunchWith(game)
            GameMenuAction.COMPLETION -> {
                val full = games.game(game.id)
                _state.value = GameMenuState.Completion(game, full?.completion ?: CompletionStatus.NONE)
            }
            GameMenuAction.REFRESH_METADATA -> { dismiss(); actions.refreshMetadata(game.id) }
            GameMenuAction.RATE -> _state.value = GameMenuState.Rate(game, game.userRating)
            GameMenuAction.HIDE -> _state.value = GameMenuState.ConfirmHide(game)
        }
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
}
