package io.vela.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.vela.core.ui.components.rememberAutoFocus
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.image.VelaImage
import io.vela.core.ui.image.artworkModel
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.ui.sound.UiSound
import io.vela.core.ui.sound.LocalUiSounds
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One pick of the stage: a system or one of the smart shelves. */
internal data class StageEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val accent: Long,
    val icon: String?,
    val iconVector: ImageVector?,
    val covers: List<String>,
    val background: String?,
    val open: () -> Unit,
)

/**
 * One system at a time: the console large on its colour glow, name and facts, a shelf of the
 * user's covers, and a dial of every console along the bottom that turns with left/right. The
 * whole stage is a single focus target: left/right pick, A opens, up leaves to the tabs.
 * Touch: swipe or tap a console on the dial, tap the stage to open.
 */
@Composable
internal fun SystemStage(
    entries: List<StageEntry>,
    initialIndex: Int,
    onSpot: (Spot) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    var selected by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(0, entries.lastIndex)) }
    var direction by remember { mutableIntStateOf(1) }
    var drag by remember { mutableFloatStateOf(0f) }
    val entry = entries[selected.coerceIn(0, entries.lastIndex)]
    val motion = VelaTheme.motion
    val colors = VelaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    val sounds = LocalUiSounds.current
    val autoFocus = rememberAutoFocus(keys = arrayOf(entries.size))
    // The tab bar may claim focus right after a tab switch; ask again once things settle.
    LaunchedEffect(entries.size) {
        delay(300)
        runCatching { autoFocus.requestFocus() }
    }
    // Read by the dial in its layout and layer phases only; the slide never recomposes the stage.
    val position = animateFloatAsState(selected.toFloat(), tween(motion.transitionDurationMs, easing = FastOutSlowInEasing), label = "dial")

    fun pick(index: Int) {
        val target = index.coerceIn(0, entries.lastIndex)
        if (target != selected) {
            direction = if (target > selected) 1 else -1
            selected = target
            sounds?.play(UiSound.FOCUS)
        }
    }

    LaunchedEffect(entry.key, entry.background) { onSpot(Spot(entry.title, entry.subtitle, entry.background, entry.accent)) }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(autoFocus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { pick(selected - 1); true }
                    Key.DirectionRight -> { pick(selected + 1); true }
                    else -> false
                }
            }
            .clickable(interactionSource = interaction, indication = null) {
                sounds?.play(UiSound.CONFIRM)
                entry.open()
            }
            // clickable alone is only focusable with a pointer device attached; the stage must take
            // controller focus on a touch screen too.
            .focusable(true, interaction)
            .pointerInput(entries.size) {
                detectHorizontalDragGestures(onDragEnd = { drag = 0f }) { _, dx ->
                    drag += dx
                    if (drag > SWIPE_PX) { pick(selected - 1); drag = 0f }
                    if (drag < -SWIPE_PX) { pick(selected + 1); drag = 0f }
                }
            },
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = VelaTheme.dimens.screenPadding),
        ) {
            AnimatedContent(
                targetState = entry,
                transitionSpec = {
                    (fadeIn(tween(motion.transitionDurationMs)) + slideInHorizontally(tween(motion.transitionDurationMs)) { direction * it / 5 }) togetherWith
                        (fadeOut(tween(motion.transitionDurationMs / 2)) + slideOutHorizontally(tween(motion.transitionDurationMs)) { -direction * it / 5 })
                },
                label = "stage",
                modifier = Modifier.weight(0.56f).fillMaxHeight(),
            ) { e ->
                val accent = Color(e.accent)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .fillMaxHeight(0.72f)
                            .aspectRatio(1f)
                            .drawBehind {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = c, radius = size.width * 0.6f), radius = size.width * 0.6f, center = c)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (e.icon != null) {
                            VelaImage(
                                model = artworkModel(e.icon),
                                contentDescription = e.title,
                                modifier = Modifier.fillMaxSize(0.82f),
                                contentScale = ContentScale.Fit,
                                colorFilter = if (VelaTheme.platformIcons.tint) ColorFilter.tint(colors.onBackground) else null,
                                placeholder = {},
                            )
                        } else if (e.iconVector != null) {
                            Icon(e.iconVector, contentDescription = e.title, tint = colors.onBackground, modifier = Modifier.fillMaxSize(0.5f))
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = VelaTheme.typography.display, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            e.subtitle.split("   ").filter { it.isNotBlank() }.joinToString("  ·  ").uppercase(),
                            style = VelaTheme.typography.overline,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(Modifier.weight(0.44f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                CoverShelf(entry.covers, Color(entry.accent))
            }
        }
        Dial(entries, position, selected, focused, onPick = ::pick, modifier = Modifier.fillMaxWidth().height(DIAL_HEIGHT))
    }
}

/** The user's recent covers of the system on a slanted shelf, front one first. */
@Composable
private fun CoverShelf(covers: List<String>, accent: Color) {
    if (covers.isEmpty()) return
    val colors = VelaTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        val coverHeight = maxHeight * 0.68f
        val coverWidth = coverHeight * VelaTheme.dimens.boxArtAspect
        val step = coverWidth * 0.56f
        // Draw back to front so the first cover ends on top.
        covers.take(3).withIndex().reversed().forEach { (index, path) ->
            val depth = index.toFloat()
            Box(
                Modifier
                    .offset(x = -(step * depth) - 8.dp)
                    .width(coverWidth)
                    .height(coverHeight)
                    .graphicsLayer {
                        rotationY = -24f
                        cameraDistance = 14f * density
                        val s = 1f - 0.08f * depth
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.22f * depth
                        shadowElevation = 10.dp.toPx()
                        shape = RoundedCornerShape(8.dp)
                        clip = true
                    }
                    .background(colors.surface),
            ) {
                VelaImage(model = artworkModel(path), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, accent = accent, placeholder = {})
            }
        }
    }
}

/**
 * Consoles on a dial: the selected one sits centred and largest, the others shrink, fade and
 * sink as they move away, like beads on a wheel seen from above.
 */
@Composable
private fun Dial(
    entries: List<StageEntry>,
    position: State<Float>,
    selected: Int,
    focused: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VelaTheme.colors
    val iconStyle = VelaTheme.platformIcons
    BoxWithConstraints(modifier) {
        val chip = 54.dp
        val spacing = 84.dp
        val centerX = maxWidth / 2 - chip / 2
        // Far beads first so the middle draws on top. Beads within reach of the slide are composed;
        // position, size and fade are computed per frame in the layout and layer phases.
        entries.indices.sortedByDescending { abs(it - selected) }.forEach { i ->
            val e = entries[i]
            if (abs(i - selected) > 7) return@forEach
            val accent = Color(e.accent)
            val isSelected = i == selected
            Box(
                Modifier
                    .offset {
                        val d = i - position.value
                        val ad = abs(d)
                        val x = centerX + spacing * d * (1f - 0.03f * ad)
                        val sink = (ad.pow(1.4f) * 5f).dp
                        IntOffset(x.roundToPx(), (10.dp + sink).roundToPx())
                    }
                    .size(chip)
                    .graphicsLayer {
                        val ad = abs(i - position.value)
                        val scale = (1.3f - 0.3f * min(ad, 1f) - 0.07f * max(ad - 1f, 0f)).coerceAtLeast(0.55f)
                        scaleX = scale
                        scaleY = scale
                        this.alpha = if (ad > 6f) 0f else (1f - 0.14f * ad).coerceIn(0.18f, 1f)
                    }
                    .clip(CircleShape)
                    .background(if (isSelected) accent.copy(alpha = 0.9f) else colors.surfaceElevated.copy(alpha = 0.8f))
                    .then(if (isSelected && focused) Modifier.drawBehind { drawCircle(colors.focusRing, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) } else Modifier)
                    // Taps only: beads must never become focus targets, the stage owns the D-pad.
                    .pointerInput(i) { detectTapGestures { onPick(i) } }
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (e.icon != null) {
                    VelaImage(
                        model = artworkModel(e.icon),
                        contentDescription = e.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = if (iconStyle.tint) ColorFilter.tint(colors.onBackground) else null,
                        placeholder = {},
                    )
                } else if (e.iconVector != null) {
                    Icon(e.iconVector, contentDescription = e.title, tint = colors.onBackground, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
        }
    }
}

private val DIAL_HEIGHT = 104.dp
private const val SWIPE_PX = 90f
