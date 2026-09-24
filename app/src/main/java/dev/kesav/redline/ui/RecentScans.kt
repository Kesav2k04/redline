package dev.kesav.redline.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Insight
import dev.kesav.redline.SavedLease
import dev.kesav.redline.scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The leases scanned on this phone, newest first, under the ways in.
 *
 * Three rows, and the rest behind "Show all". The store keeps each lease's text rather than
 * its result, because the rules change between versions, so every row is scanned again here
 * with today's rules: once per lease, off the main thread, and the row's score lands when it
 * is ready rather than holding the list back. A lease is forgotten from the row's menu. There
 * is no confirmation dialog, because the lease itself is still wherever it came from.
 */
@Composable
internal fun RecentScans(
    saved: List<SavedLease>,
    onOpen: (SavedLease) -> Unit,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    if (saved.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val scores = remember { mutableStateMapOf<String, Insight>() }
    LaunchedEffect(saved) {
        for (lease in saved) {
            if (lease.id !in scores) {
                scores[lease.id] = withContext(Dispatchers.Default) { lease.scan().insight }
            }
        }
    }
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) saved else saved.take(COLLAPSED)

    Column(modifier.fillMaxWidth()) {
        Eyebrow(
            "Recent scans",
            modifier = Modifier
                .padding(start = Space.xs, bottom = Space.m)
                .semantics { heading() },
        )
        KitCard(
            padding = 0.dp,
            modifier = Modifier.fillMaxWidth().animateContentSize(motion(RedlineMotion.expand())),
        ) {
            shown.forEachIndexed { i, lease ->
                key(lease.id) {
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = scheme.outlineVariant)
                    LeaseRow(lease, scores[lease.id], now, onOpen = { onOpen(lease) }, onForget = { onForget(lease.id) })
                }
            }
            if (saved.size > COLLAPSED) {
                HorizontalDivider(color = scheme.outlineVariant)
                More(all = all, count = saved.size, onToggle = { all = !all })
            }
        }
    }
}

private const val COLLAPSED = 3

@Composable
private fun LeaseRow(
    lease: SavedLease,
    insight: Insight?,
    now: Long,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = risk
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val date = ago(lease.savedAt, now)
    val tint = insight?.let { colors.ofScore(it.score) }
    val tierWeight = MaterialTheme.typography.labelMedium.fontWeight
    val spoken = buildString {
        append(lease.title).append(". Scanned ").append(date.lowercase(Locale.getDefault()))
        lease.place?.let { append(", ").append(it.label) }
        if (insight != null) append(". Risk ${insight.score} of 100, ${insight.tier.label.lowercase(Locale.getDefault())}")
        append(".")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = "open the report") {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onOpen()
            }
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                customActions = listOf(CustomAccessibilityAction("Forget this lease") { onForget(); true })
            }
            .padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreRing(insight?.score, tint, Modifier.size(44.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).padding(vertical = Space.m)) {
            Text(lease.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    if (insight != null && tint != null) {
                        withStyle(SpanStyle(color = tint, fontWeight = tierWeight)) {
                            append(insight.tier.label)
                        }
                        append(DOT)
                    }
                    append(date)
                    lease.place?.let {
                        append(DOT)
                        append(it.label)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(MoreVertical, contentDescription = "More for ${lease.title}", tint = scheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Forget this lease") },
                    leadingIcon = { Icon(RedlineIcons.Trash, contentDescription = null) },
                    onClick = {
                        menu = false
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onForget()
                    },
                )
            }
        }
    }
}

private const val DOT = "  ·  "

/** "Show all 12" below the third row, and "Show fewer" once they are open. */
@Composable
private fun More(all: Boolean, count: Int, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (all) -90f else 90f, motion(RedlineMotion.spatial()), label = "turn")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (all) "Show fewer" else "Show all $count",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(RedlineIcons.Chevron, contentDescription = null, tint = scheme.primary, modifier = Modifier.rotate(turn))
    }
}

/**
 * The lease's risk score as a small ring, filled to the score in the tier's colour, with the
 * number inside. An empty track while the lease is being scanned again.
 */
@Composable
private fun ScoreRing(score: Int?, color: Color?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate = animationsEnabled()
    val fill = remember { Animatable(0f) }
    LaunchedEffect(score) {
        if (score == null) return@LaunchedEffect
        if (animate) fill.animateTo(score / 100f, RedlineMotion.spatialExpressive()) else fill.snapTo(score / 100f)
    }
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 3.5.dp.toPx()
            val arc = Size(size.width - w, size.height - w)
            val at = Offset(w / 2, w / 2)
            drawArc(scheme.outlineVariant, 0f, 360f, false, at, arc, style = Stroke(w))
            if (color != null) {
                drawArc(color, -90f, 360f * fill.value, false, at, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        if (score != null && color != null) {
            Text(
                (fill.value * 100).roundToInt().toString(),
                style = FigureStyle.copy(fontSize = 14.sp, lineHeight = 16.sp),
                color = scheme.onSurface,
            )
        }
    }
}

/**
 * When a lease was scanned, the way a person says it: "Today", "Yesterday", "3 days ago",
 * then the date in the reader's own order ("Sep 12" or "12 Sept"), with the year once it is
 * not this one.
 */
internal fun ago(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val day = Instant.ofEpochMilli(then).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> {
            val skeleton = if (day.year == today.year) "MMMd" else "MMMdyyyy"
            day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }
    }
}

// Material Symbols more_vert. The shared icon set has no overflow mark, and this list is the
// only place that needs one.
private val MoreVertical: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes(
            "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
        ),
        fill = SolidColor(Color.Black),
    )
}.build()
