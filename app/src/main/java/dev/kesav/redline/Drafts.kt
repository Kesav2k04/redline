package dev.kesav.redline

/**
 * Replies to the landlord, written from the report.
 *
 * Two channels, because a lease arrives by email from an agent and by WhatsApp from a private
 * landlord, and a message written for one reads wrong in the other. Two tones, because a first
 * reply to someone whose flat you want is friendly, and a second one, or one to a landlord whose
 * clause the law already overrides, can afford to be firm and name the statute.
 *
 * Each clause gets a proposed replacement wording, not only a complaint: a landlord asked "can we
 * change clause 4 to say this" has something to agree to, and one asked "clause 4 is unfair" has
 * only something to argue with. Where the reader has said where the home is, the wording carries
 * that place's sourced figure (the finding's ask already does), and nothing else is invented.
 */
object Drafts {

    enum class Channel { EMAIL, WHATSAPP }
    enum class Tone { FRIENDLY, FIRM }

    const val SUBJECT = "A few changes before I sign"

    /** The message for [groups], in [channel] and [tone]. Null when there is nothing to ask. */
    fun draft(
        groups: List<ClauseGroup>,
        channel: Channel,
        tone: Tone,
        void: List<VoidClause> = emptyList(),
    ): String? {
        if (groups.isEmpty()) return null
        val laws = void.groupBy { it.clauseIndex }
        return when (channel) {
            Channel.EMAIL -> email(groups, tone, laws)
            Channel.WHATSAPP -> whatsapp(groups, tone, laws)
        }
    }

    private fun email(groups: List<ClauseGroup>, tone: Tone, laws: Map<Int, List<VoidClause>>): String = buildString {
        appendLine("Hello,")
        appendLine()
        appendLine(
            when (tone) {
                Tone.FRIENDLY -> "Thank you for sending the lease. I would like to go ahead, and before I sign I would like to ask for " +
                    (if (groups.size == 1) "one change." else "${groups.size} changes.")
                Tone.FIRM -> "I have read the lease carefully. Before I can sign it, the following " +
                    (if (groups.size == 1) "clause needs to change." else "${groups.size} clauses need to change.")
            }
        )
        appendLine()
        groups.forEachIndexed { i, g ->
            val f = g.findings.first()
            appendLine("${i + 1}. ${Report.where(g.clause.text)} (${f.topic.lowercase()})")
            appendLine("It currently says: \"${excerpt(g.clause.text)}\"")
            appendLine("Proposed wording: ${counter(g)}")
            if (tone == Tone.FIRM) {
                laws[g.clause.index]?.firstOrNull()?.let { appendLine("For reference: ${it.law}. ${it.why}") }
            }
            appendLine()
        }
        appendLine(
            when (tone) {
                Tone.FRIENDLY -> "Could you let me know which of these you can agree to? I am happy to talk them through."
                Tone.FIRM -> "Please send a revised copy with these changes, or let me know which you cannot accept and why."
            }
        )
        appendLine()
        append(if (tone == Tone.FRIENDLY) "Many thanks" else "Regards")
    }

    private fun whatsapp(groups: List<ClauseGroup>, tone: Tone, laws: Map<Int, List<VoidClause>>): String = buildString {
        appendLine(
            when (tone) {
                Tone.FRIENDLY -> "Hi, thanks for sending the lease! Before I sign, could we change " +
                    (if (groups.size == 1) "one thing?" else "a few things?")
                Tone.FIRM -> "Hi, I've been through the lease. These need to change before I sign:"
            }
        )
        appendLine()
        groups.forEachIndexed { i, g ->
            append("${i + 1}. ${Report.where(g.clause.text)}: ")
            appendLine(counter(g))
            if (tone == Tone.FIRM) laws[g.clause.index]?.firstOrNull()?.let { appendLine("   (${it.law})") }
        }
        appendLine()
        append(if (tone == Tone.FRIENDLY) "Happy to talk it through. Thanks!" else "Can you send a revised copy?")
    }

    /** The clause's first words, enough to find it, never the whole paragraph. */
    internal fun excerpt(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim().replace(Regex("^\\d+(?:\\.\\d+)*[.)]?\\s+"), "")
        val words = flat.split(" ")
        return if (words.size <= 22) flat else words.take(22).joinToString(" ").trimEnd(',', ';', ':') + "..."
    }

    /**
     * The replacement wording proposed for a clause, from its most serious finding. A written
     * clause for the rules a lease most often gets wrong; for the rest, and wherever the reader's
     * place supplies its own figure, the finding's ask, set as a clause.
     */
    internal fun counter(group: ClauseGroup): String {
        val f = group.findings.first()
        COUNTERS[f.ruleId]?.let { if (!placeSpecific(f)) return it }
        val ask = f.ask.ifBlank { return "Removed from the agreement." }
        return "Revised to provide for " + ask.trimEnd('.') + "."
    }

    /** True when the place swapped in its own ask, which then beats the general wording. */
    private fun placeSpecific(f: Finding): Boolean {
        val general = Scanner.asks[f.ruleId] ?: return false
        return f.ask.isNotBlank() && f.ask != general
    }

    private val COUNTERS = mapOf(
        "late-fee" to "\"If rent is more than five days late, the Tenant shall pay a single late charge of no more than 5 percent of that month's rent.\"",
        "interest-rate" to "\"Interest on late rent, if any, shall not exceed the statutory rate for judgment debts.\"",
        "dishonour-fee" to "\"A returned payment shall incur only the bank's actual charge to the Landlord.\"",
        "deposit-size" to "\"The security deposit shall not exceed one month's rent.\"",
        "deposit-no-interest" to "\"The deposit shall be held in a separate account and any interest earned paid to the Tenant.\"",
        "deposit-refund-delay" to "\"The deposit shall be returned within 21 days of the Tenant vacating, with an itemised list of any deductions and receipts for them.\"",
        "sole-discretion" to "\"Deductions shall be limited to documented damage beyond fair wear and tear, agreed in writing or shown by receipts.\"",
        "forfeiture" to "\"The deposit may be applied only to rent owed and to documented damage beyond fair wear and tear.\"",
        "tenant-structural-repairs" to "\"The Landlord shall keep the structure, wiring, plumbing and fixed installations in repair. The Tenant shall pay only for damage the Tenant causes.\"",
        "no-setoff" to "\"Nothing in this agreement limits the Tenant's right to deduct the documented cost of an urgent repair the Landlord failed to make after written notice.\"",
        "lock-in" to "\"Either party may end this agreement after the first six months on one month's written notice.\"",
        "unexpired-liability" to "\"If the Tenant leaves early, the Tenant shall pay rent only until the premises are re-let or the notice period ends, whichever is sooner.\"",
        "long-notice" to "\"Either party may end this agreement on one month's written notice.\"",
        "liquidated-damages" to "\"If the Tenant leaves without full notice, the Tenant shall pay rent for the unserved part of the notice period and nothing more.\"",
        "rent-escalation" to "\"The rent may be increased once a year, by no more than 5 percent, on two months' written notice.\"",
        "unilateral-revision" to "\"The rent and charges may change only by written agreement of both parties.\"",
        "silence-is-consent" to "\"No change to this agreement takes effect unless both parties agree to it in writing.\"",
        "entry-without-notice" to "\"The Landlord may enter only at reasonable hours on at least 24 hours' written notice, except in an emergency.\"",
        "short-entry-notice" to "\"The Landlord may enter only at reasonable hours on at least 24 hours' written notice, except in an emergency.\"",
        "showings" to "\"Viewings by prospective tenants or buyers shall be limited to the last month of the term, at agreed times, on 24 hours' notice.\"",
        "occupant-surcharge" to "\"The Tenant's immediate family and occasional guests may stay without any change to the rent.\"",
        "auto-renewal" to "\"This agreement ends on its expiry date unless both parties sign a renewal.\"",
        "deemed-service" to "\"A notice is served only when received, by email to the address the Tenant gives or by hand.\"",
        "tenant-pays-tax" to "\"Property taxes and levies on the premises shall be paid by the Landlord.\"",
        "future-levies" to "\"Any new tax, levy or charge on the property introduced during the term shall be borne by the Landlord.\"",
        "charge-increases" to "\"Maintenance and service charges may be increased only once a year, in line with documented costs.\"",
        "mandatory-repaint" to "\"On leaving, the Tenant shall return the premises in the condition received, fair wear and tear excepted. Repainting is not required.\"",
        "restore-original" to "\"On leaving, the Tenant shall return the premises in the condition received, fair wear and tear excepted.\"",
        "no-liability" to "\"The Landlord remains responsible for loss or injury caused by the Landlord's own negligence or failure to repair.\"",
        "legal-costs" to "\"Each party shall bear its own legal costs, except as a court orders.\"",
        "as-is" to "\"The Landlord confirms the premises are fit to live in and shall fix any defect reported in the first 30 days.\"",
        "pro-cleaning" to "\"The Tenant shall return the premises in a clean condition. Professional cleaning may be charged only if the premises were professionally cleaned at the start.\"",
        "rent-in-advance" to "\"Rent shall be paid monthly in advance, one month at a time.\"",
        "confession" to "\"Nothing in this agreement waives the Tenant's right to be heard by a court.\"",
        "distraint" to "\"The Landlord shall not seize or hold the Tenant's belongings for any reason.\"",
        "pet-insurance" to "\"Any pet-related charge is limited to documented damage caused by the pet.\"",
        "uk-fee" to "\"No fee is payable other than those permitted by the Tenant Fees Act 2019.\"",
        "section-21" to "\"The section 21 notice wording is removed from this agreement.\"",
    )

    internal val counterRuleIds: Set<String> get() = COUNTERS.keys
}
