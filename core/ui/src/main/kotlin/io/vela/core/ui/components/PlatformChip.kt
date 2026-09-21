package io.vela.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/** Slim system pill for dense rows: console icon, short name and count. Lights up in the accent when focused. */
@Composable
fun PlatformChip(
    shortName: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    iconVector: ImageVector? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.chip
    val colors = VelaTheme.colors
    val iconStyle = VelaTheme.platformIcons
    val focused by rememberFocusState(interactionSource)
    Row(
        modifier
            .height(44.dp)
            .velaFocusable(shape, interactionSource, onClick, onFocused = onFocused, scaleOverride = 1.06f)
            .clip(shape)
            .background(if (focused) accent.copy(alpha = 0.85f) else colors.surfaceElevated.copy(alpha = 0.7f))
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VelaImage(
                model = artworkModel(icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit,
                colorFilter = if (iconStyle.tint) ColorFilter.tint(colors.onBackground) else null,
                placeholder = {},
            )
            Spacer(Modifier.width(10.dp))
        } else if (iconVector != null) {
            Icon(iconVector, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(shortName, style = VelaTheme.typography.bodyStrong, color = colors.onBackground, maxLines = 1)
        if (count >= 0) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = VelaTheme.typography.caption, color = colors.onBackground.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}
