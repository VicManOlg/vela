package io.vela.feature.library

import io.vela.core.ui.theme.metaLine
import io.vela.core.ui.components.rememberedItemsIndexed
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.AppsRepository
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformArt
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.model.LibraryLayout
import io.vela.core.model.PlatformId
import io.vela.core.data.repository.AppSettingsRepository
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.PlatformTile
import io.vela.core.ui.components.SystemCard
import io.vela.core.ui.components.color
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberFocusMemory
import io.vela.core.ui.components.rememberEntranceClock
import io.vela.core.ui.components.staggeredEntrance
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flow
import io.vela.core.data.system.StorageAccess
import javax.inject.Inject

/** Android shown as one more system: detected games plus pinned apps. */
data class AndroidTile(val games: Int, val apps: Int, val accent: Long, val name: String) {
    val count: Int get() = games + apps
}

@HiltViewModel
class PlatformsViewModel @Inject constructor(
    storage: StorageAccess,
    library: LibraryRepository,
    apps: AppsRepository,
    settings: AppSettingsRepository,
) : ViewModel() {
    /**
     * Optional art the user supplies per system: `Android/data/<app>/files/system-art/<platform id>.png`
     * (jpg/webp too; `all`, `favorites` and `android` name the smart shelves). Book, Columns and the
     * backdrop use it instead of the most recent game's scene.
     */
    val systemArt: StateFlow<Map<String, String>> = flow { emit(storage.systemArt()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Null until the first database read: the empty state must not flash (and take the focus) meanwhile. */
    val platforms: StateFlow<List<PlatformEntry>?> = library.observePlatformsWithGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A few recent covers and one scene per system, for the showcase cards and the backdrop. */
    val art: StateFlow<Map<PlatformId, PlatformArt>> = library.observePlatformArt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val layout: StateFlow<LibraryLayout> = settings.settings.map { it.libraryLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.loaded.libraryLayout)

    private val androidPlatform = library.platform(PlatformId.ANDROID)
    val android: StateFlow<AndroidTile> = combine(apps.observeAndroidGames(), apps.observeApps()) { games, pinned ->
        AndroidTile(games.size, pinned.size, androidPlatform?.accentColor ?: 0xFF3DDC84, androidPlatform?.shortName ?: "Android")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AndroidTile(0, 0, 0xFF3DDC84, "Android"))
}

/** What the focused card tells the header and the backdrop. */
internal data class Spot(val title: String, val subtitle: String, val artwork: String?, val accent: Long)

/** Library tab: every system with games, as poster cards or compact tiles, plus the two smart shelves. */
@Composable
fun PlatformsScreen(
    onOpenPlatform: (PlatformId) -> Unit,
    onOpenAndroid: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackgroundArtwork: (String?, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlatformsViewModel = hiltViewModel(),
) {
    val loadedPlatforms by viewModel.platforms.collectAsStateWithLifecycle()
    val platforms = loadedPlatforms.orEmpty()
    val android by viewModel.android.collectAsStateWithLifecycle()
    val art by viewModel.art.collectAsStateWithLifecycle()
    val systemArt by viewModel.systemArt.collectAsStateWithLifecycle()
    val chosen by viewModel.layout.collectAsStateWithLifecycle()
    val layout = if (chosen == LibraryLayout.THEME) LibraryLayout.fromKey(VelaTheme.spec.layout.libraryLayout) else chosen
    // Stage and Wheel draw their own title on the stage; the other layouts share the header above the list.
    val ownHeader = layout == LibraryLayout.STAGE || layout == LibraryLayout.WHEEL || layout == LibraryLayout.BOOK
    var spot by remember { mutableStateOf<Spot?>(null) }
    val colors = VelaTheme.colors
    val clock = rememberEntranceClock()
    val total = platforms.sumOf { it.gameCount }
    val allCovers = remember(art, platforms) { platforms.mapNotNull { art[it.id]?.covers?.firstOrNull() }.take(3) }

    LaunchedEffect(spot) { onBackgroundArtwork(spot?.artwork, spot?.accent ?: 0xFF3D7BFF) }

    val allSpot = Spot("All games", "Every system   $total games", art.values.firstNotNullOfOrNull { it.background }, 0xFF7FD7FF)
    val favoritesSpot = Spot("Favorites", "Your picks", null, 0xFF3D7BFF)
    val androidSpot = Spot(android.name, "${android.games} games   ${android.apps} apps", null, android.accent)
    fun spotOf(entry: PlatformEntry) = Spot(
        title = entry.displayName,
        subtitle = listOfNotNull(entry.platform.manufacturer, entry.platform.releaseYear?.toString(), if (entry.gameCount == 1) "1 game" else "${entry.gameCount} games").joinToString("   "),
        artwork = art[entry.id]?.background,
        accent = entry.platform.accentColor,
    )

    Column(modifier.fillMaxSize()) {
        if (!ownHeader) Column(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(96.dp), verticalArrangement = Arrangement.Bottom) {
            Text(spot?.title ?: "Library", style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
                metaLine(spot?.subtitle ?: "${platforms.size + 1} systems   ${total + android.games} games"),
                style = VelaTheme.typography.overline,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!ownHeader) Spacer(Modifier.height(8.dp))
        if (loadedPlatforms == null) return@Column
        if (platforms.isEmpty() && android.count == 0) {
            EmptyState(
                title = "No systems yet",
                message = "Add a ROM folder and scan it. Folder names like snes, ps2 or gba are recognised automatically.",
                actionLabel = "Add folders",
                onAction = onOpenSettings,
            )
            return@Column
        }
        // One entry list (All, Favorites, systems, Android) feeds Stage, Wheel, Mosaic and Columns.
        val entries = remember(platforms, android, art, allCovers, systemArt) {
            buildList {
                add(StageEntry("all", "All games", "Every system   $total games", 0xFF7FD7FF, null, Icons.Rounded.Apps, allCovers, systemArt["all"] ?: art.values.firstNotNullOfOrNull { it.background }, onOpenAll))
                add(StageEntry("favorites", "Favorites", "Your picks", 0xFF3D7BFF, null, Icons.Rounded.Favorite, emptyList(), systemArt["favorites"], onOpenFavorites))
                platforms.forEach { entry ->
                    val a = art[entry.id]
                    add(StageEntry(entry.id.value, entry.displayName, spotOf(entry).subtitle, entry.platform.accentColor, entry.iconPath, null, a?.covers.orEmpty(), systemArt[entry.id.value.lowercase()] ?: a?.background) { onOpenPlatform(entry.id) })
                }
                add(StageEntry("android", android.name, "${android.games} games   ${android.apps} apps", android.accent, null, Icons.Rounded.Android, emptyList(), systemArt["android"], onOpenAndroid))
            }
        }
        val firstSystem = if (platforms.isNotEmpty()) 2 else 0
        when (layout) {
            LibraryLayout.STAGE -> SystemStage(entries, initialIndex = firstSystem, onSpot = { spot = it }, modifier = Modifier.weight(1f))
            LibraryLayout.WHEEL -> WheelSystems(entries, initialIndex = firstSystem, clock = clock, onSpot = { spot = it }, modifier = Modifier.weight(1f))
            LibraryLayout.MOSAIC -> MosaicSystems(entries, clock, onSpot = { spot = it })
            LibraryLayout.COLUMNS -> ColumnsSystems(entries, clock, onSpot = { spot = it })
            LibraryLayout.BOOK -> BookSystems(entries, initialIndex = firstSystem, clock = clock, onSpot = { spot = it }, modifier = Modifier.weight(1f))
            LibraryLayout.SHOWCASE -> ShowcaseRow(
                platforms, android, art, allCovers, clock,
                onOpenPlatform, onOpenAndroid, onOpenFavorites, onOpenAll,
                onSpot = { spot = it }, allSpot = allSpot, favoritesSpot = favoritesSpot, androidSpot = androidSpot, spotOf = ::spotOf,
            )
            LibraryLayout.THEME, LibraryLayout.GRID -> TileGrid(
                platforms, android, total, clock,
                onOpenPlatform, onOpenAndroid, onOpenFavorites, onOpenAll,
                onSpot = { spot = it }, allSpot = allSpot, favoritesSpot = favoritesSpot, androidSpot = androidSpot, spotOf = ::spotOf,
            )
        }
    }
}

@Composable
private fun ShowcaseRow(
    platforms: List<PlatformEntry>,
    android: AndroidTile,
    art: Map<PlatformId, PlatformArt>,
    allCovers: List<String>,
    clock: Long,
    onOpenPlatform: (PlatformId) -> Unit,
    onOpenAndroid: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAll: () -> Unit,
    onSpot: (Spot) -> Unit,
    allSpot: Spot,
    favoritesSpot: Spot,
    androidSpot: Spot,
    spotOf: (PlatformEntry) -> Spot,
) {
    val colors = VelaTheme.colors
    val rowState = rememberLazyListState()
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(platforms.isNotEmpty()), memory = memory)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bleed = focusBleed() * 1.4f
        val cardHeight = (maxHeight - bleed * 2 - 24.dp).coerceIn(150.dp, 360.dp)
        val cardWidth = cardHeight * 0.74f
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(autoFocus)
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = bleed, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing * 1.4f),
            verticalAlignment = Alignment.Top,
        ) {
            item(key = "all") {
                SystemCard(
                    name = "All games", subtitle = allSpot.subtitle.substringAfterLast("   "), accent = colors.accent,
                    covers = allCovers, iconVector = Icons.Rounded.Apps, width = cardWidth,
                    onClick = onOpenAll, onFocused = { memory.onFocused("all"); onSpot(allSpot) }, modifier = memory.item("all").staggeredEntrance(0, clock),
                )
            }
            item(key = "favorites") {
                SystemCard(
                    name = "Favorites", subtitle = "Your picks", accent = colors.accentSecondary,
                    iconVector = Icons.Rounded.Favorite, width = cardWidth,
                    onClick = onOpenFavorites, onFocused = { memory.onFocused("favorites"); onSpot(favoritesSpot) }, modifier = memory.item("favorites").staggeredEntrance(1, clock),
                )
            }
            itemsIndexed(platforms, key = { _, it -> it.id.value }) { index, entry ->
                SystemCard(
                    // Short name on the card; the header spells the full name out.
                    name = entry.platform.shortName,
                    subtitle = if (entry.gameCount == 1) "1 game" else "${entry.gameCount} games",
                    accent = entry.platform.color(),
                    icon = entry.iconPath,
                    covers = art[entry.id]?.covers.orEmpty(),
                    width = cardWidth,
                    onClick = { onOpenPlatform(entry.id) },
                    onFocused = { memory.onFocused(entry.id.value); onSpot(spotOf(entry)) },
                    modifier = memory.item(entry.id.value).staggeredEntrance(index + 2, clock),
                )
            }
            item(key = "android") {
                SystemCard(
                    name = android.name, subtitle = "${android.games} games   ${android.apps} apps", accent = Color(android.accent),
                    iconVector = Icons.Rounded.Android, width = cardWidth,
                    onClick = onOpenAndroid, onFocused = { memory.onFocused("android"); onSpot(androidSpot) }, modifier = memory.item("android").staggeredEntrance(platforms.size + 2, clock),
                )
            }
        }
    }
}

@Composable
private fun TileGrid(
    platforms: List<PlatformEntry>,
    android: AndroidTile,
    total: Int,
    clock: Long,
    onOpenPlatform: (PlatformId) -> Unit,
    onOpenAndroid: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAll: () -> Unit,
    onSpot: (Spot) -> Unit,
    allSpot: Spot,
    favoritesSpot: Spot,
    androidSpot: Spot,
    spotOf: (PlatformEntry) -> Spot,
) {
    val colors = VelaTheme.colors
    val memory = rememberFocusMemory()
    val autoFocus = rememberAutoFocus(keys = arrayOf(platforms.isNotEmpty()), memory = memory)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VelaTheme.dimens.cardWidth * 1.35f),
        modifier = Modifier.fillMaxSize().focusRequester(autoFocus).focusRestorer().focusGroup(),
        contentPadding = PaddingValues(start = VelaTheme.dimens.screenPadding, end = VelaTheme.dimens.screenPadding, top = focusBleed(), bottom = 90.dp),
        horizontalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
        verticalArrangement = Arrangement.spacedBy(VelaTheme.dimens.railSpacing),
    ) {
        item(key = "all") {
            PlatformTile(
                name = "Every system", shortName = "All games", count = total, accent = colors.accent,
                onClick = onOpenAll, onFocused = { onSpot(allSpot) }, width = null,
                modifier = Modifier.fillMaxWidth().staggeredEntrance(0, clock),
            )
        }
        item(key = "favorites") {
            PlatformTile(
                name = "Your picks", shortName = "Favorites", count = -1, accent = colors.accentSecondary,
                onClick = onOpenFavorites, onFocused = { onSpot(favoritesSpot) }, width = null,
                modifier = Modifier.fillMaxWidth().staggeredEntrance(1, clock),
            )
        }
        rememberedItemsIndexed(memory, platforms, key = { _, it -> it.id.value }) { index, entry ->
            PlatformTile(
                name = entry.displayName,
                shortName = entry.platform.shortName,
                count = entry.gameCount,
                accent = entry.platform.color(),
                icon = entry.iconPath,
                onClick = { onOpenPlatform(entry.id) },
                onFocused = { onSpot(spotOf(entry)) },
                width = null,
                modifier = Modifier.fillMaxWidth().staggeredEntrance(index + 2, clock),
            )
        }
        item(key = "android") {
            PlatformTile(
                name = "Games and apps",
                shortName = android.name,
                count = android.count,
                accent = Color(android.accent),
                onClick = onOpenAndroid,
                iconVector = Icons.Rounded.Android,
                onFocused = { onSpot(androidSpot) },
                width = null,
                modifier = Modifier.fillMaxWidth().staggeredEntrance(platforms.size + 2, clock),
            )
        }
    }
}
