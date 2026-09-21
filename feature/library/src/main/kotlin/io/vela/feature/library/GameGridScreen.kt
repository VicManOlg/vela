package io.vela.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.vela.core.model.GameId
import io.vela.core.model.GameSort
import io.vela.core.model.GameSummary
import io.vela.core.model.LibraryView
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuCallbacks
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.GamePreviewPanel
import io.vela.core.ui.components.GameRow
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.gameFacts
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme

/**
 * Games of a platform, a collection, favourites or everything, in the view the user picked:
 * grid, compact grid, list with preview, or showcase. Header follows the focused game; X opens
 * the game menu, Y cycles sort, Start opens the display menu (view + sort).
 */
@Composable
fun GameGridScreen(
    onOpenGame: (GameId) -> Unit,
    onBackgroundArtwork: (String?, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameGridViewModel = hiltViewModel(),
) {
    val items = viewModel.paged.collectAsLazyPagingItems()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val focused by viewModel.focused.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    val accent = Color(header.accent)

    LaunchedEffect(focused, header.accent) { onBackgroundArtwork(focused?.background ?: focused?.boxArt, header.accent) }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> { viewModel.openMenuForFocused(); true }
            GamepadButton.Y -> { viewModel.cycleSort(); true }
            GamepadButton.START -> { displayMenu = true; true }
            else -> false
        }
    }

    Column(modifier.fillMaxSize()) {
        GridHeader(
            header = header,
            // The list view names the focused game in its preview panel already.
            focusedTitle = if (view == LibraryView.LIST) null else focused?.title,
            view = view,
            onOpenDisplay = { displayMenu = true },
        )
        if (items.itemCount == 0 && items.loadState.refresh !is androidx.paging.LoadState.Loading) {
            EmptyState(
                title = "Nothing here yet",
                message = if (header.title == "Favorites") "Mark games as favorites from their menu and they will show up here." else "No games matched. Check the folders in Settings > Library.",
            )
            return@Column
        }
        val callbacks = GameCallbacks(
            launch = viewModel::launch,
            menu = viewModel::openMenu,
            focus = { viewModel.focused.value = it },
        )
        when (view) {
            LibraryView.GRID -> GridContent(items, accent, callbacks, compact = false)
            LibraryView.COMPACT -> GridContent(items, accent, callbacks, compact = true)
            LibraryView.LIST -> ListContent(items, accent, callbacks, focused, showPlatform = viewModel.showsSeveralPlatforms, platformLabel = viewModel::platformLabel)
            LibraryView.SHOWCASE -> ShowcaseContent(items, accent, callbacks, focused, platformLabel = if (viewModel.showsSeveralPlatforms) viewModel::platformLabel else { _ -> null })
        }
    }

    if (displayMenu) {
        VelaMenuDialog(
            title = "Display",
            options = LibraryView.entries.map { MenuOption("view:${it.name}", it.label, description = it.description, selected = it == view) } +
                MenuOption("sort", "Sort by…", description = header.sort.label()),
            onSelect = { opt ->
                displayMenu = false
                if (opt.id == "sort") sortMenu = true else viewModel.setView(LibraryView.valueOf(opt.id.removePrefix("view:")))
            },
            onDismiss = { displayMenu = false },
        )
    }
    if (sortMenu) {
        VelaMenuDialog(
            title = "Sort by",
            options = GameSort.entries.map { MenuOption(it.name, it.label(), selected = it == header.sort) },
            onSelect = { viewModel.setSort(GameSort.valueOf(it.id)); sortMenu = false },
            onDismiss = { sortMenu = false },
        )
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

private class GameCallbacks(
    val launch: (GameSummary) -> Unit,
    val menu: (GameSummary) -> Unit,
    val focus: (GameSummary) -> Unit,
)

@Composable
private fun GridContent(items: LazyPagingItems<GameSummary>, accent: Color, callbacks: GameCallbacks, compact: Boolean) {
    val gridState = rememberLazyGridState()
    val columnsSetting = VelaTheme.dimens.gridColumns
    val cardWidth: Dp = if (compact) VelaTheme.dimens.cardWidth * 0.68f else VelaTheme.dimens.cardWidth
    val spacing = if (compact) VelaTheme.dimens.railSpacing * 0.6f else VelaTheme.dimens.railSpacing
    val bleed = focusBleed()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0))
    LazyVerticalGrid(
        state = gridState,
        columns = when {
            columnsSetting > 0 && !compact -> GridCells.Fixed(columnsSetting)
            columnsSetting > 0 -> GridCells.Fixed((columnsSetting * 1.5f).toInt())
            else -> GridCells.Adaptive(cardWidth)
        },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(autoFocus)
            .focusRestorer()
            .focusGroup(),
        contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = bleed, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing + 4.dp),
    ) {
        items(count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
            val game = items[index] ?: return@items
            GameCard(
                game = game,
                accent = accent,
                width = null,
                onClick = { callbacks.launch(game) },
                onLongPress = { callbacks.menu(game) },
                onFocused = { callbacks.focus(game) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ListContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    focused: GameSummary?,
    showPlatform: Boolean,
    platformLabel: (GameSummary) -> String?,
) {
    val listState = rememberLazyListState()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0))
    Row(Modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight()
                .focusRequester(autoFocus)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@items
                GameRow(
                    game = game,
                    accent = accent,
                    subtitle = gameFacts(game, if (showPlatform) platformLabel(game) else null).ifBlank { null },
                    onClick = { callbacks.launch(game) },
                    onLongPress = { callbacks.menu(game) },
                    onFocused = { callbacks.focus(game) },
                )
            }
        }
        Spacer(Modifier.fillMaxHeight().padding(horizontal = VelaTheme.dimens.sectionSpacing / 2))
        GamePreviewPanel(
            game = focused,
            accent = accent,
            platformLabel = focused?.let(platformLabel),
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight()
                .padding(top = 6.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun ShowcaseContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    focused: GameSummary?,
    platformLabel: (GameSummary) -> String?,
) {
    val rowState = rememberLazyListState()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0))
    val colors = VelaTheme.colors
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bleed = focusBleed() * 1.5f
            val cardHeight = maxHeight - bleed * 2
            val cardWidth = cardHeight * VelaTheme.dimens.boxArtAspect
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(autoFocus)
                    .focusRestorer()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = VelaTheme.dimens.screenPadding, vertical = bleed),
                horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing * 1.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                    val game = items[index] ?: return@items
                    GameCard(
                        game = game,
                        accent = accent,
                        width = cardWidth,
                        onClick = { callbacks.launch(game) },
                        onLongPress = { callbacks.menu(game) },
                        onFocused = { callbacks.focus(game) },
                    )
                }
            }
        }
        Text(
            focused?.let { gameFacts(it, platformLabel(it)) } ?: "",
            style = VelaTheme.typography.body,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 8.dp),
        )
    }
}

@Composable
private fun GridHeader(header: GameGridHeader, focusedTitle: String?, view: LibraryView, onOpenDisplay: () -> Unit) {
    val colors = VelaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = VelaTheme.dimens.screenPadding)
            .height(96.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(header.title, style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                focusedTitle ?: header.subtitle,
                style = VelaTheme.typography.body,
                color = if (focusedTitle != null) colors.onBackground.copy(alpha = 0.85f) else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Touch users tap this; controller users press Start.
        Box(Modifier.padding(bottom = 6.dp).clickable(onClick = onOpenDisplay)) {
            Text("${view.label}   Sorted by ${header.sort.label().lowercase()}", style = VelaTheme.typography.caption, color = colors.muted)
        }
    }
    Spacer(Modifier.height(6.dp))
}
