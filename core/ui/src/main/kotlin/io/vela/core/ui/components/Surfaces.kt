package io.vela.core.ui.components

import coil3.SingletonImageLoader
import io.vela.core.ui.theme.LocalStageTint
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

/** The Vela sail: a tall right triangle with a soft foot. */
object SailShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path().apply {
            moveTo(size.width * 0.55f, 0f)
            lineTo(size.width * 0.55f, size.height * 0.82f)
            lineTo(0f, size.height * 0.82f)
            close()
            moveTo(size.width * 0.66f, size.height * 0.15f)
            lineTo(size.width, size.height * 0.82f)
            lineTo(size.width * 0.66f, size.height * 0.82f)
            close()
            moveTo(size.width * 0.05f, size.height * 0.9f)
            lineTo(size.width * 0.95f, size.height * 0.9f)
            lineTo(size.width * 0.85f, size.height)
            lineTo(size.width * 0.15f, size.height)
            close()
        }
        return Outline.Generic(p)
    }
}

/**
 * Full-screen background driven by the focused item's artwork. Mode `artwork` blurs it heavily;
 * `hero` shows the scene almost sharp with a slow Ken Burns drift; `stage` lays the sharp scene on
 * the right, fading into its own colours spread across the screen. All dim, saturate and
 * cross-fade when the art changes. Falls back to a platform-tinted gradient. With `artworkTint`
 * the focused game's colour washes up from the bottom-left corner.
 */
@Composable
fun DynamicBackground(
    artwork: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val style = VelaTheme.background
    val colors = VelaTheme.colors
    val motion = VelaTheme.motion
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(style.staticColor ?: colors.background)) {
        if (style.mode != "static") {
            Crossfade(targetState = artwork to accent, animationSpec = tween(motion.backgroundCrossfadeMs), label = "background") { (art, tint) ->
                Box(Modifier.fillMaxSize()) {
                    if (art != null && style.mode == "stage") {
                        StageScene(art, drifting = !motion.reduceMotion, saturation = style.saturation)
                    } else if (art != null && (style.mode == "artwork" || style.mode == "hero" || style.mode == "system")) {
                        // `system` shows one image per system, sharp like the hero scene.
                        val hero = style.mode == "hero" || style.mode == "system"
                        // Read inside graphicsLayer only: the 26 s drift must never recompose the scene.
                        val drift = rememberDrift(enabled = hero && !motion.reduceMotion)
                        val request = remember(art, hero, context) {
                            if (hero) {
                                backdropRequests(context, art)[1]
                            } else {
                                ImageRequest.Builder(context).data(artworkModel(art)).size(480).precision(Precision.INEXACT).crossfade(false).build()
                            }
                        }
                        val saturation = remember(style.saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(style.saturation) }) }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = saturation,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (hero) {
                                        // Ken Burns: slow zoom plus sideways drift so the scene never sits still.
                                        val d = drift.value
                                        val zoom = 1.06f + 0.06f * d
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = (d - 0.5f) * size.width * 0.04f
                                    }
                                }
                                .then(if (style.blurRadius > 0.dp) Modifier.blur(style.blurRadius) else Modifier),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(tint.copy(alpha = 0.55f), colors.background),
                                        center = androidx.compose.ui.geometry.Offset(0.25f, 0.1f).let { androidx.compose.ui.geometry.Offset(it.x * 2000f, it.y * 1200f) },
                                        radius = 1600f,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
        val tint = if (VelaTheme.effects.artworkTint) LocalStageTint.current else null
        if (tint != null && style.mode != "static") {
            // The game's colour rising from the corner the titles sit on; drawn from the animated
            // State, so a new colour repaints this box and nothing else.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(tint.value.copy(alpha = 0.42f), tint.value.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.08f, size.height * 1.02f),
                            radius = size.maxDimension * 0.62f,
                        ),
                    )
                },
            )
        }
        if (style.mode == "hero" || style.mode == "artwork" || style.mode == "system") {
            // Depth: the scene stays brightest around where the focused art sits and falls off into a vignette.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Transparent, colors.background.copy(alpha = 0.6f)),
                            center = Offset(size.width * 0.58f, size.height * 0.42f),
                            radius = size.maxDimension * 0.72f,
                        ),
                    )
                },
            )
        }
        // Scrim: darker at the top-left where text lives, transparent to the right where art shows.
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = style.dim)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(colors.scrim, Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.9f)), startY = 900f)),
        )
    }
}

/**
 * The `stage` scene in two layers. Far: the same art decoded at a few dozen pixels and stretched to
 * the whole screen, which blurs it for free on every Android version (no RenderEffect, nothing
 * to recompute per frame). Key: the art sharp on the right two thirds, faded into the far layer
 * on its left and bottom edges, with the slow Ken Burns drift.
 */
@Composable
private fun StageScene(art: String, drifting: Boolean, saturation: Float) {
    val context = LocalContext.current
    val drift = rememberDrift(enabled = drifting)
    val filter = remember(saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    val (far, key) = remember(art, context) { backdropRequests(context, art) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = far,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.7f },
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterEnd)
                // Offscreen so the fade masks the art only, not the far layer under it.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.42f to Color.Black), blendMode = BlendMode.DstIn)
                    // Clear of the status bar at the top, melting into the rail at the bottom.
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
        ) {
            AsyncImage(
                model = key,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val d = drift.value
                        val zoom = 1.04f + 0.06f * d
                        scaleX = zoom
                        scaleY = zoom
                        translationX = (d - 0.5f) * size.width * 0.03f
                    },
            )
        }
    }
}

/**
 * The decodes behind the stage and hero backgrounds: the art at 24 px (the far, blurred layer)
 * and at 1280 px (the scene). [rememberBackdropPrefetch] issues the very same requests, so a
 * prefetch lands in the memory cache under the keys the background will ask for.
 */
private fun backdropRequests(context: android.content.Context, art: String): List<ImageRequest> = listOf(
    ImageRequest.Builder(context).data(artworkModel(art)).size(24).precision(Precision.INEXACT).crossfade(false).build(),
    ImageRequest.Builder(context).data(artworkModel(art)).size(1280).precision(Precision.INEXACT).crossfade(false).build(),
)

/**
 * Decodes a game's background before it is focused: call it with the neighbours of the focused
 * item, and the scene is in memory when the D-pad gets there instead of loading behind it.
 */
@Composable
fun rememberBackdropPrefetch(): (String?) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { art: String? ->
            if (art != null) {
                val loader = SingletonImageLoader.get(context)
                backdropRequests(context, art).forEach { loader.enqueue(it) }
            }
        }
    }
}

/** Translucent panel used for detail blocks and dialogs. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = VelaTheme.colors
    val effects = VelaTheme.effects
    val shape = VelaTheme.shapes.panel
    Box(
        modifier
            .clip(shape)
            .background(colors.surface.copy(alpha = if (effects.glassPanels) effects.panelAlpha else 1f))
            .padding(padding),
        content = content,
    )
}

/** Primary/secondary action button, gamepad-focusable. */
@Composable
fun VelaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = VelaTheme.colors
    val shape = VelaTheme.shapes.button
    val focused by rememberFocusState(interactionSource)
    val bg = when {
        primary -> colors.onBackground
        focused -> colors.surfaceElevated
        else -> colors.onBackground.copy(alpha = 0.10f)
    }
    val fg = if (primary) colors.background else colors.onBackground
    Row(
        modifier
            .velaFocusable(shape, interactionSource, onClick, onFocused = onFocused, scaleOverride = 1.05f, enabled = enabled)
            .clip(shape)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.4f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = VelaTheme.typography.bodyStrong, color = fg.copy(alpha = if (enabled) 1f else 0.5f), maxLines = 1)
    }
}

/** Small metadata pill: "1995", "RPG", "2 players". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tint: Color = VelaTheme.colors.onBackground) {
    Box(
        modifier
            .clip(VelaTheme.shapes.tag)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = VelaTheme.typography.caption, color = tint.copy(alpha = 0.9f), maxLines = 1)
    }
}

/** Empty state that says what to do next, with an optional action. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
) {
    Column(modifier.padding(VelaTheme.dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = VelaTheme.typography.title, color = VelaTheme.colors.onBackground)
        Text(message, style = VelaTheme.typography.body, color = VelaTheme.colors.muted, modifier = Modifier.fillMaxWidth(0.6f))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            VelaButton(actionLabel, onAction, primary = true, modifier = actionModifier)
        }
    }
}

/**
 * Settings/menu row: label + optional description on the left, value or switch on the right.
 * Clicking toggles or opens; the row is one focus target.
 */
@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    checked: Boolean? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onFocused: (() -> Unit)? = null,
    /** Draw [value] in the muted colour: the row is at its default ("Theme"), not a choice worth highlighting. */
    subdued: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /** Drawn after the value (a colour chip, a small icon). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VelaTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val focused by rememberFocusState(interactionSource)
    Row(
        modifier
            .fillMaxWidth()
            .velaFocusable(shape, interactionSource, onClick, onFocused = onFocused, scaleOverride = 1.01f, enabled = enabled)
            .clip(shape)
            .background(if (focused) colors.surfaceElevated.copy(alpha = 0.9f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (danger) colors.danger else colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = VelaTheme.typography.body,
                color = when {
                    !enabled -> colors.muted
                    danger -> colors.danger
                    else -> colors.onBackground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(description, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(16.dp))
            Text(value, style = VelaTheme.typography.bodyStrong, color = if (subdued) colors.muted else colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.35f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (checked != null) {
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.surfaceElevated,
                    uncheckedBorderColor = colors.muted.copy(alpha = 0.4f),
                ),
            )
        }
    }
}

/** Section heading inside settings pages and detail panels. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = VelaTheme.typography.label,
        color = VelaTheme.colors.muted,
        modifier = modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}
