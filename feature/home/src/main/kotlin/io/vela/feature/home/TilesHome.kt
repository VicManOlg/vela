package io.vela.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.GameCard
import io.vela.core.ui.components.Rail
import io.vela.core.ui.components.color
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.components.velaFocusable
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/**
 * Hybrid-handheld style Home: one centred row of large square tiles with the focused title
 * announced above it, and a bottom row of round buttons for systems, Library, Collections,
 * Search and Settings. Meant for themes that hide the tab bar.
 */
@Composable
fun TilesHome(
    state: HomeUiState,
    spotlight: Spotlight?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val games = remember(state) { state.gamesInOrder(30) }
    // The first tile is focused before any focus callback fires; announce it right away.
    LaunchedEffect(games.firstOrNull()?.id) { if (spotlight == null) games.firstOrNull()?.let(viewModel::spotlightGame) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        // Title tag above the row, like a label on the shelf.
        Box(Modifier.fillMaxWidth().padding(horizontal = VelaTheme.dimens.screenPadding).height(40.dp), contentAlignment = Alignment.BottomStart) {
            val title = spotlight?.title
            if (title != null) {
                Box(
                    Modifier
                        .clip(VelaTheme.shapes.chip)
                        .background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(title, style = VelaTheme.typography.bodyStrong, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Rail("", autoFocus = true) {
            items(games, key = { it.id.value }) { game ->
                val accent = state.platformOf(game)?.platform?.color() ?: colors.accent
                GameCard(
                    game = game,
                    accent = accent,
                    width = VelaTheme.dimens.cardWidth * 1.2f,
                    onClick = { viewModel.launch(game) },
                    onLongPress = { viewModel.openMenu(game) },
                    onFocused = { viewModel.spotlightGame(game) },
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        // Round buttons: systems first, then the rest of the app.
        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showsPlatforms) {
                items(state.platforms, key = { it.id.value }) { entry ->
                    RoundButton(label = entry.platform.shortName, accent = entry.platform.color(), icon = entry.iconPath, onClick = { navigation.openPlatform(entry.id) }, onFocused = { viewModel.spotlightPlatform(entry) })
                }
            }
            item("library") { RoundButton("Library", colors.accent, vector = Icons.Rounded.Apps, onClick = navigation.openLibrary) }
            item("collections") { RoundButton("Collections", colors.accentSecondary, vector = Icons.Rounded.CollectionsBookmark, onClick = navigation.openCollections) }
            item("search") { RoundButton("Search", colors.accent, vector = Icons.Rounded.Search, onClick = navigation.openSearch) }
            item("settings") { RoundButton("Settings", colors.muted, vector = Icons.Rounded.Settings, onClick = navigation.openSettings) }
        }
    }
}

@Composable
private fun RoundButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    icon: String? = null,
    vector: ImageVector? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = VelaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .velaFocusable(CircleShape, interaction, onClick, onFocused = onFocused, scaleOverride = 1.1f)
                .clip(CircleShape)
                .background(if (focused) accent.copy(alpha = 0.25f) else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                VelaImage(
                    model = artworkModel(icon),
                    contentDescription = label,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (VelaTheme.platformIcons.tint) ColorFilter.tint(colors.onSurface) else null,
                    placeholder = {},
                )
            } else if (vector != null) {
                Icon(vector, contentDescription = label, tint = if (focused) colors.onBackground else colors.muted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = VelaTheme.typography.caption, color = if (focused) colors.onBackground else colors.muted, maxLines = 1)
    }
}
