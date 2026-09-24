package dev.kesav.redline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The first thing on the first screen: a lease, in three dimensions, being read.
 *
 * Three pages stand in a shallow stack, tilted toward the reader. A red scan line runs down the
 * front page and each clause it passes that would cost money is marked behind it, in red or
 * amber, the way the report will mark the reader's own. Two small cards float in front of the
 * stack, the score and the money, nearer the eye than the paper, so they drift further when
 * the stack tilts. A finger drags the stack round; let go and it springs back to rest.
 *
 * It is drawn, not rendered: graphicsLayer rotations with a camera distance, and Canvas lines.
 * No 3D engine, no asset file, and no frame is spent when the screen is not showing. With
 * animations turned off it is one still frame with the marks already made.
 */
@Composable
internal fun LeaseHero(modifier: Modifier = Modifier) {
    val hero = LocalHero.current
    val colors = risk
    val moving = animationsEnabled()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val loop = rememberInfiniteTransition(label = "hero")
    val beamState = if (moving) {
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "beam")
    } else remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val driftState = if (moving) {
        loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(5200, easing = RedlineMotion.Decelerate), RepeatMode.Reverse), label = "drift")
    } else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val beam = beamState.value
    val drift = driftState.value

    // The finger's tilt, in degrees, sprung back to zero on release.
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }

    val restX = 16f
    val restY = -14f + drift * 5f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
            .clearAndSetSemantics { }
            .pointerInput(moving) {
                if (!moving) return@pointerInput
                detectDragGestures(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragEnd = {
                        scope.launch { tiltX.animateTo(0f, RedlineMotion.spatialBouncy()) }
                        scope.launch { tiltY.animateTo(0f, RedlineMotion.spatialBouncy()) }
                    },
                    onDragCancel = {
                        scope.launch { tiltX.animateTo(0f, RedlineMotion.spatialBouncy()) }
                        scope.launch { tiltY.animateTo(0f, RedlineMotion.spatialBouncy()) }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.18f).coerceIn(-28f, 28f)) }
                    scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.18f).coerceIn(-22f, 22f)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val pageW = minOf(maxWidth * 0.52f, 190.dp)
        val pageH = pageW * 1.30f
        val rx = restX + tiltX.value
        val ry = restY + tiltY.value

        // Back to front. Each page sits a little higher and to the side, so the stack reads
        // as depth rather than as three copies.
        for (depth in 2 downTo 0) {
            Box(
                Modifier
                    .size(pageW, pageH)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationX = rx
                        rotationY = ry
                        rotationZ = -4f + depth * 3.5f
                        translationX = (depth * 14).dp.toPx() + ry * 0.6f * depth
                        translationY = -(depth * 12).dp.toPx()
                        scaleX = 1f - depth * 0.05f
                        scaleY = scaleX
                        alpha = 1f - depth * 0.22f
                        transformOrigin = TransformOrigin.Center
                    }
                    .shadow(if (depth == 0) 24.dp else 8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFBFBFA)),
            ) {
                Page(front = depth == 0, beam = beam, high = colors.high, medium = colors.medium)
            }
        }

        // The two cards in front of the paper, nearer the eye, so they move further.
        FloatChip(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 18.dp)
                .graphicsLayer {
                    translationX = ry * 2.2f
                    translationY = -rx * 1.6f + drift * 3.dp.toPx()
                },
            dot = colors.high,
            label = "Risk 72",
            detail = "Toxic clauses",
            container = hero.container,
            content = hero.content,
            muted = hero.muted,
        )
        FloatChip(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 22.dp)
                .graphicsLayer {
                    translationX = ry * 2.8f
                    translationY = -rx * 2.0f - drift * 3.dp.toPx()
                },
            dot = colors.medium,
            label = "3 months",
            detail = "deposit kept",
            container = hero.container,
            content = hero.content,
            muted = hero.muted,
        )
    }
}

/**
 * One page: a heading bar, then lines of text as grey bars. On the front page the scan line
 * runs top to bottom, and the lines it has passed that belong to a costly clause get their
 * marker behind them.
 */
@Composable
private fun Page(front: Boolean, beam: Float, high: Color, medium: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val pad = size.width * 0.11f
        val line = size.height * 0.052f
        val gap = size.height * 0.034f
        val ink = Color(0xFF1B1D22)
        drawRoundRect(ink.copy(alpha = 0.85f), Offset(pad, pad), Size(size.width * 0.46f, line * 1.1f), CornerRadius(line))

        val widths = floatArrayOf(1f, 0.92f, 0.97f, 0.6f, 1f, 0.88f, 0.95f, 0.7f, 1f, 0.9f, 0.55f)
        // Which lines are part of a flagged clause, and how serious.
        val marks = mapOf(1 to high, 2 to high, 5 to medium, 8 to high, 9 to high)
        var y = pad + line * 2.4f
        val usable = size.width - pad * 2
        val beamY = pad + (size.height - pad * 2) * beam
        widths.forEachIndexed { i, w ->
            val barH = line * 0.62f
            val mark = marks[i]
            if (front && mark != null) {
                // The marker grows in from the left once the beam is past the line.
                val passed = ((beamY - y) / (line * 2.2f)).coerceIn(0f, 1f)
                if (passed > 0f) {
                    drawRoundRect(
                        mark.copy(alpha = 0.28f),
                        Offset(pad - line * 0.2f, y - line * 0.32f),
                        Size((usable * w + line * 0.4f) * passed, barH + line * 0.64f),
                        CornerRadius(line * 0.3f),
                    )
                }
            }
            drawRoundRect(ink.copy(alpha = if (mark != null && front) 0.55f else 0.16f), Offset(pad, y), Size(usable * w, barH), CornerRadius(barH / 2))
            y += barH + gap
        }
        if (front && beam < 0.995f) {
            // The scan line, with a glow trailing up behind it.
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, high.copy(alpha = 0.18f)), startY = beamY - line * 3f, endY = beamY),
                topLeft = Offset(0f, beamY - line * 3f),
                size = Size(size.width, line * 3f),
            )
            drawRoundRect(high, Offset(pad * 0.4f, beamY - 1.5f * density), Size(size.width - pad * 0.8f, 3f * density), CornerRadius(3f * density))
        }
        // A left margin rule, the red line the app is named for.
        drawLine(high.copy(alpha = 0.55f), Offset(pad * 0.55f, pad), Offset(pad * 0.55f, size.height - pad), strokeWidth = 1.2f * density)
    }
}

@Composable
private fun FloatChip(
    modifier: Modifier,
    dot: Color,
    label: String,
    detail: String,
    container: Color,
    content: Color,
    muted: Color,
) {
    Row(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(container.copy(alpha = 0.94f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        androidx.compose.foundation.layout.Column {
            Text(label, style = FigureStyle.copy(fontSize = 15.sp, lineHeight = 18.sp), color = content)
            Text(detail, style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.8.sp), color = muted)
        }
    }
}
