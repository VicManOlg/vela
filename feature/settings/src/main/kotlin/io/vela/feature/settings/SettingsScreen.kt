package io.vela.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.model.AppSettings
import io.vela.core.model.ConfirmButton
import io.vela.core.model.LibrarySource
import io.vela.core.model.LibraryView
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformKind
import io.vela.core.model.PlayerId
import io.vela.core.model.ScanProgress
import io.vela.core.model.StorageMode
import io.vela.core.scraper.ScrapeProgress
import io.vela.core.ui.components.ConfirmDialog
import io.vela.core.ui.components.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.SectionHeader
import io.vela.core.ui.components.SettingRow
import io.vela.core.ui.components.TextInputDialog
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.model.HomeLayout

/**
 * Settings: sections on the left, content on the right. L2/R2 switch sections so the user never
 * has to move focus back across the screen.
 */
@Composable
fun SettingsScreen(
    onBackgroundAccent: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val section by viewModel.section.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors

    LaunchedEffect(Unit) { onBackgroundAccent(0xFF3D7BFF) }

    GamepadHandler { button ->
        val sections = SettingsSection.entries
        when (button) {
            GamepadButton.L2 -> { viewModel.section.value = sections[(sections.indexOf(section) - 1 + sections.size) % sections.size]; true }
            GamepadButton.R2 -> { viewModel.section.value = sections[(sections.indexOf(section) + 1) % sections.size]; true }
            else -> false
        }
    }

    Row(modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Column(Modifier.height(96.dp), verticalArrangement = Arrangement.Bottom) {
                Text("Settings", style = VelaTheme.typography.display, color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text("Vela ${viewModel.appVersion}", style = VelaTheme.typography.caption, color = colors.muted)
            }
            Spacer(Modifier.height(12.dp))
            val autoFocus = rememberAutoFocus()
            LazyColumn(Modifier.focusRequester(autoFocus).focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(SettingsSection.entries, key = { it.name }) { s ->
                    SettingRow(
                        title = s.title,
                        description = s.summary,
                        value = if (s == section) "•" else null,
                        onClick = { viewModel.section.value = s },
                        onFocused = { viewModel.section.value = s },
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Spacer(Modifier.height(96.dp + 12.dp))
            LazyColumn(Modifier.fillMaxSize().focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                when (section) {
                    SettingsSection.LIBRARY -> librarySection(viewModel, settings)
                    SettingsSection.PLATFORMS -> platformsSection(viewModel)
                    SettingsSection.EMULATORS -> emulatorsSection(viewModel)
                    SettingsSection.SCRAPING -> scrapingSection(viewModel, settings)
                    SettingsSection.APPEARANCE -> appearanceSection(viewModel, settings)
                    SettingsSection.CONTROLLER -> controllerSection(viewModel, settings)
                    SettingsSection.ANDROID -> androidSection(viewModel, settings)
                    SettingsSection.STORAGE -> storageSection(viewModel, settings)
                    SettingsSection.ADVANCED -> advancedSection(viewModel)
                }
            }
        }
    }
}

private typealias Scope = androidx.compose.foundation.lazy.LazyListScope

// ---- Library ----------------------------------------------------------------------------------

private fun Scope.librarySection(vm: SettingsViewModel, settings: AppSettings) {
    item { LibrarySources(vm) }
    item { SectionHeader("Scanning") }
    item {
        val progress by vm.scanProgress.collectAsStateWithLifecycle()
        val (title, desc) = when (val p = progress) {
            ScanProgress.Idle -> "Scan now" to "Looks for new, changed and removed files"
            is ScanProgress.Running -> "Scanning ${p.source}…" to "${p.filesSeen} files seen, ${p.gamesFound} games so far"
            is ScanProgress.Finished -> "Scan now" to "Last scan: +${p.result.added} new, ${p.result.updated} changed, ${p.result.removed} missing in ${p.result.durationMs / 1000}s"
            is ScanProgress.Failed -> "Scan now" to "Last scan failed: ${p.message}"
        }
        SettingRow(title, description = desc, onClick = { if (progress is ScanProgress.Running) vm.cancelScan() else vm.scanNow() })
    }
    item { SettingRow("Scan on startup", description = "Refresh the library each time Vela opens", checked = settings.scanOnStartup, onClick = { vm.update { it.copy(scanOnStartup = !it.scanOnStartup) } }) }
    item { SettingRow("Remove missing games", description = "Delete entries whose file is gone instead of keeping their stats", checked = settings.purgeMissingGames, onClick = { vm.update { it.copy(purgeMissingGames = !it.purgeMissingGames) } }) }
    item { SectionHeader("Display") }
    item { SettingRow("Show hidden games", checked = settings.showHiddenGames, onClick = { vm.update { it.copy(showHiddenGames = !it.showHiddenGames) } }) }
    item { SettingRow("Merge regional versions", description = "Show one card per game; other regions appear in its details", checked = settings.hideDuplicateRegions, onClick = { vm.update { it.copy(hideDuplicateRegions = !it.hideDuplicateRegions) } }) }
}

@Composable
private fun LibrarySources(vm: SettingsViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val platforms by vm.platforms.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(false) }
    var pendingPlatformFor by remember { mutableStateOf<LibrarySource?>(null) }
    var removing by remember { mutableStateOf<LibrarySource?>(null) }
    var editing by remember { mutableStateOf<LibrarySource?>(null) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { vm.addTreeSource(it, null) }
    }

    Column {
        SectionHeader("Folders")
        sources.forEach { source ->
            SettingRow(
                title = source.displayName,
                description = listOfNotNull(
                    source.platformId?.let { id -> platforms.firstOrNull { it.id == id }?.displayName ?: id.value } ?: "Systems detected from folder names",
                    if (source.lastScanGameCount > 0) "${source.lastScanGameCount} games" else null,
                    if (!source.enabled) "Disabled" else null,
                ).joinToString("   "),
                icon = if (source.access.name == "FILE") Icons.Rounded.Folder else Icons.Rounded.FolderOpen,
                onClick = { editing = source },
            )
        }
        SettingRow("Add folder", icon = Icons.Rounded.Add, onClick = { addMenu = true })
    }

    if (addMenu) {
        val suggested = remember { vm.suggestedFolders() }
        val hasAccess = vm.hasAllFilesAccess()
        VelaMenuDialog(
            title = "Add folder",
            subtitle = if (hasAccess) "Pick a common location, type a path or browse" else "Browse with the system picker, or grant All files access in Storage for faster scans",
            options = buildList {
                if (hasAccess) suggested.forEach { add(MenuOption("path:${it.absolutePath}", it.name, description = it.absolutePath, icon = Icons.Rounded.Folder)) }
                if (hasAccess) add(MenuOption("type", "Type a path…", icon = Icons.Rounded.Folder))
                add(MenuOption("browse", "Browse…", description = "System folder picker", icon = Icons.Rounded.FolderOpen))
            },
            onSelect = { opt ->
                addMenu = false
                when {
                    opt.id == "browse" -> treePicker.launch(null)
                    opt.id == "type" -> pathInput = true
                    opt.id.startsWith("path:") -> vm.addPathSource(opt.id.removePrefix("path:"), null)
                }
            },
            onDismiss = { addMenu = false },
        )
    }
    if (pathInput) {
        TextInputDialog("Folder path", "/storage/emulated/0/ROMs", "/storage/emulated/0/ROMs", "Add", onConfirm = { vm.addPathSource(it, null); pathInput = false }, onDismiss = { pathInput = false })
    }
    editing?.let { source ->
        VelaMenuDialog(
            title = source.displayName,
            subtitle = source.uri,
            options = listOf(
                MenuOption("platform", "Assign to one system…", description = "Use when the folder holds a single system"),
                MenuOption("toggle", if (source.enabled) "Disable" else "Enable"),
                MenuOption("rescan", "Rescan this folder"),
                MenuOption("remove", "Remove folder", icon = Icons.Rounded.Delete, danger = true),
            ),
            onSelect = { opt ->
                editing = null
                when (opt.id) {
                    "platform" -> pendingPlatformFor = source
                    "toggle" -> vm.toggleSource(source)
                    "rescan" -> vm.scanNow()
                    "remove" -> removing = source
                }
            },
            onDismiss = { editing = null },
        )
    }
    pendingPlatformFor?.let { source ->
        VelaMenuDialog(
            title = "System for ${source.displayName}",
            options = listOf(MenuOption("auto", "Detect from folder names", selected = source.platformId == null)) +
                platforms.filter { it.platform.kind == PlatformKind.EMULATED }.map { MenuOption(it.id.value, it.displayName, selected = it.id == source.platformId) },
            onSelect = { opt ->
                pendingPlatformFor = null
                vm.setSourcePlatform(source, opt.id.takeIf { it != "auto" }?.let(::PlatformId))
            },
            onDismiss = { pendingPlatformFor = null },
        )
    }
    removing?.let { source ->
        ConfirmDialog("Remove ${source.displayName}?", "Games found in this folder leave the library. Favourites and play time for them are lost.", "Remove", onConfirm = { vm.removeSource(source.id); removing = null }, onDismiss = { removing = null }, danger = true)
    }
}

// ---- Platforms --------------------------------------------------------------------------------

private fun Scope.platformsSection(vm: SettingsViewModel) {
    item { PlatformsList(vm) }
}

@Composable
private fun PlatformsList(vm: SettingsViewModel) {
    val platforms by vm.platforms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlatformEntry?>(null) }
    var pickingCoreFor by remember { mutableStateOf<Pair<PlatformEntry, PlayerStatus>?>(null) }
    Column {
        SectionHeader("Systems with games")
        platforms.filter { it.gameCount > 0 && it.platform.kind == PlatformKind.EMULATED }.forEach { entry -> PlatformRow(entry, vm) { editing = it } }
        SectionHeader("Other systems")
        platforms.filter { it.gameCount == 0 && it.platform.kind == PlatformKind.EMULATED }.forEach { entry -> PlatformRow(entry, vm) { editing = it } }
    }
    editing?.let { entry ->
        val options = remember(entry.id) { vm.playerOptions(entry) }
        VelaMenuDialog(
            title = entry.displayName,
            subtitle = "Emulator for ${entry.gameCount} games   ${entry.platform.extensions.joinToString(" ") { ".$it" }}",
            options = listOf(
                MenuOption("enabled", if (entry.settings.enabled) "Enabled" else "Disabled", description = "Hide or show this system everywhere"),
                MenuOption("auto", "Automatic", description = "First installed emulator in the recommended order", selected = entry.settings.playerId == null),
            ) + options.map { p ->
                MenuOption(
                    id = p.definition.id.value,
                    label = p.definition.name,
                    description = if (p.installedPackage == null) "Not installed" else if (p.definition.coresFor(entry.id).isNotEmpty()) "Choose core…" else p.installedPackage,
                    selected = p.definition.id == entry.settings.playerId,
                    enabled = p.installedPackage != null,
                )
            },
            onSelect = { opt ->
                when (opt.id) {
                    "enabled" -> { vm.setPlatformEnabled(entry, !entry.settings.enabled); editing = null }
                    "auto" -> { vm.setPlatformPlayer(entry, null, null); editing = null }
                    else -> {
                        val status = options.first { it.definition.id.value == opt.id }
                        if (status.definition.coresFor(entry.id).isEmpty()) { vm.setPlatformPlayer(entry, status.definition.id, null); editing = null }
                        else { pickingCoreFor = entry to status; editing = null }
                    }
                }
            },
            onDismiss = { editing = null },
        )
    }
    pickingCoreFor?.let { (entry, status) ->
        VelaMenuDialog(
            title = "${status.definition.name} core",
            subtitle = "Download cores inside RetroArch first (Online Updater)",
            options = status.definition.coresFor(entry.id).map { MenuOption(it.id, it.name, description = it.fileName, selected = it.id == entry.settings.coreId) },
            onSelect = { opt -> vm.setPlatformPlayer(entry, status.definition.id, opt.id); pickingCoreFor = null },
            onDismiss = { pickingCoreFor = null },
        )
    }
}

@Composable
private fun PlatformRow(entry: PlatformEntry, vm: SettingsViewModel, onEdit: (PlatformEntry) -> Unit) {
    val playerName = entry.settings.playerId?.let { id -> vm.playerOptions(entry).firstOrNull { it.definition.id == id }?.definition?.name } ?: "Automatic"
    SettingRow(
        title = entry.displayName,
        description = listOfNotNull(if (entry.gameCount > 0) "${entry.gameCount} games" else null, playerName, entry.settings.coreId).joinToString("   "),
        value = if (!entry.settings.enabled) "Off" else null,
        onClick = { onEdit(entry) },
    )
}

// ---- Emulators --------------------------------------------------------------------------------

private fun Scope.emulatorsSection(vm: SettingsViewModel) {
    item {
        val all = remember { vm.allPlayers() }
        var showing by remember { mutableStateOf<PlayerStatus?>(null) }
        Column {
            SectionHeader("Installed")
            all.filter { it.installedPackage != null }.forEach { p -> SettingRow(p.definition.name, description = p.installedPackage, onClick = { showing = p }) }
            SectionHeader("Supported, not installed")
            all.filter { it.installedPackage == null }.forEach { p -> SettingRow(p.definition.name, description = p.definition.packages.first(), onClick = { showing = p }) }
            SectionHeader("Custom emulators")
            SettingRow("Add your own", description = "Drop a players.json with the same schema as the built-in catalog into Android/data/io.vela.frontend/files/ and restart", onClick = {})
        }
        showing?.let { p ->
            VelaMenuDialog(
                title = p.definition.name,
                subtitle = p.definition.notes ?: p.definition.website ?: "",
                options = p.definition.packages.map { MenuOption(it, it, description = if (it == p.installedPackage) "Installed" else "Not installed", selected = it == p.installedPackage) } +
                    (if (p.definition.platforms.isNotEmpty()) listOf(MenuOption("platforms", "Systems: " + p.definition.platforms.joinToString(", ") { it.value })) else emptyList()),
                onSelect = { showing = null },
                onDismiss = { showing = null },
            )
        }
    }
}

// ---- Scraping ---------------------------------------------------------------------------------

private fun Scope.scrapingSection(vm: SettingsViewModel, settings: AppSettings) {
    item {
        val progress by vm.scrapeProgress.collectAsStateWithLifecycle()
        val (title, desc) = when (val p = progress) {
            ScrapeProgress.Idle -> "Fetch missing artwork now" to "Downloads box art for games that have none"
            is ScrapeProgress.Running -> "Fetching ${p.done}/${p.total}…" to p.currentTitle
            is ScrapeProgress.Finished -> "Fetch missing artwork now" to "Last run: ${p.scraped} updated, ${p.notFound} not found, ${p.failed} failed"
            is ScrapeProgress.Stopped -> "Fetch missing artwork now" to "Stopped: ${p.reason}"
        }
        SettingRow(title, description = desc, onClick = { if (progress is ScrapeProgress.Running) vm.cancelScrape() else vm.scrapeMissing() })
    }
    item { SettingRow("Fetch artwork for new games", description = "Right after each scan", checked = settings.scraping.autoScrapeNewGames, onClick = { vm.update { it.copy(scraping = it.scraping.copy(autoScrapeNewGames = !it.scraping.autoScrapeNewGames)) } }) }
    item { SettingRow("Wi-Fi only", checked = settings.scraping.wifiOnly, onClick = { vm.update { it.copy(scraping = it.scraping.copy(wifiOnly = !it.scraping.wifiOnly)) } }) }
    item { SettingRow("Download videos", description = "Preview clips where a provider offers them", checked = settings.scraping.downloadVideos, onClick = { vm.update { it.copy(scraping = it.scraping.copy(downloadVideos = !it.scraping.downloadVideos)) } }) }
    item { SectionHeader("Providers") }
    item {
        var picking by remember { mutableStateOf(false) }
        val providers = remember { vm.providers() }
        val current = providers.firstOrNull { it.id == settings.scraping.metadataProviderId }
        SettingRow("Metadata source", description = current?.description, value = current?.name ?: settings.scraping.metadataProviderId, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog(
                title = "Metadata source",
                options = providers.map { MenuOption(it.id, it.name, description = it.description, selected = it.id == settings.scraping.metadataProviderId) },
                onSelect = { opt -> vm.update { it.copy(scraping = it.scraping.copy(metadataProviderId = opt.id, artworkProviderIds = listOf(opt.id, "libretro").distinct())) }; picking = false },
                onDismiss = { picking = false },
            )
        }
    }
    item { CredentialRow("ScreenScraper user", settings.scraping.screenScraperUser) { v -> vm.update { it.copy(scraping = it.scraping.copy(screenScraperUser = v)) } } }
    item { CredentialRow("ScreenScraper password", settings.scraping.screenScraperPassword, secret = true) { v -> vm.update { it.copy(scraping = it.scraping.copy(screenScraperPassword = v)) } } }
    item { CredentialRow("SteamGridDB API key", settings.scraping.steamGridDbApiKey, secret = true) { v -> vm.update { it.copy(scraping = it.scraping.copy(steamGridDbApiKey = v)) } } }
    item { SectionHeader("Preferences") }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Preferred region", value = settings.scraping.preferredRegions.first().uppercase(), onClick = { picking = true })
        if (picking) {
            val regions = listOf("eu" to "Europe", "us" to "USA", "jp" to "Japan", "wor" to "World")
            VelaMenuDialog("Preferred region", regions.map { MenuOption(it.first, it.second, selected = it.first == settings.scraping.preferredRegions.first()) },
                onSelect = { opt -> vm.update { it.copy(scraping = it.scraping.copy(preferredRegions = listOf(opt.id) + it.scraping.preferredRegions.filter { r -> r != opt.id })) }; picking = false },
                onDismiss = { picking = false })
        }
    }
}

@Composable
private fun CredentialRow(title: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    SettingRow(title, value = if (value.isEmpty()) "Not set" else if (secret) "••••••" else value, onClick = { editing = true })
    if (editing) {
        TextInputDialog(title, value, "", "Save", onConfirm = { onChange(it); editing = false }, onDismiss = { editing = false })
    }
}

// ---- Appearance -------------------------------------------------------------------------------

private fun Scope.appearanceSection(vm: SettingsViewModel, settings: AppSettings) {
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Theme", value = vm.themeById(settings.themeId).name, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Theme", vm.themes.themes.map { MenuOption(it.id, it.name, description = it.author.takeIf { a -> a.isNotBlank() }?.let { a -> "by $a" }, selected = it.id == settings.themeId) },
                onSelect = { opt -> vm.update { it.copy(themeId = opt.id) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Home layout", description = settings.homeLayout.description, value = settings.homeLayout.label, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Home layout", HomeLayout.entries.map { MenuOption(it.name, it.label, description = it.description, selected = it == settings.homeLayout) },
                onSelect = { opt -> vm.update { it.copy(homeLayout = HomeLayout.valueOf(opt.id)) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Library view", description = "Also changeable with Start inside any game list", value = settings.libraryView.label, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Library view", LibraryView.entries.map { MenuOption(it.name, it.label, description = it.description, selected = it == settings.libraryView) },
                onSelect = { opt -> vm.update { it.copy(libraryView = LibraryView.valueOf(opt.id)) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Grid columns", value = if (settings.gridColumns == 0) "Theme default" else settings.gridColumns.toString(), onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Grid columns", (listOf(0) + (4..9)).map { MenuOption(it.toString(), if (it == 0) "Theme default" else it.toString(), selected = it == settings.gridColumns) },
                onSelect = { opt -> vm.update { it.copy(gridColumns = opt.id.toInt()) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Interface size", value = "${(settings.uiScale * 100).toInt()}%", onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Interface size", listOf(0.85f, 0.92f, 1f, 1.1f, 1.2f).map { MenuOption(it.toString(), "${(it * 100).toInt()}%", selected = it == settings.uiScale) },
                onSelect = { opt -> vm.update { it.copy(uiScale = opt.id.toFloat()) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item { SettingRow("Video previews", description = "Play a muted clip after resting on a game", checked = settings.videoPreviews, onClick = { vm.update { it.copy(videoPreviews = !it.videoPreviews) } }) }
    item { SettingRow("Reduce motion", description = "Turns off scaling, parallax and crossfades", checked = settings.reduceMotion, onClick = { vm.update { it.copy(reduceMotion = !it.reduceMotion) } }) }
    item { SettingRow("Show clock", checked = settings.showClock, onClick = { vm.update { it.copy(showClock = !it.showClock) } }) }
    item { SettingRow("Show battery", checked = settings.showBattery, onClick = { vm.update { it.copy(showBattery = !it.showBattery) } }) }
    item { SectionHeader("Custom themes") }
    item { SettingRow("Theme files", description = "Copy a theme JSON (same schema as the built-in ones) into Android/data/io.vela.frontend/files/themes/ and restart", onClick = {}) }
}

// ---- Controller -------------------------------------------------------------------------------

private fun Scope.controllerSection(vm: SettingsViewModel, settings: AppSettings) {
    item {
        SettingRow(
            "Confirm button",
            description = if (settings.confirmButton == ConfirmButton.A) "A confirms, B goes back (Xbox layout)" else "B confirms, A goes back (Nintendo layout)",
            value = settings.confirmButton.name,
            onClick = { vm.update { it.copy(confirmButton = if (it.confirmButton == ConfirmButton.A) ConfirmButton.B else ConfirmButton.A) } },
        )
    }
    item { SettingRow("Navigate with analog sticks", checked = settings.analogStickNavigation, onClick = { vm.update { it.copy(analogStickNavigation = !it.analogStickNavigation) } }) }
    item { SettingRow("Vibration", checked = settings.hapticFeedback, onClick = { vm.update { it.copy(hapticFeedback = !it.hapticFeedback) } }) }
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Hold-to-scroll speed", value = when (settings.repeatIntervalMs) { in 0..60 -> "Fast"; in 61..120 -> "Normal"; else -> "Slow" }, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Hold-to-scroll speed", listOf("Slow" to 160, "Normal" to 90, "Fast" to 50).map { MenuOption(it.second.toString(), it.first, selected = it.second == settings.repeatIntervalMs) },
                onSelect = { opt -> vm.update { it.copy(repeatIntervalMs = opt.id.toInt()) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item { SectionHeader("Button map") }
    item { SettingRow("A / B", description = "Confirm / back", onClick = {}) }
    item { SettingRow("X", description = "Game menu (also hold confirm)", onClick = {}) }
    item { SettingRow("Y", description = "Sort, filter or favourite depending on the screen", onClick = {}) }
    item { SettingRow("L1 / R1", description = "Switch sections in the top bar", onClick = {}) }
    item { SettingRow("L2 / R2", description = "Switch settings pages", onClick = {}) }
    item { SettingRow("Start", description = "Sort picker in grids", onClick = {}) }
}

// ---- Android apps -----------------------------------------------------------------------------

private fun Scope.androidSection(vm: SettingsViewModel, settings: AppSettings) {
    item { SettingRow("Detect games automatically", description = "Adds apps Android classifies as games", checked = settings.autoDetectGames, onClick = { vm.update { it.copy(autoDetectGames = !it.autoDetectGames) } }) }
    item { SettingRow("Show system apps in the picker", checked = settings.showSystemApps, onClick = { vm.update { it.copy(showSystemApps = !it.showSystemApps) } }) }
    item { SettingRow("Choose games and apps", description = "Use the Android tab: X picks games, Y picks apps", onClick = {}) }
}

// ---- Storage ----------------------------------------------------------------------------------

private fun Scope.storageSection(vm: SettingsViewModel, settings: AppSettings) {
    item {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(vm.hasAllFilesAccess()) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { granted = vm.hasAllFilesAccess() }
        SettingRow(
            "All files access",
            description = if (granted) "Granted. Fast scans and RetroArch launching work." else "Recommended on handhelds: fast scans and path-based emulators such as RetroArch.",
            value = if (granted) "On" else "Off",
            onClick = { launcher.launch(vm.allFilesAccessIntent()) },
        )
        LaunchedEffect(Unit) { granted = vm.hasAllFilesAccess() }
        @Suppress("UNUSED_VARIABLE") val unused = context
    }
    item {
        SettingRow(
            "Preferred access",
            description = "How new folders are added by default",
            value = if (settings.storageMode == StorageMode.ALL_FILES) "All files" else "Folder picker",
            onClick = { vm.update { it.copy(storageMode = if (it.storageMode == StorageMode.ALL_FILES) StorageMode.SAF else StorageMode.ALL_FILES) } },
        )
    }
    item { SectionHeader("Artwork cache") }
    item {
        val bytes by vm.artworkBytes.collectAsStateWithLifecycle()
        SettingRow("Downloaded artwork", value = formatBytes(bytes), description = "Stored in app storage; works offline", onClick = { vm.refreshStorageStats() })
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

// ---- Advanced ---------------------------------------------------------------------------------

private fun Scope.advancedSection(vm: SettingsViewModel) {
    item {
        val context = LocalContext.current
        var isDefault by remember { mutableStateOf(vm.isDefaultLauncher()) }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = vm.isDefaultLauncher() }
        Column {
            SectionHeader("Home screen")
            SettingRow(
                "Use Vela as the home app",
                description = if (isDefault) "Vela opens when you press Home. Android Settings stays one press away below." else "Android will ask you to confirm. You can switch back any time.",
                value = if (isDefault) "Active" else null,
                onClick = { roleLauncher.launch(vm.requestHomeRoleIntent()) },
            )
            SettingRow("Choose default home app", description = "Opens the Android chooser, also to restore the original launcher", onClick = { context.startActivity(vm.homeSettingsIntent()) })
            SettingRow("Android settings", description = "System settings, Wi-Fi, Bluetooth, controllers", onClick = { context.startActivity(vm.androidSettingsIntent()) })
            SectionHeader("About")
            SettingRow("Vela ${vm.appVersion}", description = "Open source console frontend for Android handhelds", onClick = {})
        }
    }
}
