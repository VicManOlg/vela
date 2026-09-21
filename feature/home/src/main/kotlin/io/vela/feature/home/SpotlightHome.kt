package io.vela.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.model.GameSummary
import io.vela.core.ui.components.ButtonGlyph
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.Pill
import io.vela.core.ui.components.PlatformChip
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.color
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.input.GamepadButton
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
    val colors = VelaTheme.colors
    val games = remember(state) { state.spotlightGames() }
    val focusedGame = spotlight?.gameId?.let { id -> games.firstOrNull { it.id.value == id } }

    Column(modifier.fillMaxSize()) {
        // Title area takes whatever the rows leave; on short screens it drops to one line.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = VelaTheme.dimens.screenPadding),
        ) {
            val roomy = maxHeight >= 150.dp
            AnimatedContent(
                targetState = spotlight,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 8 }) togetherWith (fadeOut() + slideOutVertically { -it / 8 })
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
                            modifier = Modifier.height((VelaTheme.typography.display.fontSize.value * (if (roomy) 2.2f else 1.4f)).dp).fillMaxWidth(0.8f),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            placeholder = {},
                        )
                    } else {
                        Text(
                            s?.title ?: "Welcome back",
                            style = VelaTheme.typography.display,
                            color = colors.onBackground,
                            maxLines = if (roomy) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val facts = s?.subtitle?.split("   ")?.filter { it.isNotBlank() } ?: listOf("Pick up where you left off")
                        facts.take(3).forEach { Pill(it) }
                    }
                    if (roomy && s?.gameId != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ButtonGlyph(GamepadButton.A)
                            Spacer(Modifier.width(8.dp))
                            Text("Play", style = VelaTheme.typography.label, color = colors.onBackground.copy(alpha = 0.85f))
                            Spacer(Modifier.width(18.dp))
                            ButtonGlyph(GamepadButton.X)
                            Spacer(Modifier.width(8.dp))
                            Text("Menu", style = VelaTheme.typography.label, color = colors.onBackground.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }

        if (games.isNotEmpty()) {
            Rail("", autoFocus = true) {
                items(games, key = { it.id.value }) { game ->
                    val accent = state.platformOf(game)?.platform?.color() ?: colors.accentSecondary
                    GameCard(
                        game = game,
                        accent = accent,
                        width = VelaTheme.dimens.cardWidth * 0.72f,
                        onClick = { viewModel.launch(game) },
                        onLongPress = { viewModel.openMenu(game) },
                        onFocused = { viewModel.spotlightGame(game) },
                    )
                }
            }
        }
        if (state.platforms.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Rail("", autoFocus = games.isEmpty()) {
                items(state.platforms, key = { it.id.value }) { entry ->
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

/** One row for the whole Home: what you were playing, then favourites, then what is new; no repeats. */
private fun HomeUiState.spotlightGames(): List<GameSummary> =
    (continuePlaying + favorites + recentlyAdded + recommended + recent + android)
        .distinctBy { it.id }
        .take(40)
