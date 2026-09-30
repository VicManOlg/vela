package io.vela.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.vela.core.model.AppSettings
import io.vela.core.model.AppearanceOverrides
import io.vela.core.model.CardLabels
import io.vela.core.model.HomeLayout
import io.vela.core.model.HomeRail
import io.vela.core.model.LibraryLayout
import io.vela.core.model.LibraryView
import io.vela.core.model.TabBarMode
import io.vela.core.model.ThemeSpec
import io.vela.core.model.applying
import io.vela.core.ui.components.MenuOption
import io.vela.core.ui.components.SectionHeader
import io.vela.core.ui.components.SettingRow
import io.vela.core.ui.components.StepperRow
import io.vela.core.ui.components.Swatch
import io.vela.core.ui.components.SwatchPickerDialog
import io.vela.core.ui.components.VelaMenuDialog
import io.vela.core.ui.components.velaFocusable
import io.vela.core.ui.components.rememberFocusState
import io.vela.core.ui.theme.VelaTheme
import io.vela.core.ui.theme.fromArgbHex
import kotlin.math.roundToInt

// ---- Palettes ---------------------------------------------------------------------------------

private val accentSwatches = listOf(
    Swatch("#FF7FD7FF", "Sky"), Swatch("#FF3D7BFF", "Blue"), Swatch("#FF5B8DEF", "Cobalt"), Swatch("#FF9B7BFF", "Violet"),
    Swatch("#FFD070FF", "Orchid"), Swatch("#FFFF5CB8", "Magenta"), Swatch("#FFFF3C28", "Red"), Swatch("#FFFF5C6C", "Coral"),
    Swatch("#FFFF9A3D", "Orange"), Swatch("#FFFFC857", "Amber"), Swatch("#FFF2E14C", "Yellow"), Swatch("#FFB8F25C", "Lime"),
    Swatch("#FF1DB954", "Green"), Swatch("#FF5CE0A8", "Mint"), Swatch("#FF3DD6C9", "Teal"), Swatch("#FF00C3E3", "Cyan"),
    Swatch("#FFF2F5FA", "White"), Swatch("#FFB9C2D0", "Silver"), Swatch("#FF8A94A8", "Grey"), Swatch("#FF2D2D2D", "Charcoal"),
)

private val backgroundSwatches = listOf(
    Swatch("#FF000000", "Black"), Swatch("#FF07090F", "Night"), Swatch("#FF0B1020", "Navy"), Swatch("#FF0E1A14", "Forest"),
    Swatch("#FF1A0F0A", "Ember"), Swatch("#FF1B1024", "Plum"), Swatch("#FF141414", "Graphite"), Swatch("#FF1E2430", "Slate"),
    Swatch("#FF2A2E36", "Steel"), Swatch("#FF3A3F4A", "Ash"), Swatch("#FFEBEBEB", "Light grey"), Swatch("#FFF6F1E7", "Cream"),
    Swatch("#FFE8F0FF", "Ice"), Swatch("#FFFFFFFF", "White"),
)

private val fontChoices = listOf("outfit" to "Outfit", "manrope" to "Manrope", "system" to "System", "serif" to "Serif", "mono" to "Monospace")
private val backgroundModes = listOf(
    Triple("hero", "Hero", "Sharp scene of the focused game with a slow drift"),
    Triple("artwork", "Blurred art", "Heavily blurred cover of the focused game"),
    Triple("platform", "System colour", "Gradient in the focused system's colour"),
    Triple("static", "Plain", "Flat background colour"),
)
private val iconSets = listOf(
    Triple("systematic", "Systematic", "Console illustrations"),
    Triple("flatui", "Flat", "Flat colour icons"),
    Triple("monochrome", "Monochrome", "White glyphs tinted with the text colour"),
    Triple("none", "None", "No console icons on tiles"),
)
private val coverShapes = listOf(
    Triple(0.72f, "Portrait", "Classic box art, 5:7"),
    Triple(0.66f, "Tall", "PlayStation-style cases, 2:3"),
    Triple(1.0f, "Square", "Even tiles, like a hybrid handheld"),
    Triple(1.33f, "Landscape", "Wide cards, 4:3"),
    Triple(1.78f, "Widescreen", "Screenshot-shaped cards, 16:9"),
)

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

// ---- Section ----------------------------------------------------------------------------------

/**
 * Appearance: pick a theme visually, then tune anything on top of it. Every row shows the value
 * in force; a muted value means the theme's own. Steppers move with left/right on the pad, and
 * pressing them offers presets plus "Theme default". One row resets every tweak at once.
 */
internal fun LazyListScope.appearanceSection(vm: SettingsViewModel, settings: AppSettings) {
    val o = settings.appearance
    val base = vm.themeById(settings.themeId)
    val eff = base.applying(o)
    val set: ((AppearanceOverrides) -> AppearanceOverrides) -> Unit = { vm.updateAppearance(it) }

    item(key = "row1") {
        val allThemes by vm.themes.themes.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.reloadThemes() }
        ThemeCarousel(allThemes, settings.themeId, onSelect = { id -> vm.update { it.copy(themeId = id) } })
    }
    item(key = "row2") {
        // Always present so the list never shifts under the focused row; disabled when there is nothing to undo.
        val count = countOverrides(o)
        SettingRow(
            "Reset look to theme",
            description = if (count == 0) "Everything below is as ${base.name} designed it; tweak anything and this undoes it" else "Removes your $count tweak${if (count == 1) "" else "s"} and shows ${base.name} as designed",
            value = if (count == 0) "No tweaks" else "$count tweak${if (count == 1) "" else "s"}",
            subdued = count == 0,
            enabled = count > 0,
            onClick = vm::resetAppearance,
        )
    }

    // ---- Colours
    item(key = "row3") { SectionHeader("Colours") }
    item(key = "row4") {
        SwatchSetting("Accent colour", "Focus rings, highlights, selected values", accentSwatches, o.accent, base.colors.accent) { hex -> set { it.copy(accent = hex) } }
    }
    item(key = "row5") {
        SwatchSetting("Secondary accent", "Gradients and secondary highlights", accentSwatches, o.accentSecondary, base.colors.accentSecondary) { hex -> set { it.copy(accentSecondary = hex) } }
    }
    item(key = "row6") {
        SwatchSetting("Background colour", "Base colour under the scene and behind panels", backgroundSwatches, o.backgroundColor, base.colors.background) { hex -> set { it.copy(backgroundColor = hex) } }
    }
    item(key = "row7") {
        TriSwitch("Colour from artwork", "Glows and rings take the focused game's colour instead of the accent", o.dynamicAccent, base.effects.dynamicAccent) { v -> set { it.copy(dynamicAccent = v) } }
    }

    // ---- Background
    item(key = "row8") { SectionHeader("Background") }
    item(key = "row9") {
        ChoiceSetting(
            title = "Scene", description = "What is painted behind everything",
            current = o.backgroundMode, themeDefault = base.background.mode,
            options = backgroundModes.map { (k, l, d) -> ChoiceOption(k, l, d) },
            onChange = { v -> set { it.copy(backgroundMode = v) } },
        )
    }
    val sceneHasArt = eff.background.mode == "hero" || eff.background.mode == "artwork"
    item(key = "row10") {
        StepperSetting("Blur", "Softens the scene; 0 keeps it sharp", eff.background.blurRadius, o.backgroundBlur != null, base.background.blurRadius, 0f..60f, 4f, { "${it.roundToInt()}" }, enabled = sceneHasArt, presets = listOf(0f, 12f, 24f, 40f, 60f)) { v -> set { it.copy(backgroundBlur = v) } }
    }
    item(key = "row11") {
        StepperSetting("Dim", "Darkens the scene so text stays readable", eff.background.dim, o.backgroundDim != null, base.background.dim, 0f..0.95f, 0.05f, ::pct, enabled = eff.background.mode != "static", presets = listOf(0.3f, 0.5f, 0.7f, 0.85f)) { v -> set { it.copy(backgroundDim = v) } }
    }
    item(key = "row12") {
        StepperSetting("Saturation", "Colour intensity of the scene; 0 is black and white", eff.background.saturation, o.backgroundSaturation != null, base.background.saturation, 0f..2f, 0.1f, ::pct, enabled = sceneHasArt, presets = listOf(0f, 0.6f, 1f, 1.3f, 1.8f)) { v -> set { it.copy(backgroundSaturation = v) } }
    }

    // ---- Cards
    item(key = "row13") { SectionHeader("Cards") }
    item(key = "row14") {
        StepperSetting("Card size", "Width of a cover on Home and in rails", eff.layout.cardWidth, o.cardWidth != null, base.layout.cardWidth, 100f..220f, 10f, { "${it.roundToInt()}" }, presets = listOf(120f, 150f, 175f, 200f)) { v -> set { it.copy(cardWidth = v) } }
    }
    item(key = "row15") {
        ChoiceSetting(
            title = "Cover shape", description = "Proportions of every game card",
            current = o.boxArtAspect?.let { a -> coverShapes.firstOrNull { it.first == a }?.first?.toString() ?: a.toString() }, themeDefault = base.layout.boxArtAspect.toString(),
            options = coverShapes.map { (a, l, d) -> ChoiceOption(a.toString(), l, d) },
            labelFor = { key -> coverShapes.firstOrNull { it.first.toString() == key }?.second ?: key },
            onChange = { v -> set { it.copy(boxArtAspect = v?.toFloat()) } },
        )
    }
    item(key = "row16") {
        StepperSetting("Corners", "Roundness of cards, tiles and panels", eff.shapes.cardRadius, o.cardRadius != null, base.shapes.cardRadius, 0f..28f, 2f, { "${it.roundToInt()}" }, presets = listOf(0f, 6f, 14f, 22f)) { v -> set { it.copy(cardRadius = v) } }
    }
    item(key = "row17") {
        ChoiceSetting(
            title = "Titles on cards", description = "When a card prints the game's name over the art",
            current = o.cardLabels?.key, themeDefault = base.layout.cardLabels,
            options = CardLabels.entries.map { ChoiceOption(it.key, it.label, it.description) },
            onChange = { v -> set { it.copy(cardLabels = v?.let(CardLabels::fromKey)) } },
        )
    }
    item(key = "row18") {
        StepperSetting("Focus zoom", "How much the focused card grows", eff.motion.focusScale, o.focusScale != null, base.motion.focusScale, 1f..1.16f, 0.01f, { "${((it - 1f) * 100).roundToInt()}%" }, presets = listOf(1f, 1.04f, 1.08f, 1.12f)) { v -> set { it.copy(focusScale = v) } }
    }
    item(key = "row19") {
        val columns = o.gridColumns ?: settings.gridColumns.takeIf { it > 0 }
        StepperSetting("Grid columns", "Cards per row in game lists and search", (columns ?: base.layout.gridColumns).toFloat(), columns != null, base.layout.gridColumns.toFloat(), 3f..10f, 1f, { "${it.roundToInt()}" }, presets = listOf(4f, 5f, 6f, 7f, 8f)) { v -> vm.update { it.copy(gridColumns = 0, appearance = it.appearance.copy(gridColumns = v?.roundToInt())) } }
    }

    // ---- Text
    item(key = "row20") { SectionHeader("Text") }
    item(key = "row21") {
        ChoiceSetting("Headline font", "Titles and big numbers", o.displayFont, base.typography.displayFamily, fontChoices.map { (k, l) -> ChoiceOption(k, l) }, onChange = { v -> set { it.copy(displayFont = v) } })
    }
    item(key = "row22") {
        ChoiceSetting("Body font", "Descriptions, labels and settings", o.bodyFont, base.typography.bodyFamily, fontChoices.map { (k, l) -> ChoiceOption(k, l) }, onChange = { v -> set { it.copy(bodyFont = v) } })
    }
    item(key = "row23") {
        StepperSetting("Text size", "Only the type; Interface size scales cards too", o.fontScale ?: 1f, o.fontScale != null, 1f, 0.8f..1.3f, 0.05f, ::pct, presets = listOf(0.9f, 1f, 1.1f, 1.2f)) { v -> set { it.copy(fontScale = v) } }
    }

    // ---- Effects & motion
    item(key = "row24") { SectionHeader("Effects & motion") }
    item(key = "row25") { TriSwitch("Glass panels", "Translucent panels over the backdrop", o.glassPanels, base.effects.glassPanels) { v -> set { it.copy(glassPanels = v) } } }
    item(key = "row26") {
        StepperSetting("Panel opacity", "How solid glass panels look", eff.effects.panelAlpha, o.panelAlpha != null, base.effects.panelAlpha, 0.3f..1f, 0.05f, ::pct, enabled = eff.effects.glassPanels, presets = listOf(0.5f, 0.62f, 0.8f, 0.95f)) { v -> set { it.copy(panelAlpha = v) } }
    }
    item(key = "row27") { TriSwitch("Card shadow", "Drop shadow under the focused card", o.cardShadow, base.effects.cardShadow) { v -> set { it.copy(cardShadow = v) } } }
    item(key = "row28") { TriSwitch("Focus glow", "Soft coloured halo around the focused card", o.focusGlow, base.effects.focusGlow) { v -> set { it.copy(focusGlow = v) } } }
    item(key = "row29") { SettingRow("Reduce motion", description = "Turns off scaling, parallax, drift and crossfades", checked = settings.reduceMotion, onClick = { vm.update { it.copy(reduceMotion = !it.reduceMotion) } }) }

    // ---- Layout
    item(key = "row30") { SectionHeader("Layout") }
    item(key = "row31") {
        EnumSetting("Home layout", HomeLayout.entries, settings.homeLayout, { it.label }, { it.description }, themeHint = base.layout.homeLayout?.let { k -> HomeLayout.entries.firstOrNull { it.name.equals(k, ignoreCase = true) }?.label } ?: HomeLayout.RAILS.label) { v -> vm.update { it.copy(homeLayout = v) } }
    }
    item(key = "row32") {
        var open by remember { mutableStateOf(false) }
        val shown = HomeRail.entries.count { it !in settings.hiddenHomeRails }
        SettingRow("Home sections", description = "Which rows the Home shows, and in what order", value = "$shown of ${HomeRail.entries.size}", onClick = { open = true })
        if (open) HomeSectionsEditor(settings, vm, onClose = { open = false })
    }
    item(key = "row33") {
        EnumSetting("Systems view", LibraryLayout.entries, settings.libraryLayout, { it.label }, { it.description }, themeHint = LibraryLayout.fromKey(base.layout.libraryLayout).label) { v -> vm.update { it.copy(libraryLayout = v) } }
    }
    item(key = "row34") {
        EnumSetting("Library view", LibraryView.entries, settings.gameListView, { it.label }, { it.description }, description = "Also changeable with Start inside any game list", themeHint = LibraryView.fromKey(base.layout.libraryView).label) { v -> vm.update { it.copy(gameListView = v) } }
    }
    item(key = "row35") {
        EnumSetting("Tab bar", TabBarMode.entries, settings.tabBar, { it.label }, { it.description }, themeHint = if (base.layout.showTabs) "Always" else "Hidden on Home") { v -> vm.update { it.copy(tabBar = v) } }
    }

    // ---- Interface
    item(key = "row36") { SectionHeader("Interface") }
    item(key = "row37") {
        StepperSetting("Interface size", "Scales cards, type and spacing together", settings.uiScale, settings.uiScale != 1f, 1f, 0.8f..1.3f, 0.05f, ::pct, presets = listOf(0.85f, 0.92f, 1f, 1.1f, 1.2f)) { v -> vm.update { it.copy(uiScale = v ?: 1f) } }
    }
    item(key = "row38") {
        ChoiceSetting("Console icons", "Illustrations on system tiles", o.platformIconSet, base.platformIcons.set, iconSets.map { (k, l, d) -> ChoiceOption(k, l, d) }, onChange = { v -> set { it.copy(platformIconSet = v) } })
    }
    item(key = "row39") {
        StepperSetting("Screen margins", "Space between the content and the edges", eff.layout.screenPadding, o.screenPadding != null, base.layout.screenPadding, 12f..72f, 4f, { "${it.roundToInt()}" }, presets = listOf(24f, 40f, 56f)) { v -> set { it.copy(screenPadding = v) } }
    }
    item(key = "row40") { TriSwitch("Button hints", "Glyphs for L1/R1 and the hint bar at the bottom", o.showButtonHints, base.layout.showButtonHints) { v -> set { it.copy(showButtonHints = v) } } }
    item(key = "row41") { SettingRow("Show clock", checked = settings.showClock, onClick = { vm.update { it.copy(showClock = !it.showClock) } }) }
    item(key = "row42") { SettingRow("Show battery", checked = settings.showBattery, onClick = { vm.update { it.copy(showBattery = !it.showBattery) } }) }

    // ---- Sound
    item(key = "row43") { SectionHeader("Sound") }
    item(key = "row44") { SettingRow("Interface sounds", description = "Ticks on focus, chimes on confirm and back, a swell when a game launches", checked = settings.uiSounds, onClick = { vm.update { it.copy(uiSounds = !it.uiSounds) } }) }
    item(key = "row45") {
        StepperSetting("Sound volume", null, settings.uiSoundVolume, true, 0.5f, 0.1f..1f, 0.1f, ::pct, enabled = settings.uiSounds, presets = listOf(0.25f, 0.5f, 0.75f, 1f)) { v -> vm.update { it.copy(uiSoundVolume = v ?: 0.5f) } }
    }

    // ---- Theme files
    item(key = "row46") { SectionHeader("Theme files") }
    item(key = "row47") {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::importTheme) }
        SettingRow("Import theme file", description = "A JSON with the same schema as the bundled themes; it is copied into Android/data/io.vela.frontend/files/themes/", onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
    if (settings.customThemeJson != null) {
        item(key = "row48") { SettingRow("Remove custom theme", description = "Deletes the theme written by the old editor", onClick = vm::resetCustomTheme) }
    }
}

private fun countOverrides(o: AppearanceOverrides): Int = listOf(
    o.accent, o.accentSecondary, o.backgroundColor, o.dynamicAccent, o.backgroundMode, o.backgroundBlur, o.backgroundDim,
    o.backgroundSaturation, o.cardWidth, o.boxArtAspect, o.cardRadius, o.cardLabels, o.focusScale, o.gridColumns, o.displayFont,
    o.bodyFont, o.fontScale, o.glassPanels, o.panelAlpha, o.cardShadow, o.focusGlow, o.platformIconSet, o.showButtonHints, o.screenPadding,
).count { it != null }

// ---- Row kinds --------------------------------------------------------------------------------

private data class ChoiceOption(val key: String, val label: String, val description: String? = null)

/** Picker whose first option is always "Theme default"; a null [current] means the theme's value is in force. */
@Composable
private fun ChoiceSetting(
    title: String,
    description: String?,
    current: String?,
    themeDefault: String,
    options: List<ChoiceOption>,
    onChange: (String?) -> Unit,
    labelFor: (String) -> String = { key -> options.firstOrNull { it.key == key }?.label ?: key },
) {
    var picking by remember { mutableStateOf(false) }
    SettingRow(title, description = description, value = labelFor(current ?: themeDefault), subdued = current == null, onClick = { picking = true })
    if (picking) {
        VelaMenuDialog(
            title,
            listOf(MenuOption("__theme", "Theme default", description = labelFor(themeDefault), selected = current == null)) +
                options.map { MenuOption(it.key, it.label, description = it.description, selected = it.key == current) },
            onSelect = { opt -> onChange(if (opt.id == "__theme") null else opt.id); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** Picker over an enum setting that has no "theme" state of its own (Home layout, Tab bar…); [themeHint] names what THEME resolves to. */
@Composable
private fun <T : Enum<T>> EnumSetting(
    title: String,
    entries: List<T>,
    current: T,
    label: (T) -> String,
    describe: (T) -> String,
    description: String? = null,
    themeHint: String? = null,
    onChange: (T) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val isTheme = current.name == "THEME"
    val value = if (isTheme && themeHint != null) "Theme · $themeHint" else label(current)
    SettingRow(title, description = description ?: describe(current), value = value, subdued = isTheme, onClick = { picking = true })
    if (picking) {
        VelaMenuDialog(
            title,
            entries.map { MenuOption(it.name, label(it), description = if (it.name == "THEME" && themeHint != null) "Right now: $themeHint" else describe(it), selected = it == current) },
            onSelect = { opt -> onChange(entries.first { it.name == opt.id }); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** Switch over a Boolean override: toggling sets it explicitly; the description says what the theme wanted. */
@Composable
private fun TriSwitch(title: String, description: String, current: Boolean?, themeDefault: Boolean, onChange: (Boolean?) -> Unit) {
    val effective = current ?: themeDefault
    val note = if (current == null) description else "$description · theme: ${if (themeDefault) "on" else "off"}"
    SettingRow(title, description = note, checked = effective, onClick = { onChange(if (current == null) !themeDefault else if (!current == themeDefault) null else !current) })
}

/** Stepper plus a presets menu (with "Theme default") on press. */
@Composable
private fun StepperSetting(
    title: String,
    description: String?,
    value: Float,
    overridden: Boolean,
    themeDefault: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    enabled: Boolean = true,
    presets: List<Float>,
    onChange: (Float?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    StepperRow(
        title = title, description = description, value = value, range = range, step = step, format = format,
        overridden = overridden, enabled = enabled,
        onChange = { onChange(it) },
        onClick = { picking = true },
    )
    if (picking) {
        VelaMenuDialog(
            title,
            subtitle = "Left and right on the pad adjust it in place",
            options = listOf(MenuOption("__theme", "Theme default", description = format(themeDefault), selected = !overridden)) +
                presets.filter { it in range }.map { MenuOption(it.toString(), format(it), selected = overridden && it == value) },
            onSelect = { opt -> onChange(if (opt.id == "__theme") null else opt.id.toFloat()); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun SwatchSetting(title: String, description: String, swatches: List<Swatch>, current: String?, themeDefault: String, onChange: (String?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val colors = VelaTheme.colors
    val shown = current ?: themeDefault
    val name = swatches.firstOrNull { it.hex.equals(shown, ignoreCase = true) }?.name ?: if (current == null) "Theme" else "Custom"
    SettingRow(
        title, description = description, value = name, subdued = current == null, onClick = { picking = true },
        trailing = {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.fromArgbHex(shown))
                    .border(1.dp, colors.onBackground.copy(alpha = 0.3f), CircleShape),
            )
        },
    )
    if (picking) {
        SwatchPickerDialog(title, swatches, selectedHex = current, themeDefaultHex = themeDefault, subtitle = description, onSelect = { onChange(it); picking = false }, onDismiss = { picking = false })
    }
}

// ---- Theme carousel ---------------------------------------------------------------------------

/** One card per theme with a miniature of its look; the selected one is ticked. */
@Composable
private fun ThemeCarousel(themes: List<ThemeSpec>, selectedId: String, onSelect: (String) -> Unit) {
    Column {
        SectionHeader("Theme")
        LazyRow(
            Modifier.fillMaxWidth().focusRestorer().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(themes, key = { it.id }) { spec ->
                ThemePreviewCard(spec, selected = spec.id == selectedId, onClick = { onSelect(spec.id) })
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val ui = VelaTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by rememberFocusState(interactionSource)
    val bg = Color.fromArgbHex(spec.background.staticColor ?: spec.colors.background)
    val surface = Color.fromArgbHex(spec.colors.surfaceElevated)
    val accent = Color.fromArgbHex(spec.colors.accent)
    val accent2 = Color.fromArgbHex(spec.colors.accentSecondary)
    val text = Color.fromArgbHex(spec.colors.onBackground)
    val cardRadius = (spec.shapes.cardRadius / 3f).dp
    val layoutName = spec.layout.homeLayout?.replaceFirstChar { it.uppercase() } ?: "Rails"

    Column(Modifier.width(172.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .velaFocusable(shape, interactionSource, onClick, edge = true)
                .clip(shape)
                .background(bg)
                .background(Brush.radialGradient(listOf(accent2.copy(alpha = 0.35f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(300f, 40f), radius = 260f))
                .border(if (selected) 2.dp else 0.dp, if (selected) ui.accent else Color.Transparent, shape)
                .padding(10.dp),
        ) {
            // Chrome: a row of tab pills when the theme keeps tabs, a single mark otherwise.
            Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                if (spec.layout.showTabs) {
                    repeat(3) { i -> Box(Modifier.width(if (i == 0) 22.dp else 14.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(text.copy(alpha = if (i == 0) 0.8f else 0.35f))) }
                }
            }
            // Three mini cards in the theme's proportions; the first carries the focus ring.
            Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .width(if (i == 0) 34.dp else 30.dp)
                            .aspectRatio(spec.layout.boxArtAspect.coerceIn(0.5f, 1.8f))
                            .clip(RoundedCornerShape(cardRadius))
                            .background(if (i == 0) accent.copy(alpha = 0.85f) else surface)
                            .border(if (i == 0) 1.5.dp else 0.dp, if (i == 0) Color.fromArgbHex(spec.colors.focusRing) else Color.Transparent, RoundedCornerShape(cardRadius)),
                    )
                }
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(ui.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = ui.background, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(spec.name, style = VelaTheme.typography.bodyStrong, color = if (selected || focused) ui.onBackground else ui.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Text(
            listOfNotNull(layoutName, if (!spec.layout.showTabs) "no tabs" else null, spec.author.takeIf { it.isNotBlank() }?.substringBefore(" · ")).joinToString(" · "),
            style = VelaTheme.typography.caption, color = ui.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
