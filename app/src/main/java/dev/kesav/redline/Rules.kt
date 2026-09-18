package dev.kesav.redline

enum class Severity { HIGH, MEDIUM }

data class Finding(
    val clause: Clause,
    val ruleId: String,
    val headline: String,
    val reason: String,
    val severity: Severity,
)

/**
 * One thing worth flagging, expressed as words that must appear, words that must not,
 * and optionally a quantity that has to cross a line.
 *
 * Every rule has to be able to say what it found and why, because a flag a reader
 * cannot check is worth no more than a guess.
 */
private class Pattern(
    val id: String,
    val severity: Severity,
    val all: List<Regex>,
    val none: List<Regex> = emptyList(),
    val unit: Regex? = null,
    val atLeast: Int? = null,
    val near: Regex? = null,
    val headline: (Int?) -> String,
    val reason: String,
) {
    fun check(clause: Clause): Finding? {
        val text = clause.text.lowercase()
        if (all.any { !it.containsMatchIn(text) }) return null
        if (none.any { it.containsMatchIn(text) }) return null

        var value: Int? = null
        if (unit != null) {
            value = Numbers.valueBefore(window(clause.text), unit) ?: return null
            if (atLeast != null && value < atLeast) return null
        }
        return Finding(clause, id, headline(value), reason, severity)
    }

    /**
     * A long clause can mention two different quantities in the same unit. Reading the
     * first one found turns "three months rent as damages, adjusted against the deposit"
     * into a claim about a six month deposit, which is simply false. When [near] is set
     * the quantity has to sit beside the thing it is supposed to describe.
     */
    private fun window(text: String): String {
        val anchor = near?.find(text) ?: return text
        return text.substring(
            (anchor.range.first - WINDOW).coerceAtLeast(0),
            (anchor.range.last + WINDOW).coerceAtMost(text.length),
        )
    }

    private companion object { const val WINDOW = 120 }
}

private val PERCENT = Regex("percent")
private val DAYS = Regex("days?")
private val MONTHS = Regex("months?")

private val patterns = listOf(
    Pattern(
        id = "late-fee", severity = Severity.HIGH,
        all = listOf(Regex("unpaid|overdue|default|delay|late")),
        unit = PERCENT, atLeast = 3,
        headline = { "Late payment penalty of $it percent" },
        reason = "A penalty this size compounds quickly. Two percent a month is the usual " +
            "ceiling in residential agreements.",
    ),
    Pattern(
        id = "interest-rate", severity = Severity.HIGH,
        all = listOf(Regex("interest"), Regex("per annum|annually")),
        unit = PERCENT, atLeast = 12,
        headline = { "Interest charged at $it percent a year" },
        reason = "That is above what a bank charges on an unsecured loan.",
    ),
    Pattern(
        id = "dishonour-fee", severity = Severity.MEDIUM,
        all = listOf(Regex("dishonour|dishonor|bounce|returned unpaid"), Regex("cheque|check")),
        headline = { "A fee applies if a payment is returned" },
        reason = "Compare it against what your bank charges you for the same event.",
    ),
    Pattern(
        id = "deposit-size", severity = Severity.HIGH,
        all = listOf(Regex("deposit")),
        unit = MONTHS, atLeast = 4, near = Regex("(?i)deposit"),
        headline = { "Deposit equal to $it months rent" },
        reason = "Several states cap residential deposits at two or three months. This is " +
            "money you cannot touch for the whole term.",
    ),
    Pattern(
        id = "deposit-no-interest", severity = Severity.MEDIUM,
        all = listOf(Regex("deposit"), Regex("interest free|not carry interest|without interest")),
        headline = { "The deposit earns you nothing" },
        reason = "The landlord holds a large sum for the full term and keeps any return on it.",
    ),
    Pattern(
        id = "deposit-refund-delay", severity = Severity.HIGH,
        all = listOf(Regex("deposit|refund"), Regex("vacat|hand.{0,3}over|quit")),
        unit = DAYS, atLeast = 31,
        headline = { "Deposit returned only after $it days" },
        reason = "You will have paid a deposit on your next home long before this one comes back.",
    ),
    Pattern(
        id = "sole-discretion", severity = Severity.HIGH,
        all = listOf(Regex("sole discretion|absolute discretion")),
        headline = { "The landlord alone decides what to deduct" },
        reason = "Sole discretion means the amount is not open to challenge on its merits.",
    ),
    Pattern(
        id = "forfeiture", severity = Severity.HIGH,
        all = listOf(Regex("forfeit")),
        headline = { "Money can be forfeited outright" },
        reason = "Forfeiture is a penalty, not compensation for a loss anyone has to prove.",
    ),
    Pattern(
        id = "tenant-structural-repairs", severity = Severity.HIGH,
        all = listOf(
            Regex("repair|structur"),
            Regex("tenant"),
            Regex("own expense|own cost|tenant.s cost|borne solely by the tenant|borne by the tenant"),
        ),
        headline = { "You pay for repairs the owner would normally cover" },
        reason = "Structural upkeep usually stays with the owner, because it is their asset.",
    ),
    Pattern(
        id = "no-setoff", severity = Severity.HIGH,
        all = listOf(Regex("set off|set-off|setoff"), Regex("rent")),
        headline = { "You cannot deduct what you spend from the rent" },
        reason = "In effect you pay twice: once for the repair, once for the full rent.",
    ),
    Pattern(
        id = "lock-in", severity = Severity.HIGH,
        all = listOf(Regex("lock.?in")),
        headline = { "There is a lock-in period" },
        reason = "Leaving during a lock-in usually means paying for months you do not live there.",
    ),
    Pattern(
        id = "unexpired-liability", severity = Severity.HIGH,
        all = listOf(Regex("unexpired|remainder of the term|balance of the term")),
        headline = { "Leaving early still costs you the rest of the term" },
        reason = "That can be many months of rent for a home you have already left.",
    ),
    Pattern(
        id = "long-notice", severity = Severity.MEDIUM,
        all = listOf(Regex("notice"), Regex("tenant|vacat|terminat")),
        unit = MONTHS, atLeast = 3,
        headline = { "You must give $it months notice to leave" },
        reason = "One or two months is the common term. Longer than that ties you in.",
    ),
    Pattern(
        id = "liquidated-damages", severity = Severity.HIGH,
        all = listOf(Regex("liquidated damages")),
        headline = { "A fixed sum is payable as damages" },
        reason = "It is charged whether or not the landlord actually loses that much.",
    ),
    Pattern(
        id = "rent-escalation", severity = Severity.MEDIUM,
        all = listOf(Regex("enhanc|escalat|increas|revis"), Regex("rent")),
        unit = PERCENT, atLeast = 6,
        headline = { "Rent rises by $it percent" },
        reason = "Compare that against what rents in the area are actually doing.",
    ),
    Pattern(
        id = "unilateral-revision", severity = Severity.HIGH,
        all = listOf(Regex("revise the rent|revision of rent|alter the rent"), Regex("at any time")),
        headline = { "The landlord can raise the rent at any time" },
        reason = "A rent you cannot plan around is not a fixed rent.",
    ),
    Pattern(
        id = "silence-is-consent", severity = Severity.HIGH,
        all = listOf(
            Regex("continued occupation|continued use|continued possession"),
            Regex("acceptance|accepted|consent"),
        ),
        headline = { "Staying on counts as agreeing" },
        reason = "You would have to move out in order to refuse the new terms.",
    ),
    Pattern(
        id = "entry-without-notice", severity = Severity.HIGH,
        all = listOf(Regex("enter|entry|inspect"), Regex("without prior notice|without notice")),
        headline = { "The landlord may enter without telling you" },
        reason = "Notice before entry is the normal protection for a home.",
    ),
    Pattern(
        id = "showings", severity = Severity.MEDIUM,
        all = listOf(Regex("prospective tenant|prospective purchaser|show the premises")),
        headline = { "Strangers may be shown round while you live there" },
        reason = "Check the hours, and whether you get any notice.",
    ),
    Pattern(
        id = "occupant-surcharge", severity = Severity.MEDIUM,
        all = listOf(Regex("occupant|reside|person other than")),
        unit = PERCENT, atLeast = 5,
        headline = { "Another occupant raises the rent by $it percent" },
        reason = "A guest who stays too long can become a rent increase.",
    ),
    Pattern(
        id = "auto-renewal", severity = Severity.HIGH,
        all = listOf(Regex("automatically renew|stand renewed|deemed renewed|renewed for a further")),
        headline = { "The agreement renews itself" },
        reason = "Missing the notice window binds you for another full term.",
    ),
    Pattern(
        id = "deemed-service", severity = Severity.HIGH,
        all = listOf(Regex("deemed to have been served|deemed to have been delivered")),
        headline = { "A notice counts as delivered even if it never arrives" },
        reason = "You can miss a deadline nobody actually told you about.",
    ),
    Pattern(
        id = "tenant-pays-tax", severity = Severity.HIGH,
        all = listOf(Regex("municipal tax|property tax|corporation tax"), Regex("tenant|bear|outgoing")),
        headline = { "Property taxes are passed to you" },
        reason = "Tax on the owner's asset is normally the owner's cost.",
    ),
    Pattern(
        id = "future-levies", severity = Severity.HIGH,
        all = listOf(Regex("hereafter imposed|any levy|future levies")),
        headline = { "Charges that do not exist yet are yours too" },
        reason = "This is an open-ended promise to pay an unknown amount.",
    ),
    Pattern(
        id = "charge-increases", severity = Severity.MEDIUM,
        all = listOf(
            Regex("increase"),
            Regex("maintenance|society|association"),
            Regex("borne by the tenant|entire increase"),
        ),
        headline = { "You absorb every rise in society charges" },
        reason = "The rent stays the same while your total outgoing does not.",
    ),
    Pattern(
        id = "mandatory-repaint", severity = Severity.MEDIUM,
        all = listOf(Regex("repaint|repainting")),
        headline = { "Repainting is charged to you on the way out" },
        reason = "Look for whether it applies regardless of the actual condition.",
    ),
    Pattern(
        id = "restore-original", severity = Severity.MEDIUM,
        all = listOf(Regex("restore|reinstate"), Regex("original condition|same condition")),
        headline = { "You must put the place back as it was" },
        reason = "Normal wear over a long tenancy can make that expensive.",
    ),
)

object Scanner {
    val ruleCount: Int get() = patterns.size

    fun scan(clauses: List<Clause>): List<Finding> =
        clauses.flatMap { clause -> patterns.mapNotNull { it.check(clause) } }
}
