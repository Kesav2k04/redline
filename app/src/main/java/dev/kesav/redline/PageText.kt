package dev.kesav.redline

/**
 * Joins text read off pages into something [ClauseSplitter] can take.
 *
 * Text recognition hands back blocks, and a PDF text layer hands back a run per page.
 * Neither knows where a clause ends. A block boundary is sometimes a paragraph and
 * sometimes just a column edge or the bottom of a page cutting a sentence in half, and
 * splitting there would turn one clause into two fragments, each too short for a rule to
 * find its number and its trigger together.
 *
 * So the join is decided by the text itself: a piece that ends a sentence ends a
 * paragraph, and a piece that stops mid-sentence carries on into the next one. Line breaks
 * inside a piece are kept, because the splitter already knows how to rejoin wrapped lines
 * and which ones start a numbered clause.
 */
internal object PageText {

    // Running heads and page numbers: "Page 2 of 5", "2", "- 2 -". Kept out entirely,
    // since glued into a clause they put a stray figure next to the words a rule anchors
    // on.
    private val furniture = Regex(
        """^\s*(page\s+\d{1,3}(\s+of\s+\d{1,3})?|\d{1,3}|-\s*\d{1,3}\s*-)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // A colon is left out on purpose: "as follows:" belongs with the list under it.
    private val sentenceEnders = setOf('.', ';', '!', '?', ')', '"', '”')

    fun assemble(pages: List<List<String>>): String = buildString {
        for (piece in pages.flatten()) {
            val lines = piece.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !furniture.matches(it) }
            if (lines.isEmpty()) continue

            if (isNotEmpty()) {
                append(if (last() in sentenceEnders) "\n\n" else "\n")
            }
            append(lines.joinToString("\n"))
        }
    }
}
