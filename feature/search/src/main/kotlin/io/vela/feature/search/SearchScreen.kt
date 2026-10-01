package io.vela.feature.search

import kotlinx.coroutines.flow.Flow
import io.vela.core.model.GameMenuEvent
import io.vela.core.model.GameMenuActions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.velaFocusable
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
import io.vela.core.model.GameId
import io.vela.core.model.GameMenuState
import io.vela.core.model.GameSummary
import io.vela.core.model.Platform
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuEvents
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
    private val menuController: GameMenuController,
) : ViewModel() {

    val query = MutableStateFlow("")
    private val platformFilter = MutableStateFlow<PlatformId?>(null)
    private val genres = MutableStateFlow<List<String>>(emptyList())

    // Unfiltered matches, kept so the system filter can cycle through every system that has results.
    private val matches: StateFlow<List<GameSummary>> = query.debounce(180)
        .mapLatest { q -> if (q.isBlank()) emptyList() else games.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SearchUiState> = combine(query, platformFilter, matches, library.observeAllPlatforms(), genres) { q, p, r, platforms, g ->
        SearchUiState(q, p, r.filter { p == null || it.platformId == p }, platforms.associate { it.id to it.platform }, g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    val menu: GameMenuActions = menuController.attach(viewModelScope)
    val menuState: StateFlow<GameMenuState> = menuController.state
    val menuEvents: Flow<GameMenuEvent> = menuController.events

    init { viewModelScope.launch { genres.value = games.genres() } }

    fun setQuery(q: String) { query.value = q }
    fun cyclePlatformFilter() {
        val ids = matches.value.map { it.platformId }.distinct()
        if (ids.isEmpty()) { platformFilter.value = null; return }
        val current = platformFilter.value
        platformFilter.value = if (current == null) ids.first() else ids.getOrNull(ids.indexOf(current) + 1)
    }

    fun launch(game: GameSummary) = viewModelScope.launch { actions.launch(game.id) }
    fun openMenu(game: GameSummary) = menuController.open(game)
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
    // The text field only takes focus (and shows the keyboard) after an explicit press.
    var editing by remember { mutableStateOf(false) }
    val fieldInteraction = remember { MutableInteractionSource() }
    val wrapperFocus = rememberAutoFocus()

    LaunchedEffect(Unit) { onBackgroundArtwork(null, 0xFF7FD7FF) }
    LaunchedEffect(editing) { if (editing) runCatching { fieldFocus.requestFocus() } }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.Y -> { viewModel.cyclePlatformFilter(); true }
            GamepadButton.X -> { editing = true; true }
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
                    .focusRequester(wrapperFocus)
                    .velaFocusable(VelaTheme.shapes.chip, fieldInteraction, onClick = { editing = true }, scaleOverride = 1.01f)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .focusProperties { canFocus = editing }
                            .onFocusChanged { if (!it.isFocused) editing = false },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            val filterName = state.platformFilter?.let { state.platforms[it]?.shortName } ?: "All systems"
            VelaButton(filterName, viewModel::cyclePlatformFilter)
        }
        Spacer(Modifier.height(8.dp))
        if (query.isBlank() && state.genres.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = VelaTheme.dimens.screenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.genres.take(8).forEach { genre -> VelaButton(genre, { viewModel.setQuery(genre) }) }
            }
        }
        if (query.isNotBlank()) {
            Text(
                when (state.results.size) { 0 -> "No matches"; 1 -> "1 result"; else -> "${state.results.size} results" },
                style = VelaTheme.typography.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = VelaTheme.dimens.gridColumns.let { if (it > 0) GridCells.Fixed(it) else GridCells.Adaptive(VelaTheme.dimens.cardWidth) },
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

    GameMenuEvents(viewModel.menuEvents) { event ->
        if (event is GameMenuEvent.OpenDetails) onOpenGame(event.game.id)
    }
    GameMenuHost(state = menuState, actions = viewModel.menu)
}
