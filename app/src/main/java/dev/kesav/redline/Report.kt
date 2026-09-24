package dev.kesav.redline

/**
 * The report as plain text, for sending to whoever can do something about it.
 *
 * Finding the clauses is only half the job. A tenant who learns their lease has eleven
 * costly clauses still has to raise them with a landlord, a parent or a lawyer, and
 * retyping nineteen findings into a message is where that stops happening. The lease
 * arrives by share and the argument leaves the same way.
 *
 * Plain text rather than PDF or HTML on purpose: it pastes into WhatsApp, email and a
 * letter without a renderer, an export dialogue or a file the reader has to open.
 */
object Report {

    /** Kept short. This travels into other people's inboxes. */
    const val SUBJECT = "Clauses in this lease worth raising"

    /**
     * Grouped by clause, not by finding.
     *
     * A single security-deposit paragraph routinely trips four rules: the size, the
     * ninety-day wait, the missing interest and the landlord's sole discretion. Listed
     * one finding at a time, that paragraph was quoted in full four times in a row and
     * the message became unreadable at exactly the point it mattered. Grouping also
     * happens to be how a person raises it: not "I have nineteen objections" but
     * "clause four has four problems with it".
     */
    fun build(state: ScanState.Scanned): String {
        if (state.findings.isEmpty()) {
            return buildString {
                appendLine("Redline read ${state.clauseCount} clauses and matched none of its rules.")
                appendLine()
                appendLine(DISCLAIMER)
            }.trim()
        }

        // groupBy preserves first-appearance order, so the clause carrying the worst
        // finding stays at the top, the same order the screen showed.
        val byClause = state.findings.groupBy { it.clause.index }

        return buildString {
            appendLine(
                "Redline read ${state.clauseCount} clauses in this lease. " +
                    "${state.flaggedClauses} of them could cost money."
            )
            appendLine()

            byClause.values.forEachIndexed { i, group ->
                appendLine("${i + 1}) ${quote(group.first().clause.text)}")
                appendLine()
                for (f in group) {
                    appendLine("   ${label(f.severity)}  ${f.headline}")
                    appendLine("   ${f.reason}")
                    // The line the reader actually sends. Without it the message lists
                    // what is wrong and leaves the landlord to guess what would fix it.
                    if (f.ask.isNotBlank()) appendLine("   Ask for ${f.ask}.")
                    appendLine()
                }
            }

            appendLine(DISCLAIMER)
        }.trim()
    }

    /** Subject for the message to the landlord: a request, not an alarm. */
    const val LETTER_SUBJECT = "A few changes before I sign"

    /**
     * A message to the landlord asking for each fix, one clause at a time.
     *
     * The report above is for the tenant and whoever advises them: it quotes the clause and
     * says why it costs money. The landlord already has the lease and does not need to be
     * told it is unfair, so this carries only where to look and what to change, in the
     * polite first person of someone who still wants the flat. Null when nothing was found,
     * because an empty list of requests is not a message anyone should send.
     */
    fun letter(state: ScanState.Scanned): String? {
        if (state.findings.isEmpty()) return null
        val byClause = state.findings.groupBy { it.clause.index }
        return buildString {
            appendLine("Hello,")
            appendLine()
            appendLine(
                "Thank you for sending the lease. Before I sign, I would like to ask for " +
                    if (byClause.size == 1) "one change:" else "a few changes:"
            )
            appendLine()
            for (group in byClause.values) {
                appendLine(where(group.first().clause.text))
                for (ask in group.map { it.ask }.filter { it.isNotBlank() }.distinct()) {
                    appendLine("- ${ask.replaceFirstChar { it.uppercase() }}")
                }
                appendLine()
            }
            appendLine("Could you let me know which of these you can agree to? I am happy to talk them through.")
            appendLine()
            append("Thank you")
        }
    }

    /**
     * How the letter points at a clause: by its own number when the lease numbers it, and
     * otherwise by its opening words, which is how anyone finds an unnumbered paragraph.
     */
    internal fun where(text: String): String {
        val flat = collapse(text)
        OWN_NUMBER.find(flat)?.let { return "Clause ${it.groupValues[1]}" }
        val words = flat.split(" ")
        val opening = words.take(6).joinToString(" ").trimEnd(',', ';', ':', '.')
        return if (words.size > 6) "The clause starting \"$opening...\"" else "The clause \"$opening\""
    }

    private fun label(severity: Severity): String = when (severity) {
        Severity.HIGH -> "SERIOUS"
        Severity.MEDIUM -> "WORTH CHECKING"
    }

    /**
     * Lease text arrives hard-wrapped from whatever produced it. Left alone, every quoted
     * clause would break at the original column width inside the message and look like
     * it had been mangled in transit.
     */
    private fun collapse(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()

    /**
     * The lease's own clause number, when it has one, becomes a label instead of being
     * quoted straight after the list number. "1) 3. Late payment" read as a numbering
     * accident in the message a tenant sends; "1) Clause 3: Late payment" says which
     * clause to turn to. A clause that merely starts with a quantity keeps its text.
     */
    private fun quote(text: String): String {
        val flat = collapse(text)
        val own = OWN_NUMBER.find(flat) ?: return flat
        return "Clause ${own.groupValues[1]}: ${flat.substring(own.range.last + 1)}"
    }

    // "4." and "4)" always, and a bare "4.2" when a capitalised word follows it, which is
    // how most leases number sub-clauses. "30 days notice" stays quoted as written.
    private val OWN_NUMBER = Regex("^(\\d+(?:\\.\\d+)*)(?:[.)]\\s+|\\s+(?=[A-Z]))")

    private const val DISCLAIMER =
        "Found with Redline, which matches clauses against a fixed set of rules. " +
            "It catches what those rules describe and nothing else, so this is a list of " +
            "things to ask about rather than legal advice."
}
