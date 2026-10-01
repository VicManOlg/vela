package io.vela.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.max

/** Settings > Controller > Vibration: a short tick on confirm. */
val LocalHapticsEnabled = staticCompositionLocalOf { false }

/**
 * The one focus treatment used everywhere: scale up, thin light ring, optional glow underneath.
 * Long-press on the confirm button triggers [onLongPress] (contextual menus without X/Y).
 *
 * Every animated value (scale, ring, glow) is read in the layer and draw phases only, so a focus
 * change animates without recomposing the card it decorates.
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
    val focusedState = interactionSource.collectIsFocusedAsState()
    val focused = focusedState.value
    val motion = VelaTheme.motion
    val colors = VelaTheme.colors
    val shapes = VelaTheme.shapes
    val effects = VelaTheme.effects
    val glow = LocalDynamicAccent.current ?: colors.accent
    val targetScale = if (focused) (scaleOverride ?: motion.focusScale) else 1f
    val scale = animateFloatAsState(targetScale, tween(motion.focusDurationMs), label = "focusScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(motion.focusDurationMs), label = "focusRing")
    val density = LocalDensity.current
    val ringWidthPx = with(density) { shapes.focusBorderWidth.toPx() }
    val edgeWidthPx = with(density) { 1.dp.toPx() }
    val shadowPx = with(density) { 18.dp.toPx() }
    val edgeColor = colors.onBackground
    val ringColor = colors.focusRing
    val latestFocused = rememberUpdatedState(onFocused)
    val sounds = LocalUiSounds.current
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
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
            val s = scale.value
            scaleX = s
            scaleY = s
            // Focused items draw above their neighbours while scaled.
            shadowElevation = if (focusedState.value && effects.cardShadow) shadowPx else 0f
            spotShadowColor = glow
            ambientShadowColor = glow
            this.shape = shape
            clip = false
        }
        .drawWithContent {
            val r = ring.value
            val outline = if (r > 0f || edge) shape.createOutline(size, layoutDirection, this) else null
            if (outline != null && r > 0f && effects.focusGlow) {
                drawOutline(
                    outline,
                    brush = Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.4f * r), Color.Transparent),
                        center = Offset(size.width / 2, size.height),
                        radius = size.maxDimension,
                    ),
                )
            }
            drawContent()
            if (outline != null) {
                // Hairline edge at rest so cards read as objects, replaced by the ring when focused.
                if (edge && r < 1f) drawBorder(outline, edgeColor.copy(alpha = 0.10f * (1f - r)), edgeWidthPx)
                if (r > 0f) {
                    drawBorder(outline, ringColor.copy(alpha = r), ringWidthPx)
                    // Specular sheen along the top edge while focused.
                    drawOutline(
                        outline,
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f * r), Color.Transparent), endY = size.height * 0.45f),
                    )
                }
            }
        }
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
            sounds?.play(UiSound.CONFIRM)
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
        .focusable(enabled, interactionSource)
}

/** Same geometry as `Modifier.border`: the stroke sits inside the outline and the corners follow it. */
private fun DrawScope.drawBorder(outline: Outline, color: Color, width: Float) {
    if (width <= 0f || color.alpha <= 0f) return
    val half = width / 2f
    when (outline) {
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(rr.left + half, rr.top + half),
                size = Size(max(0f, rr.width - width), max(0f, rr.height - width)),
                cornerRadius = CornerRadius(max(0f, rr.topLeftCornerRadius.x - half), max(0f, rr.topLeftCornerRadius.y - half)),
                style = Stroke(width),
            )
        }
        is Outline.Rectangle -> {
            val r = outline.rect
            drawRect(color, Offset(r.left + half, r.top + half), Size(max(0f, r.width - width), max(0f, r.height - width)), style = Stroke(width))
        }
        is Outline.Generic -> drawOutline(outline, color, style = Stroke(width))
    }
}

private const val LONG_PRESS_MS = 450L

@Composable
fun rememberFocusState(interactionSource: MutableInteractionSource): State<Boolean> = interactionSource.collectIsFocusedAsState()

/** Simple scale-only variant for tiles that draw their own ring. */
fun Modifier.focusScale(focused: Boolean, durationMs: Int, scale: Float): Modifier = composed {
    val s = animateFloatAsState(if (focused) scale else 1f, tween(durationMs), label = "scale")
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Spacing that keeps a scaled card from being clipped by its rail. */
@Composable
fun focusBleed(): Dp = (VelaTheme.dimens.cardWidth.value * (VelaTheme.motion.focusScale - 1f) / 2f + 6f).dp


/**
 * Focus requester that fires once the composable is on screen. Attach it to a focus group (grid,
 * row, list) and focus lands on its first child, or to a single focusable. With [memory], focus
 * goes back to the item that had it last, when that item is still there.
 */
@Composable
fun rememberAutoFocus(enabled: Boolean = true, vararg keys: Any?, memory: FocusMemory? = null): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled, *keys) {
        if (!enabled) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (memory?.restore() != true) runCatching { requester.requestFocus() }
    }
    return requester
}

/**
 * Which item of a list had the focus, kept across the list leaving composition (a Shell tab
 * switch keeps the tab's saveable state). `focusRestorer` alone forgets it there. Tag every item
 * with [item] and report focus with [onFocused]; pass it to [rememberAutoFocus].
 */
@Stable
class FocusMemory internal constructor(private val saved: MutableState<String?>) {
    private val requester = FocusRequester()

    fun onFocused(key: String) {
        saved.value = key
    }

    fun item(key: String): Modifier = if (key == saved.value) Modifier.focusRequester(requester) else Modifier

    /** False when nothing was focused yet or that item is gone (not composed). */
    internal fun restore(): Boolean = saved.value != null && runCatching { requester.requestFocus() }.getOrDefault(false)
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(saved) { FocusMemory(saved) }
}
