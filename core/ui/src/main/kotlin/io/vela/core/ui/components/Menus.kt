package io.vela.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.vela.core.ui.input.GamepadButton
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.ui.sound.LocalUiSounds
import io.vela.core.ui.sound.UiSound

/**
 * Dialogs get their own window, which would bring the system bars back. Hide them so menus feel
 * like part of the console UI instead of an Android popup.
 */
@Composable
fun ImmersiveDialogWindow() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Tallest a dialog panel may grow: leaves breathing room above and below on any screen. */
@Composable
fun dialogMaxHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

/** One choice in a contextual menu. */
data class MenuOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Contextual menu in the console style: a glass panel anchored bottom-right, first option
 * focused on open, B closes. Also used for pickers (player, sort, theme...).
 */
@Composable
fun VelaMenuDialog(
    title: String,
    options: List<MenuOption>,
    onSelect: (MenuOption) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val colors = VelaTheme.colors
    val sounds = LocalUiSounds.current
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        BackHandler(onBack = onDismiss)
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
                    .widthIn(min = 320.dp, max = 440.dp)
                    .heightIn(max = dialogMaxHeight())
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column {
                    Text(title, style = VelaTheme.typography.title, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = VelaTheme.typography.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(options, key = { _, o -> o.id }) { index, option ->
                            SettingRow(
                                title = option.label,
                                description = option.description,
                                icon = option.icon,
                                value = if (option.selected) "•" else null,
                                enabled = option.enabled,
                                danger = option.danger,
                                onClick = { onSelect(option) },
                                modifier = if (index == options.indexOfFirst { it.selected }.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        ButtonGlyph(GamepadButton.B)
                        Spacer(Modifier.width(8.dp))
                        Text("Back", style = VelaTheme.typography.label, color = colors.muted)
                    }
                }
            }
        }
        LaunchedEffect(options.size) { runCatching { firstFocus.requestFocus() } }
    }
}

/** Yes/no confirmation with the destructive action never focused first. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val colors = VelaTheme.colors
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.widthIn(min = 360.dp, max = 520.dp)) {
                Column {
                    Text(title, style = VelaTheme.typography.title, color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = VelaTheme.typography.body, color = colors.muted)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        VelaButton("Cancel", onDismiss, modifier = Modifier.focusRequester(cancelFocus))
                        VelaButton(confirmLabel, onConfirm, primary = !danger)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    }
}

/** Single-line text entry (collection names, search) with a confirm button. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VelaTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ImmersiveDialogWindow()
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.widthIn(min = 360.dp, max = 520.dp)) {
                Column {
                    Text(title, style = VelaTheme.typography.title, color = colors.onBackground)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(VelaTheme.shapes.card)
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (text.isEmpty()) Text(placeholder, style = VelaTheme.typography.body, color = colors.muted)
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = VelaTheme.typography.body.copy(color = colors.onBackground),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        VelaButton("Cancel", onDismiss)
                        VelaButton(confirmLabel, { if (text.isNotBlank()) onConfirm(text.trim()) }, primary = true, enabled = text.isNotBlank())
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    }
}
