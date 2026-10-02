package io.vela.feature.settings

import io.vela.core.ui.components.pageEntrance
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import io.vela.core.model.ControllerLayout
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Check
import io.vela.core.model.HomeRail
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
import androidx.compose.runtime.produceState
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
import io.vela.core.model.PlatformId
import io.vela.core.model.PlatformKind
import io.vela.core.model.ScanProgress
import io.vela.core.model.StorageMode
import io.vela.core.model.ScrapeProgress
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

    val sectionList = rememberLazyListState()
    val sectionFocus = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val scope = rememberCoroutineScope()
    // L2/R2 change the page and take the focus to its row, or the ring stays on the old page.
    fun showSection(next: SettingsSection) {
        viewModel.selectSection(next)
        scope.launch {
            sectionList.scrollToItem(next.ordinal)
            withFrameNanos { }
            runCatching { sectionFocus.getValue(next).requestFocus() }
        }
    }

    GamepadHandler { button ->
        val sections = SettingsSection.entries
        when (button) {
            GamepadButton.L2 -> { showSection(sections[(sections.indexOf(section) - 1 + sections.size) % sections.size]); true }
            GamepadButton.R2 -> { showSection(sections[(sections.indexOf(section) + 1) % sections.size]); true }
            else -> false
        }
    }

    Row(modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            Spacer(Modifier.height(12.dp))
            val memory = rememberFocusMemory()
            val autoFocus = rememberAutoFocus(memory = memory)
            LazyColumn(Modifier.focusRequester(autoFocus).focusRestorer().focusGroup(), state = sectionList, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                rememberedItems(memory, SettingsSection.entries, key = { it.name }) { s ->
                    SettingRow(
                        modifier = Modifier.focusRequester(sectionFocus.getValue(s)),
                        title = s.title,
                        description = s.summary,
                        value = if (s == section) "•" else null,
                        onClick = { viewModel.selectSection(s) },
                        onFocused = { viewModel.selectSection(s) },
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Spacer(Modifier.height(12.dp))
            // A new section rises into place, so L2/R2 read as turning a page.
            LazyColumn(Modifier.fillMaxSize().pageEntrance(section).focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
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
        val suggested by produceState(emptyList<java.io.File>()) { value = vm.suggestedFolders() }
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
                    "rescan" -> vm.rescanSource(source)
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
        val loaded by produceState<List<PlayerStatus>?>(null, entry.id) { value = vm.playerOptions(entry) }
        val options = loaded ?: return@let
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
    val playerName = entry.settings.playerId?.let(vm::playerName) ?: "Automatic"
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
        // Re-check installed packages every time this page opens; the user may just have installed one.
        LaunchedEffect(Unit) { vm.refreshPlayers() }
        val all = vm.players.collectAsStateWithLifecycle().value.orEmpty()
        var showing by remember { mutableStateOf<PlayerStatus?>(null) }
        Column {
            SectionHeader("Installed")
            all.filter { it.installedPackage != null }.forEach { p -> SettingRow(p.definition.name, description = p.installedPackage, onClick = { showing = p }) }
            SectionHeader("Supported, not installed")
            all.filter { it.installedPackage == null }.forEach { p -> SettingRow(p.definition.name, description = p.definition.packages.firstOrNull() ?: "No package listed", onClick = { showing = p }) }
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
    item { SettingRow("Wikipedia descriptions", description = "Use the article lead as the description (CC BY-SA)", checked = settings.scraping.wikipediaDescriptions, onClick = { vm.update { it.copy(scraping = it.scraping.copy(wikipediaDescriptions = !it.scraping.wikipediaDescriptions)) } }) }
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
        val preferred = settings.scraping.preferredRegions.firstOrNull() ?: "eu"
        SettingRow("Preferred region", value = preferred.uppercase(), onClick = { picking = true })
        if (picking) {
            val regions = listOf("eu" to "Europe", "us" to "USA", "jp" to "Japan", "wor" to "World")
            VelaMenuDialog("Preferred region", regions.map { MenuOption(it.first, it.second, selected = it.first == preferred) },
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

// ---- Appearance: see AppearanceSection.kt ------------------------------------------------------

/**
 * Two-level editor for the Home sections: pick a section, then move it up or down or hide it.
 * Every choice is saved at once; the list reopens so several changes take a few presses.
 */
@Composable
internal fun HomeSectionsEditor(settings: AppSettings, vm: SettingsViewModel, onClose: () -> Unit) {
    val ordered = settings.homeRails + HomeRail.entries.filter { it !in settings.homeRails }
    var editing by remember { mutableStateOf<HomeRail?>(null) }
    val target = editing
    if (target == null) {
        VelaMenuDialog(
            title = "Home sections",
            subtitle = "Pick a section to move it or hide it. Empty sections never show.",
            options = ordered.map { r ->
                val hidden = r in settings.hiddenHomeRails
                MenuOption(r.name, r.label, description = if (hidden) "Hidden" else r.description, icon = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Check)
            },
            onSelect = { editing = HomeRail.valueOf(it.id) },
            onDismiss = onClose,
        )
    } else {
        val index = ordered.indexOf(target)
        val hidden = target in settings.hiddenHomeRails
        VelaMenuDialog(
            title = target.label,
            subtitle = target.description,
            options = buildList {
                if (index > 0) add(MenuOption("up", "Move up", icon = Icons.Rounded.KeyboardArrowUp))
                if (index < ordered.lastIndex) add(MenuOption("down", "Move down", icon = Icons.Rounded.KeyboardArrowDown))
                add(if (hidden) MenuOption("show", "Show", icon = Icons.Rounded.Visibility) else MenuOption("hide", "Hide", icon = Icons.Rounded.VisibilityOff))
            },
            onSelect = { opt ->
                vm.update { s ->
                    val list = ordered.toMutableList()
                    when (opt.id) {
                        "up" -> java.util.Collections.swap(list, index, index - 1)
                        "down" -> java.util.Collections.swap(list, index, index + 1)
                    }
                    s.copy(
                        homeRails = list,
                        hiddenHomeRails = when (opt.id) {
                            "hide" -> (s.hiddenHomeRails + target).distinct()
                            "show" -> s.hiddenHomeRails - target
                            else -> s.hiddenHomeRails
                        },
                    )
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ---- Controller -------------------------------------------------------------------------------

private fun Scope.controllerSection(vm: SettingsViewModel, settings: AppSettings) {
    item {
        var picking by remember { mutableStateOf(false) }
        SettingRow("Button layout", description = settings.controllerLayout.description, value = settings.controllerLayout.label, onClick = { picking = true })
        if (picking) {
            VelaMenuDialog("Button layout", ControllerLayout.entries.map { MenuOption(it.name, it.label, description = it.description, selected = it == settings.controllerLayout) },
                onSelect = { opt -> vm.update { it.copy(controllerLayout = ControllerLayout.valueOf(opt.id)) }; picking = false }, onDismiss = { picking = false })
        }
    }
    item {
        val layout = settings.controllerLayout
        val confirm = if (settings.confirmButton == ConfirmButton.A) "A" else "B"
        val back = if (settings.confirmButton == ConfirmButton.A) "B" else "A"
        SettingRow(
            "Confirm button",
            description = "${layout.glyphOf(confirm)} (${layout.positionOf(confirm)}) confirms, ${layout.glyphOf(back)} (${layout.positionOf(back)}) goes back",
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
