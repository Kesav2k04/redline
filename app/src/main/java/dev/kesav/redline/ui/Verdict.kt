package dev.kesav.redline.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The scopes the scan transition runs in, handed down without threading them through every
 * screen's parameters. Null outside the transition, which is how the screenshot tests and
 * any preview render the screens: as plain layouts with nothing shared.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The Scan button and the verdict panel are one object seen twice.
 *
 * Tapping Scan grows the red button into the dark panel at the top of the report, so the
 * answer arrives out of the tap instead of from nowhere. `sharedBounds` rather than
 * `sharedElement`, because the two ends are different content: the red face crossfades into
 * ink while the bounds travel.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.verdict(): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val visibility = LocalVisibilityScope.current ?: return this
    if (!animationsEnabled()) return this
    val shape = MaterialTheme.shapes.extraLarge
    return with(shared) {
        this@verdict.sharedBounds(
            sharedContentState = rememberSharedContentState("verdict"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> RedlineMotion.spatialExpressive() },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
