package dev.kesav.redline.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Offer
import dev.kesav.redline.Plan
import dev.kesav.redline.ScanState
import dev.kesav.redline.Severity

/** Why the paywall opened, so its headline can answer the tap that raised it. */
internal enum class PaywallReason { REPORT, DRAFT, COMPARE }

/**
 * The paywall, as a sheet over the report the reader has just seen scored.
 *
 * It opens only after the value has landed (the dial, the count, the money and the first
 * clause are all free), and it says what the payment opens in this lease's own numbers rather
 * than in adjectives. Two plans at most, read from the RevenueCat offering by package type: a
 * pass for this lease, and Pro for every lease plus the comparison. Prices come from the store
 * in the reader's own currency. The terms sit above the button, not under a fold, and "Not
 * now" is as easy to hit as the purchase.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PaywallSheet(
    state: ScanState.Scanned,
    offers: List<Offer>,
    reason: PaywallReason,
    busy: Boolean,
    known: Boolean,
    pitch: String?,
    onBuy: (Plan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    storeKey: Boolean = true,
    message: String? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val colors = risk
    val scheme = MaterialTheme.colorScheme

    // The comparison is Pro only, so a tap on Compare preselects Pro; everything else starts on
    // the smaller purchase, because the reader came to open one lease.
    val passOffer = offers.firstOrNull { it.plan == Plan.PASS }
    val proOffers = offers.filter { it.plan != Plan.PASS }
    val defaultPlan = when {
        reason == PaywallReason.COMPARE -> proOffers.firstOrNull()?.plan
        else -> passOffer?.plan ?: proOffers.firstOrNull()?.plan
    }
    var chosen by rememberSaveable(offers.map { it.plan }) { mutableStateOf(defaultPlan) }
    // Without a store key there is nothing to reach, so asking again would only spin.
    LaunchedEffect(offers.isEmpty()) { if (offers.isEmpty() && storeKey) onRetry() }

    val locked = (state.groups.size - 1).coerceAtLeast(0)
    val serious = state.groups.drop(1).count { it.worst == Severity.HIGH }
    val exposure = state.insight.exposure

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.surface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .navigationBarsPadding()
                .padding(bottom = Space.l),
        ) {
            Eyebrow(
                when (reason) {
                    PaywallReason.REPORT -> "Full report"
                    PaywallReason.DRAFT -> "Negotiation drafts"
                    PaywallReason.COMPARE -> "Lease comparison"
                },
                color = colors.high,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                text = when (reason) {
                    PaywallReason.COMPARE -> "Hold two leases side by side"
                    PaywallReason.DRAFT -> "Send the landlord fairer wording"
                    else -> if (locked == 1) "See the other clause, and what to ask for" else "See the other $locked clauses, and what to ask for"
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.s))
            Text(
                text = pitch ?: buildString {
                    if (serious > 0) append(if (serious == 1) "1 of them is serious. " else "$serious of them are serious. ")
                    val total = exposure?.total()
                    when {
                        exposure == null || exposure.oneOff.isEmpty() -> Unit
                        total != null && total > 0 -> append("The lease puts ${money(exposure.symbol, total)} on the line in writing. ")
                        exposure.months > 0 -> append("The lease puts ${monthsText(exposure.months)} of rent on the line in writing. ")
                    }
                    append("Every clause quoted, why it costs you, and a reply to send.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.xl))
            Benefit(RedlineIcons.Documents, "Each flagged clause, quoted in full", "With the reason and the fair wording to ask for")
            Benefit(RedlineIcons.Calculator, "Where the money goes", "Every sum the lease states, and the clause it sits in")
            Benefit(RedlineIcons.Pen, "Replies drafted for you", "An email and a WhatsApp message, one tap to send")
            Benefit(RedlineIcons.Download, "The report as a PDF", "To hand to a parent, a friend or a lawyer")

            Spacer(Modifier.height(Space.xl))
            if (offers.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    if (storeKey) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        noOffersLine(storeKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    passOffer?.let { offer ->
                        PlanCard(
                            offer = offer,
                            title = "This lease",
                            detail = "The full report for the lease on screen, on this phone.",
                            terms = "Pay once",
                            badge = null,
                            selected = chosen == offer.plan,
                            onSelect = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                chosen = offer.plan
                            },
                        )
                    }
                    proOffers.forEachIndexed { i, offer ->
                        // Two cards both called "Renter Pro" with the same line under them read as
                        // one plan listed twice, so each says how it is paid and who it suits.
                        val several = proOffers.size > 1
                        PlanCard(
                            offer = offer,
                            title = when {
                                !several -> "Renter Pro"
                                offer.plan == Plan.PRO_LIFETIME -> "Renter Pro for good"
                                offer.plan == Plan.PRO_ANNUAL -> "Pro by the year"
                                else -> "Pro by the month"
                            },
                            detail = when {
                                !several || i == 0 -> "Every lease you scan, plus comparing two side by side. For flat hunting."
                                offer.plan == Plan.PRO_MONTHLY -> "The same while you are looking. Cancel once you have signed."
                                else -> "The same, renewed each year."
                            },
                            terms = when (offer.plan) {
                                Plan.PRO_LIFETIME -> "Pay once, keep it"
                                Plan.PRO_ANNUAL -> offer.trial?.let { "$it, then yearly" } ?: if (several) "Cancel any time" else "Yearly, cancel any time"
                                Plan.PRO_MONTHLY -> offer.trial?.let { "$it, then monthly" } ?: if (several) "Cancel any time" else "Monthly, cancel any time"
                                Plan.PASS -> ""
                            },
                            badge = if (passOffer != null && i == 0) "Best value" else null,
                            selected = chosen == offer.plan,
                            onSelect = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                chosen = offer.plan
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Assure(RedlineIcons.ShieldCheck, "Lease text stays on this phone")
                Assure(RedlineIcons.Verified, "No account needed")
                // A pass is remembered on the phone against the lease, so only Pro comes back
                // after an uninstall. Saying so under the pass would be the one untrue line here.
                if (chosen != Plan.PASS) Assure(RedlineIcons.Restore, "Restores on reinstall")
            }

            Spacer(Modifier.height(Space.l))
            // What the last purchase or restore came to. The snackbar draws under this sheet,
            // so a message sent there is never seen from here.
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.m)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val offer = offers.firstOrNull { it.plan == chosen }
            val press = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (!busy && offer != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onBuy(offer.plan)
                    }
                },
                enabled = known && offer != null,
                shape = MaterialTheme.shapes.medium,
                interactionSource = press,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(press),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text(ctaLabel(offer), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(Space.s))
            Text(
                text = termsLine(offer),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = onRestore,
                    enabled = known && !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant),
                ) { Text("Restore purchase") }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant),
                ) { Text("Not now") }
            }
        }
    }
}

/**
 * What stands in for the plan cards while there are no offers. With a store key the wait is
 * real and ends; a build made without one never gets offers, so it says so once and stops.
 */
internal fun noOffersLine(storeKey: Boolean): String =
    if (storeKey) "Reaching the store for prices. Your scan is kept."
    else "This build has no store key, so purchases are off. The v1.0.0 Release APK on GitHub has them."

internal fun ctaLabel(offer: Offer?): String = when {
    offer == null -> "Choose a plan"
    offer.plan == Plan.PASS -> "Open this report for ${offer.price}"
    offer.trial != null -> "Start ${offer.trial}"
    offer.plan == Plan.PRO_LIFETIME -> "Get Renter Pro for ${offer.price}"
    offer.plan == Plan.PRO_ANNUAL -> "Get Renter Pro, ${offer.price} a year"
    else -> "Get Renter Pro, ${offer.price} a month"
}

/** The whole deal in one sentence, above the fold and next to the button that makes it. */
internal fun termsLine(offer: Offer?): String = when (offer?.plan) {
    null -> "Prices come from the store in your own currency."
    Plan.PASS -> "One payment of ${offer.price}. Opens this lease only, on this phone. No subscription."
    Plan.PRO_LIFETIME -> "One payment of ${offer.price}. Every lease you scan, for as long as you keep the app. No subscription."
    Plan.PRO_ANNUAL, Plan.PRO_MONTHLY -> {
        val every = if (offer.plan == Plan.PRO_ANNUAL) "year" else "month"
        if (offer.trial != null) {
            "${offer.trial}, then ${offer.price} a $every until you cancel in the store. Cancel before the trial ends and nothing is charged."
        } else {
            "${offer.price} a $every until you cancel in the store."
        }
    }
}

@Composable
private fun Benefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.onSurface, size = 40.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest)
        Column(Modifier.padding(start = Space.m)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Assure(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = risk.low)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanCard(
    offer: Offer,
    title: String,
    detail: String,
    terms: String,
    badge: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, motion(RedlineMotion.effects()), label = "edge")
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, motion(RedlineMotion.effects()), label = "edgeWidth")
    val press = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainer,
        border = BorderStroke(width, edge),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(press)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton, interactionSource = press, indication = null),
    ) {
        Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
            Radio(selected)
            Column(Modifier.weight(1f).padding(horizontal = Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) Badge(badge, scheme.primary, onColor = scheme.onPrimary)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(offer.price, style = FigureStyle.copy(fontSize = 20.sp))
                Text(terms, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent, motion(RedlineMotion.effects()), label = "radio")
    Box(
        Modifier
            .size(22.dp)
            .border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape)
            .padding(5.dp),
    ) {
        Box(Modifier.size(12.dp).background(fill, CircleShape))
    }
}
