package dev.kesav.redline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
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
    viewModel: ScanViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(sharedText) { viewModel.seed(sharedText) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val state = ui.state) {
            ScanState.Editing -> Editor(
                text = ui.text,
                onText = viewModel::edit,
                onScan = viewModel::scan,
                onSample = viewModel::loadSample,
                modifier = Modifier.padding(padding),
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
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun Editor(
    text: String,
    onText: (String) -> Unit,
    onScan: () -> Unit,
    onSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Paste your lease, or share it here from any app.",
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Lease text" },
            label = { Text("Lease text") },
            minLines = 10,
            maxLines = 16,
        )

        Button(
            onClick = onScan,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Scan")
        }

        TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Try it on a sample lease")
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
    modifier: Modifier = Modifier,
) {
    val flagged = state.flaggedClauses

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = if (flagged == 0) {
                    "Nothing matched"
                } else {
                    "$flagged of ${state.clauseCount} clauses will cost you money"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (flagged == 0) {
                    "No rule matched this text. That is not the same as a clean lease."
                } else {
                    "${state.highClauses} of them are worth arguing about before you sign."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.findings) { index, finding ->
                // The first one is shown in full so the rest are a known quantity.
                FindingCard(finding, revealed = unlocked || index == 0, row = index)
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
        if (!unlocked && state.findings.size > 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onUnlock,
                    enabled = known && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            if (busy) contentDescription = "Completing your purchase"
                        },
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        // "findings", not a bare number. The heading counts clauses and
                        // this counts findings, and without the noun the two numbers
                        // read as a contradiction.
                        Text(
                            "Show the other ${state.findings.size - 1} findings" +
                                (price?.let { " for $it" } ?: "")
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onRestore,
                        enabled = known && !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Already bought it? Restore")
                    }
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Edit text")
                    }
                }
            }
        } else if (state.findings.isNotEmpty()) {
            // Finding the clauses is half the job. The reader still has to raise them
            // with a landlord, a parent or a lawyer, and retyping nineteen findings into
            // a message is exactly where that stops happening.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Send this list")
                }
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Edit text")
                }
            }
        } else {
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
            ) {
                Text("Edit text")
            }
        }
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
private fun animationsEnabled(): Boolean {
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

@Composable
private fun FindingCard(finding: Finding, revealed: Boolean, row: Int = 0) {
    val spoken = if (revealed) {
        "${finding.severity.name.lowercase()} risk. ${finding.headline}. ${finding.reason}"
    } else {
        "Locked finding, ${finding.severity.name.lowercase()} risk. Buy the report to read it."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        // A filled Material 3 card sits at elevation zero, and this one's container is the
        // same paper the page is printed on, so without a hairline there was nothing to
        // say where one finding stopped and the next began. Squinting at the screen gave
        // a single column of text. A border reads better than a shadow here, because the
        // whole surface is meant to look like a document rather than a stack of tiles.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val delay = if (animationsEnabled()) {
            minOf(row, REVEAL_STAGGER_CAP) * REVEAL_STAGGER_MS
        } else {
            0
        }
        val duration = if (animationsEnabled()) REVEAL_MS else 0

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeverityChip(finding.severity)

                // Without this word the three grey bars below are a loading skeleton,
                // which is the single most expensive misread available: a reviewer
                // watching the video concludes the app is still fetching rather than
                // that there is something behind a paywall.
                if (!revealed) {
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    fadeIn(tween(duration, delayMillis = delay)) togetherWith
                        fadeOut(tween(duration / 2))
                },
                label = "finding",
            ) { open ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open) {
                Text(finding.headline, style = MaterialTheme.typography.titleMedium)
                Text(finding.reason, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = finding.clause.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Redaction rather than a blur: Modifier.blur does nothing below API 31
                // and would quietly show the text it is meant to hide.
                //
                // Widths come from the finding rather than being fixed, because four
                // identical cards in a row read as one component repeated, and a
                // repeated component reads as a placeholder. Sentences are not all the
                // same length.
                val bars = redactionWidths(finding.headline)
                Redacted(widthFraction = bars[0], height = 16.dp)
                Redacted(widthFraction = bars[1], height = 12.dp)
                Redacted(widthFraction = bars[2], height = 12.dp)
            }
            }
            }
        }
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
    val (label, tint) = when (severity) {
        Severity.HIGH -> "Costly" to MaterialTheme.colorScheme.primary
        Severity.MEDIUM -> "Worth checking" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 8.dp, height = 8.dp)
                .background(tint, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
