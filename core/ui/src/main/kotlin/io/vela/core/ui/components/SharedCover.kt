package io.vela.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import io.vela.core.ui.theme.VelaTheme

/** The app's shared-transition layout, around the navigation host. Null where there is none (previews, tests). */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit scope of the navigation destination being composed. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** True when a game's cover will fly between screens instead of fading with them. */
val sharedCoversEnabled: Boolean
    @Composable @ReadOnlyComposable get() =
        LocalSharedTransitionScope.current != null && LocalNavAnimatedScope.current != null && !VelaTheme.motion.reduceMotion

/**
 * Marks a game's cover as the same object on every screen: opening a game's details flies its
 * card into the detail's box art, and going back flies it home. A no-op without a shared
 * layout or under Reduce motion, so cards work anywhere.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(gameId: Long, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    if (VelaTheme.motion.reduceMotion) return this
    return with(shared) {
        this@sharedCover.sharedElement(
            sharedContentState = rememberSharedContentState("cover-$gameId"),
            animatedVisibilityScope = scope,
            boundsTransform = CoverBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** The cover travels on the expressive default spatial spring: quick, with a whisper of overshoot. */
private val CoverBounds = BoundsTransform { _: Rect, _: Rect -> VelaSprings.spatial() }
