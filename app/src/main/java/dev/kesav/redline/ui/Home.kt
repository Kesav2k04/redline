package dev.kesav.redline.ui

import androidx.compose.animation.core.LinearEasing
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
) {
    // Set by the paste tile when the clipboard is empty, so there is a field to type or
    // paste into. Saved, so rotating the phone does not throw the reader back a step.
    var composing by rememberSaveable { mutableStateOf(false) }

    when {
        reading != null -> Reading(reading, modifier)

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Promise(onChecks = onChecks)

        Text(
            text = "Start with your lease",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp).semantics { heading() },
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

        LooksFor(onChecks)

        Tile(
            icon = RedlineIcons.Sparkle,
            title = "No lease to hand? Try a sample",
            detail = null,
            onClick = onSample,
            quiet = true,
            modifier = Modifier.fillMaxWidth(),
        )

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
                text = "The scan and its count are free. The full report is " +
                    (price?.let { "a one-time $it." } ?: "a one-time purchase."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What a scan looks for, before anyone hands over a lease.
 *
 * Read from the rule table, like the checks sheet it opens, so this row cannot name a
 * subject the scanner does not actually examine.
 */
@Composable
private fun LooksFor(onChecks: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "What it looks for",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(Scanner.topics) { topic ->
                Surface(
                    onClick = onChecks,
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerLowest,
                ) {
                    Column(
                        modifier = Modifier.width(132.dp).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${topic.ruleCount}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = scheme.primary,
                        )
                        Text(
                            topic.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
@Composable
private fun Promise(onChecks: () -> Unit) {
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
                Surface(
                    onClick = onChecks,
                    shape = MaterialTheme.shapes.small,
                    color = hero.content.copy(alpha = 0.10f),
                    contentColor = hero.content,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(RedlineIcons.Checks, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("${Scanner.ruleCount} checks", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                for (line in l.getLineForOffset(start)..l.getLineForOffset(end - 1)) {
                    val from = maxOf(start, l.getLineStart(line))
                    val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                    if (from >= to) continue
                    val x1 = l.getHorizontalPosition(from, usePrimaryDirection = true)
                    val x2 = l.getHorizontalPosition(to, usePrimaryDirection = true)
                    val y = l.getLineBaseline(line) + 5.dp.toPx()
                    drawRoundRect(
                        color = mark,
                        topLeft = Offset(minOf(x1, x2), y),
                        size = Size(kotlin.math.abs(x2 - x1), thickness),
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
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(hero.content.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = hero.accent)
        Text(label, style = MaterialTheme.typography.labelLarge, color = hero.content)
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
    val badge = if (filled) scheme.onPrimary.copy(alpha = 0.16f) else scheme.primaryContainer
    val badgeTint = if (filled) scheme.onPrimary else scheme.primary

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = if (quiet) BorderStroke(1.dp, scheme.outlineVariant) else null,
        modifier = modifier.heightIn(min = 56.dp),
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
 * Waiting on a PDF or a photo. A page with a red line travelling down it, because the
 * wait is the app reading, and a spinner says nothing about what it is waiting for.
 */
@Composable
private fun Reading(step: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val moving = animationsEnabled()
    val sweep = if (moving) {
        rememberInfiniteTransition(label = "reading").animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "sweep",
        ).value
    } else {
        0.5f
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 132.dp, height = 172.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(scheme.surfaceContainerLowest)
                .drawBehind {
                    val pad = 18.dp.toPx()
                    val gap = 14.dp.toPx()
                    var y = pad + 6.dp.toPx()
                    var i = 0
                    while (y < size.height - pad) {
                        val w = (size.width - pad * 2) * if (i % 4 == 3) 0.55f else 1f
                        drawRoundRect(
                            color = scheme.outlineVariant,
                            topLeft = Offset(pad, y),
                            size = Size(w, 5.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                        y += gap
                        i++
                    }
                    drawRect(
                        color = scheme.primary,
                        topLeft = Offset(0f, size.height * sweep),
                        size = Size(size.width, 3.dp.toPx()),
                    )
                },
        )
        Text(
            text = step,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        LinearProgressIndicator(modifier = Modifier.width(180.dp).clip(RoundedCornerShape(2.dp)))
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
            Button(
                onClick = onScan,
                enabled = text.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Scan", style = MaterialTheme.typography.labelLarge)
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
