package dev.kesav.redline.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.onClick
import kotlinx.coroutines.delay
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import android.content.ClipData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animate
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import dev.kesav.redline.Place
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
    var letterFor by remember { mutableStateOf<ScanState.Scanned?>(null) }
    var choosingPlace by remember { mutableStateOf(false) }
    var paywall by rememberSaveable { mutableStateOf<PaywallReason?>(null) }
    // Where the marked-up lease is open, and at which clause; -1 is the top of the document.
    var reading by rememberSaveable { mutableStateOf<Int?>(null) }

    // A purchase that lands closes the paywall behind it: the report opening underneath is the
    // receipt. The comparison needs Pro, so a pass bought from that tap leaves it open.
    LaunchedEffect(ui.unlocked, ui.pro) {
        val done = when (paywall) {
            PaywallReason.COMPARE -> ui.pro
            null -> false
            else -> ui.unlocked
        }
        if (done) paywall = null
    }

    // A paywall with no price is an app that started offline. Keep asking, quietly, while
    // it is on screen, so the price turns up when the signal does.
    val priceMissing = ui.offer == null && !ui.unlocked &&
        (ui.state as? ScanState.Scanned)?.sellable == true
    LaunchedEffect(priceMissing) {
        while (priceMissing) {
            viewModel.retryOffer()
            delay(15_000)
        }
    }

    // Without this, a back swipe on the results screen finishes the activity and closes
    // the app. The reader's own lease is still in the text field behind it, so the app
    // shutting down looks like it crashed rather than like it navigated.
    // Back from the report previews itself: during the gesture the report shrinks toward
    // 90% and drifts from the edge being swiped, as Material's guidance describes for an
    // app that manages its own screens. Letting go commits; a cancelled swipe springs back
    // with a slight overshoot that absorbs the tension of the drag.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val previewBack = animationsEnabled()
    PredictiveBackHandler(enabled = ui.state is ScanState.Scanned) { events ->
        try {
            events.collect { e ->
                if (previewBack) {
                    backProgress = e.progress
                    backEdge = e.swipeEdge
                }
            }
            viewModel.back()
            backProgress = 0f
        } catch (c: CancellationException) {
            animate(backProgress, 0f, animationSpec = RedlineMotion.spatialBouncy()) { v, _ -> backProgress = v }
            throw c
        }
    }

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
        // The scan is one continuous move: the Scan button grows into the verdict panel
        // (see Verdict.kt). Only the change between editing and a report animates; a
        // rescan from the report keeps its screen.
        val enter = motion(tween<Float>(220, delayMillis = 120))
        val leave = motion(tween<Float>(90))
        val backIn = motion(tween<Float>(200, delayMillis = 60))
        val backOut = motion(tween<Float>(120, easing = RedlineMotion.Accelerate))
        SharedTransitionLayout {
        AnimatedContent(
            targetState = ui.state,
            contentKey = { it is ScanState.Scanned },
            transitionSpec = {
                if (targetState is ScanState.Scanned) {
                    fadeIn(enter) togetherWith (fadeOut(leave) + scaleOut(targetScale = 0.98f))
                } else {
                    (fadeIn(backIn) + scaleIn(initialScale = 0.98f)) togetherWith fadeOut(backOut)
                }
            },
            label = "scan",
        ) { shown ->
        CompositionLocalProvider(
            LocalSharedScope provides this@SharedTransitionLayout,
            LocalVisibilityScope provides this,
        ) {
        when (val state = shown) {
            ScanState.Editing -> Editor(
                text = ui.text,
                price = ui.offer?.product?.price?.formatted,
                onText = viewModel::edit,
                onScan = viewModel::scan,
                onSample = viewModel::loadSample,
                onChecks = { showChecks = true },
                reading = ui.reading,
                readingProgress = ui.readingProgress,
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
                onUnlock = { paywall = PaywallReason.REPORT },
                onRestore = viewModel::restore,
                onBack = viewModel::back,
                onShare = { scope.launch { context.startActivity(shareReport(context, state)) } },
                onLetter = { letterFor = state },
                pro = ui.pro,
                fromPrice = ui.offers.firstOrNull()?.price ?: ui.offer?.product?.price?.formatted,
                onDocument = { focus -> reading = focus ?: -1 },
                onDraft = {
                    if (ui.unlocked || !state.sellable) letterFor = state else paywall = PaywallReason.DRAFT
                },
                onCompare = { if (ui.pro) viewModel.say("Scan a second lease to compare.") else paywall = PaywallReason.COMPARE },
                place = ui.place,
                onPlace = { choosingPlace = true },
                pitch = ui.pitch,
                onShareCount = { scope.launch { context.startActivity(shareCardIntent(context, state)) } },
                onChecks = { showChecks = true },
                modifier = content.graphicsLayer {
                    val p = RedlineMotion.Decelerate.transform(backProgress)
                    scaleX = 1f - 0.1f * p
                    scaleY = scaleX
                    val shift = (size.width / 20f - 8.dp.toPx()) * p
                    translationX = if (backEdge == BackEventCompat.EDGE_LEFT) shift else -shift
                    shape = RoundedCornerShape(28.dp * p)
                    clip = p > 0f
                },
            )
        }
        }
        }
        }

        if (showChecks) {
            ChecksSheet(onDismiss = { showChecks = false })
        }
        val scanned = ui.state as? ScanState.Scanned
        if (paywall != null && scanned != null) {
            PaywallSheet(
                state = scanned,
                offers = ui.offers,
                reason = paywall ?: PaywallReason.REPORT,
                busy = ui.busy,
                known = ui.entitlementsKnown,
                pitch = ui.pitch,
                onBuy = { plan -> activity?.let { viewModel.buy(it, plan) } },
                onRestore = viewModel::restore,
                onRetry = viewModel::retryOffer,
                onDismiss = { paywall = null },
            )
        }
        if (choosingPlace) {
            PlaceSheet(
                current = ui.place,
                onChoose = {
                    viewModel.choosePlace(it)
                    choosingPlace = false
                },
                onDismiss = { choosingPlace = false },
            )
        }
        letterFor?.let { scanned ->
            LetterSheet(
                state = scanned,
                onSend = { letter ->
                    context.startActivity(shareLetter(letter))
                    letterFor = null
                },
                onDismiss = { letterFor = null },
            )
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
                // Four of six panel readers asked which law the numbers came from. By
                // default none: they are the ranges usual in residential leases, and saying
                // so is the difference between a flag and a legal claim. A chosen place
                // swaps in figures read from that place's statutes (Places.kt).
                text = "Each one looks for a specific term and, where there is a number, " +
                    "reads it and compares it with the range usual in residential leases. " +
                    "Tell the report where the home is and, for six places, deposits and " +
                    "late fees are measured against that place's own law instead. " +
                    "Either way, treat a flag as a question to ask.",
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
 * being sent wherever the system last defaulted to. The PDF is written to the app's own
 * cache under one fixed name, so each share replaces the last, and it leaves the phone only
 * when its owner picks somewhere to send it.
 */
private suspend fun shareReport(context: android.content.Context, state: ScanState.Scanned): Intent =
    withContext(Dispatchers.Default) {
        // The text is the message body; the PDF rides along as the page to hand over. If the
        // PDF cannot be written, the text still goes.
        val pdf = runCatching { ReportPdf.write(context, state) }.getOrNull()
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, Report.SUBJECT)
            putExtra(Intent.EXTRA_TEXT, Report.build(state))
            if (pdf != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf)
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        Intent.createChooser(send, "Send the full report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
internal fun Results(
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
    place: Place? = null,
    onPlace: () -> Unit = {},
    pitch: String? = null,
    pro: Boolean = unlocked,
    fromPrice: String? = price,
    onDocument: (Int?) -> Unit = {},
    onDraft: () -> Unit = onLetter,
    onCompare: () -> Unit = {},
) {
    // Never offered for text that is not a lease: the exported report opens "Redline
    // read 4 clauses in this lease", and sending that about a recipe puts the app's
    // mistake in someone else's inbox with its name on it.
    val shareable = state.findings.isNotEmpty() && state.looksLikeLease && (unlocked || !state.sellable)
    val locked = !unlocked && state.sellable
    val listState = rememberLazyListState()
    val lifted by remember { derivedStateOf { listState.canScrollForward } }
    val pastSummary by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val countIn = motion(tween<Float>(150))
    val countSlide = motion(tween<IntOffset>(150, easing = RedlineMotion.Decelerate))
    val countOut = motion(tween<Float>(100))

    // A tap on the locked index never starts a payment: an accidental tap mid-scroll must
    // not raise a purchase sheet. It points at the button that does, by feel, by motion
    // and, for a screen reader, in words.
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val moving = animationsEnabled()
    val nudge = remember { Animatable(1f) }
    var hint by remember { mutableStateOf("") }

    // The reveal plays only for a purchase made in this session: a reader who paid last
    // week, or whose entitlement lands a moment after the screen, sees the report open
    // without ceremony.
    var buying by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) buying = true }
    val revealing = buying && unlocked
    val density = LocalDensity.current
    LaunchedEffect(unlocked) {
        if (unlocked && buying) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            val more = (state.groups.size - 1).coerceAtLeast(0)
            hint = if (more == 1) "Report open. 1 more clause." else "Report open. $more more clauses."
            delay(120)
            // The summary and the free card sit above the first new card; the place row
            // moves to the foot as the report opens.
            val firstNew = 2
            if (moving) {
                listState.animateScrollToItem(firstNew, -with(density) { 96.dp.roundToPx() })
            }
        }
    }

    // Cards in the first screenful rise in once; anything scrolled to later just appears.
    val settled = remember(state) { mutableStateOf(!moving) }
    LaunchedEffect(state) {
        delay(1200)
        settled.value = true
    }
    val onNudge: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        // Alternating the final character makes a repeated tap announce again.
        hint = if (hint.endsWith(".")) "Open them with the button below" else "Open them with the button below."
        if (moving) scope.launch {
            nudge.animateTo(1.04f, RedlineMotion.spatialBouncy())
            nudge.animateTo(1f, RedlineMotion.spatialBouncy())
        }
    }

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
            // Once the verdict panel scrolls away, its number waits here: every card below
            // is evidence for it, and a reader deep in the list should not have to scroll
            // back to remember what it adds up to.
            AnimatedVisibility(
                visible = pastSummary && state.looksLikeLease && state.flaggedClauses > 0,
                enter = fadeIn(countIn) + slideInVertically(countSlide) { it / 3 },
                exit = fadeOut(countOut),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                            append("${state.flaggedClauses}")
                        }
                        append(" of ${state.clauseCount}")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clearAndSetSemantics {
                            contentDescription = "${state.flaggedClauses} of ${state.clauseCount} clauses flagged"
                        },
                )
            }
            if (shareable) {
                IconButton(onClick = onShare) {
                    Icon(RedlineIcons.Share, contentDescription = "Send the full report")
                }
            }
        }

        Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().fadeTopEdge {
                if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset.toFloat()
            },
            // Extra room at the foot so the last card clears the pinned offer bar.
            // Without it the bar sits on top of the final card and crops it, which
            // reads as an unfinished screen rather than as a scroll position.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(state, locked = !unlocked && state.sellable, onShareCount = onShareCount) }

            val insight = state.insight
            if (state.looksLikeLease && state.flaggedClauses > 0) {
                item(key = "bento-title") { SectionTitle("Where the risk sits", Modifier.entrance(1, settled.value)) }
                item(key = "bento") {
                    BentoGrid(
                        categories = insight.categories,
                        // A tile opens the lease at the first clause in its category, marked up.
                        onOpen = { category ->
                            val first = state.groups.firstOrNull { g ->
                                g.findings.any { dev.kesav.redline.Insights.categoryOf(it.ruleId) == category }
                            }
                            onDocument(first?.clause?.index)
                        },
                        modifier = Modifier.entrance(2, settled.value),
                    )
                }
                insight.exposure?.takeIf { it.items.isNotEmpty() }?.let { exposure ->
                    item(key = "money") { MoneyCard(exposure, locked = locked, modifier = Modifier.entrance(3, settled.value)) }
                }
                if (place != null && insight.void.isNotEmpty()) {
                    item(key = "void") { VoidCard(place, insight.void, locked = locked, modifier = Modifier.entrance(4, settled.value)) }
                }
                item(key = "tools") {
                    ToolsRow(
                        onDocument = { onDocument(null) },
                        onDraft = onDraft,
                        onCompare = onCompare,
                        draftLocked = locked,
                        compareLocked = !pro,
                        modifier = Modifier.entrance(5, settled.value),
                    )
                }
                item(key = "clauses-title") {
                    SectionTitle(
                        "The clauses",
                        detail = if (state.flaggedClauses == 1) "1 flagged" else "${state.flaggedClauses} flagged, worst first",
                    )
                }
            }

            // While locked, under the free card, where its ask has just said "the local legal
            // limit" and the reader wants the number. Above it, the row pushed that card down.
            val showPlace = state.looksLikeLease && state.findings.isNotEmpty()
            val placeItem: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item(key = "place") { PlaceRow(place, onClick = onPlace, modifier = Modifier.animateItem()) }
            }

            if (!state.looksLikeLease) {
                item { NotALeaseNotice() }
            }

            if (locked) {
                // The first one is shown in full so the rest are a known quantity. The
                // rest are one list of subjects rather than a column of black bars: two
                // of six readers took the bars for a broken app and tapped them.
                state.groups.firstOrNull()?.let { first ->
                    item(key = "clause-${first.clause.index}") {
                        ClauseCard(first, Modifier.entrance(0, settled.value).animateItem())
                    }
                    if (showPlace) placeItem()
                }
                val rest = state.groups.drop(1)
                if (rest.isNotEmpty()) {
                    item(key = "locked") {
                        LockedIndex(
                            rest,
                            onTap = onNudge,
                            modifier = Modifier
                                .entrance(1, settled.value)
                                .animateItem(fadeOutSpec = motion(tween(150))),
                        )
                    }
                }
            } else {
                state.groups.forEachIndexed { i, group ->
                    item(key = "clause-${group.clause.index}") {
                        // After a purchase the index gives way to the clauses it named, each
                        // growing in a beat after the one above it.
                        val stagger = minOf(i, RedlineMotion.STAGGER_CAP) * RedlineMotion.STAGGER_MS
                        ClauseCard(
                            group,
                            modifier = Modifier
                                .entrance(i, settled.value)
                                .animateItem(
                                    fadeInSpec = motion(tween(240, delayMillis = stagger)),
                                    placementSpec = motion(RedlineMotion.spatial()),
                                ),
                            drawMark = revealing,
                        )
                    }
                }
                // Once every clause is open the row would split the report between its first
                // two cards, so it waits at the foot, where the asks above already carry it.
                if (showPlace) placeItem()
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
                            "See all ${Scanner.ruleCount} checks",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(RedlineIcons.Chevron, contentDescription = null)
                    }
                }
            }
        }

        // A card passing under the bar fades out instead of being sliced by its edge,
        // which three of six readers called unfinished.
        if (locked || shareable) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
                    )
            )
        }
        // Spoken only: the hint the locked index gives a screen reader when tapped.
        Box(
            Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (hint.isNotEmpty()) contentDescription = hint
                }
        )
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
        val barIn = motion(tween<Float>(220, delayMillis = 90))
        val barOut = motion(tween<Float>(150))
        AnimatedContent(
            targetState = locked,
            transitionSpec = { fadeIn(barIn) togetherWith fadeOut(barOut) },
            label = "bottomBar",
        ) { offer ->
        if (offer) {
            BottomBar(lifted) {
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
                    // At the largest font sizes three lines of this bar covered the free
                    // card entirely on arrival, so the line keeps only what the button
                    // cannot say: that the price is paid once and covers every lease.
                    val large = LocalDensity.current.fontScale > 1.3f
                    Text(
                        // The entitlement is lifetime. Without saying so, the price read as
                        // the cost of this one lease.
                        text = when {
                            large -> fromPrice?.let { "From $it, paid once." } ?: "Paid once."
                            fromPrice != null -> "From $fromPrice, paid once: each clause, what it costs you, and a reply to send."
                            else -> "Paid once: each clause, what it costs you, and a reply to send."
                        },
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
                val press = remember { MutableInteractionSource() }
                Button(
                    onClick = { if (!busy) onUnlock() },
                    enabled = known,
                    shape = MaterialTheme.shapes.medium,
                    interactionSource = press,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .graphicsLayer {
                            scaleX = nudge.value
                            scaleY = nudge.value
                        }
                        .pressScale(press)
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
                        Text(unlockLabel(state.groups.size - 1, null), style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(
                    onClick = onRestore,
                    enabled = known && !busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        // A second red button beside the red one made restoring look as
                        // loud as paying.
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text("Already bought it? Restore")
                }
            }
        } else if (shareable) {
            // Finding the clauses is half the job. The reader still has to raise them
            // with a landlord, a parent or a lawyer, and retyping nineteen findings into
            // a message is exactly where that stops happening.
            //
            // Two readers, two messages. The landlord gets the requests and nothing else;
            // a parent or an adviser gets the quoted clauses and the reasons.
            BottomBar(lifted) {
                val press = remember { MutableInteractionSource() }
                Button(
                    onClick = onLetter,
                    shape = MaterialTheme.shapes.medium,
                    interactionSource = press,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(press),
                ) {
                    Icon(RedlineIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Ask the landlord for these changes", style = MaterialTheme.typography.labelLarge)
                }
                // On a phone turned sideways the bar was a third of the screen. The share
                // icon in the header sends the same report, so the link can go when height
                // is short and the clauses get the room.
                if (LocalConfiguration.current.screenHeightDp >= 480) {
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
    }
}

/**
 * The pinned foot of the report.
 *
 * It casts a shadow only while there is content scrolled under it, because elevation over
 * nothing is decoration. In dark mode a shadow cannot be seen, so a hairline does the job.
 */
@Composable
private fun BottomBar(lifted: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val shadow by animateDpAsState(
        targetValue = if (lifted && !dark) 3.dp else 0.dp,
        animationSpec = motion(tween(150)),
        label = "barShadow",
    )
    Surface(
        color = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = shadow,
        border = if (dark) BorderStroke(1.dp, scheme.outlineVariant) else null,
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
 * The verdict: the risk score as a dial, then the count it comes from, the split by severity,
 * and the money the flagged clauses write down.
 *
 * The dial is what a reader sees in the first second, and it answers the only question they
 * arrived with (is this lease bad) before they read a word. The count under it is the evidence
 * for the dial, and it climbs in step with the sweep, because a figure that arrives already
 * sitting there reads as a label rather than as a result.
 */
@Composable
private fun Summary(state: ScanState.Scanned, locked: Boolean, onShareCount: () -> Unit) {
    val hero = LocalHero.current
    val flagged = state.flaggedClauses
    val insight = state.insight
    val heading = when {
        flagged == 0 -> "Nothing matched"
        !state.looksLikeLease -> "This does not read like a lease"
        else -> "$flagged of ${state.clauseCount} clauses could cost you money"
    }

    val moving = animationsEnabled()
    val shown = remember(state) { Animatable(if (moving) 0f else flagged.toFloat()) }
    val legend = remember(state) { Animatable(if (moving) 0f else 1f) }
    val visibility = LocalVisibilityScope.current
    LaunchedEffect(state) {
        if (!moving || flagged == 0) {
            shown.snapTo(flagged.toFloat())
            legend.snapTo(1f)
            return@LaunchedEffect
        }
        // The count starts when the Scan button has finished becoming this panel, so the
        // number climbs in place rather than inside a moving box.
        visibility?.let { v ->
            withTimeoutOrNull(900) { snapshotFlow { v.transition.isRunning }.first { !it } }
        }
        shown.animateTo(
            flagged.toFloat(),
            tween(durationMillis = (520 + 30 * flagged).coerceAtMost(900), easing = RedlineMotion.Decelerate),
        )
        legend.animateTo(1f, tween(150))
    }

    val glow = risk.ofScore(insight.score)
    Surface(
        color = hero.container,
        contentColor = hero.content,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.verdict().fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                // A soft glow in the tier's colour behind the dial, so the panel reads as lit
                // from the score rather than as a black box with a chart in it.
                .drawBehind {
                    if (flagged > 0 && state.looksLikeLease) {
                        val c = Offset(size.width / 2f, size.width * 0.36f)
                        drawCircle(
                            brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent), center = c, radius = size.width * 0.6f),
                            radius = size.width * 0.6f,
                            center = c,
                        )
                    }
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                RiskGauge(
                    score = insight.score,
                    tier = insight.tier,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 260.dp)
                        .fillMaxWidth(0.78f),
                )

                Column(Modifier.clearAndSetSemantics { contentDescription = heading; heading() }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        // Reserved at the final number's width, so " of 16 clauses" holds
                        // still while the digits run instead of sliding as they change.
                        val countStyle = FigureStyle.copy(fontSize = 40.sp, lineHeight = 40.sp)
                        val measurer = rememberTextMeasurer()
                        val finalWidth = with(LocalDensity.current) {
                            measurer.measure(flagged.toString(), countStyle).size.width.toDp()
                        }
                        Box(Modifier.width(finalWidth), contentAlignment = Alignment.BottomStart) {
                            Text(
                                text = shown.value.toInt().toString(),
                                style = countStyle,
                                color = hero.accent,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Text(
                            text = " of ${state.clauseCount} clauses",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text("could cost you money", style = MaterialTheme.typography.titleLarge)
                }

                SeverityBar(
                    serious = state.highClauses,
                    other = flagged - state.highClauses,
                    clear = (state.clauseCount - flagged).coerceAtLeast(0),
                    spoken = severitySplit(state.highClauses, flagged),
                    filled = { if (flagged == 0) 1f else shown.value / flagged },
                    legendAlpha = { legend.value },
                )

                insight.exposure?.takeIf { it.total > 0 }?.let { exposure ->
                    ExposureLine(exposure, alpha = { legend.value })
                }

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
                            Text("Share the score, not the lease", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The money the flagged clauses put in writing, as one figure on the dark panel. Every rupee
 * or dollar in it is a number printed in the lease itself; the breakdown card below says which.
 */
@Composable
private fun ExposureLine(exposure: dev.kesav.redline.Exposure, alpha: () -> Float) {
    val hero = LocalHero.current
    val spoken = "${money(exposure.symbol, exposure.total)} written into the flagged clauses"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha() }
            .clip(MaterialTheme.shapes.medium)
            .background(hero.content.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(RedlineIcons.Wallet, tint = hero.accent, container = hero.accent.copy(alpha = 0.16f), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Eyebrow("Money at stake", color = hero.muted)
            Text(
                money(exposure.symbol, exposure.total),
                style = FigureStyle.copy(fontSize = 26.sp, lineHeight = 30.sp),
                color = hero.content,
            )
        }
        Text(
            text = if (exposure.items.size == 1) "in 1 clause" else "in ${exposure.items.size} clauses",
            style = MaterialTheme.typography.labelMedium,
            color = hero.muted,
        )
    }
}

/** Serious, worth checking and clear, as one bar the width of the card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityBar(
    serious: Int,
    other: Int,
    clear: Int,
    spoken: String,
    filled: () -> Float = { 1f },
    legendAlpha: () -> Float = { 1f },
) {
    val hero = LocalHero.current
    val amber = MaterialTheme.colorScheme.tertiary
    val total = (serious + other + clear).coerceAtLeast(1)
    // Read aloud as one sentence rather than as three coloured boxes and three labels.
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            // Fills left to right in step with the count above it.
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .drawWithContent {
                    clipRect(right = size.width * filled().coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (serious > 0) Box(Modifier.weight(serious.toFloat() / total).fillMaxHeight().background(hero.accent))
            if (other > 0) Box(Modifier.weight(other.toFloat() / total).fillMaxHeight().background(amber))
            if (clear > 0) Box(Modifier.weight(clear.toFloat() / total).fillMaxHeight().background(hero.content.copy(alpha = 0.18f)))
        }
        // Wraps rather than squeezes: at the largest font size a Row stood "5 clear" on
        // end, one letter per line.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.graphicsLayer { alpha = legendAlpha() },
        ) {
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
    high == 0 -> "None is in the worst band, and all $flagged are still worth raising."
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

/** The word for a severity, the same on the card, in the legend and when read aloud. */
internal fun severityWord(severity: Severity): String = when (severity) {
    Severity.HIGH -> "Serious"
    Severity.MEDIUM -> "Worth checking"
}

@Composable
private fun ClauseCard(group: ClauseGroup, modifier: Modifier = Modifier, drawMark: Boolean = false) {
    val count = group.findings.size
    val problems = if (count == 1) "1 problem" else "$count problems"
    val scheme = MaterialTheme.colorScheme
    val mark = if (group.worst == Severity.HIGH) scheme.primary else scheme.tertiary

    // The clause text belongs in here. Leaving it out told a screen reader that a costly
    // clause existed and what it was called, then withheld the sentence the whole app
    // exists to show, which is the one thing a sighted reader gets for free by looking
    // down two lines.
    val spoken = buildString {
        append("${severityWord(group.worst)}, $problems. ")
        for (f in group.findings) {
            append("${f.headline}. ${f.reason} ")
            if (f.ask.isNotBlank()) append("Ask for ${f.ask}. ")
        }
        append("The clause reads: ${group.clause.text}")
    }

    // On a card opened by the purchase, the margin mark draws downward: the pen marking
    // the clause as it opens.
    val moving = animationsEnabled()
    val markDrawn = remember { Animatable(if (drawMark && moving) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (markDrawn.value < 1f) markDrawn.animateTo(1f, tween(280, delayMillis = 120, easing = RedlineMotion.Decelerate))
    }

    Surface(
        color = scheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeverityMark(group.worst)
                Text(problems, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            // The subject, in grey: the mark and the margin already carry the colour.
            Text(
                text = group.findings.first().topic,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )

            // A margin mark beside what the scanner says, the way a lawyer marks a clause.
            // Drawn behind the column so it follows the text at any font scale, and inset
            // rather than run down the card edge, where the corner radius clipped it.
            Column(
                modifier = Modifier
                    .drawBehind {
                        val w = 4.dp.toPx()
                        drawRoundRect(
                            color = mark,
                            topLeft = Offset(0f, 2.dp.toPx()),
                            size = Size(w, (size.height - 4.dp.toPx()) * markDrawn.value),
                            cornerRadius = CornerRadius(w / 2),
                        )
                    }
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (f in group.findings) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.headline, style = MaterialTheme.typography.titleMedium)
                        Text(f.reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        if (f.ask.isNotBlank()) Ask(f.ask, Modifier.padding(top = 6.dp))
                    }
                }
            }

            // Quoted once at the foot of the card, however many rules it tripped.
            // Repeating the paragraph under every finding was read as padding the count
            // rather than as thoroughness.
            Quote(group.clause.text, Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * A card in the first screenful of a report rises 24dp and fades in, a beat after the one
 * above it. Anything composed after the report has settled appears without motion, because
 * a list that re-animates on every scroll is noise.
 */
@Composable
private fun Modifier.entrance(index: Int, settled: Boolean): Modifier {
    val moving = animationsEnabled()
    val play = remember { moving && !settled }
    val rise = remember { Animatable(if (play) 24f else 0f) }
    val fade = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            // The stagger rides on the animation clock rather than a coroutine delay, so it
            // pauses, scales and is tested like every other part of the motion.
            val wait = minOf(index, RedlineMotion.STAGGER_CAP) * RedlineMotion.STAGGER_MS
            launch { rise.animateTo(0f, tween(360, delayMillis = wait, easing = RedlineMotion.Decelerate)) }
            fade.animateTo(1f, tween(240, delayMillis = wait))
        }
    }
    return graphicsLayer {
        translationY = rise.value * density
        alpha = fade.value
    }
}

/** A small square in the severity colour and the word beside it. No container. */
@Composable
private fun SeverityMark(severity: Severity) {
    val scheme = MaterialTheme.colorScheme
    val color = if (severity == Severity.HIGH) scheme.primary else scheme.tertiary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(severityWord(severity), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

private data class LockedTopic(val name: String, val clauses: Int, val serious: Boolean)

/**
 * What the purchase opens, named by subject, in one card.
 *
 * The subjects are public already (the checks sheet lists them), and saying them in words
 * is the honest way to sell what is behind the lock. Topics that repeat merge into one row
 * with a clause count, so "What you pay for" is never printed twice in a row, and the
 * counts add up to the same number the button offers.
 */
@Composable
private fun LockedIndex(locked: List<ClauseGroup>, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val topics = remember(locked) {
        locked.groupBy { it.findings.first().topic }
            .map { (name, groups) -> LockedTopic(name, groups.size, groups.any { it.worst == Severity.HIGH }) }
            .sortedWith(compareBy { !it.serious })
    }
    val more = if (locked.size == 1) "1 more clause, locked" else "${locked.size} more clauses, locked"
    // The rows mark severity by colour alone, so the spoken version says it in words.
    val spoken = "$more. In the full report: each clause quoted, why it costs you, and what to ask for. " +
        topics.joinToString("; ") {
            "${it.name}, " + (if (it.clauses == 1) "1 clause" else "${it.clauses} clauses") +
                if (it.serious) ", serious" else ", worth checking"
        }
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onTap,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLowest,
        interactionSource = press,
        modifier = modifier
            .pressScale(press)
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "show how to open them") { onTap(); true }
            },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(RedlineIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurface)
                Text(more, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "In the full report: each clause quoted, why it costs you, and what to ask for.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for (t in topics) {
                HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 3.dp, height = 20.dp)
                            .background(if (t.serious) scheme.primary else scheme.tertiary, RoundedCornerShape(1.5.dp))
                    )
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    Text(
                        if (t.clauses == 1) "1 clause" else "${t.clauses} clauses",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
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

/** Where the camera app writes a page. One file, reused and deleted after each read. */
private fun pagePhoto(context: android.content.Context): File =
    File(context.cacheDir, "pages").apply { mkdirs() }.resolve("page.jpg")
