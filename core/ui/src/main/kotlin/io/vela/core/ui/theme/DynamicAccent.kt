package io.vela.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accent taken from the artwork of whatever is focused (its dominant vivid colour), so glows,
 * focus rings and progress bars follow the game rather than the theme. Null falls back to the
 * theme accent.
 */
val LocalDynamicAccent = compositionLocalOf<Color?> { null }

/** The accent to use right now: the artwork's when there is one, the theme's otherwise. */
val VelaTheme.liveAccent: Color
    @Composable @ReadOnlyComposable get() = LocalDynamicAccent.current ?: colors.accent
