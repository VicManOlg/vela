package io.vela.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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

private const val ENTRANCE_WINDOW_MS = 900L
private const val MAX_STAGGERED = 14
private const val STAGGER_MS = 28
