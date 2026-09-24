package dev.kesav.redline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import dev.kesav.redline.ClauseGroup
import dev.kesav.redline.Scanner
import dev.kesav.redline.Finding
import dev.kesav.redline.Report
import dev.kesav.redline.R
import dev.kesav.redline.ScanState
import dev.kesav.redline.ScanViewModel
import dev.kesav.redline.Severity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sharedText: String = "",
    sharedFile: Uri? = null,
    viewModel: ScanViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val snackbar = remember { SnackbarHostState() }

    // Reachable from both screens, because the two questions it answers arrive at
    // different moments: "why would I paste my lease into this" before the scan, and
    // "where did eleven come from" after it.
    var showChecks by remember { mutableStateOf(false) }

    // Without this, a back swipe on the results screen finishes the activity and closes
    // the app. The reader's own lease is still in the text field behind it, so the app
    // shutting down looks like it crashed rather than like it navigated.
    BackHandler(enabled = ui.state is ScanState.Scanned) { viewModel.back() }

    LaunchedEffect(sharedText) { viewModel.seed(sharedText) }
    LaunchedEffect(sharedFile) { viewModel.seedFile(sharedFile) }

    // The system picker, not a storage permission: it hands back one document the reader
    // chose, which is all this needs, and asks for nothing at install.
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it) }
    }

    // The camera app writes the photo into this app's cache through a FileProvider, so
    // there is no camera permission either. The file is deleted once it has been read:
    // a photo of somebody's lease has no business outliving the scan.
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var photoTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = photoTarget ?: return@rememberLauncherForActivityResult
        val file = pagePhoto(context)
        if (saved) viewModel.import(target, photo = true) { file.delete() } else file.delete()
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // No app bar. Each state draws its own header: the first screen leads with what
    // the app promises, and "Redline" in a bar above it only repeated the launcher label.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Capped and centred so a landscape phone, a tablet or an unfolded foldable does
        // not stretch clause text to 150 characters a line. A portrait phone is about
        // 450dp wide and never reaches the cap, so nothing changes there.
        val content = Modifier
            .padding(padding)
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 640.dp)
        when (val state = ui.state) {
            ScanState.Editing -> Editor(
                text = ui.text,
                price = ui.offer?.product?.price?.formatted,
                onText = viewModel::edit,
                onScan = viewModel::scan,
                onSample = viewModel::loadSample,
                onChecks = { showChecks = true },
                reading = ui.reading,
                source = ui.source,
                photoPages = ui.photoPages,
                onOpen = { openFile.launch(arrayOf("application/pdf", "image/*", "text/plain")) },
                onPhoto = if (!hasCamera) null else {
                    {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.files",
                            pagePhoto(context),
                        )
                        photoTarget = uri
                        try {
                            takePhoto.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.say("No camera app is available. Take the photo first, then open it here.")
                        }
                    }
                },
                modifier = content,
            )

            is ScanState.Scanned -> Results(
                state = state,
                unlocked = ui.unlocked,
                known = ui.entitlementsKnown,
                busy = ui.busy,
                price = ui.offer?.product?.price?.formatted,
                onUnlock = { activity?.let(viewModel::buy) },
                onRestore = viewModel::restore,
                onBack = viewModel::back,
                onShare = { context.startActivity(shareReport(state)) },
                onLetter = { Report.letter(state)?.let { context.startActivity(shareLetter(it)) } },
                onShareCount = { scope.launch { context.startActivity(shareCardIntent(context, state)) } },
                onChecks = { showChecks = true },
                modifier = content,
            )
        }

        if (showChecks) {
            ChecksSheet(onDismiss = { showChecks = false })
        }
    }
}

/**
 * What the app looks for, written down where the reader can see it before paying.
 *
 * A scan that returns a number and a price, without ever saying what it examined, asks
 * for trust it has not earned. The counts come from the rule table rather than from a
 * hand-written list, so the app cannot advertise a check it does not perform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecksSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Redline checks ${Scanner.ruleCount} things",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                // Four of six panel readers asked which law the numbers came from. None:
                // they are the ranges usual in residential leases, and saying so is the
                // difference between a flag and a legal claim.
                text = "Each one looks for a specific term and, where there is a number, " +
                    "reads it and compares it with the range usual in residential leases. " +
                    "That range is not the law where you live, so treat a flag as a " +
                    "question to ask.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (topic in Scanner.topics) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(topic.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (topic.ruleCount == 1) "1 check" else "${topic.ruleCount} checks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "That is the whole list. A clause that matches none of it is not " +
                    "flagged, which is not the same as the clause being fair.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The lease arrives by share and the argument leaves the same way.
 *
 * `createChooser` rather than a bare ACTION_SEND, so the reader picks the app instead of
 * being sent wherever the system last defaulted to. Nothing is written to disk on the
 * way out: the text goes straight into the intent, which keeps the promise that the
 * lease never leaves the phone except when its owner decides to send it.
 */
private fun shareReport(state: ScanState.Scanned): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, Report.SUBJECT)
        putExtra(Intent.EXTRA_TEXT, Report.build(state))
    }
    return Intent.createChooser(send, "Send this list")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * The letter goes out the same way as the report, through the reader's own apps, so the
 * app never holds an address, an account or a copy of what was sent.
 */
private fun shareLetter(letter: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, Report.LETTER_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, letter)
    }
    return Intent.createChooser(send, "Ask the landlord")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The scan always runs in full and the count is always honest. What the paywall holds
 * back is which clauses, and why. Charging before the scan would be charging for
 * something nobody has yet been given a reason to want.
 */
@Composable
private fun Results(
    state: ScanState.Scanned,
    unlocked: Boolean,
    known: Boolean,
    busy: Boolean,
    price: String?,
    onUnlock: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onLetter: () -> Unit,
    onShareCount: () -> Unit,
    onChecks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Never offered for text that is not a lease: the exported report opens "Redline
    // read 4 clauses in this lease", and sending that about a recipe puts the app's
    // mistake in someone else's inbox with its name on it.
    val shareable = state.findings.isNotEmpty() && state.looksLikeLease && (unlocked || !state.sellable)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(RedlineIcons.Back, contentDescription = "Edit text")
            }
            Text(
                "Report",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (shareable) {
                IconButton(onClick = onShare) {
                    Icon(RedlineIcons.Share, contentDescription = "Send the full report")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            // Extra room at the foot so the last card clears the pinned offer bar.
            // Without it the bar sits on top of the final card and crops it, which
            // reads as an unfinished screen rather than as a scroll position.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(state, locked = !unlocked && state.sellable, onShareCount = onShareCount) }

            if (!state.looksLikeLease) {
                item { NotALeaseNotice() }
            }

            itemsIndexed(state.groups) { index, group ->
                // The first one is shown in full so the rest are a known quantity.
                // Nothing is redacted when nothing is for sale.
                ClauseCard(
                    group,
                    revealed = unlocked || !state.looksLikeLease || index == 0,
                    row = index,
                )
            }

            // At the foot rather than in the pinned bar. Someone who has scrolled past
            // a column of locked cards to get here is exactly the person asking what
            // the number is based on, and the bar already carries the two buttons that
            // matter more.
            item {
                Surface(
                    onClick = onChecks,
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(RedlineIcons.Checks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "What Redline checks, all ${Scanner.ruleCount} of them",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(RedlineIcons.Chevron, contentDescription = null)
                    }
                }
            }
        }

        // The offer used to be the last item in the list, which put the price below
        // eighteen locked cards where nobody met it without first deciding to scroll for
        // it. The moment a reader is willing to pay is the moment they learn how much
        // they cannot see, so from then on the offer stays on screen.
        //
        // It is drawn from the first frame and merely disabled until the entitlement
        // read lands. Deciding on `unlocked` alone would show the bar and then remove it
        // a moment later on a reader who had already paid.
        // Never offered over text that is not a lease. The findings are shown in full
        // and free in that case, so there is nothing behind a paywall to sell, and a
        // paywall over a scan of somebody's recipe is the single worst thing this app
        // could be caught doing.
        if (!unlocked && state.sellable) {
            BottomBar {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        RedlineIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pay once: every clause, what to ask for, and a letter to send your landlord.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Enabled through the purchase, guarded in the click instead.
                //
                // A disabled filled Button in Material 3 is not a dimmed red button: it
                // is `onSurface` at 12% alpha, a pale grey slab. Disabling on `busy`
                // therefore turned the one red element on the screen grey at the exact
                // second the money moves, which is the climax of the demo. Measured on
                // device: the first painted frame is already red, because the
                // entitlement read finishes while the reader is still in the editor, so
                // `known` is the honest disable and `busy` never was.
                Button(
                    onClick = { if (!busy) onUnlock() },
                    enabled = known,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics {
                            if (busy) contentDescription = "Completing your purchase"
                        },
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            // Defaults to primary, the same red as the fill underneath.
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        // Clauses, because the heading counts clauses. Offering
                        // "18 more findings" under a heading reading "11 of 16 clauses"
                        // put two units on one screen and invited a subtraction that
                        // has no sensible answer, at the exact moment someone decides
                        // whether to trust the app with money.
                        Text(unlockLabel(state.groups.size - 1, price), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onRestore,
                        enabled = known && !busy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Already bought it? Restore")
                    }
                    TextButton(
                        onClick = onBack,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            // Next to a filled button this was the same red, so going back
                            // to edit signalled just as loudly as paying.
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("Edit text")
                    }
                }
            }
        } else if (shareable) {
            // Finding the clauses is half the job. The reader still has to raise them
            // with a landlord, a parent or a lawyer, and retyping nineteen findings into
            // a message is exactly where that stops happening.
            //
            // Two readers, two messages. The landlord gets the requests and nothing else;
            // a parent or an adviser gets the quoted clauses and the reasons.
            BottomBar {
                Button(
                    onClick = onLetter,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(RedlineIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Ask the landlord for these changes", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onShare,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Send the full report to someone else")
                }
            }
        }
    }
}

/** The pinned foot of the report: lifted off the list so it never reads as the last card. */
@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/**
 * The count, set as large as anything in the app, with the split under it.
 *
 * The number counts up from zero the first time it is drawn. It is the moment the reader
 * learns what the lease costs them, and a figure that arrives already sitting there reads
 * as a label rather than as a result.
 */
@Composable
private fun Summary(state: ScanState.Scanned, locked: Boolean, onShareCount: () -> Unit) {
    val hero = LocalHero.current
    val flagged = state.flaggedClauses
    val heading = when {
        flagged == 0 -> "Nothing matched"
        !state.looksLikeLease -> "This does not read like a lease"
        else -> "$flagged of ${state.clauseCount} clauses will cost you money"
    }

    val moving = animationsEnabled()
    val shown = remember(state) { Animatable(if (moving) 0f else flagged.toFloat()) }
    LaunchedEffect(state) {
        if (moving) shown.animateTo(flagged.toFloat(), tween(durationMillis = 700))
    }

    Surface(
        color = hero.container,
        contentColor = hero.content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (flagged == 0 || !state.looksLikeLease) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = when {
                        flagged == 0 -> "No rule matched this text. That is not the same as a clean lease."
                        flagged == 1 -> "One match below, shown free. Read on for why."
                        else -> "$flagged matches below, shown free. Read on for why."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = hero.muted,
                )
            } else {
                Column(Modifier.clearAndSetSemantics { contentDescription = heading; heading() }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = shown.value.roundToInt().toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = hero.accent,
                        )
                        Text(
                            text = " of ${state.clauseCount} clauses",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text("will cost you money", style = MaterialTheme.typography.titleLarge)
                }

                SeverityBar(
                    serious = state.highClauses,
                    other = flagged - state.highClauses,
                    clear = (state.clauseCount - flagged).coerceAtLeast(0),
                    spoken = severitySplit(state.highClauses, flagged),
                )

                // The split is already in the legend, so this line says what happens
                // next instead of repeating it.
                val rest = state.groups.size - 1
                Text(
                    text = when {
                        !locked -> "Most serious first. Each one quotes the clause it came from."
                        rest == 1 -> "The first is below, free. The other one opens with a single payment."
                        else -> "The first is below, free. The other $rest open with a single payment."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = hero.muted,
                )

                // Free, locked or not. It carries the count and nothing from the lease,
                // so there is nothing in it to sell and no reason to hold it back.
                if (ShareCardText.offered(state)) {
                    Surface(
                        onClick = onShareCount,
                        shape = MaterialTheme.shapes.small,
                        color = hero.content.copy(alpha = 0.10f),
                        contentColor = hero.content,
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(RedlineIcons.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Share the count, not the lease", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Serious, worth checking and clear, as one bar the width of the card. */
@Composable
private fun SeverityBar(serious: Int, other: Int, clear: Int, spoken: String) {
    val hero = LocalHero.current
    val amber = MaterialTheme.colorScheme.tertiary
    val total = (serious + other + clear).coerceAtLeast(1)
    // Read aloud as one sentence rather than as three coloured boxes and three labels.
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (serious > 0) Box(Modifier.weight(serious.toFloat() / total).fillMaxHeight().background(hero.accent))
            if (other > 0) Box(Modifier.weight(other.toFloat() / total).fillMaxHeight().background(amber))
            if (clear > 0) Box(Modifier.weight(clear.toFloat() / total).fillMaxHeight().background(hero.content.copy(alpha = 0.18f)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(hero.accent, "$serious serious")
            if (other > 0) Legend(amber, "$other worth checking")
            Legend(hero.content.copy(alpha = 0.35f), "$clear clear")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalHero.current.muted)
    }
}

/**
 * How long the report takes to open, and why it is not instant.
 *
 * The moment the purchase lands is the only moment in this app worth animating. A hard
 * swap from redaction to text reads as a screen being replaced; opening the cards in
 * sequence down the list reads as a document being unsealed, which is what the reader
 * just paid for. The stagger caps early so a long report does not make the last card
 * wait on the first twenty.
 */
private const val REVEAL_MS = 260
private const val REVEAL_STAGGER_MS = 45
private const val REVEAL_STAGGER_CAP = 8

/**
 * Zero when the reader has turned animations off system-wide.
 *
 * Developer options and the accessibility "remove animations" setting both write this,
 * and honouring it costs one read. An app that keeps animating after someone has asked
 * the whole device to stop is not being expressive, it is ignoring an instruction.
 */
@Composable
internal fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) > 0f
    }
}

/**
 * The line under the heading, which is the legend for the chips on the cards.
 *
 * Two earlier versions were wrong in opposite directions. "9 of them are worth arguing
 * about" was backwards: if eleven clauses cost money then all eleven are worth arguing
 * about. "9 marked costly, 2 worth checking" then put the subheading in open conflict
 * with the headline above it, because a reader takes "worth checking" to mean the other
 * two are free, and they are not. Every flagged clause costs money; the split is how
 * serious, not whether. Saying only that leaves the two largest lines on the screen
 * agreeing with each other.
 */
internal fun severitySplit(high: Int, flagged: Int): String = when {
    high == flagged && flagged == 1 -> "The one below is a serious one."
    high == flagged -> "All $flagged are serious."
    high == 0 -> "None is in the worst band, and all $flagged still cost you."
    high == 1 -> "One of them is serious."
    else -> "$high of them are serious."
}

/**
 * The label on the button that sells the report.
 *
 * The count is not always plural. `sellable` opens the paywall at two flagged clauses,
 * so the smallest offer this button ever makes is one clause, and it read "Show the
 * other 1 clauses" on every two-clause lease. Every other count on this screen already
 * had its singular; this one was the exception.
 */
internal fun unlockLabel(otherClauses: Int, price: String?): String {
    val what = if (otherClauses == 1) "the other clause" else "the other $otherClauses clauses"
    return "Show $what" + (price?.let { " for $it" } ?: "")
}

/**
 * Shown above the findings when the text does not read like a tenancy agreement.
 *
 * It says what matched and why that is not the same as the document being a problem,
 * because several of these rules match ordinary commercial English. "At its sole
 * discretion" is in every employment offer written.
 */
@Composable
private fun NotALeaseNotice() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Redline only knows tenancy agreements",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "This text never says tenant, landlord, lease, rent or premises, " +
                    "so it is probably not one. Some of the rules below match ordinary " +
                    "contract language and will fire on almost any document. Nothing " +
                    "here is charged for, and none of it should be relied on.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ClauseCard(group: ClauseGroup, revealed: Boolean, row: Int = 0) {
    val count = group.findings.size
    val problems = if (count == 1) "1 problem" else "$count problems"
    val scheme = MaterialTheme.colorScheme
    val edge = if (group.worst == Severity.HIGH) scheme.primary else scheme.tertiary

    val spoken = if (revealed) {
        // The clause text belongs in here. Leaving it out told a screen reader that a
        // costly clause existed and what it was called, then withheld the sentence the
        // whole app exists to show, which is the one thing a sighted reader gets for
        // free by looking down two lines.
        buildString {
            append("${group.worst.name.lowercase()} risk, $problems. ")
            for (f in group.findings) {
                append("${f.headline}. ${f.reason} ")
                if (f.ask.isNotBlank()) append("Ask for ${f.ask}. ")
            }
            append("The clause reads: ${group.clause.text}")
        }
    } else {
        "Locked clause, ${group.worst.name.lowercase()} risk, $problems. " +
            "Buy the report to read it."
    }

    val delay = if (animationsEnabled()) minOf(row, REVEAL_STAGGER_CAP) * REVEAL_STAGGER_MS else 0
    val duration = if (animationsEnabled()) REVEAL_MS else 0

    // A red or amber rule down the left edge, which is how a lawyer marks a clause in the
    // margin, and the one thing that tells the two severities apart at a glance while
    // scrolling fast.
    Surface(
        color = scheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(edge))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeverityChip(group.worst)

                    // Without this word the grey bars below are a loading skeleton, which
                    // is the single most expensive misread available: a reviewer watching
                    // the video concludes the app is still fetching rather than that
                    // there is something behind a paywall. The count is deliberately
                    // given away for free: "3 problems" on a locked card is both a real
                    // unit of information and the most honest argument for paying.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!revealed) {
                            Icon(
                                RedlineIcons.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (revealed) problems else "$problems, locked",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                // The subject is never withheld. Knowing a clause is about the deposit is
                // not knowing what is wrong with it, and "Your deposit, 5 problems" argues
                // for itself in a way three anonymous bars never will.
                Text(
                    text = group.findings.first().topic,
                    style = MaterialTheme.typography.labelLarge,
                    color = edge,
                )

                AnimatedContent(
                    targetState = revealed,
                    transitionSpec = {
                        fadeIn(tween(duration, delayMillis = delay)) togetherWith
                            fadeOut(tween(duration / 2))
                    },
                    label = "finding",
                ) { open ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (open) {
                            for (f in group.findings) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(f.headline, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        f.reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    if (f.ask.isNotBlank()) Ask(f.ask, Modifier.padding(top = 6.dp))
                                }
                            }
                            // Quoted once at the foot of the card, however many rules it
                            // tripped. Repeating the paragraph under every finding was
                            // read as padding the count rather than as thoroughness.
                            Quote(group.clause.text, Modifier.padding(top = 4.dp))
                        } else {
                            // Redaction rather than a blur: Modifier.blur does nothing
                            // below API 31 and would quietly show the text it hides.
                            //
                            // One bar per hidden finding, up to three: a card printing
                            // "1 problem" above three bars contradicts its own count.
                            // Widths come from the clause, because identical cards in a
                            // row read as one placeholder repeated.
                            val bars = redactionWidths(group.clause.text)
                            val heights = listOf(16.dp, 12.dp, 12.dp)
                            repeat(minOf(group.findings.size, 3)) { i ->
                                Redacted(widthFraction = bars[i], height = heights[i])
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What to ask the landlord for, set apart from the reason above it.
 *
 * The reason says what is wrong and this says what to do about it, so it sits on its own
 * tint rather than as a third grey line the eye has learned to skip.
 */
@Composable
private fun Ask(ask: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) {
                    append("Ask for ")
                }
                append(ask)
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/**
 * Three bar widths for one hidden finding, stable across launches.
 *
 * Derived from the headline rather than from a random source, so the same finding always
 * redacts to the same silhouette. A reader scrolling back up finds the card looking how
 * they left it, and a screen recording can be re-shot and still match the first take.
 */
internal fun redactionWidths(headline: String): FloatArray {
    var h = 0
    for (c in headline) h = h * 31 + c.code
    fun pick(shift: Int, low: Float, span: Float): Float =
        low + span * (((h ushr shift) and 0x7) / 7f)
    return floatArrayOf(pick(0, 0.52f, 0.36f), pick(4, 0.80f, 0.19f), pick(8, 0.38f, 0.34f))
}

@Composable
private fun Redacted(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(
                // Dark, because this is a redaction and redactions are dark. At 13%
                // these were pale grey bars of text-like widths, which is the exact
                // drawing of a loading placeholder, and a still frame cannot tell a
                // reader which one it is looking at. A censored document can.
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                RoundedCornerShape(2.dp),
            )
    )
}

@Composable
private fun SeverityChip(severity: Severity) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (severity) {
        Severity.HIGH -> Triple("Costly", scheme.primaryContainer, scheme.onPrimaryContainer)
        Severity.MEDIUM -> Triple("Worth checking", scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Where the camera app writes a page. One file, reused and deleted after each read. */
private fun pagePhoto(context: android.content.Context): File =
    File(context.cacheDir, "pages").apply { mkdirs() }.resolve("page.jpg")
