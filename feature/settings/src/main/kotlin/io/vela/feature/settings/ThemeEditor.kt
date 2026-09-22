package io.vela.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.vela.core.model.ThemeSpec
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.VelaMenuDialog

/** One editable aspect of a theme: a label, a way to read the current value and the choices. */
private class ThemeProperty(
    val id: String,
    val label: String,
    val current: (ThemeSpec) -> String,
    val choices: List<ThemeChoice>,
)

private class ThemeChoice(val id: String, val label: String, val description: String? = null, val apply: (ThemeSpec) -> ThemeSpec)

private val palette: List<Pair<String, String>> = listOf(
    "Sky" to "#FF7FD7FF", "Blue" to "#FF3D7BFF", "Violet" to "#FF9B7BFF", "Magenta" to "#FFFF5CB8", "Red" to "#FFFF5C6C",
    "Orange" to "#FFFF9A3D", "Amber" to "#FFFFC857", "Green" to "#FF5CE0A8", "Teal" to "#FF3DD6C9", "White" to "#FFF2F5FA",
)

private fun colourName(hex: String) = palette.firstOrNull { it.second.equals(hex, ignoreCase = true) }?.first ?: hex

private val properties: List<ThemeProperty> = listOf(
    ThemeProperty("accent", "Accent colour", { colourName(it.colors.accent) },
        palette.map { (name, hex) -> ThemeChoice(hex, name, hex) { s -> s.copy(colors = s.colors.copy(accent = hex)) } }),
    ThemeProperty("accent2", "Secondary accent", { colourName(it.colors.accentSecondary) },
        palette.map { (name, hex) -> ThemeChoice(hex, name, hex) { s -> s.copy(colors = s.colors.copy(accentSecondary = hex)) } }),
    ThemeProperty("background", "Background", { it.background.mode.replaceFirstChar(Char::uppercase) }, listOf(
        ThemeChoice("hero", "Hero", "Sharp scene of the focused game with a slow drift") { s -> s.copy(background = s.background.copy(mode = "hero", blurRadius = 0f)) },
        ThemeChoice("artwork", "Blurred art", "Heavily blurred cover of the focused game") { s -> s.copy(background = s.background.copy(mode = "artwork", blurRadius = 40f)) },
        ThemeChoice("platform", "System colour", "Gradient in the focused system's colour") { s -> s.copy(background = s.background.copy(mode = "platform")) },
        ThemeChoice("static", "Plain", "Flat background colour") { s -> s.copy(background = s.background.copy(mode = "static")) },
    )),
    ThemeProperty("icons", "Console icons", { it.platformIcons.set.replaceFirstChar(Char::uppercase) }, listOf(
        ThemeChoice("systematic", "Systematic", "Console illustrations") { s -> s.copy(platformIcons = s.platformIcons.copy(set = "systematic", tint = false)) },
        ThemeChoice("flatui", "Flat", "Flat colour icons") { s -> s.copy(platformIcons = s.platformIcons.copy(set = "flatui", tint = false)) },
        ThemeChoice("monochrome", "Monochrome", "White glyphs tinted with the text colour") { s -> s.copy(platformIcons = s.platformIcons.copy(set = "monochrome", tint = true)) },
        ThemeChoice("none", "None") { s -> s.copy(platformIcons = s.platformIcons.copy(set = "none")) },
    )),
    ThemeProperty("cards", "Card size", { when { it.layout.cardWidth <= 135f -> "Compact"; it.layout.cardWidth >= 170f -> "Large"; else -> "Regular" } }, listOf(
        ThemeChoice("130", "Compact", "More covers per row") { s -> s.copy(layout = s.layout.copy(cardWidth = 130f)) },
        ThemeChoice("150", "Regular") { s -> s.copy(layout = s.layout.copy(cardWidth = 150f)) },
        ThemeChoice("175", "Large", "Bigger covers, fewer per row") { s -> s.copy(layout = s.layout.copy(cardWidth = 175f)) },
    )),
    ThemeProperty("corners", "Corners", { when { it.shapes.cardRadius <= 8f -> "Sharp"; it.shapes.cardRadius >= 20f -> "Round"; else -> "Soft" } }, listOf(
        ThemeChoice("sharp", "Sharp") { s -> s.copy(shapes = s.shapes.copy(cardRadius = 6f, tileRadius = 10f)) },
        ThemeChoice("soft", "Soft") { s -> s.copy(shapes = s.shapes.copy(cardRadius = 14f, tileRadius = 18f)) },
        ThemeChoice("round", "Round") { s -> s.copy(shapes = s.shapes.copy(cardRadius = 22f, tileRadius = 26f)) },
    )),
    ThemeProperty("glass", "Glass panels", { if (it.effects.glassPanels) "On" else "Off" }, listOf(
        ThemeChoice("on", "On", "Translucent panels over the backdrop") { s -> s.copy(effects = s.effects.copy(glassPanels = true)) },
        ThemeChoice("off", "Off", "Solid panels") { s -> s.copy(effects = s.effects.copy(glassPanels = false)) },
    )),
    ThemeProperty("glow", "Focus glow", { if (it.effects.focusGlow) "On" else "Off" }, listOf(
        ThemeChoice("on", "On") { s -> s.copy(effects = s.effects.copy(focusGlow = true)) },
        ThemeChoice("off", "Off") { s -> s.copy(effects = s.effects.copy(focusGlow = false)) },
    )),
)

/**
 * Two-level editor built from the menu dialog: pick an aspect, pick a value. Every choice is
 * saved at once as the "custom" theme (derived from the theme in use) and applied live, so the
 * user sees the result behind the dialog.
 */
@Composable
internal fun ThemeEditorDialogs(current: ThemeSpec, onApply: ((ThemeSpec) -> ThemeSpec) -> Unit, onClose: () -> Unit) {
    var property by remember { mutableStateOf<ThemeProperty?>(null) }
    val open = property
    if (open == null) {
        VelaMenuDialog(
            title = "Customize theme",
            subtitle = "Based on ${current.name}. Changes apply immediately.",
            options = properties.map { MenuOption(it.id, it.label, description = it.current(current)) },
            onSelect = { opt -> property = properties.first { it.id == opt.id } },
            onDismiss = onClose,
        )
    } else {
        VelaMenuDialog(
            title = open.label,
            options = open.choices.map { MenuOption(it.id, it.label, description = it.description, selected = it.id == open.choices.firstOrNull { c -> c.label == open.current(current) }?.id) },
            onSelect = { opt ->
                val choice = open.choices.first { it.id == opt.id }
                onApply(choice.apply)
                property = null
            },
            onDismiss = { property = null },
        )
    }
}
