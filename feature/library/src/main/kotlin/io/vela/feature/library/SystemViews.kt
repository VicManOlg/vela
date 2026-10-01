package io.vela.feature.library

import io.vela.core.ui.theme.metaLine
import io.vela.core.ui.components.rememberedItemsIndexed
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.components.staggeredEntrance
import io.vela.core.ui.components.velaFocusable
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/*
 * Three more ways of showing the systems, all fed by the same [StageEntry] list as the Stage:
 * Wheel (vertical list + big preview), Mosaic (tiles made of the user's covers) and Columns
 * (tall panels that open up when focused). Each reports the focused entry through onSpot so the
 * backdrop and header follow, exactly like Showcase and Grid do.
 */

private fun StageEntry.spot() = Spot(title, subtitle, background, accent)

/** Console icon or vector fallback, tinted when the theme asks for it. */
@Composable
private fun EntryIcon(entry: StageEntry, modifier: Modifier, tint: Color = VelaTheme.colors.onBackground) {
    val iconStyle = VelaTheme.platformIcons
    when {
        entry.icon != null -> VelaImage(
            model = artworkModel(entry.icon), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit,
            colorFilter = if (iconStyle.tint) ColorFilter.tint(tint) else null, placeholder = {},
        )
        entry.iconVector != null -> Icon(entry.iconVector, contentDescription = null, tint = tint, modifier = modifier)
    }
}

private fun countLine(subtitle: String): String = subtitle.substringAfterLast("   ")

// ---- Wheel ------------------------------------------------------------------------------------

/**
 * Systems stacked in a column on the left, like a vertical dial: the focused row grows and takes
 * the system colour. The right side is a stage for the selection: big title, facts and the
 * user's covers on a slanted shelf.
 */
@Composable
internal fun WheelSystems(entries: List<StageEntry>, initialIndex: Int, clock: Long, onSpot: (Spot) -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    var selected by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))) }
    val current = entries.getOrNull(selected)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(entries.size), memory = memory)

    LaunchedEffect(current?.key) { current?.let { onSpot(it.spot()) } }

    Row(modifier.fillMaxSize().padding(horizontal = VelaTheme.dimens.screenPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .focusRequester(autoFocus)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rememberedItemsIndexed(memory, entries, key = { _, e -> e.key }) { index, entry ->
                val interaction = remember { MutableInteractionSource() }
                val focused by rememberFocusState(interaction)
                val accent = Color(entry.accent)
                val shape = VelaTheme.shapes.card
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == selected) Modifier.focusRequester(autoFocus) else Modifier)
                        .velaFocusable(shape, interaction, entry.open, onFocused = { selected = index }, scaleOverride = 1.03f)
                        .clip(shape)
                        .background(if (focused) accent.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .staggeredEntrance(index, clock),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        EntryIcon(entry, Modifier.fillMaxSize(0.9f), tint = if (entry.icon == null) accent else colors.onBackground)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = if (focused) VelaTheme.typography.headline else VelaTheme.typography.body, color = if (focused || index == selected) colors.onBackground else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(countLine(entry.subtitle), style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1)
                    }
                    Box(Modifier.size(8.dp).clip(VelaTheme.shapes.chip).background(if (index == selected) accent else Color.Transparent))
                }
            }
        }
        Spacer(Modifier.width(VelaTheme.dimens.sectionSpacing))
        Box(Modifier.weight(0.6f).fillMaxHeight()) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 10 }) togetherWith (fadeOut(tween(160)) + slideOutVertically { -it / 10 }) },
                label = "wheelStage",
            ) { entry ->
                if (entry == null) return@AnimatedContent
                val accent = Color(entry.accent)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().drawBehind { drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension * 0.7f) })
                            EntryIcon(entry, Modifier.fillMaxSize(0.78f), tint = if (entry.icon == null) accent else colors.onBackground)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(metaLine(entry.subtitle), style = VelaTheme.typography.overline, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 24.dp)) {
                        if (entry.covers.isNotEmpty()) CoverShelf(entry.covers, accent)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Covers appear here once the system has scraped art", style = VelaTheme.typography.caption, color = colors.muted)
                        }
                    }
                }
            }
        }
    }
}

// ---- Mosaic -----------------------------------------------------------------------------------

/**
 * Square tiles, each a two-by-two collage of the user's covers for that system under a tint of
 * the system colour, with the console in the middle and the name along the bottom. Systems
 * without covers fall back to a colour block, so the grid never looks broken.
 */
@Composable
internal fun MosaicSystems(entries: List<StageEntry>, clock: Long, onSpot: (Spot) -> Unit, modifier: Modifier = Modifier) {
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(entries.size), memory = memory)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VelaTheme.dimens.cardWidth * 1.15f),
        modifier = modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
        contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = focusBleed(), bottom = 90.dp),
        horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
        verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
    ) {
        rememberedItemsIndexed(memory, entries, key = { _, e -> e.key }) { index, entry ->
            MosaicTile(entry, onFocused = { onSpot(entry.spot()) }, modifier = Modifier.fillMaxWidth().staggeredEntrance(index, clock))
        }
    }
}

@Composable
private fun MosaicTile(entry: StageEntry, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val shape = VelaTheme.shapes.tile
    val accent = Color(entry.accent)
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    val lift = animateFloatAsState(if (focused) 1f else 0f, tween(300), label = "mosaicLift")
    Box(
        modifier
            .aspectRatio(1f)
            .velaFocusable(shape, interaction, entry.open, onFocused = onFocused, edge = true)
            .clip(shape)
            .background(colors.surface),
    ) {
        val covers = entry.covers.take(4)
        if (covers.isNotEmpty()) {
            // Two rows of two; a missing slot repeats an earlier cover so the collage stays full.
            Column(Modifier.fillMaxSize()) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(2) { col ->
                            val cover = covers[(row * 2 + col) % covers.size]
                            VelaImage(model = artworkModel(cover), contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
                        }
                    }
                }
            }
        }
        // Tint: the system colour bleeds in from the top, the base darkens the bottom for the name.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = if (covers.isEmpty()) 0.85f else 0.55f - 0.2f * lift.value), colors.background.copy(alpha = 0.55f), colors.background.copy(alpha = 0.92f))))
            },
        )
        Box(Modifier.align(Alignment.Center).fillMaxSize(0.46f).graphicsLayer { val s = 1f + 0.1f * lift.value; scaleX = s; scaleY = s; shadowElevation = 12.dp.toPx() * lift.value }, contentAlignment = Alignment.Center) {
            EntryIcon(entry, Modifier.fillMaxSize(), tint = colors.onBackground)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(entry.title, style = VelaTheme.typography.headline, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countLine(entry.subtitle), style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1)
        }
    }
}

// ---- Columns ----------------------------------------------------------------------------------

/**
 * Tall panels side by side, each painted with the system's scene under its colour. The focused
 * column opens up to almost twice its width, revealing the full name and facts; the others stay
 * narrow with just the console and a short name, like an accordion.
 */
@Composable
internal fun ColumnsSystems(entries: List<StageEntry>, clock: Long, onSpot: (Spot) -> Unit, modifier: Modifier = Modifier) {
    val rowState = rememberLazyListState()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(entries.size), memory = memory)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val bleed = focusBleed()
        val narrow = VelaTheme.dimens.cardWidth * 0.9f
        val wide = (narrow * 1.9f).coerceAtMost(maxWidth * 0.5f)
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
            contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = bleed, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
        ) {
            rememberedItemsIndexed(memory, entries, key = { _, e -> e.key }) { index, entry ->
                SystemColumn(entry, narrow, wide, onFocused = { onSpot(entry.spot()) }, modifier = Modifier.fillMaxHeight().staggeredEntrance(index, clock))
            }
        }
    }
}

@Composable
private fun SystemColumn(entry: StageEntry, narrow: androidx.compose.ui.unit.Dp, wide: androidx.compose.ui.unit.Dp, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val shape = VelaTheme.shapes.tile
    val accent = Color(entry.accent)
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    // Width is a layout property, so this one animation does recompose; a handful of columns is cheap.
    val width by animateDpAsState(if (focused) wide else narrow, tween(VelaTheme.motion.focusDurationMs + 120), label = "columnWidth")
    Box(
        modifier
            .width(width)
            .velaFocusable(shape, interaction, entry.open, onFocused = onFocused, scaleOverride = 1f, edge = true)
            .clip(shape)
            .background(colors.surface),
    ) {
        if (entry.background != null) {
            VelaImage(model = artworkModel(entry.background), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
        } else if (entry.covers.isNotEmpty()) {
            VelaImage(model = artworkModel(entry.covers.first()), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), accent.copy(alpha = 0.25f), colors.background.copy(alpha = 0.6f), colors.background.copy(alpha = 0.95f))))
                if (!focused) drawRect(colors.background.copy(alpha = 0.25f))
            },
        )
        // Sized from the narrow width so opening the column does not grow the console over the text.
        Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(narrow * 0.55f), contentAlignment = Alignment.Center) {
            EntryIcon(entry, Modifier.fillMaxSize(), tint = colors.onBackground)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(entry.title, style = if (focused) VelaTheme.typography.title else VelaTheme.typography.headline, color = colors.onBackground, maxLines = if (focused) 2 else 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (focused) metaLine(entry.subtitle) else countLine(entry.subtitle),
                style = if (focused) VelaTheme.typography.overline else VelaTheme.typography.caption,
                color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
