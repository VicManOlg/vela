package io.vela.core.ui.components

import io.vela.core.ui.theme.LocalStageTint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.AnimationSpec
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
 * Artwork ([edge]) also lifts, casts a shadow in the game's colour (themes with `artworkTint`) and
 * catches one sweep of light when the focus lands on it.
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
    val themeGlow = colors.focusGlow ?: LocalDynamicAccent.current ?: colors.accent
    // The game's colour, when the theme tints: read in the layer and draw phases only.
    val tint = if (effects.artworkTint) LocalStageTint.current else null
    val targetScale = if (focused) (scaleOverride ?: motion.focusScale) else 1f
    // The expressive fast spatial spring: the item settles with a small overshoot, and a quick
    // D-pad run retargets it smoothly instead of restarting a fixed-length tween.
    val scaleSpec: AnimationSpec<Float> = if (motion.focusDurationMs == 0) snap() else VelaSprings.spatialFast()
    val scale = animateFloatAsState(targetScale, scaleSpec, label = "focusScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(motion.focusDurationMs), label = "focusRing")
    // One sweep of light across artwork as the focus lands; never repeats while it stays.
    val sweep = remember { Animatable(1f) }
    val sweeps = edge && !motion.reduceMotion && effects.focusGlow
    LaunchedEffect(focused, sweeps) {
        if (focused && sweeps) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SWEEP_MS, delayMillis = 70, easing = FastOutSlowInEasing))
        } else {
            sweep.snapTo(1f)
        }
    }
    val lifts = edge && !motion.reduceMotion
    val density = LocalDensity.current
    val ringWidthPx = with(density) { shapes.focusBorderWidth.toPx() }
    val ringGapPx = with(density) { shapes.focusRingGap.toPx() }
    val edgeWidthPx = with(density) { 1.dp.toPx() }
    val shadowPx = with(density) { 18.dp.toPx() }
    val igniteOutsetPx = with(density) { 4.dp.toPx() }
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
            // Artwork rises as it grows, as if picked up from the shelf.
            if (lifts) translationY = -(s - 1f) * size.height * 0.32f
            // Focused items draw above their neighbours while scaled.
            val lit = focusedState.value && effects.cardShadow
            shadowElevation = if (lit) shadowPx * (if (edge) 1.6f else 1f) else 0f
            // Only the focused item follows the animated tint: reading it everywhere would repaint
            // every card on screen for each frame of a colour change.
            val shadow = if (lit) tint?.value ?: themeGlow else themeGlow
            spotShadowColor = shadow
            ambientShadowColor = shadow
            this.shape = shape
            clip = false
        }
        .drawWithContent {
            val r = ring.value
            val outline = if (r > 0f || edge) shape.createOutline(size, layoutDirection, this) else null
            if (outline != null && r > 0f && effects.focusGlow) {
                val glow = tint?.value ?: themeGlow
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
                    // On artwork (cards, tiles) a gap lets the ring float outside, so it never covers
                    // the cover; rows and buttons keep it on their edge, inside the list's bounds.
                    // It ignites from slightly wider and closes onto the art.
                    val ignite = if (edge) (1f - r) * igniteOutsetPx else 0f
                    drawBorder(outline, ringColor.copy(alpha = r), ringWidthPx, outset = (if (edge && ringGapPx > 0f) ringGapPx + ringWidthPx else 0f) + ignite)
                    // Specular sheen along the top edge while focused.
                    drawOutline(
                        outline,
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f * r), Color.Transparent), endY = size.height * 0.45f),
                    )
                }
                val w = sweep.value
                if (w < 1f && r > 0f) {
                    // A diagonal band of light crossing the art from left to right.
                    val band = size.width * 0.45f
                    val x = -band + (size.width + 2 * band) * w
                    val fade = if (w > 0.75f) (1f - w) / 0.25f else 1f
                    drawOutline(
                        outline,
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.32f * fade * r),
                            1f to Color.Transparent,
                            start = Offset(x - band / 2, 0f),
                            end = Offset(x + band / 2, size.height * 0.35f),
                        ),
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

/**
 * Same geometry as `Modifier.border`: the stroke sits inside the outline and the corners follow it.
 * [outset] grows the outline first (corners stay concentric), for a ring that floats outside.
 */
private fun DrawScope.drawBorder(outline: Outline, color: Color, width: Float, outset: Float = 0f) {
    if (width <= 0f || color.alpha <= 0f) return
    val half = width / 2f
    when (outline) {
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(rr.left - outset + half, rr.top - outset + half),
                size = Size(max(0f, rr.width + 2 * outset - width), max(0f, rr.height + 2 * outset - width)),
                cornerRadius = CornerRadius(max(0f, rr.topLeftCornerRadius.x + outset - half), max(0f, rr.topLeftCornerRadius.y + outset - half)),
                style = Stroke(width),
            )
        }
        is Outline.Rectangle -> {
            val r = outline.rect
            drawRect(color, Offset(r.left - outset + half, r.top - outset + half), Size(max(0f, r.width + 2 * outset - width), max(0f, r.height + 2 * outset - width)), style = Stroke(width))
        }
        is Outline.Generic -> drawOutline(outline, color, style = Stroke(width))
    }
}

private const val LONG_PRESS_MS = 450L
private const val SWEEP_MS = 560

@Composable
fun rememberFocusState(interactionSource: MutableInteractionSource): State<Boolean> = interactionSource.collectIsFocusedAsState()

/** Simple scale-only variant for tiles that draw their own ring. */
fun Modifier.focusScale(focused: Boolean, durationMs: Int, scale: Float): Modifier = composed {
    val s = animateFloatAsState(if (focused) scale else 1f, tween(durationMs), label = "scale")
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Spacing that keeps a scaled card from being clipped by its rail. */
@Composable
fun focusBleed(): Dp {
    val ring = if (VelaTheme.shapes.focusRingGap.value > 0f) VelaTheme.shapes.focusRingGap.value + VelaTheme.shapes.focusBorderWidth.value else 0f
    // Scale grows the card by half the extra on each side; the lift raises it by a third of its
    // extra height (see velaFocusable).
    val grow = VelaTheme.dimens.cardWidth.value * (VelaTheme.motion.focusScale - 1f)
    val lift = grow * 0.32f / VelaTheme.dimens.boxArtAspect.coerceAtLeast(0.3f)
    return (grow / 2f + lift + 6f + ring).dp
}


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

/** Tags a list item for [memory]: remembers it when it gets the focus and gives the focus back to it. */
fun Modifier.rememberedFocus(memory: FocusMemory, key: Any): Modifier {
    val k = key.toString()
    return then(memory.item(k)).onFocusChanged { if (it.hasFocus) memory.onFocused(k) }
}

@Composable
fun rememberFocusMemory(): FocusMemory {
    val saved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(saved) { FocusMemory(saved) }
}
