package io.vela.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme

/**
 * Poster-style system card: the console sits on a glow of the system colour, a fan of the
 * user's own covers for that system spreads underneath, and the name closes the card.
 * The glow brightens and the console grows a little when focused.
 */
@Composable
fun SystemCard(
    name: String,
    subtitle: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = VelaTheme.dimens.cardWidth * 1.4f,
    icon: String? = null,
    iconVector: ImageVector? = null,
    covers: List<String> = emptyList(),
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = VelaTheme.shapes.tile
    val colors = VelaTheme.colors
    val iconStyle = VelaTheme.platformIcons
    val focused by rememberFocusState(interactionSource)
    val glow by animateFloatAsState(if (focused) 1f else 0.5f, tween(450), label = "systemGlow")

    Box(
        modifier
            .width(width)
            .aspectRatio(ASPECT)
            .velaFocusable(shape, interactionSource, onClick, onFocused = onFocused, scaleOverride = 1.06f, edge = true)
            .clip(shape)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.55f), colors.surfaceElevated.copy(alpha = 0.96f), colors.surface),
                    ),
                )
                val center = Offset(size.width / 2f, size.height * 0.3f)
                val radius = size.width * 0.58f
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.75f * glow), Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.46f).padding(top = 6.dp), contentAlignment = Alignment.Center) {
                val grow = 1f + 0.08f * ((glow - 0.5f) / 0.5f)
                if (icon != null) {
                    VelaImage(
                        model = artworkModel(icon),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = grow; scaleY = grow },
                        contentScale = ContentScale.Fit,
                        colorFilter = if (iconStyle.tint) ColorFilter.tint(colors.onBackground) else null,
                        placeholder = {},
                    )
                } else if (iconVector != null) {
                    Icon(
                        iconVector,
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = grow; scaleY = grow },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(0.34f), contentAlignment = Alignment.BottomCenter) {
                if (covers.isNotEmpty()) CoverFan(covers.take(3))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(name, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Up to three covers: sides tilted outwards and drawn first, the middle one upright on top. */
@Composable
private fun CoverFan(covers: List<String>) {
    val colors = VelaTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val coverWidth = maxWidth * 0.33f
        // (horizontal shift as a fraction of the card, tilt in degrees), drawn in this order.
        val slots: List<Pair<Float, Float>> = when (covers.size) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-0.17f to -7f, 0.17f to 7f)
            else -> listOf(-0.3f to -11f, 0.3f to 11f, 0f to 0f)
        }
        val order = if (covers.size == 3) listOf(0, 2, 1) else covers.indices.toList()
        order.forEachIndexed { drawIndex, coverIndex ->
            val (shift, tilt) = slots[drawIndex]
            Box(
                Modifier
                    .width(coverWidth)
                    .aspectRatio(VelaTheme.dimens.boxArtAspect)
                    .offset(x = maxWidth * shift, y = if (tilt == 0f) 0.dp else 8.dp)
                    .graphicsLayer {
                        rotationZ = tilt
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        shadowElevation = 6.dp.toPx()
                        shape = RoundedCornerShape(6.dp)
                        clip = true
                    }
                    .background(colors.surface),
            ) {
                VelaImage(
                    model = artworkModel(covers[coverIndex]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = {},
                )
            }
        }
    }
}

private const val ASPECT = 0.74f
