package io.vela.feature.library

import io.vela.core.ui.components.VelaSprings
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContent
import io.vela.core.ui.theme.metaLine
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.TitlePlaceholder
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.formatLastPlayed
import io.vela.core.ui.components.formatPlayTime
import io.vela.core.ui.components.gameFacts
import io.vela.core.ui.components.ratingStars
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberEntranceClock
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.components.staggeredEntrance
import io.vela.core.ui.components.velaFocusable
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.appIconModel
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/*
 * Three more game-list views next to Grid, Compact, List and Showcase: Hero (scene on top, a
 * row of covers below), Wall (gapless mosaic of square covers) and Details (a dense table).
 * They take the same paging items and callbacks as the existing views.
 */

private fun GameSummary.coverModel(): Any? =
    if (kind == GameKind.ANDROID_APP && boxArt == null) packageName?.let(::appIconModel) else artworkModel(boxArt)

// ---- Hero -------------------------------------------------------------------------------------

/**
 * The focused game's scene (background art, or its cover when there is none) fills the upper
 * two thirds with the title and facts over it; a single row of covers runs along the bottom.
 * Moving along the row crossfades the scene, so browsing feels like flipping through a gallery.
 */
@Composable
internal fun HeroContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    focused: GameSummary?,
    platformLabel: (GameSummary) -> String?,
) {
    val colors = VelaTheme.colors
    val rowState = rememberLazyListState()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    val clock = rememberEntranceClock()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focusedIndex) { if (focusedIndex >= 0) rowState.animateScrollToItem(focusedIndex) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = VelaTheme.dimens.screenPadding)
                .clip(VelaTheme.shapes.tile)
                .background(colors.surface),
        ) {
            // A new scene settles from a slight zoom like a camera finding its shot; the title
            // slides in on its own spring.
            val reduce = VelaTheme.motion.reduceMotion
            AnimatedContent(
                targetState = focused,
                contentKey = { it?.id },
                transitionSpec = {
                    if (reduce) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(VelaSprings.effectsSlow()) + scaleIn(VelaSprings.spatialSlow(), initialScale = 1.06f)) togetherWith fadeOut(VelaSprings.effects())
                    }
                },
                label = "heroScene",
            ) { game ->
                if (game == null) return@AnimatedContent
                val scene = game.background ?: game.boxArt
                Box(Modifier.fillMaxSize()) {
                    if (scene != null || game.kind == GameKind.ANDROID_APP) {
                        VelaImage(
                            model = if (scene == null) game.packageName?.let(::appIconModel) else artworkModel(scene),
                            contentDescription = game.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (scene == null) ContentScale.Inside else ContentScale.Crop,
                            accent = accent,
                            placeholder = { TitlePlaceholder(game.title, accent) },
                        )
                    } else {
                        TitlePlaceholder(game.title, accent, Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(colors.background.copy(alpha = 0.85f), colors.background.copy(alpha = 0.2f), Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.8f)), startY = 200f)))
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .animateEnterExit(enter = if (reduce) EnterTransition.None else slideInHorizontally(VelaSprings.spatial()) { -it / 8 } + fadeIn(VelaSprings.effects()))
                            .padding(24.dp)
                            .fillMaxWidth(0.6f),
                    ) {
                        if (game.logo != null) {
                            VelaImage(model = artworkModel(game.logo), contentDescription = game.title, modifier = Modifier.height(64.dp).fillMaxWidth(0.8f), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, placeholder = {})
                        } else {
                            Text(game.title, style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(metaLine(gameFacts(game, platformLabel(game))), style = VelaTheme.typography.overline, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val bleed = focusBleed()
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth().focusRequester(autoFocus).focusRestorer().focusGroup(),
            contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = bleed + 4.dp, bottom = bleed + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
        ) {
            rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@rememberedItems
                GameCard(
                    game = game,
                    accent = accent,
                    width = VelaTheme.dimens.cardWidth * 0.68f,
                    onClick = { callbacks.launch(game) },
                    onLongPress = { callbacks.menu(game) },
                    onFocused = { focusedIndex = index; callbacks.focus(game) },
                    modifier = Modifier.staggeredEntrance(index, clock),
                )
            }
        }
    }
}

// ---- Wall -------------------------------------------------------------------------------------

/**
 * Covers packed edge to edge with no gaps and no titles, like a wall of cartridges: square in
 * mixed lists, shaped like the system's boxes ([tileAspect]) inside one system, so nothing is
 * cropped. The focused tile lifts above its neighbours; the header names the game.
 */
@Composable
internal fun WallContent(items: LazyPagingItems<GameSummary>, accent: Color, callbacks: GameCallbacks, tileAspect: Float = 1f) {
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    val clock = rememberEntranceClock()
    val columns = (VelaTheme.dimens.gridColumns + 1).coerceIn(5, 12)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
        contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = focusBleed(), bottom = 90.dp),
    ) {
        rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
            val game = items[index] ?: return@rememberedItems
            WallTile(game, accent, callbacks, tileAspect, Modifier.staggeredEntrance(index, clock))
        }
    }
}

@Composable
private fun WallTile(game: GameSummary, accent: Color, callbacks: GameCallbacks, aspect: Float, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Box(
        modifier
            // The focused tile scales past its neighbours; drawing it last keeps it on top.
            .zIndex(if (focused) 1f else 0f)
            .aspectRatio(aspect)
            .velaFocusable(RectangleShape, interaction, { callbacks.launch(game) }, onLongPress = { callbacks.menu(game) }, onFocused = { callbacks.focus(game) })
            .background(colors.surface),
    ) {
        VelaImage(
            model = game.coverModel(),
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = if (game.kind == GameKind.ANDROID_APP && game.boxArt == null) ContentScale.Inside else ContentScale.Crop,
            accent = accent,
            placeholder = { TitlePlaceholder(game.title, accent) },
        )
    }
}

// ---- Details ----------------------------------------------------------------------------------

/**
 * A table: thumbnail, title, system, last played, play time and stars, one game per line,
 * with a column header. Made for big libraries where scanning text beats scanning art.
 */
@Composable
internal fun DetailsContent(
    items: LazyPagingItems<GameSummary>,
    accent: Color,
    callbacks: GameCallbacks,
    showPlatform: Boolean,
    platformLabel: (GameSummary) -> String?,
) {
    val colors = VelaTheme.colors
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(items.itemCount > 0), memory = memory)
    val clock = rememberEntranceClock()
    Column(Modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(THUMB + 14.dp))
            DetailCell("TITLE", Modifier.weight(1f), start = true, muted = true)
            if (showPlatform) DetailCell("SYSTEM", Modifier.fillMaxWidth(PLATFORM_W), muted = true)
            DetailCell("LAST PLAYED", Modifier.fillMaxWidth(PLAYED_W), muted = true)
            DetailCell("PLAY TIME", Modifier.fillMaxWidth(TIME_W), muted = true)
            DetailCell("RATING", Modifier.fillMaxWidth(RATING_W), muted = true)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.muted.copy(alpha = 0.25f)))
        LazyColumn(
            modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
        ) {
            rememberedItems(memory, count = items.itemCount, key = items.itemKey { it.id.value }) { index ->
                val game = items[index] ?: return@rememberedItems
                DetailRow(game, accent, callbacks, showPlatform, platformLabel(game), index % 2 == 1, Modifier.staggeredEntrance(index, clock))
            }
        }
    }
}

private val THUMB = 34.dp
private const val PLATFORM_W = 0.14f
private const val PLAYED_W = 0.16f
private const val TIME_W = 0.12f
private const val RATING_W = 0.12f

@Composable
private fun DetailRow(game: GameSummary, accent: Color, callbacks: GameCallbacks, showPlatform: Boolean, platform: String?, striped: Boolean, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Row(
        modifier
            .fillMaxWidth()
            .velaFocusable(shape, interaction, { callbacks.launch(game) }, onLongPress = { callbacks.menu(game) }, onFocused = { callbacks.focus(game) }, scaleOverride = 1.01f)
            .clip(shape)
            .drawBehind {
                if (focused) drawRect(colors.surfaceElevated.copy(alpha = 0.9f))
                else if (striped) drawRect(colors.surface.copy(alpha = 0.35f))
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(THUMB).clip(RoundedCornerShape(4.dp)).background(colors.surface)) {
            VelaImage(model = game.coverModel(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
        }
        Spacer(Modifier.width(14.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(game.title, style = if (focused) VelaTheme.typography.bodyStrong else VelaTheme.typography.body, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (game.favorite) Text("♥", style = VelaTheme.typography.caption, color = accent, modifier = Modifier.padding(start = 8.dp))
        }
        if (showPlatform) DetailCell(platform ?: "", Modifier.fillMaxWidth(PLATFORM_W))
        DetailCell(formatLastPlayed(game.lastPlayedAt) ?: "—", Modifier.fillMaxWidth(PLAYED_W))
        DetailCell(game.totalPlayTimeMs.takeIf { it > 0 }?.let(::formatPlayTime) ?: "—", Modifier.fillMaxWidth(TIME_W))
        DetailCell(ratingStars(game.userRating) ?: "", Modifier.fillMaxWidth(RATING_W), accentText = true)
    }
}

@Composable
private fun DetailCell(text: String, modifier: Modifier, start: Boolean = false, muted: Boolean = false, accentText: Boolean = false) {
    val colors = VelaTheme.colors
    Text(
        text,
        style = if (muted) VelaTheme.typography.overline else VelaTheme.typography.caption,
        color = when { muted -> colors.muted; accentText -> colors.accent; else -> colors.onSurface },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (start) TextAlign.Start else TextAlign.End,
        modifier = modifier,
    )
}
