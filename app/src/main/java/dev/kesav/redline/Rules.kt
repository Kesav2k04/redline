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
    /** Blank only in hand-built test findings; every rule supplies one. */
    val ask: String = "",
    /**
     * The number the rule read, for rules that read one. The money card quotes this figure,
     * so it cannot name a different percentage from the headline above it.
     */
    val quantity: Numbers.Quantity? = null,
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
    /** The quantity must be under this, for rules about too little ("12 hours notice"). */
    val below: Int? = null,
    val near: Regex? = null,
    /**
     * How far, in characters, the quantity may sit from [near]. Tight for rules whose
     * number belongs right beside a word ("three months' notice"), looser where a
     * clause routinely puts a subordinate clause between the two.
     */
    val reach: Int = 120,
    /**
     * What the words straight after the quantity's unit must match for the quantity to
     * count. "12% per annum" is an interest rate, and the late-fee rule must not read it
     * as a late fee. "A 12-month lease" is the term, not a deposit of twelve months.
     */
    val after: Regex? = null,
    val headline: (String?) -> String,
    val reason: String,
    /**
     * What to ask the landlord for instead, written to follow "Ask for".
     *
     * A finding that stops at "this is bad" leaves the reader holding a problem and no
     * next move. The same phrase reads as "Ask for ..." on the card and "I would like to
     * ask for ..." in the report a tenant sends, so it is one noun phrase with no
     * pronoun for the reader and no closing full stop.
     */
    val ask: String,
) {
    /** The same rule with a different floor, for a place whose cap sits below the default. */
    fun withAtLeast(floor: Int) = Pattern(
        id, topic, severity, all, none, unit, floor, below, near, reach, after, headline, reason, ask,
    )

    fun check(clause: Clause): Finding? {
        val text = clause.text.lowercase()
        if (all.any { !it.containsMatchIn(text) }) return null
        if (none.any { it.containsMatchIn(text) }) return null

        var shown: String? = null
        var matched: Numbers.Quantity? = null
        if (unit != null) {
            val q = quantity(text) ?: return null
            if (atLeast != null && q.value < atLeast) return null
            if (below != null && q.value >= below) return null
            shown = q.shown
            matched = q
        }
        return Finding(clause, id, topic, headline(shown), reason, severity, ask, matched)
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
        val found = Numbers.quantities(text, unit ?: return null).filter { afterFits(text, it) }
        val anchor = near ?: return found.firstOrNull()
        val anchors = anchor.findAll(text).map { it.range }.toList()
        if (anchors.isEmpty()) return null
        return found
            .map { q -> q to anchors.minOf { gap(q.at, it) } }
            .filter { (_, d) -> d <= reach }
            .minByOrNull { (_, d) -> d }
            ?.first
    }

    private fun afterFits(text: String, q: Numbers.Quantity): Boolean {
        val from = q.at.last + 1
        return after == null || after.find(text, from)?.range?.first == from
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
private val HOURS = Regex("hours?")

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
        // "3% above the Bank of England's base rate" is a margin over a published rate,
        // and in England it is the one late-rent charge the Tenant Fees Act allows.
        none = listOf(Regex("\\bbase rate")),
        unit = PERCENT, atLeast = 3,
        near = Regex("\\b(?:penalty|penal|charge|fee|fine|surcharge|interest)\\w*"), reach = 40,
        // A yearly rate is the interest-rate rule's to report, even beside "late fee".
        after = Regex("(?!\\s*(?:per annum|p\\.a\\.|a year|per year|annual|yearly))"),
        headline = { "Late payment penalty of $it percent" },
        reason = "A penalty this size compounds quickly, and it lands on top of rent that " +
            "is already owed.",
        ask = "a late fee within the local legal limit, charged once and only after a grace period",
    ),
    Pattern(
        id = "interest-rate", topic = "Paying late", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:interest)"), Regex("\\b(?:per annum|annually)")),
        unit = PERCENT, atLeast = 12,
        near = Regex("\\binterest\\b(?![ -]?free)"), reach = 60,
        headline = { "Interest charged at $it percent a year" },
        reason = "Compare it with what a bank charges for a personal loan.",
        ask = "interest on late sums at a bank's lending rate, or none at all",
    ),
    Pattern(
        id = "dishonour-fee", topic = "Paying late", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:dishonour|dishonor|bounce|returned unpaid)"), Regex("\\b(?:cheque|check)")),
        headline = { "A fee applies if a payment is returned" },
        reason = "Compare it against what your bank charges you for the same event.",
        ask = "a returned-payment fee no higher than what the bank actually charges",
    ),
    // The caps named in the reason, each read at its source on 24 Sep 2026:
    // India, Model Tenancy Act 2021, two months for residential premises. A model law the
    //   states may adopt; Maharashtra, Karnataka and Delhi have not, and Tamil Nadu's own Act
    //   sets three months: https://prsindia.org/billtrack/the-model-tenancy-act-2021
    // England, up to five weeks' rent where the year's rent is under 50,000 pounds:
    //   https://www.gov.uk/private-renting/deposits
    // California, one month since 1 July 2024 (AB 12):
    //   https://leginfo.legislature.ca.gov/faces/billNavClient.xhtml?bill_id=202320240AB12
    // New York, one month: https://ag.ny.gov/publications/residential-tenants-rights-guide
    Pattern(
        id = "deposit-size", topic = "Your deposit", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deposit)")),
        unit = MONTHS, atLeast = 4, near = Regex("\\bdeposit"),
        // Months of rent, the shape Money prices. "This 12-month lease" is the term.
        after = Regex("'?s?\\s+(?:of\\s+(?:the\\s+)?(?:monthly\\s+)?)?rent"),
        headline = { "Deposit equal to $it months rent" },
        reason = "India's Model Tenancy Act proposes two months for a home, England caps most " +
            "deposits at five weeks' rent, and New York, Massachusetts and, for most landlords, " +
            "California at one month. This is money you cannot touch for the whole term.",
        ask = "a deposit no larger than the local legal cap",
    ),
    Pattern(
        id = "deposit-no-interest", topic = "Your deposit", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:deposit)"), Regex("\\b(?:interest.free|without interest|not (?:carry|earn|bear|accrue|attract) (?:any )?interest|(?:bear|earn|carry) no interest)")),
        headline = { "The deposit earns you nothing" },
        reason = "The landlord holds a large sum for the full term and keeps any return on it.",
        ask = "the deposit held in a separate account, with any interest it earns added when it is returned",
    ),
    Pattern(
        id = "deposit-refund-delay", topic = "Your deposit", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deposit|refund)"), Regex("\\b(?:vacat\\w*|hand.{0,3}over|quit(?:s|ting)?\\b|surrender\\w*)")),
        unit = DAYS, atLeast = 31,
        near = Regex("\\b(?:refund\\w*|return\\w*|repa(?:y|id)\\w*)"), reach = 80,
        headline = { "Deposit returned only after $it days" },
        reason = "You will have paid a deposit on your next home long before this one comes back.",
        ask = "the deposit returned within 30 days of handing back the keys, with any deduction itemised in writing",
    ),
    Pattern(
        id = "sole-discretion", topic = "Who decides", severity = Severity.HIGH,
        // Only where money is being kept back. US leases say "sole discretion" about pets,
        // sublets and alterations too, and the headline below would be false there.
        all = listOf(
            Regex("\\b(?:sole discretion|absolute discretion)"),
            Regex("\\b(?:deduct\\w*|deposit|retain\\w*|damages?)\\b"),
        ),
        headline = { "The landlord alone decides what to deduct" },
        reason = "Sole discretion leaves the amount to the landlord's judgement, with " +
            "nothing in the lease to measure it against.",
        ask = "deductions limited to damage beyond normal wear, each one itemised with a receipt",
    ),
    Pattern(
        id = "forfeiture", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:forfeit)")),
        headline = { "Money can be forfeited outright" },
        reason = "Forfeiture takes the money outright, without anyone having to show a loss.",
        ask = "forfeiture replaced by deductions for an actual loss, shown with receipts",
    ),
    Pattern(
        id = "tenant-structural-repairs", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:repair|structur)"),
            Regex("\\b(?:tenant)"),
            Regex("\\b(?:own expense|own cost|tenant.s cost|borne solely by the tenant|borne by the tenant|(?:shall|will|must|agrees? to) (?:pay for|be responsible for|bear the cost of) all (?:maintenance and |necessary )?repairs)"),
        ),
        none = listOf(Regex("\\b(?:landlord|lessor|owner) (?:shall|will|must|agrees? to) (?:pay for|be responsible for|bear the cost of) all")),
        headline = { "You pay for repairs the owner would normally cover" },
        reason = "Structural upkeep usually stays with the owner, because it is their asset.",
        ask = "structural and major repairs kept with the owner, leaving only minor day-to-day repairs to the tenant",
    ),
    Pattern(
        id = "no-setoff", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:set off|set-off|setoff)"), Regex("\\b(?:rent)")),
        headline = { "You cannot deduct what you spend from the rent" },
        reason = "In effect you pay twice: once for the repair, once for the full rent.",
        ask = "the right to deduct the cost of an urgent repair from the rent if it is not made within 14 days of written notice",
    ),
    Pattern(
        id = "lock-in", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:lock.?in)")),
        headline = { "There is a lock-in period" },
        reason = "Leaving during a lock-in usually means paying for months you do not live there.",
        ask = "a shorter lock-in, or the right to leave early by finding a replacement tenant",
    ),
    Pattern(
        id = "unexpired-liability", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:unexpired|remainder of the term|balance of the term)|\\b(?:liable|responsible)\\b[^.;]{0,40}\\b(?:rest|remainder|balance) of the (?:lease )?term")),
        headline = { "Leaving early still costs you the rest of the term" },
        reason = "That can be many months of rent for a home you have already left.",
        ask = "liability that ends when a new tenant moves in, and never more than two months rent",
    ),
    Pattern(
        id = "long-notice", topic = "Leaving early", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:notice)"), Regex("\\b(?:tenant|vacat|terminat)")),
        unit = MONTHS, atLeast = 3,
        // Close enough to be the notice period itself. At 40, "renews for a further twelve
        // months unless the Tenant gives written notice" read as twelve months notice.
        near = Regex("\\bnotice"), reach = 24,
        headline = { "You must give $it months notice to leave" },
        reason = "One or two months is the common term. Longer than that ties you in.",
        ask = "a notice period of one month, the same for both sides",
    ),
    Pattern(
        id = "liquidated-damages", topic = "Leaving early", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:liquidated damages)")),
        headline = { "A fixed sum is payable as damages" },
        reason = "It is charged whether or not the landlord actually loses that much.",
        ask = "the fixed sum removed, so any claim covers only a loss that can be shown",
    ),
    Pattern(
        id = "rent-escalation", topic = "Rent going up", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:enhanc|escalat|increas|revis)"), Regex("\\b(?:rent)")),
        unit = PERCENT, atLeast = 6,
        near = Regex("\\b(?:enhanc|escalat|increas|revis)\\w*"), reach = 60,
        headline = { "Rent rises by $it percent" },
        reason = "Compare that against what rents in the area are actually doing.",
        ask = "rises capped at 5 percent a year, or tied to the official inflation figure",
    ),
    Pattern(
        id = "unilateral-revision", topic = "Rent going up", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:revise the rent|revision of rent|alter the rent|(?:increase|raise|change|adjust) the rent)"),
            Regex("\\b(?:at any time)"),
        ),
        none = listOf(Regex("\\b(?:may|shall|will|can) ?not\\s+(?:\\w+\\s+){0,2}(?:revise|alter|increase|raise|change|adjust)")),
        headline = { "The landlord can raise the rent at any time" },
        reason = "A rent you cannot plan around is not a fixed rent.",
        ask = "a rent fixed for the whole term, with any rise agreed in writing at renewal",
    ),
    Pattern(
        id = "silence-is-consent", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:continued occupation|continued use|continued possession)"),
            Regex("\\b(?:acceptance|accepted|consent)"),
        ),
        headline = { "Staying on counts as agreeing" },
        reason = "You would have to move out in order to refuse the new terms.",
        ask = "changes to the terms taking effect only once both sides have signed them",
    ),
    Pattern(
        id = "entry-without-notice", topic = "Access to your home", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:enter(?:s|ed|ing)?\\b|entry|inspect)"),
            Regex("\\b(?:without prior notice|without notice|at any time)"),
        ),
        // An emergency exception is the lawful norm, not the problem. "At any time" is
        // only the problem when no notice period is given anywhere in the clause.
        none = listOf(
            Regex("\\bemergenc"),
            Regex("\\b(?:hours?|days?).{0,2} (?:prior |advance |written )?notice"),
        ),
        headline = { "The landlord may enter without telling you" },
        reason = "Notice before entry is the normal protection for a home.",
        ask = "at least 24 hours written notice before any entry, except in an emergency",
    ),
    Pattern(
        id = "showings", topic = "Access to your home", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:prospective tenant|prospective purchaser|show the premises)")),
        headline = { "Strangers may be shown round while you live there" },
        reason = "Check the hours, and whether you get any notice.",
        ask = "viewings only in the last month of the term, at agreed times, with 24 hours notice",
    ),
    Pattern(
        id = "occupant-surcharge", topic = "Access to your home", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:occupants?|reside[sd]?|residing|person other than)\\b")),
        unit = PERCENT, atLeast = 5,
        near = Regex("\\b(?:occupants?|reside[sd]?|residing|person other than)\\b"), reach = 80,
        headline = { "Another occupant raises the rent by $it percent" },
        reason = "A guest who stays too long can become a rent increase.",
        ask = "guests staying up to 30 days left out of any surcharge",
    ),
    Pattern(
        id = "auto-renewal", topic = "Renewal and notices", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:automatically (?:renew|be renewed|extend)|renew(?:s|ed)? automatically|stand renewed|deemed renewed|renewed for a further)")),
        headline = { "The agreement renews itself" },
        reason = "Missing the notice window binds you for another full term.",
        ask = "renewal only when both sides agree in writing, with a reminder before the notice window closes",
    ),
    Pattern(
        id = "deemed-service", topic = "Renewal and notices", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:deemed to have been served|deemed to have been delivered)")),
        headline = { "A notice counts as delivered even if it never arrives" },
        reason = "You can miss a deadline nobody actually told you about.",
        ask = "notices that count only once they are received, with a copy sent by email",
    ),
    Pattern(
        id = "tenant-pays-tax", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:municipal tax|property tax|corporation tax)"), Regex("\\b(?:tenant|bear|outgoing)")),
        headline = { "Property taxes are passed to you" },
        reason = "Tax on the owner's asset is normally the owner's cost.",
        ask = "property taxes kept with the owner",
    ),
    Pattern(
        id = "future-levies", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\b(?:hereafter imposed|any levy|future levies)")),
        headline = { "Charges that do not exist yet are yours too" },
        reason = "This is an open-ended promise to pay an unknown amount.",
        ask = "charges limited to the ones this agreement names, with anything new agreed in writing first",
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
        ask = "a cap on how much of any rise in society charges passes to the tenant",
    ),
    Pattern(
        id = "mandatory-repaint", topic = "Moving out", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:repaint|repainting)")),
        headline = { "Repainting is charged to you on the way out" },
        reason = "Look for whether it applies regardless of the actual condition.",
        ask = "repainting charged only where the walls are damaged beyond normal wear",
    ),
    Pattern(
        id = "restore-original", topic = "Moving out", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:restore|reinstate)"), Regex("\\b(?:original condition|same condition)")),
        headline = { "You must put the place back as it was" },
        reason = "Normal wear over a long tenancy can make that expensive.",
        ask = "an exception for normal wear and tear, with the move-in condition recorded in dated photos",
    ),
    Pattern(
        id = "no-liability", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(
            Regex("\\b(?:landlord|lessor|owner)\\b[^.]{0,40}?\\b(?:not|no)\\b[^.]{0,15}\\b(?:liable|responsible)\\b"),
            Regex("\\b(?:loss|damage|injur)"),
        ),
        none = listOf(Regex("\\bnot caused by|\\bunless (?:caused|due)|\\bexcept where|\\bsave where|\\b(?:save|except) to the extent")),
        headline = { "The landlord takes no responsibility for loss or injury" },
        reason = "Excusing a landlord even for their own negligence is void in many places, " +
            "but the clause still puts people off claiming.",
        ask = "the landlord staying responsible for loss or injury caused by their own negligence",
    ),
    Pattern(
        id = "legal-costs", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(
            Regex("\\battorney.?s?.? fees?|\\blegal (?:fees|costs)|\\bsolicitor.?s?.? (?:fees|costs)"),
            Regex("\\btenant"),
        ),
        none = listOf(Regex("\\bprevailing party|\\beither party|\\beach party|\\bsuccessful party")),
        headline = { "Legal costs fall on you" },
        reason = "A one-sided costs clause makes a dispute expensive for you even when you are right.",
        ask = "each side paying its own legal costs, or costs following the result",
    ),
    Pattern(
        id = "as-is", topic = "What you pay for", severity = Severity.MEDIUM,
        all = listOf(
            Regex("\\baccept"),
            // "As is" is ordinary English ("as is required"), so bare it counts only
            // where the phrase closes: "accepts the apartment as is and", "as is, where is".
            Regex("\\bin (?:its|their) present (?:condition|state)|\\bas.is.? (?:condition|basis)|\\bas.is\\b(?=[,.;]| and\\b)"),
        ),
        headline = { "You take the home in whatever state it is in" },
        reason = "Accepting the present condition can shift repairs onto you that the law " +
            "leaves with the landlord.",
        ask = "a written list of defects the landlord will fix before the move-in date",
    ),
    Pattern(
        id = "uk-fee", topic = "What you pay for", severity = Severity.HIGH,
        all = listOf(Regex("\\bfees?\\b"), Regex("£|\\bpounds?\\b")),
        // "Pounds" is also a weight, and a US pet clause names both a fee and a weight
        // limit. A clause priced in dollars is not an English fee either.
        none = listOf(Regex("\\$|\\bdollars?\\b|\\bweigh\\w*|\\blbs?\\b")),
        headline = { "A fee that may be banned in England" },
        reason = "Since the Tenant Fees Act 2019, a landlord or agent in England can charge rent, " +
            "a capped deposit and a few set charges. Almost every other fee is banned.",
        ask = "the fee to be dropped, or the provision of the Tenant Fees Act that allows it",
    ),
    Pattern(
        id = "pro-cleaning", topic = "Moving out", severity = Severity.MEDIUM,
        all = listOf(Regex("\\bprofessional(?:ly)? (?:\\w+ )?clean")),
        headline = { "Professional cleaning is charged to you on the way out" },
        reason = "Cleaning should be judged against the condition at the start, not bought " +
            "from a firm the landlord picks.",
        ask = "cleaning judged against the check-in inventory, done by anyone to that standard",
    ),
    Pattern(
        id = "rent-in-advance", topic = "What you pay for", severity = Severity.MEDIUM,
        all = listOf(Regex("\\bin advance"), Regex("\\brent")),
        unit = MONTHS, atLeast = 2, near = Regex("\\bin advance"), reach = 40,
        headline = { "$it months rent paid up front" },
        reason = "Some places limit rent in advance. In England it is one month before the " +
            "tenancy starts.",
        ask = "no more than one month's rent before the tenancy starts",
    ),
    Pattern(
        id = "short-entry-notice", topic = "Access to your home", severity = Severity.MEDIUM,
        all = listOf(Regex("\\b(?:enter(?:s|ed|ing)?\\b|entry)"), Regex("\\bnotice")),
        unit = HOURS, below = 24, near = Regex("\\bnotice"), reach = 20,
        headline = { "Only $it hours notice before the landlord comes in" },
        reason = "Twenty-four hours is the usual minimum, and some places require two days.",
        ask = "at least 24 hours written notice before any entry, except in an emergency",
    ),
    Pattern(
        id = "confession", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(Regex("\\bconsents? in advance to\\b[^.]{0,80}\\bjudgment|\\bconfess\\w* (?:of )?judgment|\\bcognovit")),
        headline = { "You agree in advance to lose in court" },
        reason = "Consenting to judgment before any dispute removes the chance to defend yourself.",
        ask = "the clause removed, so any claim is decided by a court on its merits",
    ),
    Pattern(
        id = "distraint", topic = "Who decides", severity = Severity.HIGH,
        all = listOf(Regex("\\bpersonal property as (?:a )?(?:pledge|security)|\\bdistrain|\\blien (?:on|upon)\\b[^.]{0,40}\\bproperty")),
        headline = { "The landlord may hold on to your belongings" },
        reason = "Seizing a tenant's belongings without a court order is unlawful in many places.",
        ask = "the clause removed, so unpaid sums are recovered through a court",
    ),
    Pattern(
        id = "pet-insurance", topic = "What you pay for", severity = Severity.MEDIUM,
        all = listOf(
            Regex("\\bpet"),
            Regex("\\binsurance"),
            Regex("\\b(?:must|shall|required to) (?:take out|obtain|buy|purchase|maintain)"),
        ),
        headline = { "Keeping a pet means buying insurance" },
        reason = "In England a landlord cannot require a tenant to buy insurance; the pet " +
            "can be covered by the deposit instead.",
        ask = "pet damage covered within the capped deposit instead of compulsory insurance",
    ),
    Pattern(
        id = "section-21", topic = "Renewal and notices", severity = Severity.HIGH,
        // A US lease can number its own sections, so "Section 21" alone is not enough.
        all = listOf(Regex("\\bsection 21\\b"), Regex("\\b(?:housing act|possession)")),
        headline = { "The clause relies on section 21, which no longer exists" },
        reason = "No-fault eviction under section 21 ended in England on 1 May 2026. A clause " +
            "built on it has no effect.",
        ask = "the section 21 wording removed from the agreement",
    ),
)

/** One heading in the disclosure, and how many rules sit under it. */
data class RuleTopic(val name: String, val ruleCount: Int)

object Scanner {
    val ruleCount: Int get() = patterns.size

    /** Every rule's ask, by rule id. For tests that hold the wording to its contract. */
    internal val asks: Map<String, String> by lazy { patterns.associate { it.id to it.ask } }

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

    /**
     * [scan] for a home in [place]: the deposit is measured against that place's cap
     * rather than the loose four-month line, and each ask and reason the place has a
     * sourced figure for names it. "A deposit no larger than the local legal cap" sends
     * the reader off to look the number up; "no more than one month's rent" is a sentence
     * they can put in the letter. Rules the place has nothing on keep their general words.
     */
    fun scan(clauses: List<Clause>, place: Place?): List<Finding> {
        if (place == null) return scan(clauses)
        val limits = place.limits
        val rules = patterns.map { rule -> limits.floors[rule.id]?.let(rule::withAtLeast) ?: rule }
        return clauses.flatMap { clause -> rules.mapNotNull { it.check(clause) } }.map { f ->
            f.copy(
                reason = limits.reasons[f.ruleId] ?: f.reason,
                ask = limits.asks[f.ruleId] ?: f.ask,
            )
        }
    }

    /**
     * [scan] in the order the report shows it. Severity first, then findings that name an
     * actual figure: "Deposit equal to ten months rent" is a harder fact to argue with than
     * "there is a lock-in period", and the top card is the one a reader sees before
     * deciding. The screen and anything that pictures the screen both take this order.
     */
    fun ranked(clauses: List<Clause>, place: Place? = null): List<Finding> =
        scan(clauses, place).sortedWith(
            compareBy(
                { it.severity.ordinal },
                { if (it.headline.any(Char::isDigit)) 0 else 1 },
                { it.clause.index },
            )
        )
}
