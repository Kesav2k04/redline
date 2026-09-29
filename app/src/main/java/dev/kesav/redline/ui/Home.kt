package dev.kesav.redline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.kesav.redline.PriceLead
import dev.kesav.redline.R
import dev.kesav.redline.SavedLease
import dev.kesav.redline.Scanner
import kotlin.math.roundToInt

/**
 * The first screen, in three states: choosing how the lease comes in, waiting while a
 * file is read, and the lease itself ready to scan.
 *
 * The first version was one column of text with a text field in the middle, and it read
 * as a form: something to fill in rather than something that does a job. Most people
 * holding a lease hold a PDF or a sheet of paper, not text on a clipboard, so the ways in
 * are now the largest things on the screen and the text field only appears once there is
 * text to show.
 */
@Composable
internal fun Editor(
    text: String,
    price: PriceLead?,
    onText: (String) -> Unit,
    onScan: () -> Unit,
    onSample: () -> Unit,
    onChecks: () -> Unit,
    reading: String?,
    source: String?,
    photoPages: Int,
    onOpen: () -> Unit,
    onPhoto: (() -> Unit)?,
    modifier: Modifier = Modifier,
    readingProgress: Float? = null,
    saved: List<SavedLease> = emptyList(),
    onOpenSaved: (SavedLease) -> Unit = {},
    onForget: (String) -> Unit = {},
    backEnabled: Boolean = true,
) {
    // Set by the paste tile when the clipboard is empty, so there is a field to type or
    // paste into. Saved, so rotating the phone does not throw the reader back a step.
    var composing by rememberSaveable { mutableStateOf(false) }

    when {
        reading != null -> Reading(reading, readingProgress, modifier)

        text.isNotEmpty() || composing -> {
            // Back from the lease returns to the ways in, as Start over does, instead of
            // closing the app. The camera registers its back before a page arrives here, so
            // the caller turns this off while the camera is open or it would take the press.
            BackHandler(enabled = backEnabled) {
                composing = false
                onText("")
            }
            Document(
                text = text,
                source = source,
                photoPages = photoPages,
                onText = onText,
                onScan = onScan,
                onChecks = onChecks,
                onPhoto = onPhoto,
                onStartOver = {
                    composing = false
                    onText("")
                },
                focusOnOpen = composing && text.isEmpty(),
                modifier = modifier,
            )
        }

        else -> Start(
            price = price,
            onOpen = onOpen,
            onPhoto = onPhoto,
            onPaste = { clip ->
                if (clip.isNullOrBlank()) composing = true else onText(clip)
            },
            onSample = onSample,
            onChecks = onChecks,
            saved = saved,
            onOpenSaved = onOpenSaved,
            onForget = onForget,
            modifier = modifier,
        )
    }
}

/**
 * Above this the two-column layouts become single columns. At twice the default size a
 * word like "Paste" is wider than half a phone, and a grid of broken words is worse than
 * a list of whole ones.
 */
private const val LARGE_TEXT = 1.3f

@Composable
private fun Start(
    price: PriceLead?,
    onOpen: () -> Unit,
    onPhoto: (() -> Unit)?,
    onPaste: (String?) -> Unit,
    onSample: () -> Unit,
    onChecks: () -> Unit,
    saved: List<SavedLease>,
    onOpenSaved: (SavedLease) -> Unit,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    // Three things this column has to survive: a short phone, a reader at 200% font
    // scale, and a landscape window. It scrolls rather than squeezing, so nothing is cut.
    // On a 1179x2556 phone at 420dpi the promise, the headline and all three ways in sit
    // above the fold; what it looks for starts just under them.
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .fadeTopEdge { scroll.value.toFloat() }
            .verticalScroll(scroll)
            .padding(horizontal = Space.l, vertical = Space.s),
    ) {
        Promise()

        Eyebrow(
            "Start with your lease",
            modifier = Modifier
                .padding(start = Space.xs, top = Space.xl, bottom = Space.m)
                .semantics { heading() },
        )
        WaysIn(onOpen = onOpen, onPhoto = onPhoto, onPaste = { onPaste(clipboard.getText()?.text) })

        // Leases already scanned on this phone, straight under the ways in: reopening one is
        // the other way a returning reader starts.
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            RecentScans(saved = saved, onOpen = onOpenSaved, onForget = onForget)
        }

        // Space between groups is larger than space inside them, so the screen reads as
        // promise, ways in, what it checks, then the sample, and not as one long list.
        Spacer(Modifier.height(Space.xxl))
        LooksFor(onChecks)
        Spacer(Modifier.height(Space.m))
        Sample(onSample)
        Spacer(Modifier.height(Space.l))
        PriceLine(price)
        Spacer(Modifier.height(Space.l))
    }
}

/**
 * The three ways a lease arrives, as one grid.
 *
 * The PDF is the common case, since a lease is emailed far more often than it is handed
 * over on paper, so it takes the tall filled tile and the other two stack beside it: the
 * size of each tile is how likely it is to be the one the reader wants. Red belongs to that
 * one tile only. The others were once pink badges with red icons, which made red mean "a
 * tile" instead of "look here".
 */
@Composable
private fun WaysIn(onOpen: () -> Unit, onPhoto: (() -> Unit)?, onPaste: () -> Unit) {
    if (LocalDensity.current.fontScale > LARGE_TEXT) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Way(RedlineIcons.Document, "Open a PDF", "The file your landlord or agent sent", onOpen, Modifier.fillMaxWidth(), filled = true)
            if (onPhoto != null) Way(RedlineIcons.Camera, "Scan paper", "Page by page", onPhoto, Modifier.fillMaxWidth())
            Way(RedlineIcons.Paste, "Paste text", "Mail or chat", onPaste, Modifier.fillMaxWidth())
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Pdf(onOpen, Modifier.weight(1f).fillMaxHeight())
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (onPhoto != null) {
                Way(RedlineIcons.Camera, "Scan paper", "Page by page", onPhoto, Modifier.fillMaxWidth().weight(1f))
            }
            Way(RedlineIcons.Paste, "Paste text", "Mail or chat", onPaste, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** The tall tile: the badge at the top, the words at the foot, and the tile itself the button. */
@Composable
private fun Pdf(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    KitCard(
        onClick = onOpen,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        border = null,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RedlineIcons.Document, tint = scheme.onPrimary, size = 48.dp, container = scheme.onPrimary.copy(alpha = 0.16f))
            Spacer(Modifier.weight(1f))
            Icon(RedlineIcons.Chevron, contentDescription = null, tint = scheme.onPrimary.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(Space.l))
        Spacer(Modifier.weight(1f))
        Text("Open a PDF", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.xs))
        Text(
            "The file your landlord or agent sent",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onPrimary.copy(alpha = 0.82f),
        )
    }
}

/**
 * One way in, as a row: badge, then the name and a line saying what it means. A tile
 * rather than a button because "Open a PDF" alone does not tell a reader it means the
 * file the landlord sent.
 */
@Composable
private fun Way(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (filled) scheme.onPrimary else scheme.onSurface
    KitCard(
        onClick = onClick,
        color = if (filled) scheme.primary else scheme.surfaceContainer,
        contentColor = content,
        border = if (filled) null else scheme.outlineVariant,
        modifier = modifier,
    ) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon,
                tint = content,
                container = if (filled) scheme.onPrimary.copy(alpha = 0.16f) else scheme.onSurface.copy(alpha = 0.07f),
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filled) content.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * What a scan looks for, before anyone hands over a lease.
 *
 * Read from the rule table, like the checks sheet it opens, so this card cannot name a
 * subject the scanner does not actually examine. All nine subjects show at once: the row of
 * tiles it replaces fitted exactly three on a phone, which read as a complete set, and the
 * per-subject counts were all three, which carried nothing. The whole card is one target,
 * so nine labels do not pretend to be nine buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LooksFor(onChecks: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val spoken = "What it looks for: " + Scanner.topics.joinToString(", ") { it.name.lowercase() } +
        ". ${Scanner.ruleCount} checks."
    KitCard(
        onClick = onChecks,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "open the list") { onChecks(); true }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RedlineIcons.Checks, tint = scheme.onSurface, container = scheme.onSurface.copy(alpha = 0.07f))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("What it looks for", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Scanner.topics.size} subjects, ${Scanner.ruleCount} checks",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Space.l))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            for (topic in Scanner.topics) {
                Text(
                    topic.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(scheme.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "See all ${Scanner.ruleCount} checks",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(RedlineIcons.Chevron, contentDescription = null, tint = scheme.primary)
        }
    }
}

/**
 * The sample, as the quietest thing on the screen: an outline and no fill. It is for the
 * reader with no lease to hand, and it should never be mistaken for the way in.
 */
@Composable
private fun Sample(onSample: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // A document, not a sparkle: the sparkle is the stock glyph for "an AI feature", and
    // the case this app makes is that nothing here is a model guessing.
    KitCard(
        onClick = onSample,
        color = Color.Transparent,
        padding = Space.m,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RedlineIcons.Documents, tint = scheme.onSurfaceVariant, size = 36.dp)
            Spacer(Modifier.width(Space.m))
            Text(
                "No lease to hand? Open a sample lease",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(RedlineIcons.Chevron, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The price belongs on this screen, not only on the paywall. Reading a lease in is work,
 * and learning the cost only after doing that work is the shape of an ambush even when the
 * number is small. The offering loads over the network and lands after the first frame, so
 * the sentence reads properly without it. The price is set as a figure, like every other
 * number the app states.
 */
@Composable
private fun PriceLine(price: PriceLead?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(
            RedlineIcons.Lock,
            contentDescription = null,
            modifier = Modifier.padding(top = 3.dp).size(16.dp),
            tint = scheme.onSurfaceVariant,
        )
        Text(
            text = buildAnnotatedString {
                append("The scan, the score and the count are free. ")
                if (price != null) {
                    append("The full report is ")
                    if (price.from) append("from ")
                    withStyle(FigureStyle.toSpanStyle().copy(color = scheme.onSurface)) { append(price.price) }
                    price.per?.let { append(" a $it") }
                    append(if (price.once) ", paid once." else ".")
                } else {
                    append("The full report unlocks with a plan.")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * What the app does and what it will not do, before anything is asked of the reader.
 *
 * "Nothing is uploaded" used to be a grey sentence under the instructions. It is the
 * reason to use this rather than pasting a lease into a chatbot, so it sits on the
 * masthead beside the name, on the one dark panel the eye lands on first. The panel is
 * held to the hero, the headline and one sentence, so the ways in still reach the first
 * screen underneath it.
 */
@Composable
private fun Promise() {
    val hero = LocalHero.current
    val large = LocalDensity.current.fontScale > LARGE_TEXT
    Surface(
        color = hero.container,
        contentColor = hero.content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.redline_lockup),
                    contentDescription = "Redline",
                    modifier = Modifier.height(24.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!large) Assurances()
            }
            if (large) {
                Spacer(Modifier.height(Space.s))
                Assurances()
            }

            LeaseHero(Modifier.padding(top = Space.s))

            Spacer(Modifier.height(Space.m))
            RedlinedHeadline(
                before = "Find the clauses that ",
                marked = "cost you money",
                after = ".",
                color = hero.content,
                mark = hero.accent,
            )
            Spacer(Modifier.height(Space.m))
            Text(
                text = "Before you sign, on your own phone. The lease is never uploaded, " +
                    "and there is no account.",
                style = MaterialTheme.typography.bodyMedium,
                color = hero.muted,
            )
        }
    }
}

/**
 * Plain marks, not chips: they state facts and do nothing when tapped, and a filled shape
 * the size of a button promises a tap it cannot keep.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Assurances() {
    // Wraps as whole marks, so large text moves "Offline" to its own line instead of
    // breaking it after the N.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Assurance(RedlineIcons.ShieldCheck, "On this phone")
        Assurance(RedlineIcons.Offline, "Offline")
    }
}

@Composable
private fun Assurance(icon: ImageVector, label: String) {
    val hero = LocalHero.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = hero.accent)
        Eyebrow(label, color = hero.muted)
    }
}

/**
 * The headline with its key phrase underlined in red, which is what a redline is.
 *
 * Drawn from the text layout rather than as a span, so the stroke sits under the words on
 * every line they wrap onto, at any font scale, instead of being a decoration that only
 * lines up at the size it was designed at.
 */
@Composable
private fun RedlinedHeadline(before: String, marked: String, after: String, color: Color, mark: Color) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val start = before.length
    val end = start + marked.length

    // The pen stroke draws itself the first time, which is the product showing what it
    // does in its first second. Saved, so rotating the phone does not replay it.
    val moving = animationsEnabled()
    var played by rememberSaveable { mutableStateOf(false) }
    val drawn = remember { Animatable(if (played || !moving) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played && moving) {
            drawn.animateTo(1f, tween(durationMillis = 520, delayMillis = 280, easing = RedlineMotion.Decelerate))
        }
        played = true
    }
    Text(
        text = buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(color = color)) { append(marked) }
            append(after)
        },
        style = MaterialTheme.typography.displaySmall,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier
            .semantics { heading() }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val thickness = 4.dp.toPx()
                val segments = (l.getLineForOffset(start)..l.getLineForOffset(end - 1)).mapNotNull { line ->
                    val from = maxOf(start, l.getLineStart(line))
                    val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                    if (from >= to) return@mapNotNull null
                    val x1 = l.getHorizontalPosition(from, usePrimaryDirection = true)
                    val x2 = l.getHorizontalPosition(to, usePrimaryDirection = true)
                    Triple(minOf(x1, x2), kotlin.math.abs(x2 - x1), l.getLineBaseline(line) + 5.dp.toPx())
                }
                // Each wrapped line gets its share of the stroke in reading order.
                var budget = segments.sumOf { it.second.toDouble() }.toFloat() * drawn.value
                for ((x, width, y) in segments) {
                    if (budget <= 0f) break
                    val w = minOf(width, budget)
                    budget -= w
                    drawRoundRect(
                        color = mark,
                        topLeft = Offset(x, y),
                        size = Size(w, thickness),
                        cornerRadius = CornerRadius(thickness / 2),
                    )
                }
            },
    )
}

/**
 * Waiting on a PDF or a photo. A sheet on a desk with a red pen line reading down it, the
 * lines above the pen darkening as they are read, because the wait is the app reading and
 * a spinner says nothing about what it is waiting for. The sheet is drawn like the pages in
 * the hero, heading bar and red margin rule, so the wait looks like the same lease arriving.
 */
@Composable
private fun Reading(step: String, progress: Float?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val moving = animationsEnabled()
    // One way, top to bottom, then back to the top unseen. Going back and forth read as a
    // barcode scanner; a reader only ever moves down the page.
    val sweep = if (moving) {
        rememberInfiniteTransition(label = "reading").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "sweep",
        ).value
    } else {
        progress ?: 0.5f
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val sheet = MaterialTheme.shapes.medium
        Box(
            Modifier
                .size(width = 140.dp, height = 184.dp)
                // Tipped back like a page lying in front of the reader. It holds still; only
                // the pen moves.
                .graphicsLayer {
                    rotationX = 12f
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(sheet)
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant, sheet)
                .drawBehind {
                    val pad = 18.dp.toPx()
                    val gap = 13.dp.toPx()
                    val bar = 5.dp.toPx()
                    val pen = size.height * sweep
                    // In over the first tenth of a pass and out over the last, so the jump
                    // back to the top happens while nothing is drawn. The read lines fade
                    // back with it rather than snapping to unread.
                    val ink = if (moving) (minOf(sweep, 1f - sweep) / 0.1f).coerceIn(0f, 1f) else 1f
                    val read = lerp(scheme.outlineVariant, scheme.onSurfaceVariant, ink)
                    drawLine(
                        color = scheme.primary.copy(alpha = 0.35f),
                        start = Offset(pad - 7.dp.toPx(), pad),
                        end = Offset(pad - 7.dp.toPx(), size.height - pad),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawRoundRect(
                        color = if (pad < pen) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        topLeft = Offset(pad, pad),
                        size = Size((size.width - pad * 2) * 0.6f, bar * 1.6f),
                        cornerRadius = CornerRadius(bar),
                    )
                    var y = pad + bar * 1.6f + gap
                    var i = 0
                    while (y < size.height - pad) {
                        val w = (size.width - pad * 2) * if (i % 4 == 3) 0.55f else 1f
                        drawRoundRect(
                            color = if (y < pen) read else scheme.outlineVariant,
                            topLeft = Offset(pad, y),
                            size = Size(w, bar),
                            cornerRadius = CornerRadius(bar / 2),
                        )
                        y += gap
                        i++
                    }
                    val trail = 28.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, scheme.primary.copy(alpha = 0.2f * ink)),
                            startY = pen - trail,
                            endY = pen,
                        ),
                        topLeft = Offset(0f, pen - trail),
                        size = Size(size.width, trail),
                    )
                    drawRect(
                        color = scheme.primary.copy(alpha = ink),
                        topLeft = Offset(0f, pen),
                        size = Size(size.width, 2.dp.toPx()),
                    )
                },
        )
        Spacer(Modifier.height(Space.xxl))
        Text(
            text = step,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Space.l))
        // A count when there is one to give. A photo is one step with no parts to count.
        if (progress != null) {
            Meter(
                value = (progress * 100).roundToInt(),
                color = scheme.primary,
                modifier = Modifier.width(200.dp),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.width(200.dp).height(6.dp),
                color = scheme.primary,
                trackColor = scheme.outlineVariant,
                strokeCap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Icon(RedlineIcons.ShieldCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.primary)
            Text(
                text = "Read on this phone. Nothing leaves it.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The lease, ready to scan. The text stays editable, because text recognition gets the
 * odd word wrong and the reader should be able to fix a misread "10" before being told
 * what it costs them.
 *
 * It is set as a document, in the serif the report quotes it in, on a sheet with the same
 * red margin rule as the pages in the hero: what the reader is looking at is their lease,
 * not a text box in an app.
 */
@Composable
private fun Document(
    text: String,
    source: String?,
    photoPages: Int,
    onText: (String) -> Unit,
    onScan: () -> Unit,
    onChecks: () -> Unit,
    onPhoto: (() -> Unit)?,
    onStartOver: () -> Unit,
    focusOnOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnOpen) { if (focusOnOpen) focus.requestFocus() }
    val words = remember(text) { text.split(WHITESPACE).count { it.isNotEmpty() } }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.xs, end = Space.l, top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onStartOver) {
                Icon(RedlineIcons.Close, contentDescription = "Start over")
            }
            Text(
                "Your lease",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // A paper lease is several pages, so the next page is offered here rather
            // than making the reader start over to add one.
            if (onPhoto != null && photoPages > 0) {
                Surface(
                    onClick = onPhoto,
                    shape = MaterialTheme.shapes.small,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(RedlineIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add page ${photoPages + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (source != null) {
            Row(
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(RedlineIcons.ShieldCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.primary)
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val sheet = MaterialTheme.shapes.large
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Space.l, vertical = Space.s)
                .clip(sheet)
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant, sheet),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow("Lease text", modifier = Modifier.weight(1f))
                if (words > 0) Eyebrow(if (words == 1) "1 word" else "%,d words".format(words))
            }
            val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = RedlineFonts.Serif)
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Paste or type the lease here", style = body) },
                textStyle = body.copy(color = scheme.onSurface),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = scheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // The margin rule of a printed page, just inside the text's left edge.
                        val x = 9.dp.toPx()
                        drawLine(
                            color = scheme.primary.copy(alpha = 0.3f),
                            start = Offset(x, 12.dp.toPx()),
                            end = Offset(x, size.height - 12.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .focusRequester(focus)
                    .semantics { contentDescription = "Lease text" },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Never disabled. A dead button told a TalkBack reader nothing about why it did
            // not respond; a tap on an empty field now says what is missing.
            val haptic = LocalHapticFeedback.current
            var empty by remember { mutableStateOf(false) }
            val press = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (text.isBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        empty = true
                    } else {
                        onScan()
                    }
                },
                shape = MaterialTheme.shapes.medium,
                interactionSource = press,
                modifier = Modifier.verdict().pressScale(press).fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(RedlineIcons.Scanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.s))
                Text("Scan", style = MaterialTheme.typography.labelLarge)
            }
            if (empty && text.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier
                        .padding(top = Space.s)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Box(Modifier.size(6.dp).background(scheme.primary, RoundedCornerShape(3.dp)))
                    Text(
                        "Paste or type the lease first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Surface(onClick = onChecks, color = Color.Transparent, shape = MaterialTheme.shapes.small) {
                Text(
                    "Runs all ${Scanner.ruleCount} checks on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
                )
            }
        }
    }
}

private val WHITESPACE = Regex("""\s+""")

/** The quoted-clause block: a document excerpt with a red rule down its margin. */
@Composable
internal fun Quote(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f)),
    ) {
        Spacer(Modifier.width(3.dp).fillMaxHeight().background(scheme.outline.copy(alpha = 0.45f)))
        Text(
            text = text,
            style = QuoteStyle,
            color = scheme.onSurface.copy(alpha = 0.86f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
