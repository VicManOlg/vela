package io.vela.core.data.usecase

import io.vela.core.common.Outcome
import io.vela.core.common.VelaError
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.launcher.GameLauncher
import io.vela.core.launcher.LaunchedGame
import io.vela.core.launcher.PlaySessionTracker
import io.vela.core.launcher.PlayerResolver
import io.vela.core.model.CoreOption
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameLocation
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Player choices for a game, as shown in "Launch with..." menus and Settings > Emulators. */
data class PlayerOption(
    val definition: PlayerDefinition,
    val installedPackage: String?,
    val cores: List<CoreOption>,
    val isCurrent: Boolean,
) {
    val isInstalled: Boolean get() = installedPackage != null
}

/** Facade over the launcher so features do not depend on the launcher module directly. */
@Singleton
class PlayGame @Inject constructor(
    private val games: GameRepository,
    private val library: LibraryRepository,
    private val launcher: GameLauncher,
    private val resolver: PlayerResolver,
    private val sessions: PlaySessionTracker,
) {
    val activeSession: StateFlow<PlaySessionTracker.ActiveSession?> get() = sessions.active

    suspend operator fun invoke(gameId: GameId): Outcome<LaunchedGame> {
        val game = games.game(gameId) ?: return Outcome.failure(VelaError.Unexpected("Game $gameId no longer exists"))
        return invoke(game)
    }

    suspend operator fun invoke(game: Game): Outcome<LaunchedGame> {
        val settings = if (game.location is GameLocation.AndroidApp) null else library.platformSettings(game.platformId)
        return launcher.launch(game, settings)
    }

    /** Launches once with an explicit player, without persisting the choice. */
    suspend fun launchWith(game: Game, player: PlayerId, coreId: String?): Outcome<LaunchedGame> {
        val settings = library.platformSettings(game.platformId).copy(playerId = player, coreId = coreId)
        return launcher.launch(game.copy(playerOverride = player, coreOverride = coreId), settings)
    }

    /** Which player would run this game right now, if any. */
    suspend fun currentPlayer(game: Game): PlayerResolver.Resolution =
        resolver.resolve(game, library.platformSettings(game.platformId))

    suspend fun optionsFor(game: Game): List<PlayerOption> {
        val platform = library.platform(game.platformId) ?: return emptyList()
        val settings = library.platformSettings(game.platformId)
        val current = (resolver.resolve(game, settings) as? PlayerResolver.Resolution.Ready)?.player?.definition?.id
        return resolver.optionsFor(platform).map { (def, pkg) ->
            PlayerOption(def, pkg, def.coresFor(platform.id), isCurrent = def.id == current)
        }
    }
}
