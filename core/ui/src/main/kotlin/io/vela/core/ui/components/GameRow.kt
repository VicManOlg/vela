package io.vela.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.model.GameKind
import io.vela.core.model.GameSummary
import io.vela.core.ui.image.FittedArtwork
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.appIconModel
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

private fun GameSummary.imageModel(): Any? =
    if (kind == GameKind.ANDROID_APP && boxArt == null) packageName?.let(::appIconModel) else artworkModel(boxArt)

/** One line of a list view: small box art, title and a secondary line. The whole row is focusable. */
@Composable
fun GameRow(
    game: GameSummary,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onLongPress: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.tile
    val colors = VelaTheme.colors
    val focused by rememberFocusState(interactionSource)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .velaFocusable(shape, interactionSource, onClick, onLongPress, onFocused, scaleOverride = 1f)
            .clip(shape)
            .background(if (focused) colors.surfaceElevated else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(VelaTheme.dimens.boxArtAspect)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface),
        ) {
            VelaImage(
                model = game.imageModel(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                accent = accent,
                placeholder = { Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.35f))) },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(game.title, style = VelaTheme.typography.bodyStrong, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (game.favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colors.onBackground.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
        }
    }
}

/** Right-hand panel of the list view: big art plus a facts line for the focused game. */
@Composable
fun GamePreviewPanel(
    game: GameSummary?,
    accent: Color,
    modifier: Modifier = Modifier,
    platformLabel: String? = null,
) {
    val colors = VelaTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (game != null) {
                FittedArtwork(
                    model = game.imageModel(),
                    contentDescription = game.title,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(VelaTheme.dimens.boxArtAspect)
                        .clip(VelaTheme.shapes.card)
                        .background(colors.surface),
                    accent = accent,
                    placeholder = { TitlePlaceholder(game.title, accent) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            game?.title ?: "",
            style = VelaTheme.typography.headline,
            color = colors.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            game?.let { gameFacts(it, platformLabel) } ?: "",
            style = VelaTheme.typography.caption,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** "SNES   2h 15m   Yesterday   Favorite": whatever is known about the game, in one line. */
fun gameFacts(game: GameSummary, platformLabel: String? = null): String = listOfNotNull(
    platformLabel,
    game.totalPlayTimeMs.takeIf { it > 0 }?.let(::formatPlayTime),
    formatLastPlayed(game.lastPlayedAt),
    if (game.favorite) "Favorite" else null,
).joinToString("   ")
