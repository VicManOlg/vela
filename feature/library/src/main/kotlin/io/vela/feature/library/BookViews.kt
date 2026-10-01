package io.vela.feature.library

import io.vela.core.ui.components.rememberedItemsIndexed
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.vela.core.model.Game
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary
import io.vela.core.ui.components.Pill
import io.vela.core.ui.components.TitlePlaceholder
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.formatPlayTime
import io.vela.core.ui.components.ratingStars
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberEntranceClock
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.components.staggeredEntrance
import io.vela.core.ui.components.velaFocusable
import io.vela.core.ui.image.FittedArtwork
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.appIconModel
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme
import kotlin.math.abs

/*
 * "Book" views, after the art-book style of catalogue frontends: systems as wide scene cards on
 * a centred carousel, and games as a title list with a facts panel (cover, chips, description).
 */

// ---- Systems: art-book carousel --------------------------------------------------------------

/**
 * Wide 16:10 cards on a snapping carousel; the centre card sits upright and full size while its
 * neighbours shrink and fade. Each card is the system's scene (user art from `system-art/`, or
 * the most recent game's background) with the console and the name over a gradient. The title
 * and facts of the centred system are printed above the carousel.
 */
@Composable
internal fun BookSystems(entries: List<StageEntry>, initialIndex: Int, clock: Long, onSpot: (Spot) -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val motion = VelaTheme.motion
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0)))
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(entries.size), memory = memory)
    var selected by remember { mutableIntStateOf(initialIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))) }
    val current = entries.getOrNull(selected)

    LaunchedEffect(current?.key) { current?.let { onSpot(Spot(it.title, it.subtitle, it.background, it.accent)) } }
    LaunchedEffect(selected) { rowState.animateScrollToItem(selected) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(96.dp), verticalArrangement = Arrangement.Bottom) {
            Crossfade(targetState = current, animationSpec = tween(200), label = "bookTitle") { entry ->
                Column {
                    Text(entry?.title ?: "Library", style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (entry?.subtitle ?: "").split("   ").filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
                        style = VelaTheme.typography.overline, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bleed = focusBleed()
            val cardHeight = (maxHeight - bleed * 2 - 16.dp).coerceIn(140.dp, 420.dp)
            val cardWidth = (cardHeight * 1.6f).coerceAtMost(maxWidth * 0.62f)
            val spacing = VelaTheme.dimens.railSpacing * 1.5f
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            LazyRow(
                state = rowState,
                flingBehavior = rememberSnapFlingBehavior(rowState),
                modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = bleed),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rememberedItemsIndexed(memory, entries, key = { _, e -> e.key }) { index, entry ->
                    BookSystemCard(
                        entry = entry,
                        width = cardWidth,
                        onFocused = { selected = index },
                        modifier = Modifier
                            .then(if (index == selected) Modifier.focusRequester(autoFocus) else Modifier)
                            .staggeredEntrance(index, clock)
                            .graphicsLayer {
                                val info = rowState.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
                                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val itemCenter = item.offset + item.size / 2f
                                val distance = ((itemCenter - center) / (item.size + spacing.toPx())).coerceIn(-3f, 3f)
                                val near = abs(distance).coerceAtMost(1f)
                                val shrink = 1f - 0.12f * near
                                scaleX = shrink
                                scaleY = shrink
                                alpha = 1f - 0.45f * near
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookSystemCard(entry: StageEntry, width: androidx.compose.ui.unit.Dp, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val iconStyle = VelaTheme.platformIcons
    val shape = VelaTheme.shapes.tile
    val accent = Color(entry.accent)
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Box(
        modifier
            .width(width)
            .aspectRatio(1.6f)
            .velaFocusable(shape, interaction, entry.open, onFocused = onFocused, scaleOverride = 1.03f, edge = true)
            .clip(shape)
            .background(colors.surface),
    ) {
        val scene = entry.background
        when {
            scene != null -> VelaImage(model = artworkModel(scene), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
            entry.covers.isNotEmpty() -> {
                // No scene for this system yet: its own covers stand on a shelf over a blurred blow-up of the first one.
                VelaImage(model = artworkModel(entry.covers.first()), contentDescription = null, modifier = Modifier.fillMaxSize().blur(22.dp), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
                Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxSize().padding(top = 12.dp, bottom = 56.dp, end = 8.dp)) { CoverShelf(entry.covers, accent) }
            }
            else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), colors.surfaceElevated))))
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.35f), colors.background.copy(alpha = 0.92f)), startY = size.height * 0.3f))
                drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = if (focused) 0.35f else 0.2f), Color.Transparent), endX = size.width * 0.6f))
            },
        )
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                when {
                    entry.icon != null -> VelaImage(
                        model = artworkModel(entry.icon), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        colorFilter = if (iconStyle.tint) ColorFilter.tint(colors.onBackground) else null, placeholder = {},
                    )
                    entry.iconVector != null -> Icon(entry.iconVector, contentDescription = null, tint = colors.onBackground, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.subtitle.substringAfterLast("   "), style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1)
            }
        }
    }
}

// ---- Games: title list + facts panel ---------------------------------------------------------

/**
 * Titles down the left like a table of contents; the right page shows the focused game's cover,
 * a row of fact chips (year, developer, genre, players, system, play time, stars) and its
 * description. The page crossfades as focus moves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    focused: GameSummary?,
    details: Game?,
    showPlatform: Boolean,
    platformLabel: (GameSummary) -> String?,
) {
    val colors = VelaTheme.colors
    val listState = rememberLazyListState()
    val clock = rememberEntranceClock()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    Row(Modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(0.4f).fillMaxHeight().focusRequester(autoFocus).focusRestorer().focusGroup(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@rememberedItems
                BookTitleRow(game, accent, callbacks, subtitle = if (showPlatform) platformLabel(game) else null, modifier = Modifier.staggeredEntrance(index, clock))
            }
        }
        Spacer(Modifier.width(VelaTheme.dimens.sectionSpacing))
        Box(Modifier.weight(0.6f).fillMaxHeight().padding(top = 6.dp, bottom = 24.dp)) {
            Crossfade(targetState = focused?.id, animationSpec = tween(220), label = "bookPage") { id ->
                val game = focused?.takeIf { it.id == id } ?: return@Crossfade
                val full = details?.takeIf { it.id == id }
                val meta = full?.metadata
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().weight(0.62f)) {
                        val model = if (game.kind == GameKind.ANDROID_APP && game.boxArt == null) game.packageName?.let(::appIconModel) else artworkModel(game.boxArt)
                        FittedArtwork(
                            model = model,
                            contentDescription = game.title,
                            modifier = Modifier.fillMaxHeight().aspectRatio(VelaTheme.dimens.boxArtAspect).clip(VelaTheme.shapes.card).background(colors.surface),
                            accent = accent,
                            placeholder = { TitlePlaceholder(game.title, accent) },
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                            Text(full?.displayTitle ?: game.title, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val chips = listOfNotNull(
                                    meta?.releaseYear?.toString(),
                                    meta?.developer?.takeIf { it.isNotBlank() },
                                    meta?.genres?.firstOrNull(),
                                    meta?.players?.takeIf { it.isNotBlank() }?.let { p -> when { p.trim() == "1" -> "1 player"; p.any { c -> c.isDigit() } -> "$p players"; else -> p } },
                                    platformLabel(game),
                                    game.totalPlayTimeMs.takeIf { it > 0 }?.let(::formatPlayTime),
                                    ratingStars(game.userRating),
                                    if (game.favorite) "Favorite" else null,
                                )
                                chips.forEach { Pill(it, tint = if (it == ratingStars(game.userRating)) accent else colors.onBackground) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().weight(0.38f)) {
                        Text(
                            meta?.description?.takeIf { it.isNotBlank() } ?: "No description yet. Scrape this system from Settings > Metadata to fill in the story, the developer and the year.",
                            style = VelaTheme.typography.body,
                            color = if (meta?.description.isNullOrBlank()) colors.muted else colors.onSurface,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxSize()
                                // Fade the last lines of the text itself instead of cutting a line in half.
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookTitleRow(game: GameSummary, accent: Color, callbacks: GameCallbacks, subtitle: String?, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Row(
        modifier
            .fillMaxWidth()
            .velaFocusable(shape, interaction, { callbacks.launch(game) }, onLongPress = { callbacks.menu(game) }, onFocused = { callbacks.focus(game) }, scaleOverride = 1.01f)
            .clip(shape)
            .drawBehind {
                if (focused) {
                    drawRect(colors.surfaceElevated.copy(alpha = 0.85f))
                    drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(game.title, style = if (focused) VelaTheme.typography.bodyStrong else VelaTheme.typography.body, color = if (focused) colors.onBackground else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1)
        }
        if (game.favorite) Text("♥", style = VelaTheme.typography.caption, color = accent)
    }
}
