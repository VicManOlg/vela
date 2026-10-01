package io.vela.feature.home

import io.vela.core.ui.components.rememberedItemsIndexed
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.PlatformChip
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.color
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.theme.VelaTheme
import kotlin.math.abs

/**
 * One big carousel in the middle of the Home: the focused cover stands centred and full size
 * while its neighbours shrink, tilt away and fade, the scene behind follows it, and its title,
 * facts and a Play button sit underneath. Systems are a slim chip row at the bottom. Made for
 * themes that want the Home to feel like a single shelf of covers rather than a dashboard.
 */
@Composable
fun CarouselHome(
    state: HomeUiState,
    spotlight: Spotlight?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val motion = VelaTheme.motion
    val games = remember(state) { state.gamesInOrder(40) }
    val focusedGame = spotlight?.gameId?.let { id -> games.firstOrNull { it.id.value == id } }
    val rowState = rememberLazyListState()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(games.isNotEmpty()), memory = memory)
    var focusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(games.firstOrNull()?.id) { if (spotlight == null) games.firstOrNull()?.let(viewModel::spotlightGame) }
    LaunchedEffect(focusedIndex) { if (focusedIndex >= 0) rowState.animateScrollToItem(focusedIndex) }

    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bleed = focusBleed() * 1.2f
            val cardHeight = (maxHeight - bleed * 2).coerceAtMost(VelaTheme.dimens.cardWidth * 2.4f)
            val cardWidth = cardHeight * VelaTheme.dimens.boxArtAspect
            val spacing = VelaTheme.dimens.railSpacing * 1.6f
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            val tilt = if (motion.reduceMotion) 0f else 14f
            LazyRow(
                state = rowState,
                flingBehavior = rememberSnapFlingBehavior(rowState),
                modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = bleed),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rememberedItemsIndexed(memory, games, key = { _, g -> g.id.value }) { index, game ->
                    val accent = state.platformOf(game)?.platform?.color() ?: colors.accent
                    GameCard(
                        game = game,
                        accent = accent,
                        width = cardWidth,
                        onClick = { viewModel.launch(game) },
                        onLongPress = { viewModel.openMenu(game) },
                        onFocused = { focusedIndex = index; viewModel.spotlightGame(game) },
                        modifier = Modifier.graphicsLayer {
                            val info = rowState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCenter = item.offset + item.size / 2f
                            val distance = ((itemCenter - center) / (item.size + spacing.toPx())).coerceIn(-3f, 3f)
                            val near = abs(distance).coerceAtMost(1f)
                            val shrink = 1f - 0.2f * near
                            scaleX = shrink
                            scaleY = shrink
                            rotationY = -distance * tilt
                            cameraDistance = 16f * density
                            alpha = 1f - 0.4f * near
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    )
                }
            }
        }
        // The focused game explained, centred under the shelf.
        AnimatedContent(
            targetState = spotlight,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "carouselCaption",
            modifier = Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding),
        ) { s ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s?.title ?: "Welcome back", style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    (s?.subtitle ?: "Pick up where you left off").split("   ").filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
                    style = VelaTheme.typography.overline, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
                if (focusedGame != null && s?.gameId == focusedGame.id.value) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VelaButton(if (focusedGame.playCount > 0) "Continue" else "Play", { viewModel.launch(focusedGame) }, primary = true, icon = Icons.Rounded.PlayArrow)
                        VelaButton("Details", { navigation.openGame(focusedGame.id) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.showsPlatforms && state.platforms.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusRestorer().focusGroup(),
                contentPadding = PaddingValues(horizontal = VelaTheme.dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rememberedItems(memory, state.platforms, key = { it.id.value }) { entry ->
                    PlatformChip(
                        shortName = entry.platform.shortName,
                        count = entry.gameCount,
                        accent = entry.platform.color(),
                        icon = entry.iconPath,
                        onClick = { navigation.openPlatform(entry.id) },
                        onFocused = { viewModel.spotlightPlatform(entry) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(Modifier.height(1.dp))
    }
}
