package io.vela.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accent taken from the artwork of whatever is focused (its dominant vivid colour), so glows,
 * focus rings and progress bars follow the game rather than the theme. Null falls back to the
 * theme accent.
 */
val LocalDynamicAccent = compositionLocalOf<Color?> { null }

/**
 * The focused game's colour, animated, for themes with `artworkTint`: the background wash and the
 * focus halo and shadow follow it. A [State] read only in draw lambdas, so a colour change repaints
 * without recomposing anything. Null when the theme does not tint.
 */
val LocalStageTint = staticCompositionLocalOf<State<Color>?> { null }

/** The accent to use right now: the artwork's when there is one, the theme's otherwise. */
val VelaTheme.liveAccent: Color
    @Composable @ReadOnlyComposable get() = LocalDynamicAccent.current ?: colors.accent

/**
 * A metadata line from parts separated by three spaces ("SNES   1995   12 games"). Plain words with
 * room between them, or spaced capitals with dots for themes that set `allCapsLabels`.
 */
@Composable
@ReadOnlyComposable
fun metaLine(parts: String): String {
    val items = parts.split("   ").filter { it.isNotBlank() }
    return if (VelaTheme.spec.typography.allCapsLabels) items.joinToString("  ·  ").uppercase() else items.joinToString("     ")
}
