package io.vela.core.ui.components

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.theme.VelaTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

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
            .padding(horizontal = dimens.screenPadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VelaMark()
        Spacer(Modifier.width(28.dp))
        if (dimens.showButtonHints) ShoulderHint("L1")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                TabChip(
                    label = tab.label,
                    selected = tab.id == selectedId,
                    onClick = { onSelect(tab.id) },
                    modifier = if (index == 0 && firstTabFocus != null) Modifier.focusRequester(firstTabFocus) else Modifier,
                )
            }
        }
        if (dimens.showButtonHints) ShoulderHint("R1")
        Spacer(Modifier.weight(1f))
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
    Box(
        modifier
            .velaFocusable(shape, interaction, onClick, scaleOverride = 1.06f)
            .clip(shape)
            .background(
                when {
                    selected -> colors.onBackground
                    focused -> colors.surfaceElevated
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = VelaTheme.typography.bodyStrong,
            color = if (selected) colors.background else colors.onBackground.copy(alpha = if (focused) 1f else 0.72f),
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
    Text(time, style = VelaTheme.typography.bodyStrong, color = VelaTheme.colors.onBackground)
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
        Text(if (charging) "$level% ⚡" else "$level%", style = VelaTheme.typography.caption, color = colors.muted)
    }
}

/** One entry in the bottom hint bar. */
data class ButtonHint(val button: GamepadButton, val label: String)

/** Bottom bar: which button does what on this screen. Nintendo/Xbox glyph follows the swap setting. */
@Composable
fun ButtonHints(hints: List<ButtonHint>, modifier: Modifier = Modifier, swapped: Boolean = false) {
    if (!VelaTheme.dimens.showButtonHints || hints.isEmpty()) return
    val colors = VelaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = VelaTheme.dimens.screenPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { hint ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 22.dp)) {
                ButtonGlyph(hint.button, swapped)
                Spacer(Modifier.width(8.dp))
                Text(hint.label, style = VelaTheme.typography.label, color = colors.onBackground.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
fun ButtonGlyph(button: GamepadButton, swapped: Boolean = false, size: Int = 22) {
    val colors = VelaTheme.colors
    val physical = when {
        swapped && button == GamepadButton.A -> "B"
        swapped && button == GamepadButton.B -> "A"
        else -> button.name
    }
    val round = button in setOf(GamepadButton.A, GamepadButton.B, GamepadButton.X, GamepadButton.Y)
    Box(
        Modifier
            .then(if (round) Modifier.size(size.dp) else Modifier.height(size.dp).padding(horizontal = 0.dp))
            .clip(if (round) CircleShape else RoundedCornerShape(6.dp))
            .background(colors.onBackground.copy(alpha = 0.14f))
            .padding(horizontal = if (round) 0.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            physical,
            style = VelaTheme.typography.caption,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}
