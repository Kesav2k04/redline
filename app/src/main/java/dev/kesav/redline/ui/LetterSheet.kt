package dev.kesav.redline.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kesav.redline.Report
import dev.kesav.redline.ScanState
import dev.kesav.redline.Severity

/**
 * The letter to the landlord, seen before it goes.
 *
 * It used to go straight to the share sheet with every ask in it. Ten requests is a lot to
 * put to a landlord with other applicants waiting, and a reader who never saw the letter
 * could not choose what to lead with. Serious clauses start ticked and the rest do not; the
 * preview underneath is exactly the text that will be sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LetterSheet(state: ScanState.Scanned, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LetterChooser(state, onSend)
    }
}

/** The sheet's content, separate so it can be rendered without a window of its own. */
@Composable
internal fun ColumnScope.LetterChooser(state: ScanState.Scanned, onSend: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val chosen: SnapshotStateList<Int> = rememberSaveable(
        state,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) {
        val serious = state.groups.filter { it.worst == Severity.HIGH }.map { it.clause.index }
        // A lease with nothing serious still gets a letter: everything starts ticked.
        serious.ifEmpty { state.groups.map { it.clause.index } }.toMutableStateList()
    }
    val letter = Report.letter(state, chosen.toSet())

    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Your letter to the landlord",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Choose what to ask for. Serious clauses start ticked.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        for (group in state.groups) {
            val index = group.clause.index
            val on = index in chosen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = on, role = Role.Checkbox) {
                        if (it) chosen.add(index) else chosen.remove(index)
                    }
                    .padding(vertical = 6.dp),
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.findings.first().headline, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = Report.where(group.clause.text) + "  ·  " + severityWord(group.worst),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "What will be sent",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Surface(
            color = scheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = letter ?: "Tick at least one clause and the letter appears here.",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (letter != null) FontFamily.Serif else null,
                color = if (letter != null) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    val press = remember { MutableInteractionSource() }
    Button(
        onClick = { letter?.let(onSend) },
        enabled = letter != null,
        shape = MaterialTheme.shapes.medium,
        interactionSource = press,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(press),
    ) {
        Icon(RedlineIcons.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = when (val n = chosen.size) {
                0 -> "Send the letter"
                1 -> "Send the letter, 1 clause"
                else -> "Send the letter, $n clauses"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
