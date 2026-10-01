package io.vela.ui

import io.vela.core.ui.components.LocalControllerLayout
import io.vela.core.ui.components.LocalHapticsEnabled
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.vela.core.data.usecase.UiMessage
import io.vela.core.model.CollectionId
import io.vela.core.model.ConfirmButton
import io.vela.core.model.TabBarMode
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound
import io.vela.core.ui.sound.UiSounds
import kotlinx.coroutines.flow.Flow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import io.vela.core.ui.theme.LocalDynamicAccent
import io.vela.core.ui.theme.liveAccent
import androidx.compose.foundation.layout.Row
import io.vela.core.ui.components.BatteryIndicator
import io.vela.core.ui.components.Clock
import io.vela.core.ui.components.VelaMark

@Serializable private data object SetupRoute
@Serializable private data object ShellRoute
@Serializable private data object AndroidRoute


/** Root composable: theme, backdrop, navigation, hints, launch overlay and transient messages. */
@Composable
fun VelaApp(
    gamepad: GamepadInputController,
    homePresses: Flow<Unit>,
    sounds: UiSounds?,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val backdrop by viewModel.backdrop.collectAsStateWithLifecycle()
    val prefs = settings ?: return

    VelaTheme(spec = theme, uiScale = prefs.uiScale, reduceMotion = prefs.reduceMotion, showClock = prefs.showClock, showBattery = prefs.showBattery) {
        CompositionLocalProvider(
            LocalGamepad provides gamepad,
            LocalUiSounds provides sounds,
            LocalDynamicAccent provides if (VelaTheme.effects.dynamicAccent) backdrop.dynamicAccent?.let(::Color) else null,
            LocalHapticsEnabled provides prefs.hapticFeedback,
            LocalControllerLayout provides prefs.controllerLayout,
        ) {
            val navController = rememberNavController()
            // Fixed for the life of the NavHost: flipping it when setup completes would rebuild the
            // graph underneath the setup screen's own navigation.
            val startDestination: Any = remember { if (prefs.setupCompleted) ShellRoute else SetupRoute }
            // The Home button (Vela as launcher) always lands on the shell's Home tab. One collector
            // for both: the Shell is not composed while a detail screen is on top.
            LaunchedEffect(navController) {
                homePresses.collect {
                    navController.popBackStack<ShellRoute>(inclusive = false)
                    viewModel.selectTab(ShellTab.HOME)
                }
            }
            // Screen transitions follow the theme's motion (and collapse to nothing under Reduce motion).
            val t = VelaTheme.motion.transitionDurationMs
            val long = (t * 0.875f).toInt()
            val short = (t * 0.56f).toInt()
            Box(Modifier.fillMaxSize().background(VelaTheme.colors.background)) {
                DynamicBackground(artwork = backdrop.artwork, accent = Color(backdrop.accent))
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { fadeIn(tween(long)) + slideInHorizontally(tween(long)) { it / 14 } },
                    exitTransition = { fadeOut(tween(short)) + scaleOut(tween(long), targetScale = 0.98f) },
                    popEnterTransition = { fadeIn(tween(long)) + scaleIn(tween(long), initialScale = 0.98f) },
                    popExitTransition = { fadeOut(tween(short)) + slideOutHorizontally(tween(long)) { it / 14 } },
                ) {
                    composable<SetupRoute> {
                        SetupScreen(onDone = whileResumed { navController.navigate(ShellRoute) { popUpTo<SetupRoute> { inclusive = true } } })
                    }
                    composable<ShellRoute> {
                        Shell(navController, viewModel, swapped = prefs.confirmButton == ConfirmButton.B, tabBar = prefs.tabBar)
                    }
                    composable<GameGridRoute> {
                        Column(Modifier.fillMaxSize()) {
                            GameGridScreen(
                                onOpenGame = whileResumed { id: GameId -> navController.navigate(GameDetailRoute(id.value)) },
                                onBackgroundArtwork = viewModel::setBackdrop,
                                modifier = Modifier.weight(1f),
                            )
                            ButtonHints(listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.Y, "Sort"), ButtonHint(GamepadButton.START, "View"), ButtonHint(GamepadButton.B, "Back")), swapped = prefs.confirmButton == ConfirmButton.B)
                        }
                    }
                    composable<AndroidRoute> {
                        Column(Modifier.fillMaxSize()) {
                            AndroidScreen(
                                onOpenGame = whileResumed { id: GameId -> navController.navigate(GameDetailRoute(id.value)) },
                                onBackgroundArtwork = viewModel::setBackdrop,
                                modifier = Modifier.weight(1f),
                            )
                            ButtonHints(listOf(ButtonHint(GamepadButton.A, "Launch"), ButtonHint(GamepadButton.X, "Games"), ButtonHint(GamepadButton.Y, "Apps"), ButtonHint(GamepadButton.B, "Back")), swapped = prefs.confirmButton == ConfirmButton.B)
                        }
                    }
                    composable<GameDetailRoute> {
                        Column(Modifier.fillMaxSize()) {
                            GameDetailScreen(
                                onBack = whileResumed { navController.popBackStack() },
                                onOpenGame = whileResumed { id: GameId -> navController.navigate(GameDetailRoute(id.value)) },
                                onBackgroundArtwork = viewModel::setBackdrop,
                                modifier = Modifier.weight(1f),
                            )
                            ButtonHints(listOf(ButtonHint(GamepadButton.A, "Select"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.Y, "Favorite"), ButtonHint(GamepadButton.B, "Back")), swapped = prefs.confirmButton == ConfirmButton.B)
                        }
                    }
                }
                LaunchOverlay(viewModel)
                MessageToast(viewModel)
            }
        }
    }
}

/**
 * Navigation from a screen that is no longer the resumed destination is dropped: a second press of
 * "Finish" would stack another Shell, a second popBackStack during the exit animation would pop
 * the start destination, and a ViewModel may call back after a suspension, when the user has left.
 * The lifecycle is the current NavBackStackEntry's.
 */
@Composable
private fun whileResumed(action: () -> Unit): () -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(action)
    return remember(lifecycle) { { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) latest() } }
}

@Composable
private fun <T> whileResumed(action: (T) -> Unit): (T) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(action)
    return remember(lifecycle) { { value: T -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) latest(value) } }
}

@Composable
private fun Shell(navController: NavHostController, appViewModel: AppViewModel, swapped: Boolean, tabBar: TabBarMode) {
    val tab by appViewModel.tab.collectAsStateWithLifecycle()
    val tabs = remember { ShellTab.entries.map { TopTab(it.name, it.label) } }
    val tabState = rememberSaveableStateHolder()
    val sounds = LocalUiSounds.current
    var seenTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab != seenTab) {
            seenTab = tab
            sounds?.play(UiSound.TAB)
        }
    }
    // Back from any other tab lands on Home; from Home it leaves the app as usual.
    BackHandler(enabled = tab != ShellTab.HOME) { appViewModel.selectTab(ShellTab.HOME) }

    GamepadHandler { button ->
        when (button) {
            GamepadButton.L1 -> { appViewModel.selectTab(ShellTab.entries[(tab.ordinal - 1 + ShellTab.entries.size) % ShellTab.entries.size]); true }
            GamepadButton.R1 -> { appViewModel.selectTab(ShellTab.entries[(tab.ordinal + 1) % ShellTab.entries.size]); true }
            GamepadButton.SELECT -> { appViewModel.selectTab(ShellTab.SEARCH); true }
            else -> false
        }
    }

    val openGame = whileResumed { id: GameId -> navController.navigate(GameDetailRoute(id.value)) }
    val openPlatform = whileResumed { id: PlatformId -> navController.navigate(GameGridRoute(platformId = id.value)) }
    val openCollection = whileResumed { id: CollectionId -> navController.navigate(GameGridRoute(collectionId = id.value)) }
    val openAndroid = whileResumed { navController.navigate(AndroidRoute) }

    Column(Modifier.fillMaxSize()) {
        val showTabs = when (tabBar) {
            TabBarMode.THEME -> VelaTheme.spec.layout.showTabs || tab != ShellTab.HOME
            TabBarMode.ALWAYS -> true
            TabBarMode.HIDDEN -> false
        }
        if (showTabs) {
            TopBar(tabs = tabs, selectedId = tab.name, onSelect = { id -> appViewModel.selectTab(ShellTab.valueOf(id)) })
        } else {
            // Themes with a clean Home: just the mark and the status, tabs come back on other screens.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VelaMark()
                Spacer(Modifier.weight(1f))
                if (VelaTheme.dimens.showBattery) BatteryIndicator()
                if (VelaTheme.dimens.showClock) {
                    Spacer(Modifier.width(18.dp))
                    Clock()
                }
            }
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 16 else -it / 16 }) togetherWith fadeOut(tween(150))
                },
                label = "tab",
            ) { current ->
            // A tab leaves composition when another is shown; keep its saveable state (scroll
            // positions, open pickers) so coming back lands where the user left it.
            tabState.SaveableStateProvider(current.name) {
            when (current) {
                ShellTab.HOME -> HomeScreen(
                    navigation = HomeNavigation(
                        openGame = openGame,
                        openPlatform = openPlatform,
                        openCollection = openCollection,
                        openAndroid = openAndroid,
                        openLibrary = { appViewModel.selectTab(ShellTab.LIBRARY) },
                        openCollections = { appViewModel.selectTab(ShellTab.COLLECTIONS) },
                        openSearch = { appViewModel.selectTab(ShellTab.SEARCH) },
                        openSettings = { appViewModel.selectTab(ShellTab.SETTINGS) },
                    ),
                    onSpotlightChanged = { s -> appViewModel.setBackdrop(s?.artwork, s?.accent ?: 0xFF3D7BFF) },
                )
                ShellTab.LIBRARY -> PlatformsScreen(
                    onOpenPlatform = openPlatform,
                    onOpenAndroid = openAndroid,
                    onOpenFavorites = whileResumed { navController.navigate(GameGridRoute(favorites = true, title = "Favorites")) },
                    onOpenAll = whileResumed { navController.navigate(GameGridRoute(title = "All games")) },
                    onOpenSettings = { appViewModel.selectTab(ShellTab.SETTINGS) },
                    onBackgroundArtwork = appViewModel::setBackdrop,
                )
                ShellTab.COLLECTIONS -> CollectionsScreen(onOpenCollection = openCollection)
                ShellTab.SEARCH -> SearchScreen(onOpenGame = openGame, onBackgroundArtwork = appViewModel::setBackdrop)
                ShellTab.SETTINGS -> SettingsScreen(onBackgroundAccent = { appViewModel.setBackdrop(null, it) })
            }
            }
            }
        }
        ButtonHints(
            hints = when (tab) {
                ShellTab.HOME -> listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Menu"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.LIBRARY -> listOf(ButtonHint(GamepadButton.A, "Open"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.COLLECTIONS -> listOf(ButtonHint(GamepadButton.A, "Open"), ButtonHint(GamepadButton.X, "New"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.SEARCH -> listOf(ButtonHint(GamepadButton.A, "Play"), ButtonHint(GamepadButton.X, "Type"), ButtonHint(GamepadButton.Y, "System"), ButtonHint(GamepadButton.L1, "Tabs"))
                ShellTab.SETTINGS -> listOf(ButtonHint(GamepadButton.A, "Change"), ButtonHint(GamepadButton.L2, "Pages"), ButtonHint(GamepadButton.L1, "Tabs"))
            },
            swapped = swapped,
        )
    }
}

/**
 * Full-screen hand-off while the emulator starts: the game's logo or title, which player is
 * launching, and a pulsing accent bar. It fades out once the app has left and come back, or
 * after a safety timeout if the emulator never took the screen.
 */
@Composable
private fun LaunchOverlay(viewModel: AppViewModel) {
    val launching by viewModel.launching.collectAsStateWithLifecycle()
    val sounds = LocalUiSounds.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = VelaTheme.colors

    LaunchedEffect(launching) {
        val current = launching ?: return@LaunchedEffect
        sounds?.play(UiSound.LAUNCH)
        var wentAway = false
        lifecycle.currentStateFlow.collect { state ->
            if (state < Lifecycle.State.RESUMED) {
                wentAway = true
            } else if (wentAway && viewModel.launching.value === current) {
                delay(350)
                viewModel.clearLaunching()
            }
        }
    }
    LaunchedEffect(launching) {
        if (launching != null) {
            delay(15_000)
            viewModel.clearLaunching()
        }
    }

    AnimatedVisibility(visible = launching != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(450))) {
        val game = launching?.game
        val player = launching?.playerName
        // Read in the draw phase only: the pulse must not recompose the overlay 60 times a second.
        val pulse = rememberInfiniteTransition(label = "launchPulse").animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "launchPulseValue",
        )
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (game?.logo != null) {
                    VelaImage(
                        model = artworkModel(game.logo),
                        contentDescription = game.title,
                        modifier = Modifier.height(120.dp).fillMaxWidth(0.5f),
                        contentScale = ContentScale.Fit,
                        placeholder = {},
                    )
                } else {
                    Text(game?.title ?: "", style = VelaTheme.typography.display, color = colors.onBackground, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                }
                Spacer(Modifier.height(18.dp))
                Text(if (player != null) "Launching in $player…" else "Launching…", style = VelaTheme.typography.body, color = colors.muted)
                Spacer(Modifier.height(26.dp))
                val pulseColor = VelaTheme.liveAccent
                Box(Modifier.width(180.dp).height(3.dp).clip(VelaTheme.shapes.chip).drawBehind { drawRect(pulseColor.copy(alpha = pulse.value)) })
            }
        }
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
