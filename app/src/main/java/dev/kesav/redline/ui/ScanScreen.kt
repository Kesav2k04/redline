package dev.kesav.redline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ContextWrapper
import dev.kesav.redline.Finding
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
                busy = ui.busy,
                price = ui.offer?.product?.price?.formatted,
                onUnlock = { activity?.let(viewModel::buy) },
                onRestore = viewModel::restore,
                onBack = viewModel::back,
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan")
        }

        TextButton(onClick = onSample, modifier = Modifier.fillMaxWidth()) {
            Text("Try it on a sample lease")
        }
    }
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
    busy: Boolean,
    price: String?,
    onUnlock: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flagged = state.flaggedClauses

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = if (flagged == 0) {
                    "Nothing matched"
                } else {
                    "$flagged of ${state.clauseCount} clauses will cost you money"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (flagged == 0) {
                    "No rule matched this text. That is not the same as a clean lease."
                } else {
                    "${state.high} of them are worth arguing about before you sign."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.findings) { index, finding ->
                // The first one is shown in full so the rest are a known quantity.
                FindingCard(finding, revealed = unlocked || index == 0)
            }

            if (!unlocked && state.findings.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onUnlock,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.height(18.dp))
                            } else {
                                Text(
                                    "Show the other ${state.findings.size - 1}" +
                                        (price?.let { " for $it" } ?: "")
                                )
                            }
                        }
                        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                            Text("Already bought it? Restore")
                        }
                    }
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Edit text")
        }
    }
}

@Composable
private fun FindingCard(finding: Finding, revealed: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (revealed) {
                    "${finding.severity.name.lowercase()} risk. ${finding.headline}. ${finding.reason}"
                } else {
                    "Locked finding, ${finding.severity.name.lowercase()} risk"
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityChip(finding.severity)

            if (revealed) {
                Text(finding.headline, style = MaterialTheme.typography.titleMedium)
                Text(finding.reason, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = finding.clause.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                // Redaction rather than a blur: Modifier.blur does nothing below API 31
                // and would quietly show the text it is meant to hide.
                Redacted(widthFraction = 0.72f, height = 18.dp)
                Redacted(widthFraction = 0.95f, height = 12.dp)
                Redacted(widthFraction = 0.55f, height = 12.dp)
            }
        }
    }
}

@Composable
private fun Redacted(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f),
                RoundedCornerShape(3.dp),
            )
    )
}

@Composable
private fun SeverityChip(severity: Severity) {
    val (label, tint) = when (severity) {
        Severity.HIGH -> "Costly" to MaterialTheme.colorScheme.primary
        Severity.MEDIUM -> "Worth checking" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .height(10.dp)
                .fillMaxWidth(0.022f)
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
