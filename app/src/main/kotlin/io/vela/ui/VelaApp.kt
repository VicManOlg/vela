package io.vela.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.vela.core.data.usecase.UiMessage
import io.vela.core.model.CollectionId
import io.vela.core.model.ConfirmButton
import io.vela.core.model.GameId
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.ButtonHint
import io.vela.core.ui.components.ButtonHints
import io.vela.core.ui.components.DynamicBackground
import io.vela.core.ui.components.TopBar
import io.vela.core.ui.components.TopTab
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.input.GamepadInputController
import io.vela.core.ui.input.LocalGamepad
import io.vela.core.ui.theme.VelaTheme
import io.vela.feature.apps.AndroidScreen
import io.vela.feature.home.HomeNavigation
import io.vela.feature.home.HomeScreen
import io.vela.feature.library.CollectionsScreen
import io.vela.feature.library.GameDetailRoute
import io.vela.feature.library.GameDetailScreen
import io.vela.feature.library.GameGridRoute
import io.vela.feature.library.GameGridScreen
import io.vela.feature.library.PlatformsScreen
import io.vela.feature.search.SearchScreen
import io.vela.feature.settings.SettingsScreen
import io.vela.feature.setup.SetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.ui.graphics.Color

@Serializable private data object SetupRoute
@Serializable private data class ShellRoute(val tab: String = ShellTab.HOME.name)

private enum class ShellTab(val label: String) { HOME("Home"), LIBRARY("Library"), ANDROID("Android"), COLLECTIONS("Collections"), SEARCH("Search"), SETTINGS("Settings") }

/** Root composable: theme, backdrop, navigation, hints and transient messages. */
@Composable
fun VelaApp(gamepad: GamepadInputController, viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val backdrop by viewModel.backdrop.collectAsStateWithLifecycle()
    val prefs = settings ?: return

    VelaTheme(spec = theme, uiScale = prefs.uiScale, reduceMotion = prefs.reduceMotion) {
        CompositionLocalProvider(LocalGamepad provides gamepad) {
            val navController = rememberNavController()
            Box(Modifier.fillMaxSize().background(VelaTheme.colors.background)) {
                DynamicBackground(artwork = backdrop.artwork, accent = Color(backdrop.accent))
                NavHost(
                    navController = navController,
                    startDestination = if (prefs.setupCompleted) ShellRoute() else SetupRoute,
                ) {
                    composable<SetupRoute> {
                        SetupScreen(onDone = { navController.navigate(ShellRoute()) { popUpTo<SetupRoute> { inclusive = true } } })
                    }
                    composable<ShellRoute> { entry ->
                        val route: ShellRoute = entry.toRoute()
                        Shell(navController, route, viewModel, swapped = prefs.confirmButton == ConfirmButton.B)
                    }
                    composable<GameGridRoute> {
                        Column(Modifier.fillMaxSize()) {
                            GameGridScreen(
                                onOpenGame = { navController.navigate(GameDetailRoute(it.value)) },
                                onBackgroundArtwork = viewModel::setBackdrop,
                                modifier = Modifier.weight(1f),
                            )
                            ButtonHints(listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.Y, "Sort"), ButtonHint(GamepadButton.B, "Back")), swapped = prefs.confirmButton == ConfirmButton.B)
                        }
                    }
                    composable<GameDetailRoute> {
                        Column(Modifier.fillMaxSize()) {
                            GameDetailScreen(
                                onBack = { navController.popBackStack() },
                                onOpenGame = { navController.navigate(GameDetailRoute(it.value)) },
                                onBackgroundArtwork = viewModel::setBackdrop,
                                modifier = Modifier.weight(1f),
                            )
                            ButtonHints(listOf(ButtonHint(GamepadButton.A, "Select"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.Y, "Favorite"), ButtonHint(GamepadButton.B, "Back")), swapped = prefs.confirmButton == ConfirmButton.B)
                        }
                    }
                }
                MessageToast(viewModel)
            }
        }
    }
}

@Composable
private fun Shell(navController: NavHostController, route: ShellRoute, appViewModel: AppViewModel, swapped: Boolean) {
    var tab by rememberSaveable { mutableStateOf(runCatching { ShellTab.valueOf(route.tab) }.getOrDefault(ShellTab.HOME)) }
    val tabs = remember { ShellTab.entries.map { TopTab(it.name, it.label) } }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.L1 -> { tab = ShellTab.entries[(tab.ordinal - 1 + ShellTab.entries.size) % ShellTab.entries.size]; true }
            GamepadButton.R1 -> { tab = ShellTab.entries[(tab.ordinal + 1) % ShellTab.entries.size]; true }
            GamepadButton.SELECT -> { tab = ShellTab.SEARCH; true }
            else -> false
        }
    }

    val openGame: (GameId) -> Unit = { navController.navigate(GameDetailRoute(it.value)) }
    val openPlatform: (PlatformId) -> Unit = { navController.navigate(GameGridRoute(platformId = it.value)) }
    val openCollection: (CollectionId) -> Unit = { navController.navigate(GameGridRoute(collectionId = it.value)) }

    Column(Modifier.fillMaxSize()) {
        TopBar(tabs = tabs, selectedId = tab.name, onSelect = { id -> tab = ShellTab.valueOf(id) })
        Box(Modifier.weight(1f)) {
            when (tab) {
                ShellTab.HOME -> HomeScreen(
                    navigation = HomeNavigation(
                        openGame = openGame,
                        openPlatform = openPlatform,
                        openCollection = openCollection,
                        openAndroid = { tab = ShellTab.ANDROID },
                        openLibrary = { tab = ShellTab.LIBRARY },
                        openSettings = { tab = ShellTab.SETTINGS },
                    ),
                    onSpotlightChanged = { s -> appViewModel.setBackdrop(s?.artwork, s?.accent ?: 0xFF3D7BFF) },
                )
                ShellTab.LIBRARY -> PlatformsScreen(
                    onOpenPlatform = openPlatform,
                    onOpenFavorites = { navController.navigate(GameGridRoute(favorites = true, title = "Favorites")) },
                    onOpenAll = { navController.navigate(GameGridRoute(title = "All games")) },
                    onOpenSettings = { tab = ShellTab.SETTINGS },
                    onBackgroundAccent = { appViewModel.setBackdrop(null, it) },
                )
                ShellTab.ANDROID -> AndroidScreen(onOpenGame = openGame, onBackgroundArtwork = appViewModel::setBackdrop)
                ShellTab.COLLECTIONS -> CollectionsScreen(onOpenCollection = openCollection)
                ShellTab.SEARCH -> SearchScreen(onOpenGame = openGame, onBackgroundArtwork = appViewModel::setBackdrop)
                ShellTab.SETTINGS -> SettingsScreen(onBackgroundAccent = { appViewModel.setBackdrop(null, it) })
            }
        }
        ButtonHints(
            hints = when (tab) {
                ShellTab.HOME -> listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.LIBRARY -> listOf(ButtonHint(GamepadButton.A, "Open"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.ANDROID -> listOf(ButtonHint(GamepadButton.A, "Launch"), ButtonHint(GamepadButton.X, "Games"), ButtonHint(GamepadButton.Y, "Apps"))
                ShellTab.COLLECTIONS -> listOf(ButtonHint(GamepadButton.A, "Open"), ButtonHint(GamepadButton.X, "New"))
                ShellTab.SEARCH -> listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Type"), ButtonHint(GamepadButton.Y, "System"))
                ShellTab.SETTINGS -> listOf(ButtonHint(GamepadButton.A, "Change"), ButtonHint(GamepadButton.L2, "Pages"))
            },
            swapped = swapped,
        )
    }
}

@Composable
private fun MessageToast(viewModel: AppViewModel) {
    var current by remember { mutableStateOf<UiMessage?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            current = message
            scope.launch {
                delay(3200)
                if (current == message) current = null
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = current != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            val message = current ?: return@AnimatedVisibility
            val colors = VelaTheme.colors
            Box(
                Modifier
                    .clip(VelaTheme.shapes.chip)
                    .background(if (message.isError) colors.danger else colors.onBackground)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(message.text, style = VelaTheme.typography.bodyStrong, color = if (message.isError) colors.onBackground else colors.background)
            }
        }
    }
}
