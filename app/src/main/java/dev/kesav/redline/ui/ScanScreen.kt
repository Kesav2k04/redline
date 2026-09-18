package dev.kesav.redline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kesav.redline.Clause
import dev.kesav.redline.R
import dev.kesav.redline.ScanState
import dev.kesav.redline.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sharedText: String = "",
    viewModel: ScanViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(sharedText) { viewModel.seed(sharedText) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { padding ->
        when (val state = ui.state) {
            ScanState.Editing -> Editor(
                text = ui.text,
                onText = viewModel::edit,
                onScan = viewModel::scan,
                modifier = Modifier.padding(padding),
            )

            is ScanState.Scanned -> Results(
                clauses = state.clauses,
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
    }
}

@Composable
private fun Results(
    clauses: List<Clause>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${clauses.size} clauses found",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(clauses, key = { it.index }) { clause ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = clause.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Text("Edit text")
        }
    }
}
