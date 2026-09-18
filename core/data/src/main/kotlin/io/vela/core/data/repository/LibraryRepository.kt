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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A platform as the UI needs it: catalog definition + user settings + how many games it holds. */
data class PlatformEntry(
    val platform: Platform,
    val settings: PlatformSettings,
    val gameCount: Int,
) {
    val id: PlatformId get() = platform.id
    val displayName: String get() = settings.customName ?: platform.name
}

@Singleton
class LibraryRepository @Inject constructor(
    private val catalog: PlatformCatalog,
    private val libraryDao: LibraryDao,
    private val gameDao: GameDao,
    private val scanner: LibraryScanner,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    val scanProgress: StateFlow<ScanProgress> = scanner.progress
    val isScanning: Boolean get() = scanner.isRunning

    /** Emulated platforms that have at least one game, enabled, in catalog order. */
    fun observePlatformsWithGames(): Flow<List<PlatformEntry>> =
        observeAllPlatforms().map { list -> list.filter { it.gameCount > 0 && it.settings.enabled && it.platform.kind == PlatformKind.EMULATED } }

    /** Every catalog platform with its settings and count (Settings > Platforms). */
    fun observeAllPlatforms(): Flow<List<PlatformEntry>> =
        combine(gameDao.observePlatformCounts(), libraryDao.observePlatformSettings()) { counts, settings ->
            val countById = counts.associate { it.platformId to it.count }
            val settingsById = settings.associateBy { it.platformId }
            catalog.platforms.map { p ->
                PlatformEntry(
                    platform = p,
                    settings = settingsById[p.id.value]?.toDomain() ?: PlatformSettings(p.id),
                    gameCount = countById[p.id.value] ?: 0,
                )
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

    suspend fun removeSource(id: LibrarySourceId) = withContext(dispatchers.io) {
        gameDao.deleteBySource(id.value)
        libraryDao.deleteSource(id.value)
    }

    suspend fun setSourceEnabled(source: LibrarySource, enabled: Boolean) = withContext(dispatchers.io) {
        libraryDao.updateSource(
            LibrarySourceEntity(
                id = source.id.value, uri = source.uri, displayName = source.displayName, access = source.access.name,
                platformId = source.platformId?.value, recursive = source.recursive, enabled = enabled,
                lastScanAt = source.lastScanAt, lastScanGameCount = source.lastScanGameCount,
            ),
        )
    }

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
