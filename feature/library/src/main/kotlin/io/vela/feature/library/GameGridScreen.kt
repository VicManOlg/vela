package io.vela.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import io.vela.core.ui.theme.LocalVelaTheme
import io.vela.core.ui.components.rememberBackdropPrefetch
import androidx.compose.ui.focus.focusProperties
import io.vela.core.ui.theme.metaLine
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import io.vela.core.model.GameMenuEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import io.vela.core.ui.components.GameMenuEvents
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.GamePreviewPanel
import io.vela.core.ui.components.GameRow
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.gameFacts
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberEntranceClock
import io.vela.core.ui.components.staggeredEntrance
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import kotlin.math.abs
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound

/**
 * Games of a platform, a collection, favourites or everything, in the view the user picked (or
 * the theme's): grid, compact grid, list with preview, showcase wheel, hero, wall or details.
 * Header follows the focused game; X opens the game menu, Y cycles sort, Start opens the display
 * menu (view + sort).
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
    val chosenView by viewModel.view.collectAsStateWithLifecycle()
    val view = if (chosenView == LibraryView.THEME) LibraryView.fromKey(VelaTheme.spec.layout.libraryView) else chosenView
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }
    val accent = Color(header.accent)

    LaunchedEffect(focused, header.accent) { onBackgroundArtwork(focused?.background ?: focused?.boxArt, header.accent) }
    // The first item receives focus before any focus callback runs; seed the header/preview with it.
    LaunchedEffect(items.itemCount, focused == null) {
        if (focused == null && items.itemCount > 0) items.peek(0)?.let { viewModel.setFocused(it) }
    }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> { viewModel.openMenuForFocused(); true }
            GamepadButton.Y -> { viewModel.cycleSort(); true }
            GamepadButton.START -> { displayMenu = true; true }
            else -> false
        }
    }

    Column(modifier.fillMaxSize()) {
        // Placeholders are enabled, so itemCount is the full result size once the first page is in.
        val shownCount = maxOf(items.itemCount, header.count)
        GridHeader(
            header = header.copy(subtitle = listOfNotNull(header.subtitle.takeIf { it.isNotBlank() }, if (shownCount == 1) "1 game" else "$shownCount games").joinToString("   ")),
            // List names the focused game in its preview panel, Showcase under the wheel, Hero on the scene, Details in its rows.
            focusedTitle = if (view == LibraryView.LIST || view == LibraryView.SHOWCASE || view == LibraryView.HERO || view == LibraryView.DETAILS || view == LibraryView.BOOK) null else focused?.title,
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
            focus = { viewModel.setFocused(it) },
        )
        // Inside one system, cards take the shape of its boxes (wide SNES, square PlayStation,
        // tall DVD cases), so covers fill them instead of sitting between blurred bands.
        val theme = LocalVelaTheme.current
        val shaped = remember(theme, header.boxArtAspect) {
            header.boxArtAspect?.let { theme.copy(dimens = theme.dimens.copy(boxArtAspect = it)) } ?: theme
        }
        CompositionLocalProvider(LocalVelaTheme provides shaped) {
            when (view) {
                LibraryView.GRID -> GridContent(items, accent, callbacks, compact = false)
                LibraryView.COMPACT -> GridContent(items, accent, callbacks, compact = true)
                LibraryView.LIST -> ListContent(items, accent, callbacks, focused, showPlatform = viewModel.showsSeveralPlatforms, platformLabel = viewModel::platformLabel)
                LibraryView.SHOWCASE -> ShowcaseContent(items, accent, callbacks, focused, platformLabel = if (viewModel.showsSeveralPlatforms) viewModel::platformLabel else { _ -> null })
                LibraryView.HERO -> HeroContent(items, accent, callbacks, focused, platformLabel = if (viewModel.showsSeveralPlatforms) viewModel::platformLabel else { _ -> null })
                LibraryView.WALL -> WallContent(items, accent, callbacks, tileAspect = header.boxArtAspect ?: 1f)
                LibraryView.DETAILS -> DetailsContent(items, accent, callbacks, showPlatform = viewModel.showsSeveralPlatforms, platformLabel = viewModel::platformLabel)
                LibraryView.BOOK -> {
                    val details by viewModel.focusedDetails.collectAsStateWithLifecycle()
                    BookContent(items, accent, callbacks, focused, details, showPlatform = viewModel.showsSeveralPlatforms, platformLabel = viewModel::platformLabel)
                }
                LibraryView.THEME -> GridContent(items, accent, callbacks, compact = false)
            }
        }
    }

    if (displayMenu) {
        VelaMenuDialog(
            title = "Display",
            options = LibraryView.entries.map { MenuOption("view:${it.name}", it.label, description = if (it == LibraryView.THEME) "Right now: ${view.label}" else it.description, selected = it == chosenView) } +
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

    GameMenuEvents(viewModel.menuEvents) { event ->
        if (event is GameMenuEvent.OpenDetails) onOpenGame(event.game.id)
    }
    GameMenuHost(state = menuState, actions = viewModel.menu)
}

internal class GameCallbacks(
    val launch: (GameSummary) -> Unit,
    val menu: (GameSummary) -> Unit,
    val focus: (GameSummary) -> Unit,
)

@Composable
private fun GridContent(items: LazyPagingItems<GameSummary>, accent: Color, callbacks: GameCallbacks, compact: Boolean) {
    val gridState = rememberLazyGridState()
    val clock = rememberEntranceClock()
    val columnsSetting = VelaTheme.dimens.gridColumns
    val cardWidth: Dp = if (compact) VelaTheme.dimens.cardWidth * 0.68f else VelaTheme.dimens.cardWidth
    val spacing = if (compact) VelaTheme.dimens.railSpacing * 0.6f else VelaTheme.dimens.railSpacing
    val bleed = focusBleed()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    val prefetch = rememberBackdropPrefetch()
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
        rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
            val game = items[index] ?: return@rememberedItems
            GameCard(
                game = game,
                accent = accent,
                width = null,
                onClick = { callbacks.launch(game) },
                onLongPress = { callbacks.menu(game) },
                onFocused = {
                    callbacks.focus(game)
                    // Neighbours' scenes, decoded ahead; peek never makes the pager load a page.
                    for (n in intArrayOf(index - 1, index + 1)) {
                        if (n in 0 until items.itemCount) items.peek(n)?.let { prefetch(it.background ?: it.boxArt) }
                    }
                },
                modifier = Modifier.fillMaxWidth().staggeredEntrance(index, clock),
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
    val clock = rememberEntranceClock()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
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
            rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@rememberedItems
                GameRow(
                    game = game,
                    accent = accent,
                    subtitle = gameFacts(game, if (showPlatform) platformLabel(game) else null).ifBlank { null },
                    onClick = { callbacks.launch(game) },
                    onLongPress = { callbacks.menu(game) },
                    onFocused = { callbacks.focus(game) },
                    modifier = Modifier.staggeredEntrance(index, clock),
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

/**
 * Wheel: the focused cover sits centred and full size; neighbours shrink, tilt away and fade
 * with distance from the centre. Focus moves the wheel, flings snap to a cover.
 */
@Composable
private fun ShowcaseContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    focused: GameSummary?,
    platformLabel: (GameSummary) -> String?,
) {
    val rowState = rememberLazyListState()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    val colors = VelaTheme.colors
    val motion = VelaTheme.motion
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) rowState.animateScrollToItem(focusedIndex)
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bleed = focusBleed() * 1.2f
            val cardHeight = maxHeight - bleed * 2
            val cardWidth = cardHeight * VelaTheme.dimens.boxArtAspect
            val spacing = VelaTheme.dimens.railSpacing * 2
            // Symmetric padding lets the first and last cover reach the centre.
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            val tilt = if (motion.reduceMotion) 0f else 16f
            LazyRow(
                state = rowState,
                flingBehavior = rememberSnapFlingBehavior(rowState),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(autoFocus)
                    .focusRestorer()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = bleed),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                    val game = items[index] ?: return@rememberedItems
                    GameCard(
                        game = game,
                        accent = accent,
                        width = cardWidth,
                        onClick = { callbacks.launch(game) },
                        onLongPress = { callbacks.menu(game) },
                        onFocused = { focusedIndex = index; callbacks.focus(game) },
                        modifier = Modifier.graphicsLayer {
                            val info = rowState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = item.offset + item.size / 2f
                            val distance = ((itemCenter - center) / (item.size + spacing.toPx())).coerceIn(-3f, 3f)
                            val near = abs(distance).coerceAtMost(1f)
                            val shrink = 1f - 0.18f * near
                            scaleX = shrink
                            scaleY = shrink
                            rotationY = -distance * tilt
                            cameraDistance = 16f * density
                            alpha = 1f - 0.35f * near
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = VelaTheme.dimens.screenPadding)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                focused?.title ?: "",
                style = VelaTheme.typography.title,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                focused?.let { gameFacts(it, platformLabel(it)) } ?: "",
                style = VelaTheme.typography.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            Spacer(Modifier.height(8.dp))
            Text(
                focusedTitle ?: metaLine(header.subtitle),
                style = if (focusedTitle != null) VelaTheme.typography.body else VelaTheme.typography.overline,
                color = if (focusedTitle != null) colors.onBackground else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Touch users tap this; controller users press Start.
        val sounds = LocalUiSounds.current
        // Touch shortcut to the Display menu (START does it on a pad); not a focus stop.
        Box(Modifier.padding(bottom = 6.dp).focusProperties { canFocus = false }.clickable { sounds?.play(UiSound.CONFIRM); onOpenDisplay() }) {
            Text("${view.label}   Sorted by ${header.sort.label().lowercase()}", style = VelaTheme.typography.caption, color = colors.muted)
        }
    }
    Spacer(Modifier.height(8.dp))
}
