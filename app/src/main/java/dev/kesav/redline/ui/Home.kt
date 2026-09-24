package dev.kesav.redline.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.dp
import dev.kesav.redline.Scanner

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
    price: String?,
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
) {
    // Set by the paste tile when the clipboard is empty, so there is a field to type or
    // paste into. Saved, so rotating the phone does not throw the reader back a step.
    var composing by rememberSaveable { mutableStateOf(false) }

    when {
        reading != null -> Reading(reading, readingProgress, modifier)

        text.isNotEmpty() || composing -> Document(
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

        else -> Start(
            price = price,
            onOpen = onOpen,
            onPhoto = onPhoto,
            onPaste = { clip ->
                if (clip.isNullOrBlank()) composing = true else onText(clip)
            },
            onSample = onSample,
            onChecks = onChecks,
            modifier = modifier,
        )
    }
}

@Composable
private fun Start(
    price: String?,
    onOpen: () -> Unit,
    onPhoto: (() -> Unit)?,
    onPaste: (String?) -> Unit,
    onSample: () -> Unit,
    onChecks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    // Three things this column has to survive: a short phone, a reader at 200% font
    // scale, and a landscape window. It scrolls rather than squeezing, so nothing is cut.
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .fadeTopEdge { scroll.value.toFloat() }
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Space between groups is larger than space inside them, so the screen reads as
        // promise, ways in, what it checks, then the sample, and not as one long list.
        Promise()

        Text(
            text = "Start with your lease",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp, start = 4.dp).semantics { heading() },
        )

        // The PDF is the common case: a lease is emailed far more often than it is
        // handed over on paper, so it gets the one filled tile on the screen.
        Tile(
            icon = RedlineIcons.Document,
            title = "Open the PDF",
            detail = "The file your landlord or agent sent",
            onClick = onOpen,
            filled = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onPhoto != null) {
                Tile(
                    icon = RedlineIcons.Camera,
                    title = "Photograph it",
                    detail = "Paper copy, a page at a time",
                    onClick = onPhoto,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    stacked = true,
                )
            }
            Tile(
                icon = RedlineIcons.Paste,
                title = "Paste text",
                detail = "Copied from mail or a chat",
                onClick = { onPaste(clipboard.getText()?.text) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                stacked = true,
            )
        }

        Spacer(Modifier.height(32.dp))

        LooksFor(onChecks)

        Spacer(Modifier.height(12.dp))

        // A document, not a sparkle: the sparkle is the stock glyph for "an AI feature",
        // and the case this app makes is that nothing here is a model guessing.
        Tile(
            icon = RedlineIcons.Document,
            title = "No lease to hand? Open a sample lease",
            detail = null,
            onClick = onSample,
            quiet = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // The price belongs on this screen, not only on the paywall. Reading a lease in
        // is work, and learning the cost only after doing that work is the shape of an
        // ambush even when the number is small. The offering loads over the network and
        // lands after the first frame, so the sentence reads properly without it.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                RedlineIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "The scan, the score and the count are free. The full report starts at " +
                    (price?.let { "a one-time $it." } ?: "a one-time payment."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    Surface(
        onClick = onChecks,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "open the list") { onChecks(); true }
            },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What it looks for", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (topic in Scanner.topics) {
                    Text(
                        topic.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
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
}

/**
 * What the app does and what it will not do, before anything is asked of the reader.
 *
 * "Nothing is uploaded" used to be a grey sentence under the instructions. It is the
 * reason to use this rather than pasting a lease into a chatbot, so it is set as large
 * as the promise itself, on the one dark panel the eye lands on first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Promise() {
    val hero = LocalHero.current
    Surface(
        color = hero.container,
        contentColor = hero.content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .background(hero.accent, RoundedCornerShape(2.dp))
                )
                Text(
                    text = "Redline",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
            }

            LeaseHero(Modifier.padding(vertical = 4.dp))

            RedlinedHeadline(
                before = "Find the clauses that ",
                marked = "cost you money",
                after = ".",
                color = hero.content,
                mark = hero.accent,
            )

            Text(
                text = "Before you sign, on your own phone. The lease is never uploaded, " +
                    "and there is no account.",
                style = MaterialTheme.typography.bodyLarge,
                color = hero.muted,
            )

            // Plain lines, not chips: they state facts and do nothing when tapped, and a
            // filled shape the size of a button promises a tap it cannot keep.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Assurance(RedlineIcons.Shield, "On this phone")
                Assurance(RedlineIcons.Offline, "Works offline")
            }
        }
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

@Composable
private fun Assurance(icon: ImageVector, label: String) {
    val hero = LocalHero.current
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = hero.accent)
        Text(label, style = MaterialTheme.typography.labelLarge, color = hero.muted)
    }
}

/**
 * One way in. A tile rather than a button because each carries a line of explanation,
 * and "Open the PDF" alone does not tell a reader it means the file the landlord sent.
 */
@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    stacked: Boolean = false,
    quiet: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        filled -> scheme.primary
        quiet -> Color.Transparent
        else -> scheme.surfaceContainerLowest
    }
    val content = if (filled) scheme.onPrimary else scheme.onSurface
    // Red belongs to the one tile that is the common case. The others were pink badges
    // with red icons, which made red mean "a tile" instead of "look here".
    val badge = if (filled) scheme.onPrimary.copy(alpha = 0.16f) else scheme.secondaryContainer
    val badgeTint = if (filled) scheme.onPrimary else scheme.onSecondaryContainer

    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = if (quiet) BorderStroke(1.dp, scheme.outlineVariant) else null,
        interactionSource = press,
        modifier = modifier.pressScale(press).heightIn(min = 56.dp),
    ) {
        val iconBox = @Composable {
            Box(
                modifier = Modifier
                    .size(if (quiet) 36.dp else 48.dp)
                    .background(badge, RoundedCornerShape(if (quiet) 10.dp else 14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = badgeTint, modifier = Modifier.size(if (quiet) 20.dp else 24.dp))
            }
        }
        val words = @Composable { m: Modifier ->
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (filled) content.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (stacked) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                iconBox()
                words(Modifier)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = if (quiet) 12.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                iconBox()
                words(Modifier.weight(1f))
                Icon(RedlineIcons.Chevron, contentDescription = null, tint = content.copy(alpha = 0.6f))
            }
        }
    }
}

/**
 * Waiting on a PDF or a photo. A sheet on a desk with a red pen line reading down it, the
 * lines above the pen darkening as they are read, because the wait is the app reading and
 * a spinner says nothing about what it is waiting for.
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
    val done by animateFloatAsState(progress ?: 0f, motion(RedlineMotion.effects()), label = "done")

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 132.dp, height = 172.dp)
                // Tipped back like a page lying in front of the reader. It holds still; only
                // the pen moves.
                .graphicsLayer {
                    rotationX = 12f
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceContainerLowest)
                .drawBehind {
                    val pad = 18.dp.toPx()
                    val gap = 14.dp.toPx()
                    val pen = size.height * sweep
                    // In over the first tenth of a pass and out over the last, so the jump
                    // back to the top happens while nothing is drawn. The read lines fade
                    // back with it rather than snapping to unread.
                    val ink = if (moving) (minOf(sweep, 1f - sweep) / 0.1f).coerceIn(0f, 1f) else 1f
                    val read = lerp(scheme.outlineVariant, scheme.onSurfaceVariant, ink)
                    var y = pad + 6.dp.toPx()
                    var i = 0
                    while (y < size.height - pad) {
                        val w = (size.width - pad * 2) * if (i % 4 == 3) 0.55f else 1f
                        drawRoundRect(
                            color = if (y < pen) read else scheme.outlineVariant,
                            topLeft = Offset(pad, y),
                            size = Size(w, 5.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        y += gap
                        i++
                    }
                    val trail = 24.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, scheme.primary.copy(alpha = 0.18f * ink)),
                            startY = pen - trail,
                            endY = pen,
                        ),
                        topLeft = Offset(0f, pen - trail),
                        size = Size(size.width, trail),
                    )
                    drawRect(
                        color = scheme.primary.copy(alpha = ink),
                        topLeft = Offset(0f, pen),
                        size = Size(size.width, 3.dp.toPx()),
                    )
                },
        )
        Text(
            text = step,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        // A count when there is one to give. A photo is one step with no parts to count.
        val bar = Modifier.width(180.dp).clip(RoundedCornerShape(2.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { done }, modifier = bar)
        } else {
            LinearProgressIndicator(modifier = bar)
        }
        Text(
            text = "Read on this phone. Nothing leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The lease, ready to scan. The text stays editable, because text recognition gets the
 * odd word wrong and the reader should be able to fix a misread "10" before being told
 * what it costs them.
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

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
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
                        modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp),
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(RedlineIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.primary)
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            color = scheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Paste or type the lease here") },
                textStyle = QuoteStyle.copy(color = scheme.onSurface),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Lease text" },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Text("Scan", style = MaterialTheme.typography.labelLarge)
            }
            if (empty && text.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

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
