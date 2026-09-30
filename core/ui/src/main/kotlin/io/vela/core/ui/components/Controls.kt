package io.vela.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.input.GamepadHandler
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound
import io.vela.core.ui.theme.VelaTheme
import kotlin.math.roundToInt

/**
 * Settings row for a numeric value that is tuned in place: with the row focused, left and right
 * on the pad step the value (holding repeats), and the bar on the right shows where it sits in
 * its range. Touch taps the chevrons. Pressing the row itself calls [onClick], meant for a menu
 * of presets and "Theme default"; [overridden] false draws the value muted, meaning the theme's
 * own value is in force.
 */
@Composable
fun StepperRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    overridden: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = VelaTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interactionSource)
    val sounds = LocalUiSounds.current
    val latestValue = rememberUpdatedState(value)
    val latestChange = rememberUpdatedState(onChange)

    fun nudge(direction: Int) {
        if (!enabled) return
        val steps = ((latestValue.value - range.start) / step).roundToInt()
        val next = (range.start + (steps + direction) * step).coerceIn(range.start, range.endInclusive)
        // Snap to the step grid so repeated presses never accumulate float noise.
        val snapped = (next / step).roundToInt() * step
        if (snapped != latestValue.value) {
            sounds?.play(UiSound.FOCUS)
            latestChange.value(snapped)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (!focused || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { nudge(-1); true }
                    Key.DirectionRight -> { nudge(+1); true }
                    else -> false
                }
            }
            .velaFocusable(shape, interactionSource, onClick, scaleOverride = 1.01f, enabled = enabled)
            .clip(shape)
            .background(if (focused) colors.surfaceElevated.copy(alpha = 0.9f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = VelaTheme.typography.body, color = if (enabled) colors.onBackground else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(description, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(16.dp))
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val fill = if (!enabled) colors.muted else if (overridden) colors.accent else colors.muted
        Icon(
            Icons.Rounded.ChevronLeft, contentDescription = "Less", tint = if (focused) colors.onBackground else colors.muted,
            modifier = Modifier.size(22.dp).pointerInput(enabled) { detectTapGestures { nudge(-1) } },
        )
        Box(
            Modifier
                .width(120.dp)
                .height(22.dp)
                .padding(horizontal = 4.dp)
                .drawBehind {
                    val trackH = 4.dp.toPx()
                    val y = (size.height - trackH) / 2f
                    drawRoundRect(colors.muted.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, trackH), CornerRadius(trackH))
                    drawRoundRect(fill, Offset(0f, y), Size(size.width * fraction, trackH), CornerRadius(trackH))
                    val knob = 7.dp.toPx()
                    drawCircle(if (focused) colors.onBackground else fill, knob, Offset(size.width * fraction, size.height / 2f))
                },
        )
        Icon(
            Icons.Rounded.ChevronRight, contentDescription = "More", tint = if (focused) colors.onBackground else colors.muted,
            modifier = Modifier.size(22.dp).pointerInput(enabled) { detectTapGestures { nudge(+1) } },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            format(value),
            style = VelaTheme.typography.bodyStrong,
            color = fill,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** A named colour offered by [SwatchPickerDialog]; [hex] is ARGB like the theme JSON. */
data class Swatch(val hex: String, val name: String)

/**
 * Colour picker for the pad: a grid of round swatches, the current one ticked, plus a
 * "Theme default" entry. Selection is immediate so the screen behind shows the result.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchPickerDialog(
    title: String,
    swatches: List<Swatch>,
    selectedHex: String?,
    themeDefaultHex: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val colors = VelaTheme.colors
    val sounds = LocalUiSounds.current
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        BackHandler(onBack = onDismiss)
        GamepadHandler { true }
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sounds?.play(UiSound.BACK); onDismiss() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            GlassPanel(
                Modifier
                    .padding(end = VelaTheme.dimens.screenPadding)
                    .widthIn(min = 360.dp, max = 480.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column {
                    Text(title, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        swatches.forEachIndexed { index, swatch ->
                            val selected = swatch.hex.equals(selectedHex, ignoreCase = true)
                            SwatchDot(
                                colour = parseHex(swatch.hex),
                                selected = selected,
                                onClick = { onSelect(swatch.hex) },
                                modifier = if (index == swatches.indexOfFirst { it.hex.equals(selectedHex, ignoreCase = true) }.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingRow(
                        title = "Theme default",
                        description = swatches.firstOrNull { it.hex.equals(themeDefaultHex, ignoreCase = true) }?.name,
                        value = if (selectedHex == null) "•" else null,
                        onClick = { onSelect(null) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        ButtonGlyph(GamepadButton.B)
                        Spacer(Modifier.width(8.dp))
                        Text("Back", style = VelaTheme.typography.label, color = colors.muted)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun SwatchDot(colour: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(44.dp)
            .velaFocusable(CircleShape, interactionSource, onClick, scaleOverride = 1.12f)
            .clip(CircleShape)
            .background(colour)
            .border(1.dp, colors.onBackground.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = if (colour.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun parseHex(hex: String): Color = runCatching {
    val clean = hex.removePrefix("#")
    val argb = if (clean.length == 6) "FF$clean" else clean
    Color(argb.toLong(16).toInt())
}.getOrDefault(Color.Magenta)
