package io.vela.feature.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.vela.core.model.GameId
import io.vela.core.model.GameSort
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuCallbacks
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme

/**
 * Grid of a platform, a collection, favourites or everything. Header follows the focused card;
 * X opens the game menu, Y cycles sort, Start opens the sort picker.
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
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var sortMenu by remember { mutableStateOf(false) }
    val accent = Color(header.accent)

    LaunchedEffect(focused, header.accent) { onBackgroundArtwork(focused?.background ?: focused?.boxArt, header.accent) }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> { viewModel.openMenuForFocused(); true }
            GamepadButton.Y -> { viewModel.cycleSort(); true }
            GamepadButton.START -> { sortMenu = true; true }
            else -> false
        }
    }

    Column(modifier.fillMaxSize()) {
        GridHeader(header, focused?.title)
        if (items.itemCount == 0 && items.loadState.refresh !is androidx.paging.LoadState.Loading) {
            EmptyState(
                title = "Nothing here yet",
                message = if (header.title == "Favorites") "Mark games as favorites from their menu and they will show up here." else "No games matched. Check the folders in Settings > Library.",
            )
            return@Column
        }
        val columnsSetting = VelaTheme.dimens.gridColumns
        val bleed = focusBleed()
        val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0))
        LazyVerticalGrid(
            state = gridState,
            columns = if (columnsSetting > 0) GridCells.Fixed(columnsSetting) else GridCells.Adaptive(VelaTheme.dimens.cardWidth),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(autoFocus)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = bleed, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
            verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing + 4.dp),
        ) {
            items(count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@items
                GameCard(
                    game = game,
                    accent = accent,
                    width = null,
                    onClick = { viewModel.launch(game) },
                    onLongPress = { viewModel.openMenu(game) },
                    onFocused = { viewModel.focused.value = game },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
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

@Composable
private fun GridHeader(header: GameGridHeader, focusedTitle: String?) {
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
        Box(Modifier.padding(bottom = 6.dp)) {
            Text("Sorted by ${header.sort.label().lowercase()}", style = VelaTheme.typography.caption, color = colors.muted)
        }
    }
    Spacer(Modifier.height(6.dp))
}
