package dev.kesav.redline

data class Clause(val index: Int, val text: String)

/**
 * Splits pasted or shared contract text into clauses.
 *
 * Lease text arrives with hard-wrapped lines, hyphenated line breaks and numbering
 * that carries the real structure. Paragraph breaks alone under-split (a whole
 * "Section 7" block becomes one clause) and sentence splitting alone over-splits
 * (every "i.e." becomes a boundary). This does both, in that order.
 */
object ClauseSplitter {

    private const val LONG_BLOCK = 400

    // 1. / 1.1 / 1.1.1 / (a) / (iv) / Section 4 / ARTICLE II / 7)
    private val numbering = Regex(
        """^\s*(\(?\d+(\.\d+)*[.)]?|\([a-z]{1,3}\)|\([ivxl]{1,5}\)|(?i:section|article|clause)\s+[\dIVXL]+\.?)\s+"""
    )

    private val sentenceEnd = Regex("""(?<=[.;:])\s+(?=[A-Z("\d])""")

    // Abbreviations whose full stop is not a sentence boundary.
    private val abbreviation = Regex("""\b(?:i\.e|e\.g|etc|no|vs|approx|Rs|viz|cf|al)\.$""", RegexOption.IGNORE_CASE)

    fun split(raw: String): List<Clause> {
        if (raw.isBlank()) return emptyList()

        return normalise(raw)
            .split(Regex("""\n{2,}"""))
            .flatMap { block -> splitBlock(block.trim()) }
            .map { it.trim() }
            .filter { it.length >= 20 && it.any(Char::isLetter) }
            .mapIndexed(::Clause)
    }

    /**
     * Rejoins hard-wrapped lines so a clause is one string, while keeping the
     * line breaks that actually separate clauses.
     */
    private fun normalise(raw: String): String {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')

        return buildString {
            val lines = text.split('\n')
            lines.forEachIndexed { i, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    append("\n\n")
                    return@forEachIndexed
                }

                // "mainten-\nance" is one word broken by the wrap, not two.
                if (trimmed.endsWith("-") && !trimmed.endsWith("--")) {
                    append(trimmed.dropLast(1))
                    return@forEachIndexed
                }

                append(trimmed)

                val next = lines.getOrNull(i + 1)?.trim()
                when {
                    next.isNullOrEmpty() -> append("\n\n")
                    // A new numbered item starts a new block even without a blank line.
                    numbering.containsMatchIn(next) -> append("\n\n")
                    else -> append(' ')
                }
            }
        }
    }

    private fun splitBlock(block: String): List<String> {
        if (block.length <= LONG_BLOCK) return listOf(block)

        val pieces = mutableListOf<String>()
        val current = StringBuilder()

        for (piece in block.split(sentenceEnd)) {
            current.append(if (current.isEmpty()) piece else " $piece")
            val holdOpen = abbreviation.containsMatchIn(piece) || current.length < 80
            if (!holdOpen) {
                pieces += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) pieces += current.toString()

        return pieces
    }
}
