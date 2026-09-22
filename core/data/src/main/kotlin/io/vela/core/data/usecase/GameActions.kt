package io.vela.core.data.usecase

import io.vela.core.common.Outcome
import io.vela.core.data.repository.CollectionRepository
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.launcher.LaunchedGame
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameId
import io.vela.core.model.PlayerId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.vela.core.data.mapper.toSummary
import io.vela.core.launcher.PlayerResolver
import io.vela.core.model.Game
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary

/** Something the UI should surface briefly (toast-like) after an action. */
data class UiMessage(val text: String, val isError: Boolean = false)

/** A launch in flight: shown full screen until the emulator takes over or the user is back. */
data class LaunchingGame(val game: GameSummary, val playerName: String?)

/**
 * The verbs every screen offers on a game (Home rails, grids, detail). Centralised so the
 * context menu behaves identically everywhere and messages are worded once.
 */
@Singleton
class GameActions @Inject constructor(
    private val games: GameRepository,
    private val collections: CollectionRepository,
    private val scrape: ScrapeRepository,
    private val play: PlayGame,
) {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _launching = MutableStateFlow<LaunchingGame?>(null)
    val launching: StateFlow<LaunchingGame?> = _launching

    fun clearLaunching() { _launching.value = null }

    suspend fun launch(id: GameId): Outcome<LaunchedGame> {
        games.game(id)?.let { game -> _launching.value = LaunchingGame(game.toSummary(), playerNameFor(game)) }
        val result = play(id)
        result.onFailureMessage()
        if (result is Outcome.Failure) _launching.value = null
        return result
    }

    private suspend fun playerNameFor(game: Game): String? {
        if (game.kind == GameKind.ANDROID_APP) return null
        return (play.currentPlayer(game) as? PlayerResolver.Resolution.Ready)?.player?.definition?.name
    }

    suspend fun launchWith(id: GameId, player: PlayerId, coreId: String?, remember: Boolean): Outcome<LaunchedGame> {
        val game = games.game(id) ?: return Outcome.failure(io.vela.core.common.VelaError.Unexpected("Game not found"))
        if (remember) games.setPlayerOverride(id, player, coreId)
        _launching.value = LaunchingGame(game.toSummary(), null)
        return play.launchWith(game, player, coreId).also { r -> r.onFailureMessage(); if (r is Outcome.Failure) _launching.value = null }
    }

    suspend fun toggleFavorite(id: GameId) {
        val game = games.game(id) ?: return
        games.setFavorite(id, !game.favorite)
        _messages.emit(UiMessage(if (game.favorite) "Removed from favorites" else "Added to favorites"))
    }

    suspend fun setCompletion(id: GameId, status: CompletionStatus) {
        games.setCompletion(id, status)
    }

    suspend fun hide(id: GameId) {
        games.setHidden(id, true)
        _messages.emit(UiMessage("Hidden. Show hidden games from Settings > Library."))
    }

    suspend fun unhide(id: GameId) = games.setHidden(id, false)

    suspend fun toggleCollection(collectionId: CollectionId, gameId: GameId, currentlyMember: Boolean) {
        collections.toggleGame(collectionId, gameId, currentlyMember)
    }

    suspend fun createCollectionWith(name: String, gameId: GameId): CollectionId {
        val id = collections.create(name)
        collections.addGame(id, gameId)
        _messages.emit(UiMessage("Created \"$name\""))
        return id
    }

    suspend fun refreshMetadata(id: GameId) {
        _messages.emit(UiMessage("Looking up metadata…"))
        when (val r = scrape.scrapeGame(id, overwrite = true)) {
            is Outcome.Success -> _messages.emit(UiMessage(if (r.value) "Metadata updated" else "Nothing found for this game"))
            is Outcome.Failure -> _messages.emit(UiMessage(r.error.message, isError = true))
        }
    }

    suspend fun clearPlayerOverride(id: GameId) = games.setPlayerOverride(id, null, null)

    private suspend fun Outcome<LaunchedGame>.onFailureMessage() {
        if (this is Outcome.Failure) _messages.emit(UiMessage(error.message, isError = true))
    }
}
