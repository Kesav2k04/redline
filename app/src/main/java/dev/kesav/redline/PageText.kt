package dev.kesav.redline

/**
 * Turns text read off pages into paragraphs, the way the lease was written.
 *
 * Text recognition hands back blocks of short lines, and a PDF text layer hands back a
 * page of lines broken wherever the typesetter ran out of width. Shown as they come, the
 * editor fills with ragged half-lines, and nobody checks text they cannot read. So the
 * lines are joined back into paragraphs here, and the paragraphs are what the reader
 * sees, edits and scans.
 *
 * Where a paragraph ends is decided by the text itself. A numbered line starts one, and
 * so does a heading. A block or page boundary starts one only when the text before it
 * finished a sentence: otherwise it is a column edge or the bottom of a page cutting a
 * clause in half, and splitting there would leave two fragments, each too short for a
 * rule to find its number and its trigger together.
 */
internal object PageText {

    // Running heads and page numbers: "Page 2 of 5", "2", "- 2 -". Kept out entirely,
    // since glued into a clause they put a stray figure next to the words a rule anchors
    // on.
    private val furniture = Regex(
        """^\s*(page\s+\d{1,3}(\s+of\s+\d{1,3})?|\d{1,3}|-\s*\d{1,3}\s*-)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // A clause marker that cannot be the start of a wrapped sentence: "12. ", "7) ",
    // "(a) ". A bare "30 days" at the start of a line is usually the middle of "refunded
    // within 30 days", and treating it as clause 30 would cut the rule's number away
    // from its trigger.
    private val marker = Regex("""^\s*(\d+[.)]|\([a-z]{1,3}\)|\([ivxl]{1,5}\))\s""")

    // A colon is left out on purpose: "as follows:" belongs with the list under it.
    private val sentenceEnders = setOf('.', ';', '!', '?', ')', '"', '”')

    fun assemble(pages: List<List<String>>): String {
        val out = StringBuilder()
        var afterHeading = false

        for (piece in pages.flatten()) {
            val lines = piece.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !furniture.matches(it) }

            lines.forEachIndexed { k, line ->
                if (out.isEmpty()) {
                    out.append(line)
                    afterHeading = isHeading(line)
                    return@forEachIndexed
                }

                val finished = out.last() in sentenceEnders
                val heading = isHeading(line) && (k == 0 || finished)
                val numbered = ClauseSplitter.numbering.containsMatchIn(line) &&
                    (finished || out.last() == ':' || marker.containsMatchIn(line))

                when {
                    afterHeading || heading || numbered || (k == 0 && finished) ->
                        out.append("\n\n").append(line)

                    // "pay-" then "ment": one word broken by the line, not two.
                    out.endsWith("-") && !out.endsWith("--") && line.first().isLowerCase() ->
                        out.setLength(out.length - 1).also { out.append(line) }

                    else -> out.append(' ').append(line)
                }
                afterHeading = heading
            }
        }
        return out.toString()
    }

    /**
     * A title or section heading: capitals, short, and not a sentence. "RENT AND
     * DEPOSIT" is one; a clause written in capitals ends with a full stop and is not.
     */
    private fun isHeading(line: String): Boolean =
        line.length <= 60 &&
            line.any(Char::isLetter) &&
            line.none(Char::isLowerCase) &&
            line.last() !in sentenceEnders
}
