package io.vela.core.launcher

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.catalog.PlayerCatalog
import io.vela.core.model.CoreOption
import io.vela.core.model.Game
import io.vela.core.model.Platform
import io.vela.core.model.PlatformSettings
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import io.vela.core.model.ResolvedPlayer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "is this emulator installed?" with a small cache; invalidated on package changes.
 * Renamed builds ("Eden Optimized", custom Yuzu forks…) are found by the emulation activity
 * they expose, so a player definition matches them without knowing their package name.
 */
@Singleton
class InstalledPackages @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<String, Boolean>()
    /** Emulation activity class -> installed packages that declare it, computed on first use. */
    private val byActivity = ConcurrentHashMap<String, List<String>>()

    fun isInstalled(packageName: String): Boolean = cache.getOrPut(packageName) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** First installed package of a player, honouring the definition's preference order, else a fork that ships its activity. */
    fun installedPackage(player: PlayerDefinition): String? = player.packages.firstOrNull(::isInstalled) ?: forkOf(player)

    private fun forkOf(player: PlayerDefinition): String? {
        val activity = player.activity ?: return null
        // Only absolute class names identify an emulator; "{package}.MainActivity" fits anything.
        if (activity.startsWith(".") || activity.contains("{")) return null
        return byActivity.getOrPut(activity) { packagesDeclaring(activity) }.firstOrNull()
    }

    private fun packagesDeclaring(activity: String): List<String> = try {
        context.packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            .filter { info -> info.packageName != context.packageName && info.activities?.any { it.name == activity } == true }
            .map { it.packageName }
            .sorted()
    } catch (e: Exception) {
        emptyList()
    }

    fun invalidate() {
        cache.clear()
        byActivity.clear()
    }
}

/**
 * Picks the emulator (and libretro core) for a game. Precedence: per-game override, per-platform
 * setting, platform defaults in catalog order, then any installed player that supports the platform.
 */
@Singleton
class PlayerResolver @Inject constructor(
    private val players: PlayerCatalog,
    private val platforms: PlatformCatalog,
    private val installed: InstalledPackages,
) {
    sealed interface Resolution {
        data class Ready(val player: ResolvedPlayer) : Resolution

        /** A player is configured or suggested but none of its packages is installed. */
        data class NotInstalled(val player: PlayerDefinition) : Resolution

        data object NoCandidate : Resolution
    }

    fun resolve(game: Game, settings: PlatformSettings?): Resolution {
        val platform = platforms[game.platformId] ?: return Resolution.NoCandidate
        val explicit = game.playerOverride ?: settings?.playerId
        val candidates: List<PlayerDefinition> = buildList {
            explicit?.let { id -> players[id]?.let(::add) }
            addAll(players.forPlatform(platform.id, platform.defaultPlayers))
        }.distinct()

        if (candidates.isEmpty()) return Resolution.NoCandidate

        // If the user explicitly chose a player we do not silently fall back to another one.
        if (explicit != null) {
            val chosen = candidates.first()
            val pkg = installed.installedPackage(chosen) ?: return Resolution.NotInstalled(chosen)
            return Resolution.Ready(ResolvedPlayer(chosen, pkg, pickCore(chosen, platform, game, settings)))
        }

        for (candidate in candidates) {
            val pkg = installed.installedPackage(candidate) ?: continue
            return Resolution.Ready(ResolvedPlayer(candidate, pkg, pickCore(candidate, platform, game, settings)))
        }
        return Resolution.NotInstalled(candidates.first())
    }

    /** All players that could run this platform, with install state, for the Settings UI. */
    fun optionsFor(platform: Platform): List<Pair<PlayerDefinition, String?>> =
        players.forPlatform(platform.id, platform.defaultPlayers).map { it to installed.installedPackage(it) }

    fun coresFor(playerId: PlayerId, platform: Platform): List<CoreOption> = players[playerId]?.coresFor(platform.id).orEmpty()

    private fun pickCore(player: PlayerDefinition, platform: Platform, game: Game, settings: PlatformSettings?): CoreOption? {
        val cores = player.coresFor(platform.id)
        if (cores.isEmpty()) return null
        val wanted = game.coreOverride ?: settings?.coreId
        return cores.firstOrNull { it.id == wanted } ?: cores.first()
    }
}
