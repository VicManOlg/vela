package io.vela.feature.home

import io.vela.core.model.GameMenuEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vela.core.model.CollectionId
import io.vela.core.model.GameId
import io.vela.core.model.GameSummary
import io.vela.core.model.HomeRail
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.CollectionTile
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.GameMenuEvents
import io.vela.core.ui.components.GameMenuHost
import io.vela.core.ui.components.HeroCard
import io.vela.core.ui.components.PlatformTile
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.color
import io.vela.core.ui.components.formatLastPlayed
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.model.HomeLayout
import io.vela.core.ui.components.VelaButton

/** Navigation the Home feature can request; the app module wires these to routes. */
class HomeNavigation(
    val openGame: (GameId) -> Unit,
    val openPlatform: (PlatformId) -> Unit,
    val openCollection: (CollectionId) -> Unit,
    val openAndroid: () -> Unit,
    val openLibrary: () -> Unit,
    val openCollections: () -> Unit,
    val openSearch: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Home: a spotlight header that follows focus, then the rails the user enabled. The whole
 * screen is one vertical list so D-pad down/up walks rails naturally.
 */
@Composable
fun HomeScreen(
    navigation: HomeNavigation,
    onSpotlightChanged: (Spotlight?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spotlight by viewModel.spotlight.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(spotlight) { onSpotlightChanged(spotlight) }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.X -> {
                val id = spotlight?.gameId
                val game = id?.let { gid -> state.allGames().firstOrNull { it.id.value == gid } }
                if (game != null) viewModel.openMenu(game)
                game != null
            }
            else -> false
        }
    }

    Box(modifier.fillMaxSize()) {
        if (state.isEmpty && state.android.isEmpty()) {
            EmptyState(
                title = "Your library is empty",
                message = "Add the folders where your games live and Vela will find them, sort them by system and fetch artwork.",
                actionLabel = "Open settings",
                onAction = navigation.openSettings,
                modifier = Modifier.padding(top = 40.dp),
            )
            return@Box
        }
        val layout = when (state.layout) {
            HomeLayout.THEME -> homeLayoutFor(VelaTheme.spec.layout.homeLayout)
            else -> state.layout
        }
        when (layout) {
            HomeLayout.SPOTLIGHT -> { SpotlightHome(state, spotlight, viewModel, navigation); return@Box }
            HomeLayout.TILES -> { TilesHome(state, spotlight, viewModel, navigation); return@Box }
            HomeLayout.STRIP -> { StripHome(state, spotlight, viewModel, navigation); return@Box }
            HomeLayout.DASHBOARD -> { DashboardHome(state, viewModel, navigation); return@Box }
            HomeLayout.CAROUSEL -> { CarouselHome(state, spotlight, viewModel, navigation); return@Box }
            HomeLayout.RAILS, HomeLayout.THEME -> Unit
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.sectionSpacing - 12.dp),
        ) {
            item(key = "spotlight") { SpotlightHeader(spotlight) }

            var railsShown = 0
            state.rails.forEach { rail ->
                val first = railsShown == 0
                when (rail) {
                    HomeRail.CONTINUE_PLAYING -> if (state.continuePlaying.isNotEmpty()) {
                        item(key = rail.name) {
                            Rail("Continue playing", autoFocus = first) {
                                items(state.continuePlaying, key = { it.id.value }) { game ->
                                    val accent = state.platformOf(game)?.platform?.color() ?: VelaTheme.colors.accentSecondary
                                    HeroCard(
                                        game = game,
                                        accent = accent,
                                        subtitle = formatLastPlayed(game.lastPlayedAt),
                                        onClick = { viewModel.launch(game) },
                                        onLongPress = { viewModel.openMenu(game) },
                                        onFocused = { viewModel.spotlightGame(game) },
                                    )
                                }
                            }
                        }
                    }
                    HomeRail.RECENT -> gameRail(rail.name, "Recent", state.recent, state, viewModel, navigation, autoFocus = first)
                    HomeRail.FAVORITES -> gameRail(rail.name, "Favorites", state.favorites, state, viewModel, navigation, autoFocus = first)
                    HomeRail.RECOMMENDED -> gameRail(rail.name, "Because you play", state.recommended, state, viewModel, navigation, subtitle = "Unplayed games from the systems you use most", autoFocus = first)
                    HomeRail.RECENTLY_ADDED -> gameRail(rail.name, "Recently added", state.recentlyAdded, state, viewModel, navigation, autoFocus = first)
                    HomeRail.TOP_RATED -> gameRail(rail.name, "Your top rated", state.topRated, state, viewModel, navigation, subtitle = "Games you gave the most stars", autoFocus = first)
                    HomeRail.ANDROID -> gameRail(rail.name, "Android games", state.android, state, viewModel, navigation, accentOverride = Color(0xFF3DDC84), autoFocus = first)
                    HomeRail.APPS -> if (state.quickApps.isNotEmpty()) {
                        item(key = rail.name) {
                            Rail("Quick apps", subtitle = "Pinned apps, one press away from the games", autoFocus = first, trailing = { VelaButton("Edit", navigation.openAndroid) }) {
                                items(state.quickApps, key = { it.id.value }) { app ->
                                    GameCard(
                                        game = app,
                                        accent = Color(0xFF8AB4F8),
                                        width = VelaTheme.dimens.cardWidth * 0.8f,
                                        onClick = { viewModel.launch(app) },
                                        onLongPress = { viewModel.openMenu(app) },
                                        onFocused = { viewModel.spotlightGame(app) },
                                    )
                                }
                            }
                        }
                    }
                    HomeRail.PLATFORMS -> if (state.platforms.isNotEmpty()) {
                        item(key = rail.name) {
                            Rail("Systems", subtitle = "${state.platforms.size} systems, ${state.totalGames} games", autoFocus = first) {
                                items(state.platforms, key = { it.id.value }) { entry ->
                                    PlatformTile(
                                        name = entry.displayName,
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
                    }
                    HomeRail.COLLECTIONS -> if (state.collections.isNotEmpty()) {
                        item(key = rail.name) {
                            Rail("Collections", autoFocus = first) {
                                items(state.collections, key = { it.id.value }) { collection ->
                                    CollectionTile(
                                        name = collection.name,
                                        count = collection.gameCount,
                                        coverArt = collection.coverArt,
                                        accent = collection.accentColor?.let(::Color) ?: VelaTheme.colors.accent,
                                        onClick = { navigation.openCollection(collection.id) },
                                        onFocused = { viewModel.spotlightCollection(collection) },
                                    )
                                }
                            }
                        }
                    }
                }
                railsShown += when (rail) {
                    HomeRail.CONTINUE_PLAYING -> if (state.continuePlaying.isNotEmpty()) 1 else 0
                    HomeRail.RECENT -> if (state.recent.isNotEmpty()) 1 else 0
                    HomeRail.FAVORITES -> if (state.favorites.isNotEmpty()) 1 else 0
                    HomeRail.RECOMMENDED -> if (state.recommended.isNotEmpty()) 1 else 0
                    HomeRail.RECENTLY_ADDED -> if (state.recentlyAdded.isNotEmpty()) 1 else 0
                    HomeRail.TOP_RATED -> if (state.topRated.isNotEmpty()) 1 else 0
                    HomeRail.ANDROID -> if (state.android.isNotEmpty()) 1 else 0
                    HomeRail.APPS -> if (state.quickApps.isNotEmpty()) 1 else 0
                    HomeRail.PLATFORMS -> if (state.platforms.isNotEmpty()) 1 else 0
                    HomeRail.COLLECTIONS -> if (state.collections.isNotEmpty()) 1 else 0
                }
            }
        }
    }

    GameMenuEvents(viewModel.menuEvents) { event ->
        if (event is GameMenuEvent.OpenDetails) navigation.openGame(event.game.id)
    }
    GameMenuHost(state = menuState, actions = viewModel.menu)
}

private fun androidx.compose.foundation.lazy.LazyListScope.gameRail(
    key: String,
    title: String,
    games: List<GameSummary>,
    state: HomeUiState,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    subtitle: String? = null,
    accentOverride: Color? = null,
    autoFocus: Boolean = false,
) {
    if (games.isEmpty()) return
    item(key = key) {
        Rail(title, subtitle = subtitle, autoFocus = autoFocus) {
            items(games, key = { it.id.value }) { game ->
                val accent = accentOverride ?: state.platformOf(game)?.platform?.color() ?: VelaTheme.colors.accentSecondary
                GameCard(
                    game = game,
                    accent = accent,
                    onClick = { viewModel.launch(game) },
                    onLongPress = { viewModel.openMenu(game) },
                    onFocused = { viewModel.spotlightGame(game) },
                )
            }
        }
    }
}

@Composable
private fun SpotlightHeader(spotlight: Spotlight?) {
    val colors = VelaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = VelaTheme.dimens.screenPadding)
            .height((VelaTheme.typography.display.fontSize.value * 2.7f).dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedContent(
            targetState = spotlight,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { -it / 6 })
            },
            label = "spotlight",
        ) { s ->
            Column {
                Text(
                    s?.title ?: "Welcome back",
                    style = VelaTheme.typography.display,
                    color = colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s?.subtitle ?: "Pick up where you left off",
                    style = VelaTheme.typography.body,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Theme key -> layout; unknown or missing keys mean the classic rails. */
internal fun homeLayoutFor(key: String?): HomeLayout = when (key?.lowercase()) {
    "spotlight" -> HomeLayout.SPOTLIGHT
    "tiles" -> HomeLayout.TILES
    "strip" -> HomeLayout.STRIP
    "dashboard" -> HomeLayout.DASHBOARD
    "carousel" -> HomeLayout.CAROUSEL
    else -> HomeLayout.RAILS
}

private fun HomeUiState.allGames(): List<GameSummary> =
    continuePlaying + recent + favorites + android + recommended + recentlyAdded + topRated + quickApps
