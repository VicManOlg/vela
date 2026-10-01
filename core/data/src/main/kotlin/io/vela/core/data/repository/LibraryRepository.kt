package io.vela.core.data.repository

import io.vela.core.catalog.PlatformCatalog
import io.vela.core.common.DispatcherProvider
import io.vela.core.data.mapper.toDomain
import io.vela.core.data.mapper.toEntity
import io.vela.core.database.dao.GameDao
import io.vela.core.database.dao.LibraryDao
import io.vela.core.database.entity.LibrarySourceEntity
import io.vela.core.model.LibrarySource
import io.vela.core.model.LibrarySourceId
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformKind
import io.vela.core.model.PlatformSettings
import io.vela.core.model.ScanProgress
import io.vela.core.model.ScanResult
import io.vela.core.model.SourceAccess
import io.vela.core.scanner.LibraryScanner
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import io.vela.core.scraper.store.PlatformIconStore
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A platform as the UI needs it: catalog definition + user settings + how many games it holds. */
data class PlatformEntry(
    val platform: Platform,
    val settings: PlatformSettings,
    val gameCount: Int,
    /** Local path of the system icon for the current theme's icon set, when downloaded. */
    val iconPath: String? = null,
) {
    val id: PlatformId get() = platform.id
    val displayName: String get() = settings.customName ?: platform.name
}

/** Up to three recent covers and one scene of a system, for its Library card and the backdrop. */
data class PlatformArt(val covers: List<String>, val background: String?)

@Singleton
class LibraryRepository @Inject constructor(
    private val catalog: PlatformCatalog,
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val scanner: LibraryScanner,
    private val settingsRepository: SettingsRepository,
    private val platformIcons: PlatformIconStore,
    private val dispatchers: DispatcherProvider,
) {
    val scanProgress: StateFlow<ScanProgress> = scanner.progress
    val isScanning: Boolean get() = scanner.isRunning

    /** Emulated platforms that have at least one game, enabled, in catalog order. */
    fun observePlatformsWithGames(): Flow<List<PlatformEntry>> =
        observeAllPlatforms().map { list -> list.filter { it.gameCount > 0 && it.settings.enabled && it.platform.kind == PlatformKind.EMULATED } }

    /** Every catalog platform with its settings and count (Settings > Platforms). */
    fun observeAllPlatforms(): Flow<List<PlatformEntry>> =
        combine(gameDao.observePlatformCounts(), libraryDao.observePlatformSettings(), platformIcons.icons) { counts, settings, icons ->
            val countById = counts.associate { it.platformId to it.count }
            val settingsById = settings.associateBy { it.platformId }
            catalog.platforms.map { p ->
                PlatformEntry(
                    platform = p,
                    settings = settingsById[p.id.value]?.toDomain() ?: PlatformSettings(p.id),
                    gameCount = countById[p.id.value] ?: 0,
                    iconPath = icons[p.id],
                )
            }
        }

    fun observePlatformArt(): Flow<Map<PlatformId, PlatformArt>> = gameDao.observePlatformArt().map { rows ->
        rows.groupBy { it.platformId }.mapKeys { PlatformId(it.key) }.mapValues { (_, list) ->
            PlatformArt(covers = list.mapNotNull { it.boxArt }.take(3), background = list.firstNotNullOfOrNull { it.background })
        }
    }

    fun observePlatform(id: PlatformId): Flow<PlatformEntry?> = observeAllPlatforms().map { list -> list.firstOrNull { it.id == id } }

    fun platform(id: PlatformId): Platform? = catalog[id]

    suspend fun platformSettings(id: PlatformId): PlatformSettings = withContext(dispatchers.io) {
        libraryDao.platformSettings(id.value)?.toDomain() ?: PlatformSettings(id)
    }

    suspend fun updatePlatformSettings(settings: PlatformSettings) = withContext(dispatchers.io) {
        libraryDao.upsertPlatformSettings(settings.toEntity())
    }

    // ---- Sources ------------------------------------------------------------------------------

    fun observeSources(): Flow<List<LibrarySource>> = libraryDao.observeSources().map { it.map { s -> s.toDomain() } }

    suspend fun addSource(uri: String, displayName: String, access: SourceAccess, platformId: PlatformId?): LibrarySourceId =
        withContext(dispatchers.io) {
            val id = libraryDao.insertSource(
                LibrarySourceEntity(uri = uri, displayName = displayName, access = access.name, platformId = platformId?.value),
            )
            LibrarySourceId(id)
        }

    suspend fun removeSource(id: LibrarySourceId) = scanner.removeSource(id.value)

    suspend fun setSourceEnabled(source: LibrarySource, enabled: Boolean) = withContext(dispatchers.io) {
        libraryDao.updateSource(source.toEntity().copy(enabled = enabled))
    }

    /**
     * Pins the folder to one system (or back to folder-name detection with null). Games already
     * scanned keep their ids, favourites and play time; the next [scanSource] moves them to the
     * right platform.
     */
    suspend fun setSourcePlatform(source: LibrarySource, platformId: PlatformId?) = withContext(dispatchers.io) {
        libraryDao.updateSource(source.toEntity().copy(platformId = platformId?.value))
    }

    private fun LibrarySource.toEntity() = LibrarySourceEntity(
        id = id.value, uri = uri, displayName = displayName, access = access.name,
        platformId = platformId?.value, recursive = recursive, enabled = enabled,
        lastScanAt = lastScanAt, lastScanGameCount = lastScanGameCount,
    )

    // ---- Scanning -----------------------------------------------------------------------------

    fun scanInBackground() {
        scanner.scanAllInBackground(purgeMissing = false)
    }

    suspend fun scanNow(): ScanResult {
        val purge = settingsRepository.current().purgeMissingGames
        return scanner.scanAll(purgeMissing = purge)
    }

    suspend fun scanSource(id: LibrarySourceId): ScanResult = scanner.scanSource(id.value)

    fun cancelScan() = scanner.cancel()
}
