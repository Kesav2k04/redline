package dev.kesav.redline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Category
import dev.kesav.redline.CategoryScore
import dev.kesav.redline.Tier
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The icon each category of risk is drawn with, everywhere it appears. */
internal val Category.icon: ImageVector
    get() = when (this) {
        Category.MONEY -> RedlineIcons.Wallet
        Category.ENTRY -> RedlineIcons.Key
        Category.EXIT -> RedlineIcons.Exit
        Category.UPKEEP -> RedlineIcons.Roller
    }

/** One line under each category name, saying what sits in it. */
internal val Category.blurb: String
    get() = when (this) {
        Category.MONEY -> "Deposits, fees, rent rises"
        Category.ENTRY -> "Visits, notice, who may enter"
        Category.EXIT -> "Lock-ins, penalties, renewals"
        Category.UPKEEP -> "Repairs, repainting, cleaning"
    }

/**
 * A sum of money the way the lease wrote it: the symbol it used and the grouping its readers
 * expect, so rupees group in lakhs and dollars in thousands. A blank symbol means the lease
 * named no currency, and the number stands alone rather than borrowing one.
 */
internal fun money(symbol: String, amount: Long): String {
    val locale = when (symbol) {
        "₹", "Rs", "Rs." -> Locale.forLanguageTag("en-IN")
        "£" -> Locale.UK
        else -> Locale.US
    }
    val digits = NumberFormat.getIntegerInstance(locale).format(amount)
    return if (symbol.isBlank()) digits else "$symbol$digits"
}

/**
 * A card: one step above the page, one hairline round it. Every grouped thing in the app sits
 * on one of these, so the eye learns the shape once.
 */
@Composable
internal fun KitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    padding: Dp = Space.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(padding), content = content)
    }
    if (onClick != null) {
        val haptics = LocalHapticFeedback.current
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
            modifier = modifier.pressScale(source),
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border?.let { BorderStroke(1.dp, it) },
            interactionSource = source,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border?.let { BorderStroke(1.dp, it) },
            content = body,
        )
    }
}

/** An icon on a rounded square of its own tint, so a glyph has a ground to sit on. */
@Composable
internal fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = tint.copy(alpha = 0.12f),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small uppercase label above a figure. */
@Composable
internal fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = EyebrowStyle,
) {
    Text(text.uppercase(), style = style, color = color, modifier = modifier)
}

/**
 * A whole number that counts up to [target] the first time it is shown, and moves from its
 * current value on any later change. Spoken as the final value throughout, so a screen reader
 * never reads out the digits in flight.
 */
@Composable
internal fun CountUp(
    target: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.toString() },
    durationScale: Float = 1f,
) {
    val animate = animationsEnabled()
    val value = remember { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        value.animateTo(
            target.toFloat(),
            androidx.compose.animation.core.tween(
                durationMillis = (900 * durationScale).roundToInt(),
                easing = RedlineMotion.Decelerate,
            ),
        )
    }
    Text(
        text = format(value.value.roundToInt()),
        style = style,
        color = color,
        modifier = modifier.clearAndSetSemantics { contentDescription = format(target) },
    )
}

/** The eyebrows on the report card: 12 sp where the rest of the app's eyebrows are 11. */
internal val HeroEyebrowStyle = EyebrowStyle.copy(fontSize = 12.sp, lineHeight = 16.sp)

/**
 * The dial's geometry, in dp at the reference box of 248 by 196. Every length scales by the
 * box's width over 248, so a narrow phone gets the same instrument, only smaller.
 */
private object Dial {
    const val START = 150f
    const val SWEEP = 240f
    val W = 248.dp
    val H = 196.dp
    val CX = 124.dp
    val CY = 124.dp
    val BAND_R = 104.dp
    val BAND_W = 10.dp
    val TICK_OUT = 124.dp
    val MINOR_LEN = 6.dp
    val MAJOR_LEN = 10.dp
    val MINOR_W = 1.dp
    val MAJOR_W = 1.5.dp
    val KNOCK_IN = 97.dp
    val KNOCK_OUT = 111.dp
    val KNOCK_W = 6.dp
    val INDEX_IN = 92.dp
    val INDEX_OUT = 114.dp
    val INDEX_W = 2.dp
    val ZONE_GAP = 2.dp

    /** Angle for a score of 0 to 100, in drawArc's convention: 0 is 3 o'clock, clockwise. */
    fun angle(points: Float) = START + SWEEP * points / 100f
}

/**
 * The risk score, as an instrument dial.
 *
 * A 240 degree scale of fixed ticks, a zone track that shows where Caution and Toxic begin, one
 * solid band whose length is exactly the score, and a thin index at its end. The band takes the
 * colour of the tier of the value shown, so it turns amber as the index passes 30 and crimson
 * as it passes 60. It sweeps once, slowing into the reading and never overshooting, longer for a
 * higher score, and the phone gives one firm tap as it lands. [start] holds the sweep back until
 * the panel around the dial has settled. Reduced motion shows the final frame at once.
 */
@Composable
internal fun RiskGauge(
    score: Int,
    tier: Tier,
    modifier: Modifier = Modifier,
    start: Boolean = true,
) {
    val hero = LocalHero.current
    val animate = animationsEnabled()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val sweep = remember { Animatable(if (animate) 0f else score / 100f) }
    val labelAlpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(score, start) {
        if (!start) return@LaunchedEffect
        if (animate) {
            sweep.animateTo(score / 100f, tween(500 + 6 * score, easing = RedlineMotion.Decelerate))
            labelAlpha.animateTo(1f, tween(160, easing = LinearEasing))
        } else {
            sweep.snapTo(score / 100f)
            labelAlpha.snapTo(1f)
        }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    // A tap at each tier boundary the index crosses. With motion off they would all land in one
    // frame, on top of the confirmation, so they are left out.
    if (animate) {
        LaunchedEffect(Unit) {
            var passed30 = false
            var passed60 = false
            snapshotFlow { sweep.value * 100f }.collect { s ->
                if (s >= 30f && !passed30) {
                    passed30 = true
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                if (s >= 60f && !passed60) {
                    passed60 = true
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
        }
    }

    val shown = (sweep.value * 100f).roundToInt()
    val bandColor by animateColorAsState(
        targetValue = dialColor(shown),
        animationSpec = if (animate) tween(120, easing = LinearEasing) else snap(),
        label = "band",
    )
    val tierColor = dialColor(score)
    // The eyebrow and the unit sit inside the arc, so past 1.3x they stop growing there. The
    // tier label leaves the arc and takes its size from the reader's setting instead.
    val large = fontScale > 1.3f
    fun inside(size: Float) = (size * minOf(fontScale, 1.3f) / fontScale).sp

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Risk score $score out of 100. ${tier.label}."
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = Dial.W).fillMaxWidth().aspectRatio(Dial.W / Dial.H)) {
            val k = maxWidth / Dial.W
            Canvas(Modifier.matchParentSize()) {
                val scale = size.width / Dial.W.toPx()
                fun px(d: Dp) = d.toPx() * scale
                val c = Offset(px(Dial.CX), px(Dial.CY))
                fun at(radius: Float, deg: Float): Offset {
                    val a = Math.toRadians(deg.toDouble())
                    return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
                }
                val r = px(Dial.BAND_R)
                val topLeft = Offset(c.x - r, c.y - r)
                val arcSize = Size(2 * r, 2 * r)
                val band = Stroke(width = px(Dial.BAND_W), cap = StrokeCap.Butt)

                // 1. The scale: fixed, and never lit by the reading.
                for (p in 0..100 step 2) {
                    val major = p % 10 == 0
                    val a = Dial.angle(p.toFloat())
                    drawLine(
                        color = hero.content.copy(alpha = if (major) 0.50f else 0.22f),
                        start = at(px(Dial.TICK_OUT) - px(if (major) Dial.MAJOR_LEN else Dial.MINOR_LEN), a),
                        end = at(px(Dial.TICK_OUT), a),
                        strokeWidth = px(if (major) Dial.MAJOR_W else Dial.MINOR_W),
                        cap = StrokeCap.Butt,
                    )
                }

                // 2. The zone track, with a 2 dp break at each threshold, the way a tachometer
                // marks its red zone. The thresholds are drawn, not labelled.
                val half = Math.toDegrees((px(Dial.ZONE_GAP) / r).toDouble()).toFloat() / 2f
                listOf(
                    Triple(0f, 30f, hero.content.copy(alpha = 0.10f)),
                    Triple(30f, 60f, HeroAmber.copy(alpha = 0.20f)),
                    Triple(60f, 100f, Crimson.copy(alpha = 0.22f)),
                ).forEach { (from, to, color) ->
                    val a1 = Dial.angle(from) + if (from == 0f) 0f else half
                    val a2 = Dial.angle(to) - if (to == 100f) 0f else half
                    drawArc(color, a1, a2 - a1, useCenter = false, topLeft = topLeft, size = arcSize, style = band)
                }

                // 3. The value band: one colour, butt caps, so its length is the score.
                val v = sweep.value * 100f
                if (v > 0f) {
                    drawArc(bandColor, Dial.START, Dial.SWEEP * v / 100f, useCenter = false, topLeft = topLeft, size = arcSize, style = band)
                }

                // 4. The index: a notch cut in the band, then the line itself.
                val a = Dial.angle(v)
                drawLine(hero.container, at(px(Dial.KNOCK_IN), a), at(px(Dial.KNOCK_OUT), a), px(Dial.KNOCK_W), StrokeCap.Butt)
                drawLine(hero.content, at(px(Dial.INDEX_IN), a), at(px(Dial.INDEX_OUT), a), px(Dial.INDEX_W), StrokeCap.Butt)
            }

            Eyebrow(
                "Risk score",
                color = hero.muted,
                style = HeroEyebrowStyle.copy(fontSize = inside(12f), lineHeight = inside(16f)),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 52.dp * k),
            )
            // The numeral is sized in dp, not sp: it is already 80 and must not grow through the
            // arc. The dial's spoken description carries the same reading. The spec's row top is
            // 68 dp in a CSS line box; with the line height equal to the font size, Compose puts
            // the same digits 12.8 dp lower, so the row sits at 55.2 to draw them at box y 76
            // to 136, where the spec does.
            val numeral = with(density) { (80.dp * k).toSp() }
            Row(Modifier.align(Alignment.TopCenter).offset(y = 55.2.dp * k)) {
                Text(
                    text = shown.toString(),
                    style = TextStyle(
                        fontFamily = RedlineFonts.Sans,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                        fontSize = numeral,
                        lineHeight = numeral,
                        letterSpacing = -with(density) { (3.dp * k).toSp() },
                    ),
                    color = hero.content,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                // No spacer: the spec's 4 dp draws 14 dp of ink between the digits and the unit
                // here against the 10 dp it shows, because the numeral's box already ends past
                // its last digit.
                Text(
                    "/100",
                    style = TextStyle(
                        fontFamily = RedlineFonts.Mono,
                        fontWeight = FontWeight.Medium,
                        fontSize = inside(15f),
                        lineHeight = inside(20f),
                    ),
                    color = hero.muted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (!large) {
                Text(
                    tier.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = tierColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (186.dp - 10.dp) * k)
                        .graphicsLayer { alpha = labelAlpha.value },
                )
            }
        }
        if (large) {
            Spacer(Modifier.height(8.dp))
            Text(
                tier.label,
                style = MaterialTheme.typography.labelLarge,
                color = tierColor,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha.value },
            )
        }
    }
}

/**
 * A horizontal meter, 0 to 100, with gradient shimmer fill, recessed track, and luminous cap.
 * [delayIndex] staggers a group of meters in a choreographed sequence.
 */
@Composable
internal fun Meter(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    delayIndex: Int = 0,
    height: Dp = 7.dp,
) {
    val animate = animationsEnabled()
    val fill = remember { Animatable(if (animate) 0f else value / 100f) }
    LaunchedEffect(value) {
        if (animate) kotlinx.coroutines.delay(100L + delayIndex * 60L)
        fill.animateTo(
            targetValue = value / 100f,
            animationSpec = if (animate) {
                tween(750, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            } else snap(),
        )
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        // Recessed track groove
        drawRoundRect(track.copy(alpha = 0.30f), cornerRadius = r)
        val w = size.width * fill.value.coerceIn(0f, 1f)
        if (w > 0f) {
            val barWidth = maxOf(w, size.height)
            // Gradient fill from deeper tone to vibrant accent
            val gradient = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.65f), color),
                startX = 0f,
                endX = barWidth,
            )
            drawRoundRect(gradient, size = Size(barWidth, size.height), cornerRadius = r)
            // Luminous leading edge cap
            val capRadius = size.height / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.60f),
                radius = capRadius * 0.40f,
                center = Offset(barWidth - capRadius, capRadius),
            )
        }
    }
}

/**
 * The four categories as a bento grid: the riskiest category takes the tall tile on the left,
 * the next two stack beside it, and the fourth runs the full width underneath. The layout is
 * the ranking, so the eye reaches the worst area of the lease first without reading a number.
 */
@Composable
internal fun BentoGrid(
    categories: List<CategoryScore>,
    onOpen: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = categories.sortedWith(compareByDescending<CategoryScore> { it.risk }.thenBy { it.category.ordinal })
    if (ranked.size < 4) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            BentoTile(ranked[0], tall = true, index = 0, onClick = { onOpen(ranked[0].category) }, modifier = Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                BentoTile(ranked[1], index = 1, onClick = { onOpen(ranked[1].category) }, modifier = Modifier.fillMaxWidth())
                BentoTile(ranked[2], index = 2, onClick = { onOpen(ranked[2].category) }, modifier = Modifier.fillMaxWidth())
            }
        }
        BentoTile(ranked[3], wide = true, index = 3, onClick = { onOpen(ranked[3].category) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BentoTile(
    score: CategoryScore,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    wide: Boolean = false,
) {
    val colors = risk
    val clear = score.flagged == 0
    val tint = if (clear) colors.low else colors.ofScore(score.risk)
    val spoken = "${score.category.label}. " + when {
        clear -> "Nothing flagged."
        score.flagged == 1 -> "1 clause flagged, risk ${score.risk} of 100."
        else -> "${score.flagged} clauses flagged, risk ${score.risk} of 100."
    }
    KitCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        onClick = onClick,
        padding = Space.l,
    ) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(score.category.icon, tint)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(score.category.label, style = MaterialTheme.typography.titleSmall)
                    Text(score.category.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TileFigure(score, tint)
            }
            Spacer(Modifier.height(Space.m))
            Meter(score.risk, tint, delayIndex = index)
        } else {
            IconBadge(score.category.icon, tint, size = if (tall) 48.dp else 40.dp)
            Spacer(Modifier.height(if (tall) Space.l else Space.m))
            if (tall) {
                // The worst category gets its risk as a ring, the one tile with room to draw it.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RiskRing(score.risk, tint, Modifier.size(104.dp))
                }
                Spacer(Modifier.height(Space.l))
            }
            TileFigure(score, tint, large = tall)
            Spacer(Modifier.height(Space.xs))
            Text(score.category.label, style = MaterialTheme.typography.titleSmall)
            if (tall) {
                Text(score.category.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.m))
            Meter(score.risk, tint, delayIndex = index)
        }
    }
}

@Composable
private fun TileFigure(score: CategoryScore, tint: Color, large: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        CountUp(
            target = score.flagged,
            style = FigureStyle.copy(fontSize = if (large) 44.sp else 30.sp, lineHeight = if (large) 44.sp else 30.sp),
            color = if (score.flagged == 0) MaterialTheme.colorScheme.onSurface else tint,
        )
        Text(
            text = if (score.flagged == 1) " clause" else " clauses",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = if (large) 6.dp else 3.dp),
            textAlign = TextAlign.Start,
        )
    }
}

/** A category's risk, 0 to 100, as an illuminated ring with radiant glow and luminous pip. */
@Composable
internal fun RiskRing(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    caption: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animate = animationsEnabled()
    val fill = remember { Animatable(if (animate) 0f else value / 100f) }
    LaunchedEffect(value) {
        if (animate) kotlinx.coroutines.delay(140)
        fill.animateTo(
            targetValue = value / 100f,
            animationSpec = if (animate) {
                tween(850, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            } else snap(),
        )
    }
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.10f
            val inset = w / 2f + 2.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arc.width / 2f
            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.14f * fill.value), Color.Transparent),
                    center = center,
                    radius = radius * 0.95f,
                ),
                radius = radius * 0.95f,
                center = center,
            )
            // Recessed track
            drawArc(track.copy(alpha = 0.30f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            val sweepAngle = 360f * fill.value
            if (sweepAngle > 0.5f) {
                drawArc(color, -90f, sweepAngle, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                val a = Math.toRadians((-90f + sweepAngle).toDouble())
                val tip = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                drawCircle(color.copy(alpha = 0.45f), radius = w * 1.4f, center = tip)
                drawCircle(Color.White, radius = w * 0.40f, center = tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text((fill.value * 100).roundToInt().toString(), style = FigureStyle.copy(fontSize = 26.sp, lineHeight = 28.sp), color = color)
            Text("RISK", style = EyebrowStyle.copy(fontSize = 9.sp), color = caption)
        }
    }
}

/** A value that eases between 0 and 1 as [on] flips: for small reveals that need no ceremony. */
@Composable
internal fun rememberReveal(on: Boolean): Float {
    val v by animateFloatAsState(if (on) 1f else 0f, motion(RedlineMotion.expand()), label = "reveal")
    return v
}
