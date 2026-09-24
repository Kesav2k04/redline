package dev.kesav.redline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kesav.redline.Exposure
import dev.kesav.redline.Place
import dev.kesav.redline.VoidClause

/** The label over each section of the report. Spoken as a heading, so a screen reader can jump. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Space.m, start = Space.xs, end = Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Months of rent as words a person says: "10 months", "1 month", "1.5 months". */
internal fun monthsText(months: Double): String {
    val n = if (months % 1.0 == 0.0) months.toLong().toString() else "%.1f".format(months)
    return if (n == "1") "1 month" else "$n months"
}

/** A cost the way the card shows it: money where the rent is known, the lease's own unit where not. */
internal fun costText(exposure: Exposure, cost: dev.kesav.redline.Cost, rent: Long?): String {
    exposure.amountOf(cost, rent)?.let { return money(exposure.symbol, it) }
    return if (cost.months < 1.0 && cost.every != null) "${(cost.months * 100).toInt()}% of rent" else "${monthsText(cost.months)}' rent"
}

/**
 * Where the money goes: each sum the flagged clauses write down, as one stacked bar and a row
 * per sum, then the charges that repeat. The bar is the figure's shape (one big deposit or many
 * small fees), which is the thing a total cannot say.
 *
 * Most leases state what is owed as months of rent ("ten months rent") and keep the rent itself
 * in a schedule, so the card counts in months until the reader types the rent, and then every
 * row turns into money. Locked, the sums show and the working behind each one waits for the full
 * report, the same way the clause count shows and the clauses wait.
 */
@Composable
internal fun MoneyCard(
    exposure: Exposure,
    locked: Boolean,
    rent: Long?,
    onRent: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = risk
    val scheme = MaterialTheme.colorScheme
    val monthly = exposure.rent ?: rent
    val items = exposure.oneOff.sortedByDescending { exposure.amountOf(it, monthly ?: 1) ?: 0 }
    val shares = items.map { (exposure.amountOf(it, monthly ?: 1) ?: 0).toFloat() }
    val sumShares = shares.sum().coerceAtLeast(1f)
    val shades = listOf(colors.high, colors.high.copy(alpha = 0.72f), colors.medium, colors.medium.copy(alpha = 0.7f), colors.medium.copy(alpha = 0.45f))
    val animate = animationsEnabled()
    val grow = remember { androidx.compose.animation.core.Animatable(if (animate) 0f else 1f) }
    val growSpec = motion(RedlineMotion.spatialExpressive<Float>())
    LaunchedEffect(Unit) { grow.animateTo(1f, growSpec) }
    val total = exposure.total(monthly)

    KitCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RedlineIcons.Calculator, colors.high)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Eyebrow("Money at stake")
                Text(
                    text = total?.let { money(exposure.symbol, it) } ?: "${monthsText(exposure.months)} of rent",
                    style = FigureStyle.copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = colors.high,
                )
                if (total == null && exposure.fixed > 0) {
                    Text("plus ${money(exposure.symbol, exposure.fixed)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Space.m))
        Text(
            "Sums these clauses let the landlord take or hold, added from the figures they print. Nothing here is estimated.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )

        if (exposure.rent == null && exposure.months > 0) {
            Spacer(Modifier.height(Space.l))
            RentField(symbol = exposure.symbol.ifBlank { "$" }, rent = rent, onRent = onRent)
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(Space.l))
            Canvas(Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics { }) {
                val gap = 3.dp.toPx()
                var x = 0f
                val usable = size.width * grow.value - gap * (items.size - 1).coerceAtLeast(0)
                shares.forEachIndexed { i, share ->
                    val w = (usable * share / sumShares).coerceAtLeast(size.height)
                    drawRoundRect(
                        shades[i.coerceAtMost(shades.lastIndex)],
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    x += w + gap
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        items.forEachIndexed { i, cost ->
            if (i > 0) HorizontalDivider(color = scheme.outlineVariant)
            CostRow(cost, costText(exposure, cost, monthly), shades[i.coerceAtMost(shades.lastIndex)], locked)
        }
        if (exposure.repeating.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            Eyebrow("Charges that repeat")
            exposure.repeating.forEach { cost ->
                CostRow(cost, costText(exposure, cost, monthly) + ", " + (cost.every ?: ""), colors.medium.copy(alpha = 0.5f), locked)
            }
        }
    }
}

@Composable
private fun CostRow(cost: dev.kesav.redline.Cost, figure: String, dot: Color, locked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f).padding(start = Space.m, end = Space.m)) {
            Text(cost.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = if (locked) "Which clause, and the working, are in the full report." else cost.basis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(figure, style = FigureStyle.copy(fontSize = 14.sp), textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
    }
}

/** The one input on the report: the monthly rent, digits only, kept for this lease on this phone. */
@Composable
private fun RentField(symbol: String, rent: Long?, onRent: (Long?) -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(rent?.toString() ?: "") }
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val digits = v.filter(Char::isDigit).take(9)
            text = digits
            onRent(digits.toLongOrNull())
        },
        label = { Text("Monthly rent") },
        prefix = { Text(symbol, style = FigureStyle) },
        supportingText = { Text("Turns months of rent into money. Stays on this phone.") },
        singleLine = true,
        textStyle = FigureStyle.copy(fontSize = 18.sp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Clauses the chosen place's own law already overrides. Said as "may not hold up", because the
 * app has read the statute and not the court's view of this lease, and the reader should carry
 * the law's name into the conversation rather than the app's certainty.
 */
@Composable
internal fun VoidCard(place: Place, void: List<VoidClause>, locked: Boolean, modifier: Modifier = Modifier) {
    val colors = risk
    val n = void.size
    KitCard(modifier = modifier.fillMaxWidth(), color = colors.highTint, border = colors.high.copy(alpha = 0.25f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(RedlineIcons.Law, colors.high, container = colors.high.copy(alpha = 0.14f))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Eyebrow("${place.label} law", color = colors.high)
                Text(
                    if (n == 1) "1 clause may not hold up" else "$n clauses may not hold up",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        for (v in void) {
            Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.Top) {
                Icon(RedlineIcons.Danger, null, tint = colors.high, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Column(Modifier.padding(start = Space.s)) {
                    Text(v.law, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (!locked) {
                        Text(v.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (locked) {
            Text(
                "Which clauses, and why, are in the full report.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

/**
 * What to do with the report, as three tiles: read the lease with every flag marked, draft the
 * reply to the landlord, and hold it against another lease. A tile the reader has not paid for
 * says so with a small lock, and still opens: the paywall it leads to names what it is for.
 */
@Composable
internal fun ToolsRow(
    onDocument: () -> Unit,
    onDraft: () -> Unit,
    onCompare: () -> Unit,
    draftLocked: Boolean,
    compareLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ToolTile(RedlineIcons.Eye, "Read it marked up", "Every flag in place", false, onDocument, Modifier.weight(1f).fillMaxHeight())
        ToolTile(RedlineIcons.Pen, "Draft a reply", "Email or WhatsApp", draftLocked, onDraft, Modifier.weight(1f).fillMaxHeight())
        ToolTile(RedlineIcons.Compare, "Compare", "Two leases side by side", compareLocked, onCompare, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ToolTile(
    icon: ImageVector,
    title: String,
    detail: String,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    KitCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$title. $detail." + if (locked) " In the full report." else ""
        },
        onClick = onClick,
        padding = Space.m,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, tint, size = 36.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer(Modifier.weight(1f))
            if (locked) {
                Icon(RedlineIcons.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Space.m))
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small rounded label ("Best value", "Pro"). */
@Composable
internal fun Badge(text: String, color: Color, modifier: Modifier = Modifier, onColor: Color = Color.White) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = onColor,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
