package dev.kesav.redline

enum class Severity { HIGH, MEDIUM }

data class Finding(
    val clause: Clause,
    val ruleId: String,
    /**
     * The subject this finding belongs to, from the rule that produced it.
     *
     * It is the one thing about a locked finding that can be shown without giving it
     * away. "Your deposit, 5 problems, locked" is a reason to pay. Three grey bars and
     * a count is a loading screen with a price on it.
     */
    val topic: String,
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
    /**
     * The heading this rule appears under when the app discloses what it checks.
     *
     * It lives on the rule rather than in a hand-written list on the screen, because a
     * hand-written list is a second copy of the truth and second copies go stale the
     * first time a rule is added. [Scanner.topics] derives the disclosure from this, and
     * [TopicsTest] pins that the disclosed total still equals the number of rules.
     */
    val topic: String,
    val severity: Severity,
    val all: List<Regex>,
    val none: List<Regex> = emptyList(),
    val unit: Regex? = null,
    val atLeast: Int? = null,
    val near: Regex? = null,
    /**
     * How far, in characters, the quantity may sit from [near]. Tight for rules whose
     * number belongs right beside a word ("three months' notice"), looser where a
     * clause routinely puts a subordinate clause between the two.
     */
    val reach: Int = 120,
    val headline: (String?) -> String,
    val reason: String,
) {
    fun check(clause: Clause): Finding? {
        val text = clause.text.lowercase()
        if (all.any { !it.containsMatchIn(text) }) return null
        if (none.any { it.containsMatchIn(text) }) return null

        var shown: String? = null
        if (unit != null) {
            val q = quantity(text) ?: return null
            if (atLeast != null && q.value < atLeast) return null
            shown = q.shown
        }
        return Finding(clause, id, topic, headline(shown), reason, severity)
    }

    /**
     * The quantity this rule is about, which is the one nearest its anchor.
     *
     * This used to take the first matching number within a window, and six of the seven
     * quantity rules had no anchor at all, so the window was the whole clause. "The rent
     * shall be escalated by ten percent" was reported as a ten percent late fee, because
     * the late-fee rule saw a lateness word somewhere and a percentage somewhere, and
     * nothing asked whether the two had anything to do with each other.
     *
     * With an anchor set, a rule whose anchor is absent does not fire. Falling back to
     * the whole clause is exactly the behaviour that produced the false headlines.
     */
    private fun quantity(text: String): Numbers.Quantity? {
        val found = Numbers.quantities(text, unit ?: return null)
        val anchor = near ?: return found.firstOrNull()
        val anchors = anchor.findAll(text).map { it.range }.toList()
        if (anchors.isEmpty()) return null
        return found
            .map { q -> q to anchors.minOf { gap(q.at, it) } }
            .filter { (_, d) -> d <= reach }
            .minByOrNull { (_, d) -> d }
            ?.first
    }

    private fun gap(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }
}


private val PERCENT = Regex("percent")
private val DAYS = Regex("days?")
private val MONTHS = Regex("months?")

private val TOPIC_ORDER = listOf(
    "Your deposit",
    "Paying late",
    "Rent going up",
    "What you pay for",
    "Who decides",
    "Access to your home",
    "Leaving early",
    "Renewal and notices",
    "Moving out",
)

private val patterns = listOf(
    Pattern(
        id = "late-fee", topic = "Paying late", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:unpaid|overdue|defaults?|delay\\w*|late)\\b")),
        unit = PERCENT, atLeast = 3,
        near = Regex("\\b(?:penalty|penal|charge|fee|fine|surcharge|interest)\\w*"), reach = 40,
        headline = { "Late payment penalty of $it percent" },
        reason = "A penalty this size compounds quickly. Two percent a month is the usual " +
            "ceiling in residential agreements.",
    ),
    Pattern(
        id = "interest-rate", topic = "Paying late", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:interest)"), Regex("\\b(?:per annum|annually)")),
        unit = PERCENT, atLeast = 12,
        near = Regex("\\binterest\\b(?![ -]?free)"), reach = 60,
        headline = { "Interest charged at $it percent a year" },
        reason = "That is above what a bank charges on an unsecured loan.",
    ),
    Pattern(
        id = "dishonour-fee", topic = "Paying late", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:dishonour|dishonor|bounce|returned unpaid)"), Regex("\\b(?:cheque|check)")),
        headline = { "A fee applies if a payment is returned" },
        reason = "Compare it against what your bank charges you for the same event.",
    ),
    Pattern(
        id = "deposit-size", topic = "Your deposit", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deposit)")),
        unit = MONTHS, atLeast = 4, near = Regex("\\bdeposit"),
        headline = { "Deposit equal to $it months rent" },
        reason = "Several states cap residential deposits at two or three months. This is " +
            "money you cannot touch for the whole term.",
    ),
    Pattern(
        id = "deposit-no-interest", topic = "Your deposit", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:deposit)"), Regex("\\b(?:interest free|not carry interest|without interest)")),
        headline = { "The deposit earns you nothing" },
        reason = "The landlord holds a large sum for the full term and keeps any return on it.",
    ),
    Pattern(
        id = "deposit-refund-delay", topic = "Your deposit", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deposit|refund)"), Regex("\\b(?:vacat\\w*|hand.{0,3}over|quit(?:s|ting)?\\b)")),
        unit = DAYS, atLeast = 31,
        near = Regex("\\b(?:refund\\w*|return\\w*|repa(?:y|id)\\w*)"), reach = 80,
        headline = { "Deposit returned only after $it days" },
        reason = "You will have paid a deposit on your next home long before this one comes back.",
    ),
    Pattern(
        id = "sole-discretion", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:sole discretion|absolute discretion)")),
        headline = { "The landlord alone decides what to deduct" },
        reason = "Sole discretion means the amount is not open to challenge on its merits.",
    ),
    Pattern(
        id = "forfeiture", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:forfeit)")),
        headline = { "Money can be forfeited outright" },
        reason = "Forfeiture is a penalty, not compensation for a loss anyone has to prove.",
    ),
    Pattern(
        id = "tenant-structural-repairs", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:repair|structur)"),
            Regex("\\b(?:tenant)"),
            Regex("\\b(?:own expense|own cost|tenant.s cost|borne solely by the tenant|borne by the tenant)"),
        ),
        headline = { "You pay for repairs the owner would normally cover" },
        reason = "Structural upkeep usually stays with the owner, because it is their asset.",
    ),
    Pattern(
        id = "no-setoff", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:set off|set-off|setoff)"), Regex("\\b(?:rent)")),
        headline = { "You cannot deduct what you spend from the rent" },
        reason = "In effect you pay twice: once for the repair, once for the full rent.",
    ),
    Pattern(
        id = "lock-in", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:lock.?in)")),
        headline = { "There is a lock-in period" },
        reason = "Leaving during a lock-in usually means paying for months you do not live there.",
    ),
    Pattern(
        id = "unexpired-liability", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:unexpired|remainder of the term|balance of the term)")),
        headline = { "Leaving early still costs you the rest of the term" },
        reason = "That can be many months of rent for a home you have already left.",
    ),
    Pattern(
        id = "long-notice", topic = "Leaving early", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:notice)"), Regex("\\b(?:tenant|vacat|terminat)")),
        unit = MONTHS, atLeast = 3,
        near = Regex("\\bnotice"), reach = 40,
        headline = { "You must give $it months notice to leave" },
        reason = "One or two months is the common term. Longer than that ties you in.",
    ),
    Pattern(
        id = "liquidated-damages", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:liquidated damages)")),
        headline = { "A fixed sum is payable as damages" },
        reason = "It is charged whether or not the landlord actually loses that much.",
    ),
    Pattern(
        id = "rent-escalation", topic = "Rent going up", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:enhanc|escalat|increas|revis)"), Regex("\\b(?:rent)")),
        unit = PERCENT, atLeast = 6,
        near = Regex("\\b(?:enhanc|escalat|increas|revis)\\w*"), reach = 60,
        headline = { "Rent rises by $it percent" },
        reason = "Compare that against what rents in the area are actually doing.",
    ),
    Pattern(
        id = "unilateral-revision", topic = "Rent going up", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:revise the rent|revision of rent|alter the rent)"), Regex("\\b(?:at any time)")),
        headline = { "The landlord can raise the rent at any time" },
        reason = "A rent you cannot plan around is not a fixed rent.",
    ),
    Pattern(
        id = "silence-is-consent", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:continued occupation|continued use|continued possession)"),
            Regex("\\b(?:acceptance|accepted|consent)"),
        ),
        headline = { "Staying on counts as agreeing" },
        reason = "You would have to move out in order to refuse the new terms.",
    ),
    Pattern(
        id = "entry-without-notice", topic = "Access to your home", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:enter(?:s|ed|ing)?\\b|entry|inspect)"), Regex("\\b(?:without prior notice|without notice)")),
        headline = { "The landlord may enter without telling you" },
        reason = "Notice before entry is the normal protection for a home.",
    ),
    Pattern(
        id = "showings", topic = "Access to your home", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:prospective tenant|prospective purchaser|show the premises)")),
        headline = { "Strangers may be shown round while you live there" },
        reason = "Check the hours, and whether you get any notice.",
    ),
    Pattern(
        id = "occupant-surcharge", topic = "Access to your home", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:occupants?|reside[sd]?|residing|person other than)\\b")),
        unit = PERCENT, atLeast = 5,
        near = Regex("\\b(?:occupants?|reside[sd]?|residing|person other than)\\b"), reach = 80,
        headline = { "Another occupant raises the rent by $it percent" },
        reason = "A guest who stays too long can become a rent increase.",
    ),
    Pattern(
        id = "auto-renewal", topic = "Renewal and notices", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:automatically renew|stand renewed|deemed renewed|renewed for a further)")),
        headline = { "The agreement renews itself" },
        reason = "Missing the notice window binds you for another full term.",
    ),
    Pattern(
        id = "deemed-service", topic = "Renewal and notices", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deemed to have been served|deemed to have been delivered)")),
        headline = { "A notice counts as delivered even if it never arrives" },
        reason = "You can miss a deadline nobody actually told you about.",
    ),
    Pattern(
        id = "tenant-pays-tax", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:municipal tax|property tax|corporation tax)"), Regex("\\b(?:tenant|bear|outgoing)")),
        headline = { "Property taxes are passed to you" },
        reason = "Tax on the owner's asset is normally the owner's cost.",
    ),
    Pattern(
        id = "future-levies", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:hereafter imposed|any levy|future levies)")),
        headline = { "Charges that do not exist yet are yours too" },
        reason = "This is an open-ended promise to pay an unknown amount.",
    ),
    Pattern(
        id = "charge-increases", topic = "Rent going up", severity = Severity.MEDIUM,
        all = listOf(
            Regex("\\b(?:increase)"),
            Regex("\\b(?:maintenance|society|association)"),
            Regex("\\b(?:borne by the tenant|entire increase)"),
        ),
        headline = { "You absorb every rise in society charges" },
        reason = "The rent stays the same while your total outgoing does not.",
    ),
    Pattern(
        id = "mandatory-repaint", topic = "Moving out", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:repaint|repainting)")),
        headline = { "Repainting is charged to you on the way out" },
        reason = "Look for whether it applies regardless of the actual condition.",
    ),
    Pattern(
        id = "restore-original", topic = "Moving out", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:restore|reinstate)"), Regex("\\b(?:original condition|same condition)")),
        headline = { "You must put the place back as it was" },
        reason = "Normal wear over a long tenancy can make that expensive.",
    ),
)

/** One heading in the disclosure, and how many rules sit under it. */
data class RuleTopic(val name: String, val ruleCount: Int)

object Scanner {
    val ruleCount: Int get() = patterns.size

    /**
     * What the app checks, grouped for reading.
     *
     * A paywall in front of an unexplained judgement is the thing a reader is right to
     * distrust, and this app spent the whole results screen asking for money before it
     * ever said what it looks for. The list is derived from the rules themselves, so it
     * cannot claim a check the scanner does not perform.
     */
    val topics: List<RuleTopic> by lazy {
        val counts = patterns.groupingBy { it.topic }.eachCount()
        TOPIC_ORDER.filter { counts.containsKey(it) }.map { RuleTopic(it, counts.getValue(it)) }
    }

    fun scan(clauses: List<Clause>): List<Finding> =
        clauses.flatMap { clause -> patterns.mapNotNull { it.check(clause) } }
}
