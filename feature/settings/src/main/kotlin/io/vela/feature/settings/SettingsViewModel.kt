package io.vela.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.data.system.HomeAppRole
import io.vela.core.data.system.StorageAccess
import io.vela.core.data.usecase.PlayGame
import io.vela.core.model.AppSettings
import io.vela.core.model.AppearanceOverrides
import io.vela.core.model.LibrarySource
import io.vela.core.model.LibrarySourceId
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.PlatformId
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import io.vela.core.model.ScanProgress
import io.vela.core.model.SourceAccess
import io.vela.core.model.ThemeSpec
import io.vela.core.model.ScrapeProgress
import io.vela.core.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import io.vela.core.data.repository.ThemeRepository

enum class SettingsSection(val title: String, val summary: String) {
    LIBRARY("Library", "Folders, scanning, hidden games"),
    PLATFORMS("Systems", "Enable systems and pick emulators"),
    EMULATORS("Emulators", "Installed players and cores"),
    SCRAPING("Metadata", "Artwork and descriptions"),
    APPEARANCE("Appearance", "Theme, colours, cards, motion"),
    CONTROLLER("Controller", "Buttons, sticks, repeat speed"),
    ANDROID("Android apps", "Games and app detection"),
    STORAGE("Storage", "File access and artwork cache"),
    ADVANCED("Advanced", "Default launcher, Android settings"),
}

data class PlayerStatus(val definition: PlayerDefinition, val installedPackage: String?)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    private val storage: StorageAccess,
    private val homeRole: HomeAppRole,
    private val library: LibraryRepository,
    private val scrape: ScrapeRepository,
    private val play: PlayGame,
    val themes: ThemeRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsRepository.loaded)
    val sources: StateFlow<List<LibrarySource>> = library.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val platforms: StateFlow<List<PlatformEntry>> = library.observeAllPlatforms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scanProgress: StateFlow<ScanProgress> = library.scanProgress
    val scrapeProgress: StateFlow<ScrapeProgress> = scrape.progress

    private val _artworkBytes = MutableStateFlow(0L)
    val artworkBytes: StateFlow<Long> = _artworkBytes

    private val _section = MutableStateFlow(SettingsSection.LIBRARY)
    val section: StateFlow<SettingsSection> = _section.asStateFlow()
    fun selectSection(value: SettingsSection) { _section.value = value }

    init { refreshStorageStats() }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepository.update(transform) }

    // ---- Library ------------------------------------------------------------------------------

    fun addTreeSource(uri: Uri, platformId: PlatformId?) = viewModelScope.launch {
        val name = storage.persistTree(uri)
        val id = library.addSource(uri.toString(), name, SourceAccess.DOCUMENT_TREE, platformId)
        library.scanSource(id)
    }

    fun addPathSource(path: String, platformId: PlatformId?) = viewModelScope.launch {
        val file = File(path)
        if (!file.isDirectory) return@launch
        val id = library.addSource(file.absolutePath, file.name.ifEmpty { path }, SourceAccess.FILE, platformId)
        library.scanSource(id)
    }

    fun removeSource(id: LibrarySourceId) = viewModelScope.launch { library.removeSource(id) }
    fun setSourcePlatform(source: LibrarySource, platformId: PlatformId?) = viewModelScope.launch {
        library.setSourcePlatform(source, platformId)
        library.scanSource(source.id)
    }
    fun toggleSource(source: LibrarySource) = viewModelScope.launch { library.setSourceEnabled(source, !source.enabled) }
    fun scanNow() = library.scanInBackground()
    fun rescanSource(source: LibrarySource) = viewModelScope.launch { library.scanSource(source.id) }
    fun cancelScan() = library.cancelScan()

    suspend fun suggestedFolders(): List<File> = storage.suggestedFolders()

    fun hasAllFilesAccess(): Boolean = storage.hasAllFilesAccess()

    fun allFilesAccessIntent(): Intent = storage.allFilesAccessIntent()

    // ---- Platforms / players ------------------------------------------------------------------

    fun setPlatformEnabled(entry: PlatformEntry, enabled: Boolean) = viewModelScope.launch {
        library.updatePlatformSettings(entry.settings.copy(enabled = enabled))
    }

    fun setPlatformPlayer(entry: PlatformEntry, playerId: PlayerId?, coreId: String?) = viewModelScope.launch {
        library.updatePlatformSettings(entry.settings.copy(playerId = playerId, coreId = coreId))
    }

    suspend fun playerOptions(entry: PlatformEntry): List<PlayerStatus> =
        play.optionsFor(entry.platform).map { PlayerStatus(it.definition, it.installedPackage) }

    fun playerName(id: PlayerId): String? = play.playerName(id)

    private val _players = MutableStateFlow<List<PlayerStatus>?>(null)
    /** Settings > Emulators; null until the first lookup finishes. */
    val players: StateFlow<List<PlayerStatus>?> = _players.asStateFlow()

    fun refreshPlayers() = viewModelScope.launch {
        _players.value = play.allPlayers().map { PlayerStatus(it.definition, it.installedPackage) }
    }

    // ---- Scraping -----------------------------------------------------------------------------

    fun providers(): List<MetadataProviderInfo> = scrape.providers()
    fun scrapeMissing() = scrape.scrapeMissingInBackground()
    fun cancelScrape() = scrape.cancel()

    // ---- Storage ------------------------------------------------------------------------------

    fun refreshStorageStats() = viewModelScope.launch { _artworkBytes.value = scrape.artworkBytes() }

    // ---- Launcher -----------------------------------------------------------------------------

    fun isDefaultLauncher(): Boolean = homeRole.isDefaultLauncher()
    fun requestHomeRoleIntent(): Intent = homeRole.requestIntent()
    fun homeSettingsIntent(): Intent = homeRole.homeSettingsIntent()
    fun androidSettingsIntent(): Intent = homeRole.androidSettingsIntent()

    fun themeById(id: String): ThemeSpec = themes.byId(id)
    fun reloadThemes() = themes.reload()
    fun importTheme(uri: Uri) = viewModelScope.launch {
        themes.import(uri)
            .onSuccess { spec -> settingsRepository.update { it.copy(themeId = spec.id) } }
    }
    /** Changes the user's tweaks layered over the theme (Settings > Appearance). */
    fun updateAppearance(transform: (AppearanceOverrides) -> AppearanceOverrides) = update { it.copy(appearance = transform(it.appearance)) }
    fun resetAppearance() = update { it.copy(appearance = AppearanceOverrides(), gridColumns = 0) }
    fun resetCustomTheme() = viewModelScope.launch { themes.clearCustom() }

    val appVersion: String get() = homeRole.appVersion
}
