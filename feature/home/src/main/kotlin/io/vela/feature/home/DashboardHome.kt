package io.vela.feature.home

import io.vela.core.ui.components.rememberedItems
import io.vela.core.ui.components.rememberFocusMemory
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.HeroCard
import io.vela.core.ui.components.PlatformTile
import io.vela.core.ui.components.color
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.formatLastPlayed
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.theme.VelaTheme

/**
 * Dashboard style Home: blocks. The first block pairs one big 16:9 tile of the last played
 * game with two rows of square tiles for what came before; below it, a block of system tiles
 * and one of pinned apps. Everything is flat and edge-aligned.
 */
@Composable
fun DashboardHome(
    state: HomeUiState,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val recent = remember(state) { state.gamesInOrder(25) }
    val hero = recent.firstOrNull()
    val rest = recent.drop(1).take(24)
    val gap = VelaTheme.dimens.railSpacing
    val small = VelaTheme.dimens.cardWidth * 0.72f
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(recent.isNotEmpty()), memory = memory)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = focusBleed(), bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.sectionSpacing - 8.dp),
    ) {
        item(key = "play") {
            Column(Modifier.padding(horizontal = VelaTheme.dimens.screenPadding)) {
                BlockTitle("Play")
                Row(Modifier.fillMaxWidth().height(small / VelaTheme.dimens.boxArtAspect * 2 + gap)) {
                    if (hero != null) {
                        val accent = state.platformOf(hero)?.platform?.color() ?: colors.accent
                        HeroCard(
                            game = hero,
                            accent = accent,
                            subtitle = formatLastPlayed(hero.lastPlayedAt),
                            width = (small / VelaTheme.dimens.boxArtAspect * 2 + gap) * VelaTheme.dimens.heroAspect,
                            onClick = { viewModel.launch(hero) },
                            onLongPress = { viewModel.openMenu(hero) },
                            onFocused = { viewModel.spotlightGame(hero) },
                            modifier = Modifier.fillMaxHeight().focusRequester(autoFocus),
                        )
                        Spacer(Modifier.width(gap))
                    }
                    // Two stacked rows of square tiles filling the remaining width.
                    val half = rest.chunked(maxOf(1, (rest.size + 1) / 2)).let { if (it.size < 2) it + List(2 - it.size) { emptyList() } else it }
                    Column(Modifier.weight(1f).fillMaxHeight().focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        half.take(2).forEach { row ->
                            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                rememberedItems(memory, row, key = { it.id.value }) { game ->
                                    val accent = state.platformOf(game)?.platform?.color() ?: colors.accent
                                    GameCard(
                                        game = game,
                                        accent = accent,
                                        width = small,
                                        onClick = { viewModel.launch(game) },
                                        onLongPress = { viewModel.openMenu(game) },
                                        onFocused = { viewModel.spotlightGame(game) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.showsPlatforms && state.platforms.isNotEmpty()) {
            item(key = "systems") {
                Column {
                    BlockTitle("Systems", Modifier.padding(horizontal = VelaTheme.dimens.screenPadding))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = VelaTheme.dimens.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        rememberedItems(memory, state.platforms, key = { it.id.value }) { entry ->
                            PlatformTile(
                                name = entry.displayName,
                                shortName = entry.platform.shortName,
                                count = entry.gameCount,
                                accent = entry.platform.color(),
                                icon = entry.iconPath,
                                width = VelaTheme.dimens.cardWidth * 1.15f,
                                onClick = { navigation.openPlatform(entry.id) },
                                onFocused = { viewModel.spotlightPlatform(entry) },
                            )
                        }
                    }
                }
            }
        }
        // Android games already flow into the tiles above when their section is on; this block is the pinned apps.
        if (state.showsQuickApps && state.quickApps.isNotEmpty()) {
            item(key = "apps") {
                Column {
                    BlockTitle("Quick apps", Modifier.padding(horizontal = VelaTheme.dimens.screenPadding))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = VelaTheme.dimens.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        rememberedItems(memory, state.quickApps, key = { it.id.value }) { app ->
                            GameCard(
                                game = app,
                                accent = Color(0xFF3DDC84),
                                width = small,
                                onClick = { viewModel.launch(app) },
                                onLongPress = { viewModel.openMenu(app) },
                                onFocused = { viewModel.spotlightGame(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = VelaTheme.typography.overline, color = VelaTheme.colors.muted, modifier = modifier.padding(bottom = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
