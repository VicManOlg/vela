package io.vela.core.data.repository

import io.vela.core.apps.AndroidAppsSync
import io.vela.core.apps.InstalledAppsSource
import io.vela.core.data.mapper.toDomain
import io.vela.core.database.dao.GameDao
import io.vela.core.model.GameSummary
import io.vela.core.model.InstalledApp
import io.vela.core.model.PlatformId
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Installed application, plus whether it is already shown in the games or apps section. */
data class AppEntry(val app: InstalledApp, val shownAs: PlatformId?)

@Singleton
class AppsRepository @Inject constructor(
    private val source: InstalledAppsSource,
    private val sync: AndroidAppsSync,
    private val gameDao: GameDao,
    private val settings: SettingsRepository,
) {
    /** Games detected/added by the user, for the Android rail and section. */
    fun observeAndroidGames(limit: Int = 500): Flow<List<GameSummary>> =
        gameDao.observeByPlatformPreview(PlatformId.ANDROID.value, limit).map { it.map { v -> v.toDomain() } }

    fun observeApps(limit: Int = 200): Flow<List<GameSummary>> =
        gameDao.observeByPlatformPreview(PlatformId.ANDROID_APPS.value, limit).map { it.map { v -> v.toDomain() } }

    /** Every launchable app with its current section, for the picker in Settings > Android Apps. */
    suspend fun installedApps(): List<AppEntry> {
        val prefs = settings.current()
        val shown = gameDao.androidApps().filter { !it.hidden }.associate { it.locationValue to PlatformId(it.platformId) }
        return source.installedApps(includeSystem = prefs.showSystemApps).map { AppEntry(it, shown[it.packageName]) }
    }

    suspend fun syncInstalled() {
        val prefs = settings.current()
        sync.sync(autoDetectGames = prefs.autoDetectGames, includeSystem = prefs.showSystemApps)
    }

    suspend fun addAsGame(packageName: String) = sync.add(packageName, asGame = true)
    suspend fun addAsApp(packageName: String) = sync.add(packageName, asGame = false)
    suspend fun remove(packageName: String) = sync.remove(packageName)

    /** Suggested apps (Settings, Chrome, Moonlight...) that are installed and not yet pinned. */
    suspend fun suggestions(): List<Pair<String, String>> {
        val shown = gameDao.androidApps().filter { !it.hidden }.map { it.locationValue }.toSet()
        return InstalledAppsSource.SUGGESTED_APPS.mapNotNull { (label, candidates) ->
            val pkg = candidates.firstOrNull { source.isInstalled(it) } ?: return@mapNotNull null
            if (pkg in shown) null else label to pkg
        }
    }
}
