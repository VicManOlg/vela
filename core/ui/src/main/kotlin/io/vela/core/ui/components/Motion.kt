package io.vela.core.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.delay

/** Time a screen appeared; items composed shortly after it stagger in, later ones just appear. */
@Composable
fun rememberEntranceClock(): Long = remember { System.currentTimeMillis() }

/**
 * Fade + rise on first composition, delayed by [index] so a grid or list cascades in. Only items
 * that show up within the screen's first moments animate; scrolling never triggers it. Honours
 * reduce-motion.
 */
fun Modifier.staggeredEntrance(index: Int, clock: Long, enabled: Boolean = true): Modifier = composed {
    val motion = VelaTheme.motion
    val late = System.currentTimeMillis() - clock > ENTRANCE_WINDOW_MS
    if (!enabled || motion.reduceMotion || late) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(MAX_STAGGERED) * STAGGER_MS).toLong())
        progress.animateTo(1f, tween(motion.transitionDurationMs + 120, easing = FastOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { 26.dp.toPx() }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Slow 0..1..0 drift used for Ken Burns backgrounds; frozen at 0.5 when [enabled] is false. */
@Composable
fun rememberDrift(enabled: Boolean, periodMs: Int = 26_000): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0.5f) }
    val transition = rememberInfiniteTransition(label = "drift")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftValue",
    )
}

/**
 * A dialog panel arriving: from the side it is anchored to, or rising and growing a touch for a
 * centred one, on the spatial spring. Read in the layer phase only; nothing under Reduce motion.
 */
fun Modifier.panelEntrance(fromSide: Boolean = true): Modifier = composed {
    val reduce = VelaTheme.motion.reduceMotion
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) progress.animateTo(1f, VelaSprings.spatial()) }
    val travel = with(LocalDensity.current) { 56.dp.toPx() }
    graphicsLayer {
        val v = progress.value
        alpha = v.coerceIn(0f, 1f)
        if (fromSide) {
            translationX = (1f - v) * travel
        } else {
            translationY = (1f - v) * travel * 0.5f
            val s = 0.94f + 0.06f * v
            scaleX = s
            scaleY = s
        }
    }
}

/**
 * A page that replaces another in place (a Settings section, a setup step) settles in on the
 * spatial spring each time [key] changes: a short travel along [dx]/[dy] dp and a quick fade up.
 * Layer-only, so the page is composed once, not twice as AnimatedContent would during the swap.
 */
fun Modifier.pageEntrance(key: Any?, dx: Float = 0f, dy: Float = 18f): Modifier = composed {
    val reduce = VelaTheme.motion.reduceMotion
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        // The first page is already there when the screen opens; only a change animates.
        if (first) { first = false; return@LaunchedEffect }
        if (reduce) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, VelaSprings.spatial())
    }
    val density = LocalDensity.current
    val tx = with(density) { dx.dp.toPx() }
    val ty = with(density) { dy.dp.toPx() }
    graphicsLayer {
        val v = progress.value
        alpha = (0.35f + 0.65f * v).coerceIn(0f, 1f)
        translationX = (1f - v) * tx
        translationY = (1f - v) * ty
    }
}

/** The dim behind a dialog, faded in instead of snapping on; drawn, so it never recomposes. */
fun Modifier.animatedScrim(color: Color): Modifier = composed {
    val reduce = VelaTheme.motion.reduceMotion
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) progress.animateTo(1f, VelaSprings.effectsSlow()) }
    drawBehind { drawRect(color.copy(alpha = color.alpha * progress.value)) }
}

/**
 * Springs of the Material 3 Expressive motion scheme, copied because the Material version in use
 * (1.4) does not ship `MotionScheme`. Spatial springs move things (scale, position, size) and may
 * overshoot; effect springs fade and recolour and never do.
 */
object VelaSprings {
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsSlow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

private const val ENTRANCE_WINDOW_MS = 900L
private const val MAX_STAGGERED = 14
private const val STAGGER_MS = 28
