package dev.kesav.redline.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Drafts
import dev.kesav.redline.Report
import dev.kesav.redline.ScanState
import dev.kesav.redline.Severity

/**
 * The negotiation drafter: pick the channel, the tone and the clauses, read the message as the
 * landlord will, and send it through the reader's own apps.
 *
 * The preview is the message itself, updated as each choice changes, so nothing is sent that
 * the reader has not read. Serious clauses start ticked and the rest do not, because ten
 * requests is a lot to put to a landlord with other applicants waiting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftSheet(state: ScanState.Scanned, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    var channel by rememberSaveable { mutableStateOf(Drafts.Channel.EMAIL) }
    var tone by rememberSaveable { mutableStateOf(Drafts.Tone.FRIENDLY) }
    var chosen by rememberSaveable(state) {
        mutableStateOf(state.groups.filter { it.worst == Severity.HIGH }.map { it.clause.index }.ifEmpty { state.groups.map { it.clause.index } })
    }
    val groups = state.groups.filter { it.clause.index in chosen }
    val text = Drafts.draft(groups, channel, tone, state.insight.void)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding()
                .padding(bottom = Space.l),
        ) {
            Eyebrow("Negotiation draft", color = risk.high)
            Spacer(Modifier.height(Space.s))
            Text("Ask for fairer wording", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Space.xs))
            Text(
                "Each clause you tick gets a replacement the landlord can agree to, not just a complaint.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.l))
            Segmented(
                options = listOf(
                    Triple(Drafts.Channel.EMAIL, "Email", RedlineIcons.Letter),
                    Triple(Drafts.Channel.WHATSAPP, "WhatsApp", RedlineIcons.Chat),
                ),
                selected = channel,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    channel = it
                },
            )
            Spacer(Modifier.height(Space.s))
            Segmented(
                options = listOf(
                    Triple(Drafts.Tone.FRIENDLY, "Friendly", RedlineIcons.Star),
                    Triple(Drafts.Tone.FIRM, "Firm, with the law", RedlineIcons.Law),
                ),
                selected = tone,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    tone = it
                },
            )

            Spacer(Modifier.height(Space.l))
            // The checklist folds away, so the message itself is what the sheet opens on.
            var editing by rememberSaveable { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.surfaceContainer)
                    .animateContentSize(motion(RedlineMotion.expand()))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(value = editing, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            editing = it
                        }
                        .padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RedlineIcons.Checks, null, Modifier.size(18.dp), tint = scheme.onSurface)
                    Text(
                        if (chosen.size == 1) "1 clause in the message" else "${chosen.size} clauses in the message",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = Space.m).weight(1f),
                    )
                    Text(if (editing) "Done" else "Change", style = MaterialTheme.typography.labelLarge, color = risk.high)
                }
                if (editing) {
                    for (g in state.groups) {
                        val on = g.clause.index in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .toggleable(value = on, role = Role.Checkbox) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    chosen = if (on) chosen - g.clause.index else chosen + g.clause.index
                                }
                                .padding(horizontal = Space.s, vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Box(
                                Modifier
                                    .padding(start = Space.s)
                                    .size(width = 3.dp, height = 22.dp)
                                    .background(risk.of(g.worst), RoundedCornerShape(2.dp))
                            )
                            Column(Modifier.padding(start = Space.m).weight(1f)) {
                                Text(g.findings.first().headline, style = MaterialTheme.typography.bodyMedium)
                                Text(Report.where(g.clause.text), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                }
            }

            Spacer(Modifier.height(Space.l))
            Eyebrow("Preview")
            Spacer(Modifier.height(Space.s))
            val inSpec = motion(androidx.compose.animation.core.tween<Float>(160))
            val outSpec = motion(androidx.compose.animation.core.tween<Float>(90))
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(inSpec) togetherWith fadeOut(outSpec) },
                label = "draft",
            ) { shown ->
                Surface(
                    shape = if (channel == Drafts.Channel.WHATSAPP) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp) else MaterialTheme.shapes.medium,
                    color = if (channel == Drafts.Channel.WHATSAPP) risk.lowTint else scheme.surfaceContainer,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        shown ?: "Tick at least one clause to write the message.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = if (shown == null) scheme.onSurfaceVariant else scheme.onSurface,
                        modifier = Modifier.padding(Space.l),
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            val press = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    val body = text ?: return@Button
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    context.startActivity(sendIntent(context, body, channel))
                },
                enabled = text != null,
                shape = MaterialTheme.shapes.medium,
                interactionSource = press,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(press),
            ) {
                Icon(if (channel == Drafts.Channel.WHATSAPP) RedlineIcons.Chat else RedlineIcons.Send, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (channel == Drafts.Channel.WHATSAPP) "Open in WhatsApp" else "Write the email", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(Space.s))
            OutlinedButton(
                onClick = {
                    val body = text ?: return@OutlinedButton
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText(Drafts.SUBJECT, body))
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                },
                enabled = text != null,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(RedlineIcons.Copy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Copy the text")
            }
        }
    }
}

/**
 * WhatsApp by its package when it is installed, and the system chooser otherwise, so the button
 * never dead-ends on a phone without it. Email goes through the chooser with a subject line.
 */
private fun sendIntent(context: Context, body: String, channel: Drafts.Channel): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        if (channel == Drafts.Channel.EMAIL) putExtra(Intent.EXTRA_SUBJECT, Drafts.SUBJECT)
    }
    if (channel == Drafts.Channel.WHATSAPP) {
        val whatsapp = Intent(send).setPackage("com.whatsapp")
        if (whatsapp.resolveActivity(context.packageManager) != null) return whatsapp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return Intent.createChooser(send, if (channel == Drafts.Channel.EMAIL) "Send the email" else "Send the message")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun <T> Segmented(options: List<Triple<T, String, ImageVector>>, selected: T, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerHighest)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label, icon) in options) {
            val on = value == selected
            val bg by animateColorAsState(if (on) scheme.surfaceContainerLowest else scheme.surfaceContainerHighest, motion(RedlineMotion.effects()), label = "seg")
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .selectable(selected = on, role = Role.Tab) { onSelect(value) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (on) scheme.onSurface else scheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) scheme.onSurface else scheme.onSurfaceVariant)
            }
        }
    }
}
