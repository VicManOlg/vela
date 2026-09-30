package io.vela.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vela.core.model.ArgbHexSerializer
import io.vela.core.model.ThemeSpec

/** Resolved, Compose-ready theme values derived from a [ThemeSpec]. Screens read only this. */
@Immutable
data class VelaColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val muted: Color,
    val focusRing: Color,
    val danger: Color,
    val success: Color,
    val scrim: Color,
)

@Immutable
data class VelaTypography(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Small spaced capitals for system/maker lines: "NINTENDO · 1990 · 12 GAMES". */
    val overline: TextStyle,
)

@Immutable
data class VelaShapes(
    val card: Shape,
    val tile: Shape,
    val chip: Shape,
    val panel: Shape,
    val focusBorderWidth: Dp,
)

@Immutable
data class VelaDimens(
    val cardWidth: Dp,
    val boxArtAspect: Float,
    val heroAspect: Float,
    val railSpacing: Dp,
    val sectionSpacing: Dp,
    val screenPadding: Dp,
    val gridColumns: Int,
    val showButtonHints: Boolean,
    val showClock: Boolean,
    val showBattery: Boolean,
    /** `focused`, `always` or `never`: when game cards print their title. */
    val cardLabels: String,
)

@Immutable
data class VelaMotion(
    val focusScale: Float,
    val focusDurationMs: Int,
    val transitionDurationMs: Int,
    val backgroundCrossfadeMs: Int,
    val parallax: Boolean,
    val reduceMotion: Boolean,
)

@Immutable
data class VelaBackgroundStyle(
    val mode: String,
    val blurRadius: Dp,
    val dim: Float,
    val saturation: Float,
    val staticColor: Color?,
)

@Immutable
data class VelaEffects(
    val glassPanels: Boolean,
    val panelAlpha: Float,
    val cardShadow: Boolean,
    val focusGlow: Boolean,
    val videoPreviews: Boolean,
    val videoPreviewDelayMs: Int,
    val dynamicAccent: Boolean,
)

@Immutable
data class VelaPlatformIcons(
    val set: String,
    val tint: Boolean,
    val alpha: Float,
)

@Immutable
data class VelaThemeValues(
    val spec: ThemeSpec,
    val colors: VelaColors,
    val typography: VelaTypography,
    val shapes: VelaShapes,
    val dimens: VelaDimens,
    val motion: VelaMotion,
    val background: VelaBackgroundStyle,
    val effects: VelaEffects,
    val platformIcons: VelaPlatformIcons,
)

val LocalVelaTheme = staticCompositionLocalOf<VelaThemeValues> { resolveTheme(ThemeSpec(id = "fallback", name = "Fallback")) }

/** Accessor used by every screen: `VelaTheme.colors.accent`, `VelaTheme.typography.title`... */
object VelaTheme {
    val spec: ThemeSpec @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.spec
    val colors: VelaColors @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.colors
    val typography: VelaTypography @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.typography
    val shapes: VelaShapes @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.shapes
    val dimens: VelaDimens @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.dimens
    val motion: VelaMotion @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.motion
    val background: VelaBackgroundStyle @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.background
    val effects: VelaEffects @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.effects
    val platformIcons: VelaPlatformIcons @Composable @ReadOnlyComposable get() = LocalVelaTheme.current.platformIcons
}

/** Height the theme's default sizes were designed for (a 1080p 16:9 handheld at ~2x density). */
private const val REFERENCE_HEIGHT_DP = 540f

@Composable
fun VelaTheme(
    spec: ThemeSpec,
    uiScale: Float = 1f,
    reduceMotion: Boolean = false,
    showClock: Boolean = true,
    showBattery: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Fit-to-screen: shorter screens get proportionally smaller cards and type so rails still fit.
    val heightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val fit = (heightDp / REFERENCE_HEIGHT_DP).coerceIn(0.72f, 1.15f)
    val effectiveScale = uiScale * fit
    val values = remember(spec, effectiveScale, reduceMotion, showClock, showBattery) { resolveTheme(spec, effectiveScale, reduceMotion, showClock, showBattery) }
    val scheme = remember(values) { values.materialScheme() }
    CompositionLocalProvider(LocalVelaTheme provides values) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun Color.Companion.fromArgbHex(hex: String): Color = Color(ArgbHexSerializer.parse(hex))

fun resolveTheme(spec: ThemeSpec, uiScale: Float = 1f, reduceMotion: Boolean = false, showClock: Boolean = true, showBattery: Boolean = true): VelaThemeValues {
    val c = spec.colors
    val colors = VelaColors(
        background = Color.fromArgbHex(c.background),
        surface = Color.fromArgbHex(c.surface),
        surfaceElevated = Color.fromArgbHex(c.surfaceElevated),
        accent = Color.fromArgbHex(c.accent),
        accentSecondary = Color.fromArgbHex(c.accentSecondary),
        onBackground = Color.fromArgbHex(c.onBackground),
        onSurface = Color.fromArgbHex(c.onSurface),
        muted = Color.fromArgbHex(c.muted),
        focusRing = Color.fromArgbHex(c.focusRing),
        danger = Color.fromArgbHex(c.danger),
        success = Color.fromArgbHex(c.success),
        scrim = Color.fromArgbHex(c.scrim),
    )
    val t = spec.typography
    val display = VelaFonts.family(t.displayFamily)
    val body = VelaFonts.family(t.bodyFamily)
    val s = uiScale
    val typography = VelaTypography(
        display = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = (t.displaySize * s).sp, lineHeight = (t.displaySize * s * 1.08f).sp, letterSpacing = (t.letterSpacingDisplay * t.displaySize).sp),
        title = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = (t.titleSize * s).sp, lineHeight = (t.titleSize * s * 1.2f).sp, letterSpacing = (-0.01f * t.titleSize).sp),
        headline = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = (t.headlineSize * s).sp, lineHeight = (t.headlineSize * s * 1.3f).sp),
        body = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = (t.bodySize * s).sp, lineHeight = (t.bodySize * s * 1.5f).sp),
        bodyStrong = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = (t.bodySize * s).sp, lineHeight = (t.bodySize * s * 1.5f).sp),
        label = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = (t.labelSize * s).sp, lineHeight = (t.labelSize * s * 1.4f).sp, letterSpacing = 0.2.sp),
        caption = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = (t.labelSize * s * 0.92f).sp, lineHeight = (t.labelSize * s * 1.35f).sp),
        overline = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = (t.labelSize * s * 0.86f).sp, lineHeight = (t.labelSize * s * 1.3f).sp, letterSpacing = 1.6.sp),
    )
    val sh = spec.shapes
    val shapes = VelaShapes(
        card = RoundedCornerShape(sh.cardRadius.dp),
        tile = RoundedCornerShape(sh.tileRadius.dp),
        chip = RoundedCornerShape(sh.chipRadius.coerceAtMost(200f).dp),
        panel = RoundedCornerShape((sh.tileRadius + 4f).dp),
        focusBorderWidth = sh.focusBorderWidth.dp,
    )
    val l = spec.layout
    val dimens = VelaDimens(
        cardWidth = (l.cardWidth * s).dp,
        boxArtAspect = l.boxArtAspect,
        heroAspect = l.heroAspect,
        railSpacing = (l.railSpacing * s).dp,
        sectionSpacing = (l.sectionSpacing * s).dp,
        screenPadding = (l.screenPadding * s).dp,
        gridColumns = l.gridColumns,
        showButtonHints = l.showButtonHints,
        // The theme proposes, the user's Settings switch disposes.
        showClock = l.showClock && showClock,
        showBattery = l.showBattery && showBattery,
        cardLabels = l.cardLabels,
    )
    val m = spec.motion
    val reduce = reduceMotion || m.reduceMotion
    val motion = VelaMotion(
        focusScale = if (reduce) 1f else m.focusScale,
        focusDurationMs = if (reduce) 0 else m.focusDurationMs,
        transitionDurationMs = if (reduce) 0 else m.transitionDurationMs,
        backgroundCrossfadeMs = if (reduce) 0 else m.backgroundCrossfadeMs,
        parallax = m.parallax && !reduce,
        reduceMotion = reduce,
    )
    val b = spec.background
    val background = VelaBackgroundStyle(
        mode = b.mode,
        blurRadius = b.blurRadius.dp,
        dim = b.dim,
        saturation = b.saturation,
        staticColor = b.staticColor?.let { Color.fromArgbHex(it) },
    )
    val e = spec.effects
    val effects = VelaEffects(e.glassPanels, e.panelAlpha, e.cardShadow, e.focusGlow, e.videoPreviews, e.videoPreviewDelayMs, e.dynamicAccent)
    val pi = spec.platformIcons
    val platformIcons = VelaPlatformIcons(pi.set, pi.tint, pi.alpha.coerceIn(0f, 1f))
    return VelaThemeValues(spec, colors, typography, shapes, dimens, motion, background, effects, platformIcons)
}

private fun VelaThemeValues.materialScheme(): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.background,
    secondary = colors.accentSecondary,
    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onSurface,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.muted,
    error = colors.danger,
    outline = colors.muted,
)
