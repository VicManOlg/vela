package io.vela.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.appIconModel
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/** Portrait box-art card. Title appears only when there is no art, so grids stay clean. */
@Composable
fun GameCard(
    game: GameSummary,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for rails; null lets the parent (a grid cell) decide. */
    width: Dp? = VelaTheme.dimens.cardWidth,
    onLongPress: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.card
    val colors = VelaTheme.colors
    val focused by rememberFocusState(interactionSource)
    val isApp = game.kind == GameKind.ANDROID_APP
    val model = if (isApp && game.boxArt == null) game.packageName?.let(::appIconModel) else artworkModel(game.boxArt)

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(VelaTheme.dimens.boxArtAspect)
            .velaFocusable(shape, interactionSource, onClick, onLongPress, onFocused)
            .clip(shape)
            .background(colors.surface),
    ) {
        if (isApp && game.boxArt == null) {
            AppIconTile(model, accent, game.title)
        } else {
            VelaImage(
                model = model,
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                accent = accent,
                placeholder = { TitlePlaceholder(game.title, accent) },
            )
        }
        if (game.favorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp),
            )
        }
        if (focused && game.boxArt != null) {
            // Title strip only while focused, so the art breathes when browsing.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.85f))))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(game.title, style = VelaTheme.typography.label, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppIconTile(model: Any?, accent: Color, title: String) {
    val colors = VelaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), colors.surface))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VelaImage(model = model, contentDescription = null, modifier = Modifier.size(64.dp), contentScale = ContentScale.Fit, accent = accent)
        Spacer(Modifier.height(12.dp))
        Text(title, style = VelaTheme.typography.label, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

/** Shown when a game has no box art: platform colour + title, readable at a glance. */
@Composable
fun TitlePlaceholder(title: String, accent: Color, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), colors.surfaceElevated))),
    ) {
        Text(
            title,
            style = VelaTheme.typography.headline,
            color = colors.onBackground,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}

/** 16:9 card with background/screenshot and logo or title; used for "Continue playing". */
@Composable
fun HeroCard(
    game: GameSummary,
    accent: Color,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = VelaTheme.dimens.cardWidth * 2.1f,
    onLongPress: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.tile
    val colors = VelaTheme.colors
    val art = game.background ?: game.boxArt
    Box(
        modifier
            .width(width)
            .aspectRatio(VelaTheme.dimens.heroAspect)
            .velaFocusable(shape, interactionSource, onClick, onLongPress, onFocused, scaleOverride = 1.04f)
            .clip(shape)
            .background(colors.surface),
    ) {
        VelaImage(
            model = if (game.kind == GameKind.ANDROID_APP && art == null) game.packageName?.let(::appIconModel) else artworkModel(art),
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize(),
            accent = accent,
            contentScale = if (game.kind == GameKind.ANDROID_APP && art == null) ContentScale.Inside else ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.9f)), startY = 80f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            if (game.logo != null) {
                VelaImage(
                    model = artworkModel(game.logo),
                    contentDescription = game.title,
                    modifier = Modifier.height(44.dp).fillMaxWidth(0.7f),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    placeholder = {},
                )
            } else {
                Text(game.title, style = VelaTheme.typography.headline, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (subtitle != null) {
                Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1)
            }
        }
    }
}

/** Platform tile: accent colour block with name and count. Icons/logos arrive with themes later. */
@Composable
fun PlatformTile(
    name: String,
    shortName: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = VelaTheme.dimens.cardWidth * 1.35f,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.tile
    val colors = VelaTheme.colors
    val focused by rememberFocusState(interactionSource)
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(1.6f)
            .velaFocusable(shape, interactionSource, onClick, onFocused = onFocused, scaleOverride = 1.05f)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = if (focused) 0.95f else 0.75f), accent.copy(alpha = 0.35f), colors.surfaceElevated),
                ),
            ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(shortName, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(name, style = VelaTheme.typography.caption, color = colors.onBackground.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (count >= 0) Text("$count", style = VelaTheme.typography.label, color = colors.onBackground.copy(alpha = 0.85f))
        }
    }
}

/** Collection tile: cover on the left, name and count on the right. */
@Composable
fun CollectionTile(
    name: String,
    count: Int,
    coverArt: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = VelaTheme.dimens.cardWidth * 1.6f,
    onLongPress: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.tile
    val colors = VelaTheme.colors
    Row(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(2.2f)
            .velaFocusable(shape, interactionSource, onClick, onLongPress, onFocused, scaleOverride = 1.05f)
            .clip(shape)
            .background(colors.surfaceElevated),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(0.72f)) {
            VelaImage(model = artworkModel(coverArt), contentDescription = null, modifier = Modifier.fillMaxSize(), accent = accent)
        }
        Column(Modifier.padding(14.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(name, style = VelaTheme.typography.headline, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 game" else "$count games", style = VelaTheme.typography.caption, color = colors.muted)
        }
    }
}
