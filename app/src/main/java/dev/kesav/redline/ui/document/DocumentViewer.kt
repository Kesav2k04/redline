package dev.kesav.redline.ui.document

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Clause
import dev.kesav.redline.ClauseGroup
import dev.kesav.redline.Finding
import dev.kesav.redline.Severity
import dev.kesav.redline.ui.Eyebrow
import dev.kesav.redline.ui.FigureStyle
import dev.kesav.redline.ui.IconBadge
import dev.kesav.redline.ui.KitCard
import dev.kesav.redline.ui.RedlineFonts
import dev.kesav.redline.ui.RedlineIcons
import dev.kesav.redline.ui.RedlineMotion
import dev.kesav.redline.ui.Space
import dev.kesav.redline.ui.animationsEnabled
import dev.kesav.redline.ui.motion
import dev.kesav.redline.ui.risk
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * The lease as the reader signed it, with the report's marks on it.
 *
 * Every clause is set in order on one paper sheet, in the serif the report quotes from, with
 * the lease's own clause numbers hung in the margin the way a printed contract sets them.
 * Each flagged clause is marked the way a person marks paper: a highlighter stroke behind the
 * lines, red where a clause is serious and amber where it is worth checking, slightly uneven at
 * the ends because a hand drew it. Tap a mark and the finding opens under the clause; tap
 * again and it closes. A locked finding shows its topic and nothing it would give away.
 *
 * The rail down the right edge is the whole document at once: one tick for every flag, in
 * the place it sits, and a band for the part on screen. Tapping the rail jumps to the flag
 * nearest the finger. [focus] opens the document at that clause and pulses its mark twice,
 * so a reader arriving from the report sees at once which lines were meant.
 */
@Composable
internal fun DocumentViewer(
    clauses: List<Clause>,
    groups: List<ClauseGroup>,
    focus: Int?,
    locked: Set<Int>,
    onBack: () -> Unit,
    onUnlock: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val scheme = MaterialTheme.colorScheme
    val byClause = remember(groups) { groups.associateBy { it.clause.index } }
    val layout = remember(clauses) { DocumentLayout(clauses) }
    // One item before the clauses (the sheet's head) and one after (its foot).
    val focusItem = focus?.let { f -> clauses.indexOfFirst { it.index == f } }?.takeIf { it >= 0 }?.plus(1)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = focusItem ?: 0)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = with(LocalDensity.current) { 72.dp.roundToPx() }

    var open by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val marked = remember { mutableSetOf<Int>() }
    val pulse = remember { Animatable(0f) }
    val animate = animationsEnabled()
    LaunchedEffect(focus) {
        if (focusItem == null) return@LaunchedEffect
        // Room above the clause for the line before it, so it reads as part of the page.
        list.scrollToItem(focusItem, -context)
        if (animate) {
            repeat(2) {
                pulse.animateTo(1f, tween(260, easing = RedlineMotion.Decelerate))
                pulse.animateTo(0f, tween(380))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        TopBar(flags = groups.size, onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(start = Space.l, end = Space.l + RAIL_GUTTER, top = Space.s, bottom = bottom + Space.xl),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "head") {
                    SheetHead(clauses = clauses.size, flags = groups.size)
                }
                itemsIndexed(clauses, key = { _, c -> c.index }) { _, clause ->
                    val group = byClause[clause.index]
                    ClauseBlock(
                        clause = clause,
                        group = group,
                        locked = clause.index in locked,
                        open = clause.index in open,
                        drawnBefore = clause.index in marked,
                        onDrawn = { marked += clause.index },
                        pulse = if (clause.index == focus) pulse.value else 0f,
                        onToggle = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            open = if (clause.index in open) open - clause.index else open + clause.index
                        },
                        onUnlock = onUnlock,
                    )
                }
                item(key = "foot") { SheetFoot() }
            }
            FlagRail(
                layout = layout,
                groups = groups,
                visible = {
                    val info = list.layoutInfo
                    val items = info.visibleItemsInfo
                    if (items.isEmpty()) 0f to 0f else {
                        layout.fraction(items.first().index - 1, (-items.first().offset).toFloat() / items.first().size.coerceAtLeast(1)) to
                            layout.fraction(items.last().index - 1, (info.viewportEndOffset - items.last().offset).toFloat() / items.last().size.coerceAtLeast(1))
                    }
                },
                onJump = { clauseIndex ->
                    val item = clauses.indexOfFirst { it.index == clauseIndex } + 1
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch { list.animateScrollToItem(item, -context) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = Space.m, bottom = bottom + Space.m)
                    .width(RAIL_GUTTER + RAIL_REACH),
            )
        }
    }
}

private val RAIL_GUTTER = 18.dp
/**
 * How far the rail's touch area reaches left of the rail it draws: the empty strip of the list's
 * end padding between the sheet and the rail, so a tap there lands and nothing on the page moves.
 */
private val RAIL_REACH = Space.l
private val MARGIN = 36.dp

@Composable
private fun TopBar(flags: Int, onBack: () -> Unit) {
    val colors = risk
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = Space.xs, end = Space.l, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(RedlineIcons.Back, contentDescription = "Back") }
        Text(
            "The lease",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Row(
            Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.highTint)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$flags", style = FigureStyle.copy(fontSize = 15.sp), color = colors.high)
            Text(if (flags == 1) " flag" else " flags", style = MaterialTheme.typography.labelLarge, color = colors.high)
        }
    }
}

/** The top of the sheet: what the document is, how long, and how to use the marks. */
@Composable
private fun SheetHead(clauses: Int, flags: Int) {
    Column(
        Modifier
            .widthIn(max = SHEET_MAX)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
            .padding(start = Space.xl, end = Space.xl, top = Space.xl, bottom = Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(if (clauses == 1) "1 clause" else "$clauses clauses", modifier = Modifier.weight(1f))
            if (flags > 0) Eyebrow("Tap a mark to see why")
        }
        Spacer(Modifier.height(Space.m))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun SheetFoot() {
    Column(
        Modifier
            .widthIn(max = SHEET_MAX)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize))
            .padding(horizontal = Space.xl, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(48.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(Space.m))
        Eyebrow("End of the lease")
    }
}

private val SHEET_MAX = 640.dp

@Composable
private fun ClauseBlock(
    clause: Clause,
    group: ClauseGroup?,
    locked: Boolean,
    open: Boolean,
    drawnBefore: Boolean,
    onDrawn: () -> Unit,
    pulse: Float,
    onToggle: () -> Unit,
    onUnlock: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = risk
    val parts = remember(clause.text) { Numbered.of(clause.text) }
    val heading = remember(parts.body) { isTitle(parts.body) }
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = RedlineFonts.Serif, lineHeight = 26.sp)
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontFamily = RedlineFonts.Serif, letterSpacing = 1.2.sp)
    val shown = remember(parts.body, heading) {
        buildAnnotatedString {
            val lead = if (heading) null else leadIn(parts.body)
            if (lead != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
                append(parts.body.substring(lead.length))
            } else {
                append(parts.body)
            }
        }
    }
    val mark = group?.let { colors.of(it.worst) }

    // The marker draws itself across the lines the first time the clause comes into view,
    // and stays drawn after that, so scrolling back does not replay it.
    val animate = animationsEnabled()
    val drawn = remember { Animatable(if (drawnBefore || !animate || group == null) 1f else 0f) }
    LaunchedEffect(group) {
        if (group != null && drawn.value < 1f) {
            drawn.animateTo(1f, tween(620, delayMillis = 120, easing = RedlineMotion.Decelerate))
        }
        if (group != null) onDrawn()
    }

    var text by remember { mutableStateOf<TextLayoutResult?>(null) }
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier
            .widthIn(max = SHEET_MAX)
            .fillMaxWidth()
            .background(scheme.surfaceContainer)
            .padding(start = Space.l, end = Space.xl, top = Space.s, bottom = Space.s),
    ) {
        Row {
            // The lease's own number, hung in the margin. Nothing when the clause has none.
            Box(Modifier.width(MARGIN).padding(top = 3.dp)) {
                if (parts.number != null) {
                    Text(
                        parts.number,
                        style = FigureStyle.copy(fontSize = 13.sp),
                        color = mark ?: scheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = shown,
                style = if (heading) titleStyle else body,
                color = scheme.onSurface,
                textAlign = TextAlign.Start,
                onTextLayout = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        // A one-line clause was a 26dp target. Every multi-line clause is taller
                        // than the minimum already, so only the short ones change.
                        if (group == null) Modifier else Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                interactionSource = source,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = if (open) "hide the finding" else "show the finding",
                                onClick = onToggle,
                            )
                            .semantics {
                                stateDescription = (if (group.worst == Severity.HIGH) "Serious" else "Worth checking") +
                                    if (open) ", finding shown" else ""
                            },
                    )
                    .drawBehind {
                        val layout = text ?: return@drawBehind
                        if (mark == null) return@drawBehind
                        marker(layout, mark, clause.index, drawn.value, pulse)
                    },
            )
        }
        if (group != null) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(motion(RedlineMotion.expand())) + fadeIn(motion(tween(160, delayMillis = 60))),
                exit = shrinkVertically(motion(RedlineMotion.expand())) + fadeOut(motion(tween(100))),
            ) {
                FindingCard(group, locked, Modifier.padding(start = MARGIN, top = Space.m, bottom = Space.s), onUnlock)
            }
        }
    }
}

/**
 * A highlighter stroke behind each line of the clause, drawn left to right across the lines in
 * reading order as [progress] runs from 0 to 1.
 *
 * The band covers the lower two thirds of the line, where a marker lands when it is dragged
 * along under the words, and its ends are cut at a slight angle with a small wobble on each
 * edge. The wobble is seeded by the clause and line, so a stroke keeps its shape from frame to
 * frame instead of shimmering. [pulse] deepens the ink and swells the band for the focus.
 */
private fun DrawScope.marker(
    layout: TextLayoutResult,
    color: Color,
    seed: Int,
    progress: Float,
    pulse: Float,
) {
    val lines = (0 until layout.lineCount).map { line ->
        Triple(line, layout.getLineLeft(line), layout.getLineRight(line))
    }.filter { (_, l, r) -> r - l > 1f }
    var budget = lines.sumOf { (_, l, r) -> (r - l).toDouble() }.toFloat() * progress
    val ink = color.copy(alpha = (0.24f + 0.22f * pulse).coerceAtMost(0.6f))
    val lip = 3.dp.toPx()
    for ((line, left, right) in lines) {
        if (budget <= 0f) break
        val width = minOf(right - left, budget)
        budget -= width
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val h = bottom - top
        val swell = h * 0.08f * pulse
        val y0 = top + h * 0.30f - swell
        val y1 = bottom - h * 0.10f + swell
        val rnd = Random(seed * 131 + line)
        fun j(amount: Float) = (rnd.nextFloat() - 0.5f) * 2f * amount
        val x0 = left - lip + j(lip * 0.6f)
        val x1 = left + width + lip + j(lip * 0.6f)
        val path = Path().apply {
            moveTo(x0 + lip * 0.8f, y0 + j(1.2f))
            // The top edge sags a hair across the line, the way a hand drifts.
            quadraticTo((x0 + x1) / 2, y0 + j(2.4f), x1, y0 + j(1.5f))
            lineTo(x1 - lip * 0.8f + j(0.8f), y1 + j(1.2f))
            quadraticTo((x0 + x1) / 2, y1 + j(2.4f), x0, y1 + j(1.5f))
            close()
        }
        drawPath(path, ink)
    }
}

/** The finding under its clause: what it says and what to ask for, or only the topic when locked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingCard(group: ClauseGroup, locked: Boolean, modifier: Modifier = Modifier, onUnlock: (() -> Unit)? = null) {
    val colors = risk
    val scheme = MaterialTheme.colorScheme
    val tint = colors.of(group.worst)
    KitCard(
        color = colors.tintOf(group.worst),
        border = null,
        shape = MaterialTheme.shapes.medium,
        padding = Space.l,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(Space.s))
            Eyebrow(if (group.worst == Severity.HIGH) "Serious" else "Worth checking", color = tint)
        }
        if (locked) {
            val topics = group.findings.map(Finding::topic).distinct()
            Spacer(Modifier.height(Space.s))
            // Topics are names in their own right ("Who decides"), so each is a tag that wraps
            // whole, the way the start screen lists them, rather than a run-on list.
            Spacer(Modifier.height(Space.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                for (topic in topics) {
                    Text(
                        topic,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(scheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                IconBadge(RedlineIcons.Lock, tint = scheme.onSurfaceVariant, size = 24.dp)
                Text(
                    if (group.findings.size == 1) "What it says is in the full report." else
                        "${group.findings.size} findings. What they say is in the full report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            // The reader is looking at the clause and wondering what is wrong with it, which
            // is the one moment the report is worth the most, so the way to it is here.
            if (onUnlock != null) {
                Spacer(Modifier.height(Space.m))
                FilledTonalButton(
                    onClick = onUnlock,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.surface, contentColor = tint),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (group.findings.size == 1) "Read what it says" else "Read what they say", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            for ((i, f) in group.findings.withIndex()) {
                Spacer(Modifier.height(if (i == 0) Space.s else Space.l))
                Eyebrow(f.topic)
                Spacer(Modifier.height(Space.xs))
                Text(f.headline, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.xs))
                Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (f.ask.isNotBlank()) {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) { append("Ask for ") }
                            append(f.ask)
                            append(".")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Every flag as a tick in its place in the document, and a band for the part on screen.
 *
 * Positions come from where each clause starts in the text, which tracks where it sits on the
 * page closely enough for a rail a few hundred pixels tall, and is known for every clause
 * before any of them has been laid out.
 */
@Composable
private fun FlagRail(
    layout: DocumentLayout,
    groups: List<ClauseGroup>,
    visible: () -> Pair<Float, Float>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = risk
    val scheme = MaterialTheme.colorScheme
    val ticks = remember(layout, groups) {
        groups.map { Triple(it.clause.index, layout.centre(it.clause.index), it.worst) }.sortedBy { it.second }
    }
    Canvas(
        modifier
            .clearAndSetSemantics { }
            .pointerInput(ticks) {
                detectTapGestures { at ->
                    val f = (at.y / size.height).coerceIn(0f, 1f)
                    ticks.minByOrNull { abs(it.second - f) }?.let { onJump(it.first) }
                }
            }
            // After the touch area and before the drawing, so taps reach wider and the rail
            // is drawn exactly where it was.
            .padding(start = RAIL_REACH),
    ) {
        val x = size.width / 2
        val track = 2.dp.toPx()
        drawRoundRect(
            color = scheme.outlineVariant,
            topLeft = Offset(x - track / 2, 0f),
            size = Size(track, size.height),
            cornerRadius = CornerRadius(track),
        )
        val (from, to) = visible()
        val band = 6.dp.toPx()
        if (to > from) {
            drawRoundRect(
                color = scheme.onSurface.copy(alpha = 0.14f),
                topLeft = Offset(x - band / 2, from * size.height),
                size = Size(band, ((to - from) * size.height).coerceAtLeast(band)),
                cornerRadius = CornerRadius(band / 2),
            )
        }
        val tick = 3.dp.toPx()
        for ((_, at, worst) in ticks) {
            drawRoundRect(
                color = colors.of(worst),
                topLeft = Offset(x - 5.dp.toPx(), at * size.height - tick / 2),
                size = Size(10.dp.toPx(), tick),
                cornerRadius = CornerRadius(tick / 2),
            )
        }
    }
}

/** Where each clause starts and ends in the text, as fractions of the whole document. */
private class DocumentLayout(clauses: List<Clause>) {
    // Each clause is counted with a paragraph's worth of spacing, as it is on the page.
    private val lengths = clauses.map { it.text.length + GAP }
    private val starts = lengths.runningFold(0) { a, b -> a + b }
    private val total = starts.last().coerceAtLeast(1).toFloat()
    private val position = clauses.withIndex().associate { (i, c) -> c.index to i }

    /** How far through the document [through] of the clause at list position [at] is. */
    fun fraction(at: Int, through: Float): Float {
        if (lengths.isEmpty()) return 0f
        val i = at.coerceIn(0, lengths.lastIndex)
        val t = if (at < 0) 0f else if (at > lengths.lastIndex) 1f else through.coerceIn(0f, 1f)
        return ((starts[i] + lengths[i] * t) / total).coerceIn(0f, 1f)
    }

    fun centre(clauseIndex: Int): Float {
        val i = position[clauseIndex] ?: return 0f
        return (starts[i] + lengths[i] / 2f) / total
    }

    private companion object {
        const val GAP = 60
    }
}

/** A clause split into the number the lease gave it and the words after. */
private data class Numbered(val number: String?, val body: String) {
    companion object {
        // "1.", "12.", "4.2", "4.2.1", "(a)", "(iv)", "a)" at the very start of the clause.
        // A bare number needs its dot, so a clause that opens "2024 rent" keeps its year.
        private val lead = Regex("""^\s*((?:\d+\.)+\d*|\([a-z]{1,4}\)|[a-z]\))\s+""", RegexOption.IGNORE_CASE)

        fun of(text: String): Numbered {
            val m = lead.find(text) ?: return Numbered(null, text.trim())
            return Numbered(m.groupValues[1].trimEnd('.'), text.substring(m.range.last + 1).trim())
        }
    }
}

/** A title line: short, and no lowercase letters, like "RESIDENTIAL LEASE AGREEMENT". */
private fun isTitle(text: String): Boolean =
    text.length <= 80 && text.any(Char::isLetter) && text.none(Char::isLowerCase)

/**
 * The clause's own heading when it opens with one, "Term." in "Term. The term of this
 * agreement...", set in the semibold a printed lease uses for it. At most five words, ending in
 * a full stop, so an ordinary first sentence is never mistaken for a heading.
 */
private fun leadIn(text: String): String? {
    val m = Regex("""^([A-Z][A-Za-z'&/ -]{1,40}?\.)\s""").find(text) ?: return null
    val words = m.groupValues[1].trim().split(' ').size
    return if (words <= 5) m.groupValues[1] else null
}
