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
                    "${state.flaggedClauses} of them will cost money."
            )
            appendLine()

            byClause.values.forEachIndexed { i, group ->
                appendLine("${i + 1}) ${collapse(group.first().clause.text)}")
                appendLine()
                for (f in group) {
                    appendLine("   ${label(f.severity)}  ${f.headline}")
                    appendLine("   ${f.reason}")
                    appendLine()
                }
            }

            appendLine(DISCLAIMER)
        }.trim()
    }

    private fun label(severity: Severity): String = when (severity) {
        Severity.HIGH -> "COSTLY"
        Severity.MEDIUM -> "WORTH CHECKING"
    }

    /**
     * Lease text arrives hard-wrapped from whatever produced it. Left alone, every quoted
     * clause would break at the original column width inside the message and look like
     * it had been mangled in transit.
     */
    private fun collapse(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()

    private const val DISCLAIMER =
        "Found with Redline, which matches clauses against a fixed set of rules. " +
            "It catches what those rules describe and nothing else, so this is a list of " +
            "things to ask about rather than legal advice."
}
