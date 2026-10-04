package io.vela.feature.home

import io.vela.core.ui.components.rememberBackdropPrefetch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import io.vela.core.ui.components.VelaSprings
import io.vela.core.ui.theme.metaLine
import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.model.GameSummary
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.PlatformChip
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.color
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/**
 * Console-style Home: the focused game owns the screen. Its scene is the background (hero
 * mode), its logo or title sits large at the bottom-left, and one row of covers plus a slim row
 * of system chips live along the bottom. Everything else is one press away in Library.
 */
@Composable
fun SpotlightHome(
    state: HomeUiState,
    spotlight: Spotlight?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val memory = rememberFocusMemory()
    val colors = VelaTheme.colors
    val games = remember(state) { state.spotlightGames() }
    val focusedGame = spotlight?.gameId?.let { id -> games.firstOrNull { it.id.value == id } }
    val prefetch = rememberBackdropPrefetch()

    Column(modifier.fillMaxSize()) {
        // Title area takes whatever the rows leave; on short screens it drops to one line.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = VelaTheme.dimens.screenPadding),
        ) {
            val roomy = maxHeight >= 150.dp
            // Room left for the 8 dp gap, the facts line and the 10 dp bottom padding.
            val logoHeight = (maxHeight - 48.dp).coerceIn(40.dp, 120.dp)
            AnimatedContent(
                targetState = spotlight,
                transitionSpec = {
                    // The new title rises on the expressive spatial spring while the old one fades
                    // quickly, so a D-pad run never shows two titles fighting.
                    (fadeIn(VelaSprings.effects()) + slideInVertically(VelaSprings.spatial()) { it / 6 }) togetherWith
                        (fadeOut(VelaSprings.effectsFast()) + slideOutVertically(VelaSprings.spatial()) { -it / 10 })
                },
                label = "spotlightHero",
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp),
            ) { s ->
                Column(Modifier.fillMaxWidth(0.66f)) {
                    val logo = focusedGame?.logo?.takeIf { s?.gameId == focusedGame.id.value }
                    if (logo != null) {
                        VelaImage(
                            model = artworkModel(logo),
                            contentDescription = s?.title,
                            // The logo is the title: as tall as the hero area allows once the facts line fits.
                            modifier = Modifier.height(logoHeight).fillMaxWidth(0.8f),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            placeholder = {},
                        )
                    } else {
                        Text(
                            s?.title ?: "Welcome back",
                            // Over a sharp scene the title carries its own soft shadow.
                            style = VelaTheme.typography.display.copy(shadow = TitleShadow),
                            color = colors.onBackground,
                            maxLines = if (roomy) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Plain facts under the title, read over the artwork: the hint bar already says
                    // what A and X do, so the hero carries only the game.
                    Text(
                        metaLine(s?.subtitle ?: "Pick up where you left off"),
                        style = VelaTheme.typography.body.copy(shadow = TitleShadow),
                        color = colors.onBackground.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (games.isNotEmpty()) {
            Rail("", autoFocus = true, memory = memory) {
                rememberedItems(memory, games, key = { it.id.value }) { game ->
                    val accent = state.platformOf(game)?.platform?.color() ?: colors.accentSecondary
                    GameCard(
                        game = game,
                        accent = accent,
                        // The covers are the stars of this Home: larger than on any rail.
                        width = VelaTheme.dimens.cardWidth * 0.9f,
                        onClick = { viewModel.launch(game) },
                        onLongPress = { viewModel.openMenu(game) },
                        onFocused = {
                            viewModel.spotlightGame(game)
                            // The next scene either way is decoded before the D-pad reaches it.
                            val i = games.indexOf(game)
                            prefetch(games.getOrNull(i + 1)?.let { it.background ?: it.boxArt })
                            prefetch(games.getOrNull(i - 1)?.let { it.background ?: it.boxArt })
                        },
                    )
                }
            }
        }
        if (state.showsPlatforms && state.platforms.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Rail("", autoFocus = games.isEmpty(), memory = memory) {
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
        }
        Spacer(Modifier.height(6.dp))
    }
}

private val TitleShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), blurRadius = 14f)

/** One row for the whole Home: what you were playing, then favourites, then what is new; no repeats. */
private fun HomeUiState.spotlightGames(): List<GameSummary> = gamesInOrder(40)
