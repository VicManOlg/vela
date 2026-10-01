package io.vela.core.launcher

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.CatalogPlayers
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
 * These are binder calls (the activity index lists every installed package): call from a
 * background dispatcher.
 */
@Singleton
class InstalledPackages @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<String, Boolean>()
    /** Activity class -> installed packages that declare it, built with one PackageManager query. */
    @Volatile private var byActivity: Map<String, List<String>>? = null

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
        val index = byActivity ?: activityIndex().also { byActivity = it }
        return index[activity]?.firstOrNull()
    }

    private fun activityIndex(): Map<String, List<String>> = try {
        context.packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            .filter { it.packageName != context.packageName }
            .flatMap { info -> info.activities.orEmpty().map { it.name to info.packageName } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, packages) -> packages.distinct().sorted() }
    } catch (e: Exception) {
        emptyMap()
    }

    fun invalidate() {
        cache.clear()
        byActivity = null
    }
}

/**
 * Picks the emulator (and libretro core) for a game. Precedence: per-game override, per-platform
 * setting, platform defaults in catalog order, any installed `players.json` player that supports
 * the platform, then any *installed* emulator the generated catalogue knows for it
 * ([CatalogPlayers]). Catalogue players only count when their package is installed and their
 * activity resolves, so they never appear as "not installed" noise.
 */
@Singleton
class PlayerResolver @Inject constructor(
    private val players: PlayerCatalog,
    private val platforms: PlatformCatalog,
    private val installed: InstalledPackages,
    private val catalog: CatalogPlayers,
    private val availability: EmulatorAvailability,
) {
    /** A player by id from either source. */
    fun definition(id: PlayerId): PlayerDefinition? = players[id] ?: catalog[id]

    /** Catalogue players for [platform] whose package is installed and whose activity exists. */
    fun catalogCandidates(platform: Platform): List<PlayerDefinition> =
        catalog.forPlatform(platform.id).filter { def ->
            val pkg = def.packages.firstOrNull { installed.isInstalled(it) } ?: return@filter false
            val activity = def.activity ?: return@filter true
            availability.activityExists(pkg, activity, def.action)
        }

    /** Every installed catalogue player across platforms (Settings > Emulators). */
    fun installedCatalogPlayers(): List<PlayerDefinition> =
        platforms.platforms.flatMap { catalogCandidates(it) }.distinctBy { it.id }

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
            explicit?.let { id -> definition(id)?.let(::add) }
            addAll(players.forPlatform(platform.id, platform.defaultPlayers))
            addAll(catalogCandidates(platform))
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
        players.forPlatform(platform.id, platform.defaultPlayers).map { it to installed.installedPackage(it) } +
            catalogCandidates(platform).map { it to installed.installedPackage(it) }

    fun coresFor(playerId: PlayerId, platform: Platform): List<CoreOption> = definition(playerId)?.coresFor(platform.id).orEmpty()

    private fun pickCore(player: PlayerDefinition, platform: Platform, game: Game, settings: PlatformSettings?): CoreOption? {
        val cores = player.coresFor(platform.id)
        if (cores.isEmpty()) return null
        val wanted = game.coreOverride ?: settings?.coreId
        return cores.firstOrNull { it.id == wanted } ?: cores.first()
    }
}
