package io.vela.feature.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vela.core.catalog.PlayerCatalog
import io.vela.core.catalog.ThemeCatalog
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.data.usecase.PlayGame
import io.vela.core.launcher.InstalledPackages
import io.vela.core.model.AppSettings
import io.vela.core.model.LibrarySource
import io.vela.core.model.LibrarySourceId
import io.vela.core.model.MetadataProviderInfo
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformSettings
import io.vela.core.model.PlayerDefinition
import io.vela.core.model.PlayerId
import io.vela.core.model.ScanProgress
import io.vela.core.model.SourceAccess
import io.vela.core.model.ThemeSpec
import io.vela.core.scraper.ScrapeProgress
import io.vela.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SettingsSection(val title: String, val summary: String) {
    LIBRARY("Library", "Folders, scanning, hidden games"),
    PLATFORMS("Systems", "Enable systems and pick emulators"),
    EMULATORS("Emulators", "Installed players and cores"),
    SCRAPING("Metadata", "Artwork and descriptions"),
    APPEARANCE("Appearance", "Theme, motion, clock"),
    CONTROLLER("Controller", "Buttons, sticks, repeat speed"),
    ANDROID("Android apps", "Games and app detection"),
    STORAGE("Storage", "File access and artwork cache"),
    ADVANCED("Advanced", "Default launcher, Android settings"),
}

data class PlayerStatus(val definition: PlayerDefinition, val installedPackage: String?)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val library: LibraryRepository,
    private val scrape: ScrapeRepository,
    private val play: PlayGame,
    private val players: PlayerCatalog,
    private val installed: InstalledPackages,
    val themes: ThemeCatalog,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val sources: StateFlow<List<LibrarySource>> = library.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val platforms: StateFlow<List<PlatformEntry>> = library.observeAllPlatforms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scanProgress: StateFlow<ScanProgress> = library.scanProgress
    val scrapeProgress: StateFlow<ScrapeProgress> = scrape.progress

    private val _artworkBytes = MutableStateFlow(0L)
    val artworkBytes: StateFlow<Long> = _artworkBytes

    val section = MutableStateFlow(SettingsSection.LIBRARY)

    init { refreshStorageStats() }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepository.update(transform) }

    // ---- Library ------------------------------------------------------------------------------

    fun addTreeSource(uri: Uri, platformId: PlatformId?) = viewModelScope.launch {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "Storage" } }.getOrDefault("Folder")
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
    fun toggleSource(source: LibrarySource) = viewModelScope.launch { library.setSourceEnabled(source, !source.enabled) }
    fun scanNow() = library.scanInBackground()
    fun cancelScan() = library.cancelScan()

    /** Common ROM locations that exist right now, offered as one-tap choices. */
    fun suggestedFolders(): List<File> {
        val ext = Environment.getExternalStorageDirectory()
        val candidates = listOf("ROMs", "Roms", "roms", "Games", "Emulation/roms", "Emulation", "RetroArch/roms", "Download").map { File(ext, it) }
        val sd = File("/storage").listFiles()?.filter { it.name != "emulated" && it.name != "self" && it.isDirectory }.orEmpty()
        val sdCandidates = sd.flatMap { root -> listOf("ROMs", "Roms", "roms", "Games").map { File(root, it) } + root }
        return (candidates + sdCandidates).filter { it.isDirectory && it.canRead() }.distinct()
    }

    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun allFilesAccessIntent(): Intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    // ---- Platforms / players ------------------------------------------------------------------

    fun setPlatformEnabled(entry: PlatformEntry, enabled: Boolean) = viewModelScope.launch {
        library.updatePlatformSettings(entry.settings.copy(enabled = enabled))
    }

    fun setPlatformPlayer(entry: PlatformEntry, playerId: PlayerId?, coreId: String?) = viewModelScope.launch {
        library.updatePlatformSettings(entry.settings.copy(playerId = playerId, coreId = coreId))
    }

    fun playerOptions(entry: PlatformEntry): List<PlayerStatus> =
        players.forPlatform(entry.id, entry.platform.defaultPlayers).map { PlayerStatus(it, installed.installedPackage(it)) }

    fun allPlayers(): List<PlayerStatus> = players.players.map { PlayerStatus(it, installed.installedPackage(it)) }

    fun refreshInstalled() = installed.invalidate()

    // ---- Scraping -----------------------------------------------------------------------------

    fun providers(): List<MetadataProviderInfo> = scrape.providers()
    fun scrapeMissing() = scrape.scrapeMissingInBackground()
    fun cancelScrape() = scrape.cancel()

    // ---- Storage ------------------------------------------------------------------------------

    fun refreshStorageStats() = viewModelScope.launch { _artworkBytes.value = scrape.artworkBytes() }

    // ---- Launcher -----------------------------------------------------------------------------

    fun isDefaultLauncher(): Boolean {
        val rm = context.getSystemService(RoleManager::class.java)
        return rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }

    /** Intent that asks Android to make Vela the home app (or opens the chooser on older builds). */
    fun requestHomeRoleIntent(): Intent {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        else Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
    fun androidSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)

    fun themeById(id: String): ThemeSpec = themes.byId(id)

    val appVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"
        }.getOrDefault("dev")
}
