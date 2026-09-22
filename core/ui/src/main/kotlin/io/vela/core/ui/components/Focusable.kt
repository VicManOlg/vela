package io.vela.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound
import androidx.compose.ui.draw.drawWithContent
import io.vela.core.ui.theme.LocalDynamicAccent

/**
 * The one focus treatment used everywhere: scale up, thin light ring, optional glow underneath.
 * Long-press on the confirm button triggers [onLongPress] (contextual menus without X/Y).
 */
fun Modifier.velaFocusable(
    shape: Shape,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    scaleOverride: Float? = null,
    enabled: Boolean = true,
    /** Hairline edge at rest; for cards and tiles, not for chips and buttons. */
    edge: Boolean = false,
): Modifier = composed {
    val focused by interactionSource.collectIsFocusedAsState()
    val motion = VelaTheme.motion
    val colors = VelaTheme.colors
    val shapes = VelaTheme.shapes
    val effects = VelaTheme.effects
    val glow = LocalDynamicAccent.current ?: colors.accent
    val targetScale = if (focused) (scaleOverride ?: motion.focusScale) else 1f
    val scale by animateFloatAsState(targetScale, tween(motion.focusDurationMs), label = "focusScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(motion.focusDurationMs), label = "focusRing")
    val ringWidth = shapes.focusBorderWidth
    val density = LocalDensity.current
    val latestFocused = rememberUpdatedState(onFocused)
    val sounds = LocalUiSounds.current
    val longPress = rememberUpdatedState(onLongPress)
    val downTime = remember { longArrayOf(0L) }

    this
        .onFocusChanged {
            if (it.isFocused) {
                sounds?.play(UiSound.FOCUS)
                latestFocused.value?.invoke()
            }
        }
        .then(
            if (onLongPress != null) {
                Modifier.onPreviewKeyEvent { event ->
                    val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.ButtonA
                    if (!confirm) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (downTime[0] == 0L) downTime[0] = System.currentTimeMillis()
                            val held = System.currentTimeMillis() - downTime[0]
                            if (held > LONG_PRESS_MS) {
                                downTime[0] = -1L
                                longPress.value?.invoke()
                                return@onPreviewKeyEvent true
                            }
                            // Swallow auto-repeat while holding so it does not click repeatedly.
                            event.nativeKeyEvent.repeatCount > 0
                        }
                        KeyEventType.KeyUp -> {
                            val consumedByLongPress = downTime[0] == -1L
                            downTime[0] = 0L
                            consumedByLongPress
                        }
                        else -> false
                    }
                }
            } else {
                Modifier
            },
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            // Focused items draw above their neighbours while scaled.
            shadowElevation = if (focused && effects.cardShadow) with(density) { 18.dp.toPx() } else 0f
            spotShadowColor = glow
            ambientShadowColor = glow
            this.shape = shape
            clip = false
        }
        .drawBehind {
            if (ring <= 0f) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            if (effects.focusGlow) {
                drawOutline(
                    outline,
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.4f * ring), Color.Transparent),
                        center = Offset(size.width / 2, size.height),
                        radius = size.maxDimension,
                    ),
                )
            }
        }
        .drawWithContent {
            drawContent()
            if (ring > 0f) {
                // Specular sheen along the top edge while focused.
                drawOutline(
                    shape.createOutline(size, layoutDirection, this),
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f * ring), Color.Transparent), endY = size.height * 0.45f),
                )
            }
        }
        // Hairline edge at rest so cards read as objects, replaced by the ring when focused.
        .then(if (edge) Modifier.border(1.dp, colors.onBackground.copy(alpha = 0.10f * (1f - ring)), shape) else Modifier)
        .border(ringWidth, colors.focusRing.copy(alpha = ring), shape)
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
            sounds?.play(UiSound.CONFIRM)
            onClick()
        }
        .focusable(enabled, interactionSource)
}

private const val LONG_PRESS_MS = 450L

@Composable
fun rememberFocusState(interactionSource: MutableInteractionSource): State<Boolean> = interactionSource.collectIsFocusedAsState()

/** Simple scale-only variant for tiles that draw their own ring. */
fun Modifier.focusScale(focused: Boolean, durationMs: Int, scale: Float): Modifier = composed {
    val s by animateFloatAsState(if (focused) scale else 1f, tween(durationMs), label = "scale")
    this.scale(s)
}

/** Spacing that keeps a scaled card from being clipped by its rail. */
@Composable
fun focusBleed(): Dp = (VelaTheme.dimens.cardWidth.value * (VelaTheme.motion.focusScale - 1f) / 2f + 6f).dp


/**
 * Focus requester that fires once the composable is on screen. Attach it to a focus group (grid,
 * row, list) and focus lands on its first child, or to a single focusable.
 */
@Composable
fun rememberAutoFocus(enabled: Boolean = true, vararg keys: Any?): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled, *keys) {
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}
