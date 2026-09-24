package dev.kesav.redline.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A pressed control sinks to 97 percent the moment the finger lands and comes back on
 * release. Answering on press rather than on release is what makes a control feel attached
 * to the finger; critically damped, so a tap never wobbles. The ripple still runs on top.
 * Pass the same [source] to the control's `interactionSource`.
 */
@Composable
internal fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = motion(RedlineMotion.effectsFast()),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
