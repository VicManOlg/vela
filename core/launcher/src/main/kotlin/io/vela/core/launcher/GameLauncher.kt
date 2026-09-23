package io.vela.core.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.ApplicationScope
import io.vela.core.common.DispatcherProvider
import io.vela.core.common.Outcome
import io.vela.core.common.VelaError
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.PlaySessionDao
import io.vela.core.database.entity.PlaySessionEntity
import io.vela.core.model.Game
import io.vela.core.model.GameId
import io.vela.core.model.GameLocation
import io.vela.core.model.PlatformSettings
import io.vela.core.model.ResolvedPlayer
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI learns after a successful launch. */
data class LaunchedGame(val game: Game, val player: ResolvedPlayer?)

/**
 * Tracks the game currently in the foreground. Android never tells a frontend when an emulator
 * exits, so the session ends when our process comes back to the foreground (ES-DE does the same).
 */
@Singleton
class PlaySessionTracker @Inject constructor(
    private val gameDao: GameDao,
    private val sessionDao: PlaySessionDao,
    private val settings: SettingsRepository,
    private val scope: ApplicationScope,
    private val dispatchers: DispatcherProvider,
) : DefaultLifecycleObserver {

    data class ActiveSession(val gameId: GameId, val sessionId: Long, val startedAt: Long)

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { recoverOpenSessions() }
    }

    /**
     * The process is routinely killed while an emulator runs. Any session left open in the
     * database is closed now, capped like a live one, so the play time is not lost.
     */
    private suspend fun recoverOpenSessions() = withContext(dispatchers.io) {
        runCatching {
            val capMs = settings.current().playTimeCapMinutes * 60_000L
            val now = System.currentTimeMillis()
            var open = sessionDao.openSession()
            var guard = 0
            while (open != null && guard++ < 50) {
                val duration = (now - open.startedAt).coerceIn(0, capMs)
                sessionDao.end(open.id, open.startedAt + duration)
                gameDao.addPlayTime(open.gameId, duration)
                Timber.i("Recovered session for game %d: %d s", open.gameId, duration / 1000)
                open = sessionDao.openSession()
            }
        }.onFailure { Timber.w(it, "Session recovery failed") }
    }

    /** Opens a session right before the emulator is started. */
    suspend fun begin(gameId: GameId) = withContext(dispatchers.io) {
        _active.value?.let { endInternal(it) }
        val now = System.currentTimeMillis()
        val id = sessionDao.insert(PlaySessionEntity(gameId = gameId.value, startedAt = now))
        gameDao.recordLaunch(gameId.value, now)
        _active.value = ActiveSession(gameId, id, now)
    }

    /** Called when `startActivity` failed: the session never really started. */
    suspend fun abort() = withContext(dispatchers.io) {
        val session = _active.value ?: return@withContext
        _active.value = null
        sessionDao.end(session.sessionId, session.startedAt)
    }

    /** Our process is back in the foreground: whatever was running has been left. */
    override fun onStart(owner: LifecycleOwner) {
        val session = _active.value ?: return
        scope.launch { endInternal(session) }
    }

    private suspend fun endInternal(session: ActiveSession) {
        // Atomic: begin() and onStart() may race for the same session.
        if (!_active.compareAndSet(session, null)) return
        val now = System.currentTimeMillis()
        val capMs = settings.current().playTimeCapMinutes * 60_000L
        val duration = (now - session.startedAt).coerceIn(0, capMs)
        sessionDao.end(session.sessionId, session.startedAt + duration)
        gameDao.addPlayTime(session.gameId.value, duration)
        Timber.i("Session for game %s ended after %d s", session.gameId, duration / 1000)
    }
}

/**
 * Entry point used by the UI: resolves the player, builds the intent, grants URI access,
 * starts the activity and opens a play session.
 */
@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: PlayerResolver,
    private val intentBuilder: LaunchIntentBuilder,
    private val sessions: PlaySessionTracker,
    private val platforms: PlatformCatalog,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun launch(game: Game, platformSettings: PlatformSettings?): Outcome<LaunchedGame> = withContext(dispatchers.default) {
        when (val location = game.location) {
            is GameLocation.AndroidApp -> launchAndroidApp(game, location)
            else -> launchWithPlayer(game, platformSettings)
        }
    }

    private suspend fun launchWithPlayer(game: Game, platformSettings: PlatformSettings?): Outcome<LaunchedGame> {
        val platformName = platforms[game.platformId]?.name ?: game.platformId.value
        val resolved = when (val r = resolver.resolve(game, platformSettings)) {
            is PlayerResolver.Resolution.Ready -> r.player
            is PlayerResolver.Resolution.NotInstalled ->
                return Outcome.failure(VelaError.NotInstalled(r.player.packages, r.player.name))
            PlayerResolver.Resolution.NoCandidate -> return Outcome.failure(VelaError.NoPlayer(platformName))
        }
        val prepared = intentBuilder.build(game, resolved, platformSettings)
        if (resolved.definition.requiresFilePath && prepared.intent.hasEmptyPathExtra(resolved)) {
            return Outcome.failure(
                VelaError.LaunchFailed("${resolved.definition.name} needs a real file path. Enable \"All files access\" in Settings > Storage."),
            )
        }
        return start(prepared, game, resolved)
    }

    private suspend fun launchAndroidApp(game: Game, location: GameLocation.AndroidApp): Outcome<LaunchedGame> {
        val pm = context.packageManager
        val activity = location.activity
        val intent = if (activity != null) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(location.packageName, activity))
        } else {
            pm.getLaunchIntentForPackage(location.packageName)
                ?: pm.getLeanbackLaunchIntentForPackage(location.packageName)
                ?: return Outcome.failure(VelaError.NotInstalled(listOf(location.packageName), game.displayTitle))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(PreparedLaunch(intent, location.packageName, emptyList()), game, null)
    }

    private suspend fun start(prepared: PreparedLaunch, game: Game, resolved: ResolvedPlayer?): Outcome<LaunchedGame> {
        prepared.urisToGrant.forEach { uri ->
            runCatching { context.grantUriPermission(prepared.targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { Timber.w(it, "grantUriPermission failed for %s", uri) }
        }
        return try {
            withContext(dispatchers.main) { context.startActivity(prepared.intent) }
            // Only a launch that really started counts as a play.
            sessions.begin(game.id)
            Timber.i("Launched %s with %s", game.displayTitle, prepared.targetPackage)
            Outcome.success(LaunchedGame(game, resolved))
        } catch (e: ActivityNotFoundException) {
            sessions.abort()
            Outcome.failure(VelaError.LaunchFailed("${prepared.targetPackage} did not accept the launch intent", e))
        } catch (e: SecurityException) {
            sessions.abort()
            Outcome.failure(VelaError.LaunchFailed("Not allowed to start ${prepared.targetPackage}: ${e.message}", e))
        } catch (e: Exception) {
            sessions.abort()
            Outcome.failure(VelaError.LaunchFailed(e.message ?: "Launch failed", e))
        }
    }

    private fun Intent.hasEmptyPathExtra(resolved: ResolvedPlayer): Boolean =
        resolved.definition.extras.any { it.value.contains("{rom.path}") && getStringExtra(it.key).isNullOrEmpty() }
}
