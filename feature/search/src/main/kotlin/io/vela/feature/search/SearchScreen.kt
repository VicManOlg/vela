package io.vela.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.GameRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.usecase.GameActions
import io.vela.core.data.usecase.GameMenuController
import io.vela.core.model.CollectionId
import io.vela.core.model.CompletionStatus
import io.vela.core.model.GameId
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.LaunchOption
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuCallbacks
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.Pill
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.color
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val platformFilter: PlatformId? = null,
    val results: List<GameSummary> = emptyList(),
    val platforms: Map<PlatformId, Platform> = emptyMap(),
    val genres: List<String> = emptyList(),
    val searching: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val games: GameRepository,
    library: LibraryRepository,
    private val actions: GameActions,
    val menu: GameMenuController,
) : ViewModel() {

    val query = MutableStateFlow("")
    private val platformFilter = MutableStateFlow<PlatformId?>(null)
    private val genres = MutableStateFlow<List<String>>(emptyList())

    private val results = combine(query.debounce(180), platformFilter) { q, p -> q to p }
        .mapLatest { (q, p) ->
            if (q.isBlank()) emptyList() else games.search(q).filter { p == null || it.platformId == p }
        }

    val state: StateFlow<SearchUiState> = combine(query, platformFilter, results, library.observeAllPlatforms(), genres) { q, p, r, platforms, g ->
        SearchUiState(q, p, r, platforms.associate { it.id to it.platform }, g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    val menuState: StateFlow<GameMenuState> = menu.state

    init { viewModelScope.launch { genres.value = games.genres() } }

    fun setQuery(q: String) { query.value = q }
    fun cyclePlatformFilter() {
        val ids = state.value.results.map { it.platformId }.distinct()
        if (ids.isEmpty()) { platformFilter.value = null; return }
        val current = platformFilter.value
        platformFilter.value = if (current == null) ids.first() else ids.getOrNull(ids.indexOf(current) + 1)
    }

    fun launch(game: GameSummary) = viewModelScope.launch { actions.launch(game.id) }
    fun openMenu(game: GameSummary) = viewModelScope.launch { menu.open(game) }
    fun onMenuAction(action: String, onOpenDetails: (GameSummary) -> Unit) = viewModelScope.launch {
        when (val r = menu.onAction(action)) {
            is GameMenuController.MenuResult.OpenDetails -> onOpenDetails(r.game)
            else -> Unit
        }
    }
    fun toggleCollection(id: CollectionId) = viewModelScope.launch { menu.toggleCollection(id) }
    fun startNewCollection() = menu.startNewCollection()
    fun createCollection(name: String) = viewModelScope.launch { menu.createCollection(name) }
    fun launchWith(option: LaunchOption, remember: Boolean) = viewModelScope.launch { menu.launchWith(option, remember) }
    fun setCompletion(status: CompletionStatus) = viewModelScope.launch { menu.setCompletion(status) }
    fun confirmHide() = viewModelScope.launch { menu.confirmHide() }
    fun dismissMenu() = menu.dismiss()
}

/** Global search: title (FTS) and genre, live as you type, filterable by system with Y. */
@Composable
fun SearchScreen(
    onOpenGame: (GameId) -> Unit,
    onBackgroundArtwork: (String?, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val colors = VelaTheme.colors
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { onBackgroundArtwork(null, 0xFF7FD7FF); runCatching { fieldFocus.requestFocus() } }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.Y -> { viewModel.cyclePlatformFilter(); true }
            GamepadButton.X -> { runCatching { fieldFocus.requestFocus() }; true }
            else -> false
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(96.dp), verticalArrangement = Arrangement.Bottom) {
            Text("Search", style = VelaTheme.typography.display, color = colors.onBackground)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = VelaTheme.dimens.screenPadding), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(VelaTheme.shapes.chip)
                    .background(colors.surfaceElevated.copy(alpha = 0.9f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.muted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Title, system or genre", style = VelaTheme.typography.body, color = colors.muted)
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        textStyle = VelaTheme.typography.body.copy(color = colors.onBackground),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            val filterName = state.platformFilter?.let { state.platforms[it]?.shortName } ?: "All systems"
            VelaButton(filterName, viewModel::cyclePlatformFilter)
        }
        Spacer(Modifier.height(8.dp))
        if (query.isBlank() && state.genres.isNotEmpty()) {
            Row(Modifier.padding(horizontal = VelaTheme.dimens.screenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.genres.take(8).forEach { genre -> VelaButton(genre, { viewModel.setQuery(genre) }) }
            }
        }
        if (query.isNotBlank()) {
            Text(
                if (state.results.isEmpty()) "No matches" else "${state.results.size} results",
                style = VelaTheme.typography.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(VelaTheme.dimens.gridColumns.takeIf { it > 0 } ?: 6),
            modifier = Modifier.fillMaxSize().focusRestorer().focusGroup(),
            contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = focusBleed(), bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
            verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing + 4.dp),
        ) {
            items(state.results, key = { it.id.value }) { game ->
                val platform = state.platforms[game.platformId]
                Column {
                    GameCard(
                        game = game,
                        accent = platform?.color() ?: colors.accentSecondary,
                        width = null,
                        onClick = { viewModel.launch(game) },
                        onLongPress = { viewModel.openMenu(game) },
                        onFocused = { onBackgroundArtwork(game.background ?: game.boxArt, platform?.accentColor ?: 0xFF7FD7FF) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Pill(platform?.shortName ?: "Android", tint = platform?.color() ?: Color(0xFF3DDC84))
                }
            }
        }
    }

    GameMenuHost(
        state = menuState,
        callbacks = GameMenuCallbacks(
            onAction = { action -> viewModel.onMenuAction(action) { onOpenGame(it.id) } },
            onDismiss = viewModel::dismissMenu,
            onToggleCollection = { viewModel.toggleCollection(it) },
            onStartNewCollection = viewModel::startNewCollection,
            onCreateCollection = { viewModel.createCollection(it) },
            onLaunchWith = { option, remember -> viewModel.launchWith(option, remember) },
            onSetCompletion = { viewModel.setCompletion(it) },
            onConfirmHide = viewModel::confirmHide,
        ),
    )
}
