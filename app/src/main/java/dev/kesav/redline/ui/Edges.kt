package dev.kesav.redline.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Content scrolled up past the top of a scrolling region dissolves over [height] instead of
 * being cut by a hard line under the header or the status bar.
 *
 * [scrolled] is how far the content has moved, in pixels. The fade deepens over the first
 * [height] of scrolling, so the page at rest keeps its first line fully drawn and the edge
 * never switches on with a jump. Goes on the scrolling node itself, ahead of any scroll
 * modifier, so the mask stays put while the content moves under it.
 */
internal fun Modifier.fadeTopEdge(height: Dp = 16.dp, scrolled: () -> Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = height.toPx()
            val depth = (scrolled() / h).coerceIn(0f, 1f)
            if (depth > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - depth),
                        1f to Color.Black,
                        startY = 0f,
                        endY = h,
                    ),
                    size = Size(size.width, h),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
