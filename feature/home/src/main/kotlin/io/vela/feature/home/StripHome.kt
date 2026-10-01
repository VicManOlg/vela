package io.vela.feature.home

import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.PlatformChip
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.VelaButton
import io.vela.core.ui.components.color
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/**
 * Living-room console style Home: a strip of square tiles along the top, and the focused game
 * owning the rest of the screen below it, logo or title large, facts in spaced capitals and a
 * Play button. Systems live in a slim chip row at the bottom.
 */
@Composable
fun StripHome(
    state: HomeUiState,
    spotlight: Spotlight?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val memory = rememberFocusMemory()
    val colors = VelaTheme.colors
    val games = remember(state) { state.gamesInOrder(40) }
    val focusedGame = spotlight?.gameId?.let { id -> games.firstOrNull { it.id.value == id } }
    // The first tile is focused before any focus callback fires; announce it right away.
    LaunchedEffect(games.firstOrNull()?.id) { if (spotlight == null) games.firstOrNull()?.let(viewModel::spotlightGame) }

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(4.dp))
        Rail("", autoFocus = true, memory = memory) {
            rememberedItems(memory, games, key = { it.id.value }) { game ->
                val accent = state.platformOf(game)?.platform?.color() ?: colors.accent
                GameCard(
                    game = game,
                    accent = accent,
                    width = VelaTheme.dimens.cardWidth * 0.78f,
                    onClick = { viewModel.launch(game) },
                    onLongPress = { viewModel.openMenu(game) },
                    onFocused = { viewModel.spotlightGame(game) },
                )
            }
        }
        // Hero: the focused game explained, with room to breathe.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding)) {
            AnimatedContent(
                targetState = spotlight,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stripHero",
                modifier = Modifier.align(Alignment.CenterStart),
            ) { s ->
                Column(Modifier.fillMaxWidth(0.6f)) {
                    val logo = focusedGame?.logo?.takeIf { s?.gameId == focusedGame.id.value }
                    if (logo != null) {
                        VelaImage(
                            model = artworkModel(logo),
                            contentDescription = s?.title,
                            modifier = Modifier.height((VelaTheme.typography.display.fontSize.value * 2f).dp).fillMaxWidth(0.8f),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            placeholder = {},
                        )
                    } else {
                        Text(s?.title ?: "Welcome back", style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (s?.subtitle ?: "Pick up where you left off").split("   ").filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
                        style = VelaTheme.typography.overline,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (focusedGame != null && s?.gameId == focusedGame.id.value) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VelaButton(if (focusedGame.playCount > 0) "Continue" else "Play", { viewModel.launch(focusedGame) }, primary = true, icon = Icons.Rounded.PlayArrow)
                            VelaButton("Details", { navigation.openGame(focusedGame.id) })
                        }
                    }
                }
            }
        }
        if (state.showsPlatforms && state.platforms.isNotEmpty()) {
            Rail("") {
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
            Spacer(Modifier.height(6.dp))
        }
    }
}
