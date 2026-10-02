package io.vela.core.ui.components

import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import io.vela.core.model.ControllerLayout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.Canvas
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.clickable
import io.vela.core.ui.input.LocalGamepad
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import io.vela.core.ui.theme.liveAccent

/** Top-level destinations shown in the header. */
data class TopTab(val id: String, val label: String)

/**
 * Header: the Vela mark, section tabs (L1/R1 switch them), clock and battery. Tabs are focusable
 * so touch and D-pad users can reach them too.
 */
@Composable
fun TopBar(
    tabs: List<TopTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstTabFocus: FocusRequester? = null,
) {
    val colors = VelaTheme.colors
    val dimens = VelaTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VelaMark()
        Spacer(Modifier.width(28.dp))
        if (dimens.showButtonHints) ShoulderHint("L1")
        // The keel: one bar under the selected tab that slides to the next one on L1/R1, so the
        // current section reads from across the room. Tab bounds are measured, the bar is drawn.
        val scroll = rememberScrollState()
        val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
        val keelX = remember { Animatable(0f) }
        val keelW = remember { Animatable(0f) }
        val instant = VelaTheme.motion.transitionDurationMs == 0
        val target = bounds[selectedId]
        LaunchedEffect(target, instant) {
            val (x, w) = target ?: return@LaunchedEffect
            if (keelW.value == 0f || instant) {
                keelX.snapTo(x)
                keelW.snapTo(w)
            } else {
                launch { keelX.animateTo(x, spring(dampingRatio = 0.75f, stiffness = 420f)) }
                keelW.animateTo(w, spring(dampingRatio = 0.75f, stiffness = 420f))
            }
        }
        val keel = colors.focusRing
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                .drawBehind {
                    val w = keelW.value
                    if (w <= 0f) return@drawBehind
                    val h = 3.dp.toPx()
                    val inset = 14.dp.toPx()
                    drawRoundRect(keel, Offset(keelX.value + inset - scroll.value, size.height - h), Size((w - 2 * inset).coerceAtLeast(h), h), CornerRadius(h / 2, h / 2))
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                TabChip(
                    label = tab.label,
                    selected = tab.id == selectedId,
                    onClick = { onSelect(tab.id) },
                    modifier = (if (index == 0 && firstTabFocus != null) Modifier.focusRequester(firstTabFocus) else Modifier)
                        .onGloballyPositioned { bounds[tab.id] = it.positionInParent().x to it.size.width.toFloat() },
                )
            }
        }
        if (dimens.showButtonHints) ShoulderHint("R1")
        Spacer(Modifier.width(16.dp))
        if (dimens.showBattery) BatteryIndicator()
        if (dimens.showClock) {
            Spacer(Modifier.width(18.dp))
            Clock()
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VelaTheme.colors
    val shape = VelaTheme.shapes.chip
    val interaction = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interaction)
    Column(
        modifier
            .velaFocusable(shape, interaction, onClick, scaleOverride = 1.04f)
            .clip(shape)
            .background(if (focused) colors.surfaceElevated.copy(alpha = 0.9f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = VelaTheme.typography.bodyStrong,
            maxLines = 1,
            softWrap = false,
            color = if (selected || focused) colors.onBackground else colors.muted,
        )
    }
}

@Composable
private fun ShoulderHint(text: String) {
    val colors = VelaTheme.colors
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.onBackground.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = VelaTheme.typography.caption, color = colors.muted, fontWeight = FontWeight.SemiBold)
    }
}

/** The mark: a small sail. Drawn with two triangles so it scales with any theme colour. */
@Composable
fun VelaMark(modifier: Modifier = Modifier, size: Int = 22) {
    val colors = VelaTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(size.dp)
                .clip(SailShape)
                .background(Brush.verticalGradient(listOf(colors.accent, colors.accentSecondary))),
        )
        Spacer(Modifier.width(10.dp))
        Text("Vela", style = VelaTheme.typography.headline, color = colors.onBackground, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Clock() {
    val time by produceState(initialValue = formatTime()) {
        while (true) {
            value = formatTime()
            delay(10_000)
        }
    }
    Text(time, style = VelaTheme.typography.bodyStrong, color = VelaTheme.colors.onBackground, maxLines = 1, softWrap = false)
}

private fun formatTime(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

@Composable
fun BatteryIndicator() {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (raw >= 0 && scale > 0) raw * 100 / scale else -1
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(30_000)
        }
    }
    if (level < 0) return
    val colors = VelaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(26.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.onBackground.copy(alpha = 0.18f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(level / 100f)
                    .height(12.dp)
                    .background(if (level <= 15 && !charging) colors.danger else colors.onBackground),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(if (charging) "$level% ⚡" else "$level%", style = VelaTheme.typography.caption, color = colors.muted, maxLines = 1, softWrap = false)
    }
}

/** One entry in the bottom hint bar. */
data class ButtonHint(val button: GamepadButton, val label: String)

/** Bottom bar: which button does what on this screen. Nintendo/Xbox glyph follows the swap setting. Each hint is also a touch button. */
@Composable
fun ButtonHints(hints: List<ButtonHint>, modifier: Modifier = Modifier, swapped: Boolean = false) {
    if (!VelaTheme.dimens.showButtonHints || hints.isEmpty()) return
    val colors = VelaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.8f))))
            .padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val gamepad = LocalGamepad.current
        val sounds = LocalUiSounds.current
        hints.forEach { hint ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clip(VelaTheme.shapes.chip)
                    // Touch targets only: once the D-pad is used Android makes a clickable focusable,
                    // and focus could sink into the hint bar where no ring shows it.
                    .focusProperties { canFocus = false }
                    .clickable(enabled = gamepad != null) {
                        // A and B make their own sound downstream; the rest chime here.
                        if (hint.button != GamepadButton.A && hint.button != GamepadButton.B) sounds?.play(UiSound.CONFIRM)
                        gamepad?.press(hint.button)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                ButtonGlyph(hint.button, swapped)
                Spacer(Modifier.width(8.dp))
                Text(hint.label, style = VelaTheme.typography.label, color = colors.onBackground.copy(alpha = 0.82f))
            }
        }
    }
}

/** The device's face-button layout; provided by the app from settings. */
val LocalControllerLayout = staticCompositionLocalOf { ControllerLayout.ODIN3 }

/**
 * One button as the hint bar shows it. Face buttons carry a four-dot diamond that marks where the
 * button physically sits on the current layout, then the label (letter or PlayStation symbol) in
 * the layout's colour, so a hint reads the same way as looking at the pad.
 */
@Composable
fun ButtonGlyph(button: GamepadButton, swapped: Boolean = false, size: Int = 26) {
    val colors = VelaTheme.colors
    val layout = LocalControllerLayout.current
    val physical = when {
        swapped && button == GamepadButton.A -> "B"
        swapped && button == GamepadButton.B -> "A"
        else -> button.name
    }
    val round = button in setOf(GamepadButton.A, GamepadButton.B, GamepadButton.X, GamepadButton.Y)
    val tint = if (round) faceColor(layout, physical, colors.onBackground) else colors.onBackground
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (round) {
            PositionDiamond(position = layout.positionOf(physical), active = tint, idle = colors.muted.copy(alpha = 0.45f), size = (size * 0.6f).dp)
            Spacer(Modifier.width(5.dp))
        }
        Box(
            Modifier
                .then(if (round) Modifier.size(size.dp) else Modifier.height(size.dp).padding(horizontal = 0.dp))
                .clip(if (round) CircleShape else RoundedCornerShape(6.dp))
                .background(if (round) tint.copy(alpha = 0.22f) else colors.onBackground.copy(alpha = 0.14f))
                .padding(horizontal = if (round) 0.dp else 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (round) layout.glyphOf(physical) else physical,
                style = VelaTheme.typography.caption,
                color = if (round) tint else colors.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Xbox and PlayStation pads colour their buttons; the Odin 3's are neutral, so the text colour is used. */
private fun faceColor(layout: ControllerLayout, label: String, neutral: Color): Color = when (layout) {
    ControllerLayout.ODIN3 -> neutral
    ControllerLayout.XBOX -> when (label) { "A" -> Color(0xFF5DC15D); "B" -> Color(0xFFE85A5A); "X" -> Color(0xFF4FA3F7); else -> Color(0xFFF2C94C) }
    ControllerLayout.PLAYSTATION -> when (label) { "A" -> Color(0xFF8FB4F0); "B" -> Color(0xFFE85A5A); "X" -> Color(0xFFE8A0D0); else -> Color(0xFF7FD6A0) }
}

/** Four dots in a diamond; the one at [position] ("top", "right", "bottom", "left") is lit. */
@Composable
private fun PositionDiamond(position: String, active: Color, idle: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f - 1.5f
        val dots = mapOf(
            "top" to Offset(c.x, c.y - r), "right" to Offset(c.x + r, c.y),
            "bottom" to Offset(c.x, c.y + r), "left" to Offset(c.x - r, c.y),
        )
        for ((name, at) in dots) {
            val lit = name == position
            drawCircle(if (lit) active else idle, radius = if (lit) 2.6f.dp.toPx() else 1.6f.dp.toPx(), center = at)
        }
    }
}
