package dev.kesav.redline.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = EyebrowStyle, color = color, modifier = modifier)
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

/**
 * The risk score, as a dial.
 *
 * A 240 degree arc that fills from green through amber to red, with the needle's end lit, and
 * the number counting up inside it in step with the sweep. Forty tick marks sit under the arc
 * so the eye reads position as quantity before it reads the number. The sweep lands on a spring
 * and the phone gives one firm tap as it settles, so the verdict arrives as an event rather than
 * as a repaint. Reduced motion shows the final frame at once.
 */
@Composable
internal fun RiskGauge(
    score: Int,
    tier: Tier,
    modifier: Modifier = Modifier,
    track: Color = LocalHero.current.muted.copy(alpha = 0.18f),
    content: Color = LocalHero.current.content,
    muted: Color = LocalHero.current.muted,
) {
    val colors = risk
    val animate = animationsEnabled()
    val haptics = LocalHapticFeedback.current
    val sweep = remember { Animatable(if (animate) 0f else score / 100f) }
    LaunchedEffect(score) {
        sweep.animateTo(score / 100f, if (animate) RedlineMotion.spatialExpressive() else androidx.compose.animation.core.snap())
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    // A light tick every ten points on the way up, so the count is felt as well as seen.
    var lastDecade by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { (sweep.value * 10).toInt() }.collect { d ->
            if (d > lastDecade) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            lastDecade = d
        }
    }
    val tierColor = colors.ofScore(score)

    Box(
        modifier = modifier
            // The arc is open at the bottom, so the square it is drawn in reports only the part
            // with ink in it and the text below sits up against the dial.
            .layout { m, c ->
                val p = m.measure(c)
                layout(p.width, (p.height * 0.84f).toInt()) { p.place(0, 0) }
            }
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = "Risk score $score out of 100. ${tier.label}."
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2f + size.minDimension * 0.06f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val start = 150f
            val total = 240f
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arcSize.width / 2f

            // Ticks just outside the arc.
            val tickOuter = radius + stroke * 1.35f
            val tickInner = radius + stroke * 0.95f
            for (i in 0..40) {
                val a = Math.toRadians((start + total * i / 40f).toDouble())
                val lit = i / 40f <= sweep.value
                val major = i % 10 == 0
                drawLine(
                    color = if (lit) tierColor.copy(alpha = 0.9f) else muted.copy(alpha = 0.35f),
                    start = Offset(center.x + tickInner * cos(a).toFloat(), center.y + tickInner * sin(a).toFloat()),
                    end = Offset(
                        center.x + (if (major) tickOuter + stroke * 0.25f else tickOuter) * cos(a).toFloat(),
                        center.y + (if (major) tickOuter + stroke * 0.25f else tickOuter) * sin(a).toFloat(),
                    ),
                    strokeWidth = if (major) 2.2f * density else 1.2f * density,
                    cap = StrokeCap.Round,
                )
            }

            drawArc(track, start, total, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            // The fill: a sweep gradient rotated so green sits at the arc's start.
            val brush = Brush.sweepGradient(
                0f to colors.low,
                0.30f to colors.low,
                0.45f to colors.medium,
                0.62f to colors.high,
                1f to colors.high,
                center = center,
            )
            val filled = total * sweep.value
            if (filled > 0.5f) {
                rotate(start, center) {
                    drawArc(brush, 0f, filled, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // The lit end of the needle.
                val a = Math.toRadians((start + filled).toDouble())
                val tip = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                drawCircle(tierColor.copy(alpha = 0.28f), radius = stroke * 1.25f, center = tip)
                drawCircle(Color.White, radius = stroke * 0.32f, center = tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow("Risk score", color = muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = (sweep.value * 100).roundToInt().toString(),
                    style = FigureStyle.copy(fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-3).sp),
                    color = content,
                )
                Text(
                    "/100",
                    style = FigureStyle.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = muted,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }
            Spacer(Modifier.height(Space.s))
            TierChip(tier, tierColor)
        }
    }
}

@Composable
internal fun TierChip(tier: Tier, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(tier.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * A horizontal meter, 0 to 100, that fills on a spring when it first appears. [delayIndex]
 * staggers a group of meters so they fill one after another rather than all at once.
 */
@Composable
internal fun Meter(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    delayIndex: Int = 0,
    height: Dp = 6.dp,
) {
    val animate = animationsEnabled()
    val fill = remember { Animatable(if (animate) 0f else value / 100f) }
    LaunchedEffect(value) {
        if (animate) kotlinx.coroutines.delay(120L + delayIndex * 70L)
        fill.animateTo(value / 100f, if (animate) RedlineMotion.spatialExpressive() else androidx.compose.animation.core.snap())
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * fill.value.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
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

/** A category's risk, 0 to 100, as a ring that fills on a spring, with the number inside. */
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
        if (animate) kotlinx.coroutines.delay(160)
        fill.animateTo(value / 100f, if (animate) RedlineMotion.spatialExpressive() else androidx.compose.animation.core.snap())
    }
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.11f
            val inset = w / 2f
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(color, -90f, 360f * fill.value, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
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
