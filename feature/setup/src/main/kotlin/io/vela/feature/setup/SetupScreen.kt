package io.vela.feature.setup

import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.system.StorageAccess
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.ScrapeRepository
import io.vela.core.model.ScanProgress
import io.vela.core.model.SourceAccess
import io.vela.core.data.repository.AppSettingsRepository
import io.vela.core.ui.components.GlassPanel
import io.vela.core.ui.components.SettingRow
import io.vela.core.ui.components.TextInputDialog
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.VelaMark
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SetupStep { WELCOME, STORAGE, FOLDERS, SCANNING, DONE }

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val storage: StorageAccess,
    private val settings: AppSettingsRepository,
    private val library: LibraryRepository,
    private val apps: AppsRepository,
    private val scrape: ScrapeRepository,
) : ViewModel() {

    private val _step = MutableStateFlow(SetupStep.WELCOME)
    val step: StateFlow<SetupStep> = _step.asStateFlow()
    fun goTo(value: SetupStep) { _step.value = value }
    val scanProgress: StateFlow<ScanProgress> = library.scanProgress
    private val _added = MutableStateFlow<List<String>>(emptyList())
    val added: StateFlow<List<String>> = _added
    private val _result = MutableStateFlow<Int?>(null)
    val foundGames: StateFlow<Int?> = _result

    fun hasAllFilesAccess(): Boolean = storage.hasAllFilesAccess()
    fun allFilesAccessIntent(): Intent = storage.allFilesAccessIntent()
    suspend fun suggestedFolders(): List<File> = storage.suggestedFolders()

    fun addPath(path: String) = viewModelScope.launch {
        val f = File(path)
        if (!f.isDirectory) return@launch
        library.addSource(f.absolutePath, f.name.ifEmpty { path }, SourceAccess.FILE, null)
        _added.value = _added.value + f.absolutePath
    }

    fun addTree(uri: Uri) = viewModelScope.launch {
        val name = storage.persistTree(uri)
        library.addSource(uri.toString(), name, SourceAccess.DOCUMENT_TREE, null)
        _added.value = _added.value + name
    }

    fun startScan() = viewModelScope.launch {
        _step.value = SetupStep.SCANNING
        val result = runCatching { library.scanNow() }.getOrNull()
        runCatching { apps.syncInstalled() }
        _result.value = result?.added ?: 0
        _step.value = SetupStep.DONE
    }

    /** B on the wizard: one step back, never out of the app. */
    fun back(): Boolean {
        val previous = when (_step.value) {
            SetupStep.STORAGE -> SetupStep.WELCOME
            SetupStep.FOLDERS -> SetupStep.STORAGE
            SetupStep.DONE -> SetupStep.FOLDERS
            else -> return false
        }
        _step.value = previous
        return true
    }

    fun finish(fetchArtwork: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        settings.update { it.copy(setupCompleted = true) }
        if (fetchArtwork) scrape.scrapeMissingInBackground()
        onDone()
    }
}

/** First run: storage access, folders, scan. Every step is one panel with a primary action focused. */
@Composable
fun SetupScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: SetupViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors
    BackHandler(enabled = step != SetupStep.WELCOME && step != SetupStep.SCANNING) { viewModel.back() }
    Box(modifier.fillMaxSize().padding(VelaTheme.dimens.screenPadding), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.92f)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                SetupStep.WELCOME -> Welcome { viewModel.goTo(SetupStep.STORAGE) }
                SetupStep.STORAGE -> Storage(viewModel) { viewModel.goTo(SetupStep.FOLDERS) }
                SetupStep.FOLDERS -> Folders(viewModel) { viewModel.startScan() }
                SetupStep.SCANNING -> Scanning(viewModel)
                SetupStep.DONE -> Done(viewModel, onDone)
                }
            }
        }
        Text("Vela", style = VelaTheme.typography.caption, color = colors.muted, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun StepTitle(title: String, text: String) {
    Column {
        VelaMark()
        Spacer(Modifier.height(18.dp))
        Text(title, style = VelaTheme.typography.display, color = VelaTheme.colors.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(text, style = VelaTheme.typography.body, color = VelaTheme.colors.muted, modifier = Modifier.fillMaxWidth(0.85f))
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column {
        StepTitle("Your games, one place", "Vela turns this device into a console: every system, every game, artwork and your Android games, all driven from the controller. Three quick steps and you are in.")
        VelaButton("Get started", onNext, primary = true, modifier = Modifier.focusRequester(focus))
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Storage(vm: SetupViewModel, onNext: () -> Unit) {
    var granted by remember { mutableStateOf(vm.hasAllFilesAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { granted = vm.hasAllFilesAccess() }
    val focus = remember { FocusRequester() }
    Column {
        StepTitle(
            "File access",
            if (granted) "All files access is on. Scans are fast and every emulator, including RetroArch, can open your games."
            else "Vela works best with All files access: scanning thousands of files takes seconds and path-based emulators like RetroArch work out of the box. You can also pick folders one by one instead.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!granted) VelaButton("Allow all files access", { launcher.launch(vm.allFilesAccessIntent()) }, primary = true, modifier = Modifier.focusRequester(focus))
            VelaButton(if (granted) "Continue" else "Use folder picker instead", onNext, primary = granted, modifier = if (granted) Modifier.focusRequester(focus) else Modifier)
        }
    }
    LaunchedEffect(granted) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Folders(vm: SetupViewModel, onNext: () -> Unit) {
    val added by vm.added.collectAsStateWithLifecycle()
    val hasAccess = vm.hasAllFilesAccess()
    // Null while the folders are listed (file I/O, off the main thread); the focus waits for them.
    val loaded by produceState<List<File>?>(null) { value = vm.suggestedFolders() }
    val suggestions = loaded ?: return
    var typing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::addTree) }
    val focus = remember { FocusRequester() }
    Column {
        StepTitle(
            "Where are your games?",
            "Point Vela at your ROM folders. Sub-folders named after systems (snes, ps2, gba…) are sorted automatically; a folder for a single system can be assigned later in Settings.",
        )
        if (hasAccess) {
            suggestions.take(5).forEachIndexed { i, f ->
                val isAdded = f.absolutePath in added
                SettingRow(f.name, description = f.absolutePath, icon = Icons.Rounded.Folder, value = if (isAdded) "Added" else null, enabled = !isAdded, onClick = { vm.addPath(f.absolutePath) }, modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier)
            }
            SettingRow("Type a path…", icon = Icons.Rounded.Folder, onClick = { typing = true })
        }
        SettingRow("Browse…", description = "System folder picker", icon = Icons.Rounded.FolderOpen, onClick = { picker.launch(null) }, modifier = if (!hasAccess || suggestions.isEmpty()) Modifier.focusRequester(focus) else Modifier)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VelaButton(if (added.isEmpty()) "Skip for now" else "Scan ${added.size} ${if (added.size == 1) "folder" else "folders"}", onNext, primary = added.isNotEmpty())
            Spacer(Modifier.width(14.dp))
            if (added.isNotEmpty()) Text(added.joinToString("  ·  ") { it.substringAfterLast('/') }, style = VelaTheme.typography.caption, color = VelaTheme.colors.muted)
        }
    }
    if (typing) TextInputDialog("Folder path", "/storage/emulated/0/ROMs", "/storage/emulated/0/ROMs", "Add", onConfirm = { vm.addPath(it); typing = false }, onDismiss = { typing = false })
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Scanning(vm: SetupViewModel) {
    val progress by vm.scanProgress.collectAsStateWithLifecycle()
    Column {
        StepTitle("Scanning", "Reading your folders. Big libraries take a moment the first time; later scans only look at what changed.")
        val p = progress
        if (p is ScanProgress.Running) Text("${p.source}   ${p.filesSeen} files   ${p.gamesFound} games", style = VelaTheme.typography.bodyStrong, color = VelaTheme.colors.onBackground)
    }
}

@Composable
private fun Done(vm: SetupViewModel, onDone: () -> Unit) {
    val found by vm.foundGames.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    Column {
        StepTitle(
            if ((found ?: 0) > 0) "${found} games found" else "You are set",
            if ((found ?: 0) > 0) "Vela can fetch box art now from libretro's public thumbnails, no account needed. Descriptions and more artwork come from ScreenScraper once you add your login in Settings."
            else "Add folders any time from Settings > Library. Your Android games are already in.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if ((found ?: 0) > 0) VelaButton("Fetch box art and start", { vm.finish(true, onDone) }, primary = true, modifier = Modifier.focusRequester(focus))
            VelaButton(if ((found ?: 0) > 0) "Start without artwork" else "Start", { vm.finish(false, onDone) }, primary = (found ?: 0) == 0, modifier = if ((found ?: 0) == 0) Modifier.focusRequester(focus) else Modifier)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
