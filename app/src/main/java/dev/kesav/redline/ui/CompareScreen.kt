package dev.kesav.redline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Category
import dev.kesav.redline.SavedLease
import dev.kesav.redline.ScanState
import dev.kesav.redline.scan

/**
 * Two leases side by side, for someone choosing between two flats.
 *
 * It answers the question first ("the second lease is the safer one, and here is why") and then
 * shows the rows that decided it, each with the better side marked. The four categories are a
 * butterfly chart: the two leases' risk grows outward from a shared spine, so a longer bar on one
 * side is the difference, read without a number. Both leases are scanned again with today's rules.
 */
@Composable
internal fun CompareScreen(
    current: ScanState.Scanned,
    currentId: String,
    saved: List<SavedLease>,
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val others = saved.filter { it.id != currentId }
    val here = saved.firstOrNull { it.id == currentId }
    var otherId by rememberSaveable { mutableStateOf(others.firstOrNull()?.id) }
    val other = others.firstOrNull { it.id == otherId } ?: others.firstOrNull()
    val otherScan = remember(other?.id) { other?.scan() }
    val haptics = LocalHapticFeedback.current

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(RedlineIcons.Back, contentDescription = "Back to the report") }
                Text("Compare leases", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (other == null || otherScan == null) {
                EmptyCompare(onScanAnother)
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l)
                    .padding(bottom = Space.xl)
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                if (others.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        others.forEach { l ->
                            FilterChip(
                                selected = l.id == other.id,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    otherId = l.id
                                },
                                label = { Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                            )
                        }
                    }
                }
                Deck(
                    leftTitle = here?.title ?: "This lease",
                    left = current,
                    rightTitle = other.title,
                    right = otherScan,
                )
            }
        }
    }
}

@Composable
private fun EmptyCompare(onScanAnother: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(RedlineIcons.Compare, MaterialTheme.colorScheme.onSurface, size = 64.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest)
        Spacer(Modifier.height(Space.l))
        Text("Scan a second lease", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s))
        Text(
            "Every lease you scan is kept on this phone. Scan the other flat's lease and the two appear here side by side.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xl))
        Button(onClick = onScanAnother, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 52.dp)) {
            Icon(RedlineIcons.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan another lease")
        }
    }
}

private data class Side(val title: String, val scan: ScanState.Scanned) {
    val score = scan.insight.score
    val serious = scan.highClauses
    val flagged = scan.flaggedClauses
    val months = scan.insight.exposure?.months ?: 0.0
}

@Composable
private fun Deck(leftTitle: String, left: ScanState.Scanned, rightTitle: String, right: ScanState.Scanned) {
    val a = Side(leftTitle, left)
    val b = Side(rightTitle, right)
    val colors = risk
    val hero = LocalHero.current

    // The verdict: the lower score wins, and the reasons are the rows where it is better.
    val winner = when {
        a.score < b.score -> a
        b.score < a.score -> b
        else -> null
    }
    val loser = if (winner === a) b else a
    val reasons = buildList {
        if (winner != null) {
            val d = loser.serious - winner.serious
            if (d > 0) add(if (d == 1) "1 fewer serious clause" else "$d fewer serious clauses")
            val m = loser.months - winner.months
            if (m >= 1) add("${monthsText(m)} less rent at stake")
            val f = loser.flagged - winner.flagged
            if (f > 0 && d <= 0) add(if (f == 1) "1 fewer flagged clause" else "$f fewer flagged clauses")
        }
    }

    Surface(color = hero.container, contentColor = hero.content, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.xl)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                SideHead(a, winner === a, Modifier.weight(1f))
                SideHead(b, winner === b, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.l))
            Text(
                text = winner?.let { "\"${it.title}\" is the safer lease" } ?: "The two leases score the same",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                Text(reasons.joinToString(", ").replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodyMedium, color = hero.muted)
            }
        }
    }

    KitCard(Modifier.fillMaxWidth()) {
        MetricRow("Risk score", a.score.toString(), b.score.toString(), better(a.score.toDouble(), b.score.toDouble()))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MetricRow("Serious clauses", a.serious.toString(), b.serious.toString(), better(a.serious.toDouble(), b.serious.toDouble()))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MetricRow("Flagged clauses", "${a.flagged} of ${left.clauseCount}", "${b.flagged} of ${right.clauseCount}", better(a.flagged.toDouble(), b.flagged.toDouble()))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MetricRow("Rent at stake", monthsText(a.months), monthsText(b.months), better(a.months, b.months))
    }

    KitCard(Modifier.fillMaxWidth()) {
        Eyebrow("Where each lease is risky")
        Spacer(Modifier.height(Space.m))
        Category.entries.forEachIndexed { i, c ->
            val ra = left.insight.categories.first { it.category == c }.risk
            val rb = right.insight.categories.first { it.category == c }.risk
            Butterfly(c.label, ra, rb, colors.ofScore(ra), colors.ofScore(rb), i)
            if (i < Category.entries.lastIndex) Spacer(Modifier.height(Space.m))
        }
    }
}

/** -1 when the left side is better (lower), 1 when the right is, 0 for a tie. */
private fun better(a: Double, b: Double): Int = when {
    a < b -> -1
    b < a -> 1
    else -> 0
}

@Composable
private fun SideHead(side: Side, wins: Boolean, modifier: Modifier = Modifier) {
    val tint = risk.ofScore(side.score)
    val hero = LocalHero.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            RiskRing(side.score, tint, Modifier.size(96.dp), track = hero.content.copy(alpha = 0.14f), caption = hero.muted)
            if (wins) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(risk.low), contentAlignment = Alignment.Center) {
                    Icon(RedlineIcons.Check, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(side.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(side.scan.insight.tier.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun MetricRow(label: String, left: String, right: String, better: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clearAndSetSemantics {
                contentDescription = "$label: $left against $right" + when (better) {
                    -1 -> ", the first is better."
                    1 -> ", the second is better."
                    else -> ", the same."
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Value(left, better == -1, Modifier.weight(1f), TextAlign.Start)
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
        Value(right, better == 1, Modifier.weight(1f), TextAlign.End)
    }
}

@Composable
private fun Value(text: String, wins: Boolean, modifier: Modifier, align: TextAlign) {
    Row(modifier, horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (wins && align == TextAlign.End) WinDot()
        Text(
            figure(text),
            style = FigureStyle.copy(fontSize = 17.sp),
            color = if (wins) risk.low else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (wins && align == TextAlign.Start) WinDot()
    }
}

@Composable
private fun WinDot() {
    Box(Modifier.size(8.dp).clip(CircleShape).background(risk.low))
}

/** One category as two bars growing outward from a shared spine. */
@Composable
private fun Butterfly(label: String, left: Int, right: Int, leftColor: Color, rightColor: Color, index: Int) {
    val animate = animationsEnabled()
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    val spec = motion(RedlineMotion.spatialExpressive<Float>())
    LaunchedEffect(Unit) {
        if (animate) kotlinx.coroutines.delay(100L + index * 80L)
        grow.animateTo(1f, spec)
    }
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.clearAndSetSemantics { contentDescription = "$label: risk $left against $right" }) {
        Row(Modifier.fillMaxWidth()) {
            Text(left.toString(), style = FigureStyle.copy(fontSize = 13.sp), color = leftColor, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(2f))
            Text(right.toString(), style = FigureStyle.copy(fontSize = 13.sp), color = rightColor, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val mid = size.width / 2f
            val gap = 2.dp.toPx()
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(track, Offset(0f, 0f), Size(mid - gap, size.height), r)
            drawRoundRect(track, Offset(mid + gap, 0f), Size(mid - gap, size.height), r)
            val wl = (mid - gap) * left / 100f * grow.value
            val wr = (mid - gap) * right / 100f * grow.value
            if (wl > 0f) drawRoundRect(leftColor, Offset(mid - gap - wl, 0f), Size(wl, size.height), r)
            if (wr > 0f) drawRoundRect(rightColor, Offset(mid + gap, 0f), Size(wr, size.height), r)
        }
    }
}

/** Digits stay in the mono figure face; the words around them ("of", "months") are set in sans. */
private fun figure(text: String) = buildAnnotatedString {
    for (part in Regex("""[0-9.,½]+|[^0-9.,½]+""").findAll(text)) {
        val digits = part.value.first().isDigit() || part.value.first() == '½'
        if (digits) append(part.value) else withStyle(SpanStyle(fontFamily = RedlineFonts.Sans, fontSize = 14.sp)) { append(part.value) }
    }
}
