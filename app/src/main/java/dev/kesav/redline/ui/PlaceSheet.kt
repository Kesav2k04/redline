package dev.kesav.redline.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kesav.redline.Place

/**
 * The line under the verdict that asks where the home is, and once answered, says whose
 * limits the asks below are using. One tap from the report, because the moment a reader
 * sees "the local legal limit" is the moment they want the number.
 */
@Composable
internal fun PlaceRow(place: Place?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLowest,
        interactionSource = press,
        modifier = modifier
            .pressScale(press)
            .fillMaxWidth()
            .semantics { onClick(label = if (place == null) "choose where the home is" else "change the place") { onClick(); true } },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (place == null) "Where is the home?" else "Limits for ${place.label}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (place == null) {
                        "Choose a place and each ask names its legal limit."
                    } else {
                        "The asks below use its figures where its law sets one."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (place == null) "Choose" else "Change",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }
    }
}

/**
 * Where the choice is kept answers the question a privacy-minded reader asks before tapping.
 * What it changes is said too: a place moves the limits, so flags and the score can move with
 * it, and a reader who was told only the asks change would read that as a fault.
 */
internal const val PLACE_NOTE =
    "Kept on this phone. It sets the local limits the lease is checked against, so the flags, the score and the asks can change."

/** The places with sourced figures, and a way to say "somewhere else". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSheet(current: Place?, onChoose: (Place?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = "Where is the home?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = PLACE_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            for (place in Place.entries) {
                PlaceOption(place.label, selected = place == current) { onChoose(place) }
            }
            PlaceOption("Somewhere else", selected = current == null) { onChoose(null) }
            Text(
                text = "Somewhere else keeps the general wording, because a limit the app " +
                    "has not checked is worse than none.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PlaceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
