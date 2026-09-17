package io.vela.core.model

import kotlinx.serialization.Serializable

/**
 * Serializable theme description. The UI layer maps this to Compose values; nothing visual is
 * hardcoded in screens. Ships as JSON (`themes/<name>.json`) and can be overridden by user files.
 * Colours are ARGB hex strings like `#FF0E1424` to keep the JSON readable.
 */
@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val author: String = "",
    val colors: ThemeColors = ThemeColors(),
    val typography: ThemeTypography = ThemeTypography(),
    val shapes: ThemeShapes = ThemeShapes(),
    val layout: ThemeLayout = ThemeLayout(),
    val motion: ThemeMotion = ThemeMotion(),
    val background: ThemeBackground = ThemeBackground(),
    val effects: ThemeEffects = ThemeEffects(),
)

@Serializable
data class ThemeColors(
    val background: String = "#FF07090F",
    val surface: String = "#FF10141F",
    val surfaceElevated: String = "#FF1A2030",
    val accent: String = "#FF7FD7FF",
    val accentSecondary: String = "#FF3D7BFF",
    val onBackground: String = "#FFF2F5FA",
    val onSurface: String = "#FFE6EAF2",
    val muted: String = "#FF8A94A8",
    val focusRing: String = "#FFFFFFFF",
    val danger: String = "#FFFF5C6C",
    val success: String = "#FF5CE0A8",
    /** Scrim drawn over the dynamic background so text stays readable. */
    val scrim: String = "#99050710",
)

@Serializable
data class ThemeTypography(
    /** Font family key resolved by the UI (`outfit`, `manrope`, `system`). */
    val displayFamily: String = "outfit",
    val bodyFamily: String = "manrope",
    val displaySize: Float = 40f,
    val titleSize: Float = 24f,
    val headlineSize: Float = 18f,
    val bodySize: Float = 15f,
    val labelSize: Float = 12f,
    val letterSpacingDisplay: Float = -0.02f,
    val allCapsLabels: Boolean = true,
)

@Serializable
data class ThemeShapes(
    val cardRadius: Float = 14f,
    val tileRadius: Float = 18f,
    val chipRadius: Float = 999f,
    val focusBorderWidth: Float = 3f,
)

@Serializable
data class ThemeLayout(
    /** Portrait box-art card width in dp at 1x. */
    val cardWidth: Float = 150f,
    /** Width/height of portrait box art. */
    val boxArtAspect: Float = 0.72f,
    val heroAspect: Float = 1.78f,
    val railSpacing: Float = 12f,
    val sectionSpacing: Float = 28f,
    val screenPadding: Float = 40f,
    val gridColumns: Int = 6,
    val showButtonHints: Boolean = true,
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
)

@Serializable
data class ThemeMotion(
    val focusScale: Float = 1.08f,
    val focusDurationMs: Int = 180,
    val transitionDurationMs: Int = 320,
    val backgroundCrossfadeMs: Int = 600,
    val parallax: Boolean = true,
    val reduceMotion: Boolean = false,
)

@Serializable
data class ThemeBackground(
    /** `artwork` (focused game), `platform` (platform colour gradient), `static`. */
    val mode: String = "artwork",
    val blurRadius: Float = 40f,
    val dim: Float = 0.55f,
    val saturation: Float = 1.1f,
    val staticColor: String? = null,
)

@Serializable
data class ThemeEffects(
    val glassPanels: Boolean = true,
    val panelAlpha: Float = 0.62f,
    val cardShadow: Boolean = true,
    val focusGlow: Boolean = true,
    val videoPreviews: Boolean = true,
    val videoPreviewDelayMs: Int = 1200,
)
