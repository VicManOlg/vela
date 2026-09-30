package io.vela.core.model

import kotlinx.serialization.Serializable

/** When a game card prints its title over the art. */
@Serializable
enum class CardLabels(val key: String, val label: String, val description: String) {
    FOCUSED("focused", "While focused", "The title slides in on the card you are on"),
    ALWAYS("always", "Always", "Every card shows its title along the bottom"),
    NEVER("never", "Never", "Only the art; titles stay in the details"),
    ;

    companion object {
        fun fromKey(key: String?): CardLabels = entries.firstOrNull { it.key == key } ?: FOCUSED
    }
}

/**
 * The user's own appearance choices, layered over whatever theme is active. Every field is
 * optional: `null` means "use the theme's value", so switching themes keeps the tweaks and a
 * single reset returns the whole look to the theme. Applied by [ThemeSpec.applying].
 *
 * Only add fields with a `null`/neutral default; never rename a serialized name.
 */
@Serializable
data class AppearanceOverrides(
    // Colours (ARGB hex like the theme JSON)
    val accent: String? = null,
    val accentSecondary: String? = null,
    val backgroundColor: String? = null,
    /** Glows and rings take the focused game's colour instead of the accent. */
    val dynamicAccent: Boolean? = null,
    // Background scene
    val backgroundMode: String? = null,
    val backgroundBlur: Float? = null,
    val backgroundDim: Float? = null,
    val backgroundSaturation: Float? = null,
    // Cards
    val cardWidth: Float? = null,
    val boxArtAspect: Float? = null,
    val cardRadius: Float? = null,
    val cardLabels: CardLabels? = null,
    val focusScale: Float? = null,
    val gridColumns: Int? = null,
    // Text
    val displayFont: String? = null,
    val bodyFont: String? = null,
    /** Multiplies every type size; the interface size setting scales cards and spacing too. */
    val fontScale: Float? = null,
    // Effects
    val glassPanels: Boolean? = null,
    val panelAlpha: Float? = null,
    val cardShadow: Boolean? = null,
    val focusGlow: Boolean? = null,
    // Console icons
    val platformIconSet: String? = null,
    // Chrome
    val showButtonHints: Boolean? = null,
    val screenPadding: Float? = null,
) {
    /** True when at least one value differs from the theme. */
    val isEmpty: Boolean
        get() = this == AppearanceOverrides()
}

/** Corner radius of tiles and panels follows the card radius, as the bundled themes do. */
private fun tileRadiusFor(cardRadius: Float): Float = if (cardRadius <= 4f) cardRadius else cardRadius + 4f

/**
 * The theme with the user's overrides on top. Pure and total: a value the theme does not
 * understand (an unknown font key, an out-of-range dim) is passed through and clamped by the UI,
 * never rejected here.
 */
fun ThemeSpec.applying(o: AppearanceOverrides): ThemeSpec {
    if (o.isEmpty) return this
    val fontScale = o.fontScale ?: 1f
    return copy(
        colors = colors.copy(
            accent = o.accent ?: colors.accent,
            accentSecondary = o.accentSecondary ?: colors.accentSecondary,
            background = o.backgroundColor ?: colors.background,
        ),
        typography = typography.copy(
            displayFamily = o.displayFont ?: typography.displayFamily,
            bodyFamily = o.bodyFont ?: typography.bodyFamily,
            displaySize = typography.displaySize * fontScale,
            titleSize = typography.titleSize * fontScale,
            headlineSize = typography.headlineSize * fontScale,
            bodySize = typography.bodySize * fontScale,
            labelSize = typography.labelSize * fontScale,
        ),
        shapes = if (o.cardRadius == null) shapes else shapes.copy(cardRadius = o.cardRadius, tileRadius = tileRadiusFor(o.cardRadius)),
        layout = layout.copy(
            cardWidth = o.cardWidth ?: layout.cardWidth,
            boxArtAspect = o.boxArtAspect ?: layout.boxArtAspect,
            gridColumns = o.gridColumns ?: layout.gridColumns,
            screenPadding = o.screenPadding ?: layout.screenPadding,
            showButtonHints = o.showButtonHints ?: layout.showButtonHints,
            cardLabels = o.cardLabels?.key ?: layout.cardLabels,
        ),
        motion = motion.copy(focusScale = o.focusScale ?: motion.focusScale),
        background = background.copy(
            mode = o.backgroundMode ?: background.mode,
            blurRadius = o.backgroundBlur ?: background.blurRadius,
            dim = o.backgroundDim ?: background.dim,
            saturation = o.backgroundSaturation ?: background.saturation,
            // A user background colour must also paint the static/hero base, or the theme's shows through.
            staticColor = if (o.backgroundColor != null) o.backgroundColor else background.staticColor,
        ),
        effects = effects.copy(
            glassPanels = o.glassPanels ?: effects.glassPanels,
            panelAlpha = o.panelAlpha ?: effects.panelAlpha,
            cardShadow = o.cardShadow ?: effects.cardShadow,
            focusGlow = o.focusGlow ?: effects.focusGlow,
            dynamicAccent = o.dynamicAccent ?: effects.dynamicAccent,
        ),
        platformIcons = when (o.platformIconSet) {
            null -> platformIcons
            // Monochrome glyphs are white; tinting them with the text colour is what every bundled theme does.
            "monochrome" -> platformIcons.copy(set = "monochrome", tint = true)
            else -> platformIcons.copy(set = o.platformIconSet, tint = false)
        },
    )
}
