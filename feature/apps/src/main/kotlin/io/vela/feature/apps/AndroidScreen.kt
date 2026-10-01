package io.vela.feature.apps

import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import kotlinx.coroutines.flow.Flow
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.AppEntry
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.GameMenuController
import io.vela.core.model.GameId
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuEvents
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AndroidUiState(
    val games: List<GameSummary> = emptyList(),
    val apps: List<GameSummary> = emptyList(),
    val picker: List<AppEntry>? = null,
    val pickerMode: PlatformId = PlatformId.ANDROID,
    val suggestions: List<Pair<String, String>> = emptyList(),
)

@HiltViewModel
class AndroidViewModel @Inject constructor(
    private val repo: AppsRepository,
    private val actions: GameActions,
    private val menuController: GameMenuController,
) : ViewModel() {

    private val picker = MutableStateFlow<List<AppEntry>?>(null)
    private val pickerMode = MutableStateFlow(PlatformId.ANDROID)
    private val suggestions = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val state: StateFlow<AndroidUiState> = combine(repo.observeAndroidGames(), repo.observeApps(), picker, pickerMode, suggestions) { g, a, p, m, s ->
        AndroidUiState(g, a, p, m, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AndroidUiState())

    val menu: GameMenuActions = menuController.attach(viewModelScope)
    val menuState: StateFlow<GameMenuState> = menuController.state
    val menuEvents: Flow<GameMenuEvent> = menuController.events

    init {
        viewModelScope.launch {
            repo.syncInstalled()
            suggestions.value = repo.suggestions()
        }
    }

    fun openPicker(asGames: Boolean) = viewModelScope.launch {
        pickerMode.value = if (asGames) PlatformId.ANDROID else PlatformId.ANDROID_APPS
        picker.value = repo.installedApps()
    }

    fun closePicker() { picker.value = null }

    fun togglePicked(entry: AppEntry) = viewModelScope.launch {
        if (entry.shownAs == pickerMode.value) repo.remove(entry.app.packageName)
        else if (pickerMode.value == PlatformId.ANDROID) repo.addAsGame(entry.app.packageName)
        else repo.addAsApp(entry.app.packageName)
        picker.value = repo.installedApps()
        suggestions.value = repo.suggestions()
    }

    fun addSuggestion(packageName: String) = viewModelScope.launch {
        repo.addAsApp(packageName)
        suggestions.value = repo.suggestions()
    }

    fun launch(game: GameSummary) = viewModelScope.launch { actions.launch(game.id) }
    fun openMenu(game: GameSummary) = menuController.open(game)
}

/** Android tab: detected games and pinned apps, with a picker to add or remove anything installed. */
@Composable
fun AndroidScreen(
    onOpenGame: (GameId) -> Unit,
    onBackgroundArtwork: (String?, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AndroidViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors
    val androidGreen = Color(0xFF3DDC84)
    val appBlue = Color(0xFF8AB4F8)

    LaunchedEffect(Unit) { onBackgroundArtwork(null, 0xFF3DDC84) }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> { viewModel.openPicker(asGames = true); true }
            GamepadButton.Y -> { viewModel.openPicker(asGames = false); true }
            else -> false
        }
    }

    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(state.games.isNotEmpty(), state.apps.isNotEmpty()), memory = memory)
    LazyColumn(modifier.fillMaxSize().focusRequester(autoFocus).focusGroup(), contentPadding = PaddingValues(bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.sectionSpacing - 12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(96.dp), verticalArrangement = Arrangement.Bottom) {
                Text("Android", style = VelaTheme.typography.display, color = colors.onBackground)
                Spacer(Modifier.height(4.dp))
                Text("${state.games.size} games   ${state.apps.size} apps", style = VelaTheme.typography.body, color = colors.muted)
            }
        }
        item {
            if (state.games.isEmpty()) {
                EmptyState("No Android games yet", "Games are detected from the Play Store category. Anything missing can be added by hand.", actionLabel = "Choose games", onAction = { viewModel.openPicker(true) })
            } else {
                Rail("Games", trailing = { VelaButton("Edit", { viewModel.openPicker(true) }) }) {
                    rememberedItems(memory, state.games, key = { it.id.value }) { game ->
                        GameCard(game, androidGreen, onClick = { viewModel.launch(game) }, onLongPress = { viewModel.openMenu(game) }, onFocused = { onBackgroundArtwork(game.boxArt, 0xFF3DDC84) })
                    }
                }
            }
        }
        item {
            if (state.apps.isEmpty()) {
                Column(Modifier.padding(horizontal = VelaTheme.dimens.screenPadding)) {
                    Text("Apps", style = VelaTheme.typography.headline, color = colors.onBackground)
                    Spacer(Modifier.height(6.dp))
                    Text("Pin Settings, a browser, Discord or a streaming client so you never need the stock launcher.", style = VelaTheme.typography.body, color = colors.muted, modifier = Modifier.fillMaxWidth(0.6f))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.suggestions.take(5).forEach { (label, pkg) -> VelaButton(label, { viewModel.addSuggestion(pkg) }) }
                        VelaButton("More…", { viewModel.openPicker(false) })
                    }
                }
            } else {
                Rail("Apps", trailing = { VelaButton("Edit", { viewModel.openPicker(false) }) }) {
                    rememberedItems(memory, state.apps, key = { it.id.value }) { app ->
                        GameCard(app, appBlue, width = VelaTheme.dimens.cardWidth * 0.8f, onClick = { viewModel.launch(app) }, onLongPress = { viewModel.openMenu(app) }, onFocused = { onBackgroundArtwork(null, 0xFF8AB4F8) })
                    }
                }
            }
        }
    }

    state.picker?.let { entries ->
        val asGames = state.pickerMode == PlatformId.ANDROID
        VelaMenuDialog(
            title = if (asGames) "Choose games" else "Choose apps",
            subtitle = "Select to add, select again to remove",
            options = entries.map { e ->
                val shownHere = e.shownAs == state.pickerMode
                MenuOption(
                    id = e.app.packageName,
                    label = e.app.label,
                    description = when {
                        shownHere -> "Shown"
                        e.shownAs != null -> "In the other section"
                        e.app.isGame -> "Detected as a game"
                        else -> null
                    },
                    icon = when {
                        shownHere -> Icons.Rounded.Check
                        e.app.isGame -> Icons.Rounded.SportsEsports
                        else -> Icons.Rounded.Apps
                    },
                    selected = shownHere,
                )
            },
            onSelect = { opt -> entries.firstOrNull { it.app.packageName == opt.id }?.let(viewModel::togglePicked) },
            onDismiss = viewModel::closePicker,
        )
    }

    GameMenuEvents(viewModel.menuEvents) { event ->
        if (event is GameMenuEvent.OpenDetails) onOpenGame(event.game.id)
    }
    GameMenuHost(state = menuState, actions = viewModel.menu)
}
