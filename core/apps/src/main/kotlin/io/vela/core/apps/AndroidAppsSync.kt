package io.vela.core.apps

import io.vela.core.common.DispatcherProvider
import io.vela.core.common.TitleCleaner
import io.vela.core.database.dao.GameDao
import io.vela.core.database.entity.GameEntity
import io.vela.core.database.entity.LocationType
import io.vela.core.model.GameKind
import io.vela.core.model.PlatformId
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors installed Android games into the `games` table (platform `android`) so they share
 * favourites, recents, collections and search with ROMs. Apps the user pins explicitly live in
 * platform `android_apps`. Uninstalled packages are removed; user edits (favourite, hidden,
 * moved between games/apps) are preserved across syncs because existing rows are never rewritten.
 */
@Singleton
class AndroidAppsSync @Inject constructor(
    private val source: InstalledAppsSource,
    private val gameDao: GameDao,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun sync(autoDetectGames: Boolean, includeSystem: Boolean) = withContext(dispatchers.io) {
        val installed = source.installedApps(includeSystem = true)
        val installedByPackage = installed.associateBy { it.packageName }
        val existing = gameDao.androidApps()
        val existingPackages = existing.map { it.locationValue }.toSet()

        val removed = existing.filter { it.locationValue !in installedByPackage }.map { it.locationValue }
        val now = System.currentTimeMillis()
        val additions = if (autoDetectGames) {
            installed.filter { it.isGame && it.packageName !in existingPackages && (!it.isSystem || includeSystem) }
                .map { it.toEntity(PlatformId.ANDROID, now) }
        } else {
            emptyList()
        }
        gameDao.replaceAndroidApps(additions, removed)
        Timber.i("Android apps synced: +%d -%d", additions.size, removed.size)
    }

    /** Adds one package as a game or as an app. Re-adding moves it between the two sections. */
    suspend fun add(packageName: String, asGame: Boolean) = withContext(dispatchers.io) {
        val app = source.installedApps(includeSystem = true).firstOrNull { it.packageName == packageName }
            ?: return@withContext
        val platform = if (asGame) PlatformId.ANDROID else PlatformId.ANDROID_APPS
        val current = gameDao.findByLocation(LocationType.ANDROID_APP, packageName)
        if (current == null) {
            gameDao.insertIgnore(app.toEntity(platform, System.currentTimeMillis()))
        } else {
            gameDao.setPlatform(current.id, platform.value)
            gameDao.setHidden(current.id, false)
        }
    }

    suspend fun remove(packageName: String) = withContext(dispatchers.io) {
        gameDao.findByLocation(LocationType.ANDROID_APP, packageName)?.let { gameDao.setHidden(it.id, true) }
    }

    private fun io.vela.core.model.InstalledApp.toEntity(platform: PlatformId, now: Long): GameEntity {
        val sort = TitleCleaner.sortKey(label)
        return GameEntity(
            platformId = platform.value,
            kind = GameKind.ANDROID_APP.name,
            title = label,
            sortTitle = sort,
            locationType = LocationType.ANDROID_APP,
            locationValue = packageName,
            locationExtra = activity,
            fileName = packageName,
            addedAt = now,
            lastModified = updatedAt,
            duplicateKey = "${platform.value}:$packageName",
        )
    }
}
