package io.vela.feature.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vela.core.model.ArtworkType
import io.vela.core.model.GameId
import io.vela.core.model.GameKind
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuCallbacks
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.GlassPanel
import io.vela.core.ui.components.Pill
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.color
import io.vela.core.ui.components.completionLabel
import io.vela.core.ui.components.formatLastPlayed
import io.vela.core.ui.components.formatPlayTime
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.appIconModel
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * Game detail: art on the left, title/logo and facts on the right, actions in a row, then the
 * description and other versions. Play is focused on entry so a single press starts the game.
 */
@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    onOpenGame: (GameId) -> Unit,
    onBackgroundArtwork: (String?, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val game = state.game
    val platform = state.platform
    val accent = platform?.color() ?: VelaTheme.colors.accentSecondary
    val colors = VelaTheme.colors

    LaunchedEffect(game?.id, game?.artwork) {
        onBackgroundArtwork(game?.artwork?.get(ArtworkType.BACKGROUND) ?: game?.artwork?.get(ArtworkType.SCREENSHOT) ?: game?.artwork?.get(ArtworkType.BOX_FRONT), platform?.accentColor ?: 0xFF3D7BFF)
    }
    LaunchedEffect(game != null) { if (game != null) runCatching { playFocus.requestFocus() } }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> { viewModel.openMenu(); true }
            GamepadButton.Y -> { viewModel.toggleFavorite(); true }
            else -> false
        }
    }

    if (game == null) {
        if (!state.loading) {
            LaunchedEffect(Unit) { onBack() }
        }
        return
    }

    val meta = game.metadata
    val isApp = game.kind == GameKind.ANDROID_APP
    val padding = VelaTheme.dimens.screenPadding
    val motion = VelaTheme.motion
    val entrance = remember { Animatable(0f) }
    val slide = with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(game.id) {
        if (motion.reduceMotion) entrance.snapTo(1f) else { entrance.snapTo(0f); entrance.animateTo(1f, tween(motion.transitionDurationMs + 160, easing = FastOutSlowInEasing)) }
    }

    Row(
        modifier
            .fillMaxSize()
            .padding(start = padding, end = padding, top = 28.dp, bottom = 8.dp),
    ) {
        // Left: box art or app icon.
        Box(
            Modifier
                .fillMaxHeight(0.78f)
                .aspectRatio(if (isApp && game.artwork[ArtworkType.BOX_FRONT] == null) 1f else VelaTheme.dimens.boxArtAspect)
                .graphicsLayer {
                    val grow = 0.94f + 0.06f * entrance.value
                    scaleX = grow
                    scaleY = grow
                    alpha = entrance.value
                }
                .clip(VelaTheme.shapes.tile),
        ) {
            val model = game.artwork[ArtworkType.BOX_FRONT]?.let(::artworkModel)
                ?: (game.location as? io.vela.core.model.GameLocation.AndroidApp)?.packageName?.let(::appIconModel)
            if (isApp && game.artwork[ArtworkType.BOX_FRONT] == null) {
                VelaImage(model = model, contentDescription = game.displayTitle, modifier = Modifier.fillMaxSize().padding(24.dp), accent = accent, contentScale = ContentScale.Fit)
            } else {
                io.vela.core.ui.image.FittedArtwork(
                    model = model,
                    contentDescription = game.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    accent = accent,
                    placeholder = { io.vela.core.ui.components.TitlePlaceholder(game.displayTitle, accent) },
                )
            }
        }
        Spacer(Modifier.width(36.dp))

        // Right: title, facts, actions, description.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = (1f - entrance.value) * slide
                    alpha = entrance.value
                }
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
        ) {
            val logo = game.artwork[ArtworkType.LOGO]
            if (logo != null) {
                VelaImage(
                    model = artworkModel(logo),
                    contentDescription = game.displayTitle,
                    modifier = Modifier.height(96.dp).fillMaxWidth(0.6f),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    placeholder = {},
                )
            } else {
                Text(game.displayTitle, style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                platform?.let { Pill(it.shortName, tint = accent) }
                meta?.releaseYear?.let { Pill(it.toString()) }
                meta?.genres?.firstOrNull()?.let { Pill(it) }
                meta?.players?.let { Pill(if (it.contains('-') || it.toIntOrNull() == 1) "$it player" else "$it players") }
                meta?.rating?.let { Pill("${(it * 10).toInt()}/10") }
                if (game.completion != io.vela.core.model.CompletionStatus.NONE) Pill(completionLabel(game.completion), tint = colors.accent)
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                VelaButton(
                    text = if (game.playCount > 0) "Continue" else "Play",
                    onClick = viewModel::launch,
                    primary = true,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.focusRequester(playFocus),
                )
                VelaButton(
                    text = if (game.favorite) "Favorite" else "Add to favorites",
                    onClick = viewModel::toggleFavorite,
                    icon = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                )
                VelaButton("Collections", viewModel::openCollections)
                if (!isApp) VelaButton("Launch with", viewModel::openLaunchWith)
                VelaButton("More", viewModel::openMenu, icon = Icons.Rounded.MoreHoriz)
            }
            Spacer(Modifier.height(18.dp))

            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Fact("Last played", formatLastPlayed(game.lastPlayedAt) ?: "Never")
                    Fact("Play time", if (game.totalPlayTimeMs > 0) formatPlayTime(game.totalPlayTimeMs) else "0m")
                    Fact("Launches", game.playCount.toString())
                    Fact(
                        if (isApp) "Runs with" else "Emulator",
                        state.playerName ?: "None set",
                        warn = state.playerMissing && !isApp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            val description = meta?.description
            if (!description.isNullOrBlank()) {
                Text(description, style = VelaTheme.typography.body, color = colors.onBackground.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth(0.9f))
                Spacer(Modifier.height(14.dp))
            }
            val credits = listOfNotNull(meta?.developer?.let { "Developed by $it" }, meta?.publisher?.let { "Published by $it" })
            if (credits.isNotEmpty()) {
                Text(credits.joinToString("  ·  "), style = VelaTheme.typography.caption, color = colors.muted)
                Spacer(Modifier.height(14.dp))
            }
            if (!isApp) {
                Text(game.fileName, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (state.otherVersions.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("Other versions", style = VelaTheme.typography.headline, color = colors.onBackground)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing)) {
                    items(state.otherVersions, key = { it.id.value }) { other ->
                        GameCard(game = other, accent = accent, width = VelaTheme.dimens.cardWidth * 0.75f, onClick = { onOpenGame(other.id) })
                    }
                }
            }
        }
    }

    GameMenuHost(
        state = menuState,
        callbacks = GameMenuCallbacks(
            onAction = { viewModel.onMenuAction(it) },
            onDismiss = viewModel::dismissMenu,
            onToggleCollection = { viewModel.toggleCollection(it) },
            onStartNewCollection = viewModel::startNewCollection,
            onCreateCollection = { viewModel.createCollection(it) },
            onLaunchWith = { option, remember -> viewModel.launchWith(option, remember) },
            onSetCompletion = { viewModel.setCompletion(it) },
            onConfirmHide = { viewModel.confirmHide(onBack) },
        ),
    )
}

@Composable
private fun Fact(label: String, value: String, warn: Boolean = false) {
    val colors = VelaTheme.colors
    Column {
        Text(label, style = VelaTheme.typography.caption, color = colors.muted)
        Text(value, style = VelaTheme.typography.bodyStrong, color = if (warn) colors.danger else colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
