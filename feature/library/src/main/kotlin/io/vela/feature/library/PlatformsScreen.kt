package io.vela.feature.library

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vela.core.data.repository.LibraryRepository
import io.vela.core.data.repository.PlatformEntry
import io.vela.core.model.PlatformId
import io.vela.core.ui.components.EmptyState
import io.vela.core.ui.components.PlatformTile
import io.vela.core.ui.components.color
import io.vela.core.ui.components.focusBleed
import io.vela.core.ui.components.rememberAutoFocus
import androidx.compose.ui.focus.focusRequester
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlatformsViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val platforms: StateFlow<List<PlatformEntry>> = library.observePlatformsWithGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Library tab: every system with games, as tiles. Also offers the two smart shelves. */
@Composable
fun PlatformsScreen(
    onOpenPlatform: (PlatformId) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackgroundAccent: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlatformsViewModel = hiltViewModel(),
) {
    val platforms by viewModel.platforms.collectAsStateWithLifecycle()
    var focusedName by remember { mutableStateOf<String?>(null) }
    val colors = VelaTheme.colors
    val total = platforms.sumOf { it.gameCount }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(96.dp), verticalArrangement = Arrangement.Bottom) {
            Text("Library", style = VelaTheme.typography.display, color = colors.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                focusedName ?: "${platforms.size} systems   $total games",
                style = VelaTheme.typography.body,
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (platforms.isEmpty()) {
            EmptyState(
                title = "No systems yet",
                message = "Add a ROM folder and scan it. Folder names like snes, ps2 or gba are recognised automatically.",
                actionLabel = "Add folders",
                onAction = onOpenSettings,
            )
            return@Column
        }
        val autoFocus = rememberAutoFocus(keys = arrayOf(platforms.isNotEmpty()))
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
                    onClick = onOpenAll, onFocused = { focusedName = "All games"; onBackgroundAccent(0xFF7FD7FF) }, width = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "favorites") {
                PlatformTile(
                    name = "Your picks", shortName = "Favorites", count = -1, accent = colors.accentSecondary,
                    onClick = onOpenFavorites, onFocused = { focusedName = "Favorites"; onBackgroundAccent(0xFF3D7BFF) }, width = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(platforms, key = { it.id.value }) { entry ->
                PlatformTile(
                    name = entry.displayName,
                    shortName = entry.platform.shortName,
                    count = entry.gameCount,
                    accent = entry.platform.color(),
                    icon = entry.iconPath,
                    onClick = { onOpenPlatform(entry.id) },
                    onFocused = { focusedName = "${entry.platform.name}   ${entry.gameCount} games"; onBackgroundAccent(entry.platform.accentColor) },
                    width = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    LaunchedEffect(Unit) { onBackgroundAccent(0xFF3D7BFF) }
}
