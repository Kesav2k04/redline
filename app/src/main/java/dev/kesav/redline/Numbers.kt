package dev.kesav.redline

/**
 * Reads quantities out of contract prose.
 *
 * Leases spell their numbers: "ten percent", "ninety days", "two thousand rupees".
 * Nothing useful can be extracted until those become integers, so this runs before
 * every rule.
 */
object Numbers {

    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19,
    )

    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private val scales = mapOf(
        "hundred" to 100, "thousand" to 1_000, "lakh" to 100_000,
        "lakhs" to 100_000, "crore" to 10_000_000, "crores" to 10_000_000,
    )

    // Words, numerals and the percent sign. A numeral keeps its thousands separators
    // and its decimal point: splitting "5,000" at the comma used to read it as 5 + 000,
    // and "1.5" as 1 + 5, which turned a one and a half percent late fee into fifteen.
    // "%" is its own token because leases, and every PDF, write "10%" far more often
    // than "ten percent", and a tokenizer that dropped it made every percentage rule
    // blind to the commonest way the number is written.
    private val token = Regex("""[a-z]+|\d+(?:,\d+)*(?:\.\d+)?|%""")
    private val numeral = Regex("""\d+(?:,\d+)*(?:\.\d+)?""")

    /**
     * The quantity stated immediately before [unit], or null when there is none.
     *
     * Scans leftwards from the unit and stops at the first word that is not part of a
     * number, so "rent for each of the ten months" yields 10 while "rent for months"
     * yields nothing.
     */
    fun valueBefore(text: String, unit: Regex): Int? = quantities(text, unit).firstOrNull()?.value

    /**
     * A stated quantity, and the character span from its first number word to its unit.
     *
     * [value] is rounded down, which keeps every "at least N" test exact for whole N:
     * 2.5 percent is below a threshold of 3 and so is 2. [shown] is the number as the
     * lease wrote it, so a headline says 12.5 percent rather than a rounded figure the
     * reader cannot find in the quoted clause.
     */
    data class Quantity(val value: Int, val at: IntRange, val shown: String = value.toString())

    /**
     * Every quantity in [text] expressed in [unit], in reading order.
     *
     * Positions are kept because the first quantity in a clause is often not the one a
     * rule is about. "The rent shall be escalated by ten percent, and arrears carry a
     * charge of two percent" has two percentages, and only the one beside the charge is
     * a late fee.
     */
    fun quantities(text: String, unit: Regex): List<Quantity> {
        val tokens = token.findAll(text.lowercase()).toList()
        val found = mutableListOf<Quantity>()
        for (i in tokens.indices) {
            // "%" and "per cent" are spellings of the unit, not separate words. For the
            // two-word form the number run starts before "per".
            val (word, first) = when {
                tokens[i].value == "%" -> "percent" to i
                tokens[i].value == "cent" && tokens.getOrNull(i - 1)?.value == "per" -> "percent" to i - 1
                else -> tokens[i].value to i
            }
            if (!unit.matches(word)) continue
            val run = mutableListOf<String>()
            var j = first - 1
            while (j >= 0 && isNumberWord(tokens[j].value)) {
                run += tokens[j].value
                j--
            }
            val words = once(run.asReversed())
            val value = parse(words) ?: continue
            val shown = words.singleOrNull()?.takeIf { numeral.matches(it) }?.replace(",", "") ?: value.toString()
            found += Quantity(value, tokens[j + 1].range.first..tokens[i].range.last, shown)
        }
        return found
    }

    /**
     * One number written twice, "thirty (30) days", read once.
     *
     * US leases spell a figure and repeat it in brackets. The tokenizer drops the brackets,
     * so the run arrives as `thirty 30` and [parse] would add the two. Only a spelled run
     * beside a single numeral of the same value is collapsed: "3 lakhs" is a numeral and a
     * scale, and "5. Ten months" is a clause number and a count, and both are left to [parse].
     */
    private fun once(words: List<String>): List<String> {
        val at = words.indices.singleOrNull { numeral.matches(words[it]) } ?: return words
        val spelled = when (at) {
            0 -> words.drop(1)
            words.lastIndex -> words.dropLast(1)
            else -> return words
        }
        val said = parse(spelled) ?: return words
        val written = words[at].replace(",", "").toDouble()
        return if (written == said.toDouble()) listOf(words[at]) else words
    }

    fun parse(words: List<String>): Int? {
        var total = 0
        var current = 0
        var seen = false

        for (w in words) {
            when {
                w == "and" -> Unit
                w in units -> { current += units.getValue(w); seen = true }
                w in tens -> { current += tens.getValue(w); seen = true }
                w == "hundred" -> { current = maxOf(current, 1) * 100; seen = true }
                w in scales -> { total += maxOf(current, 1) * scales.getValue(w); current = 0; seen = true }
                numeral.matches(w) -> { current += w.replace(",", "").toDouble().toInt(); seen = true }
                else -> return if (seen) total + current else null
            }
        }
        return if (seen) total + current else null
    }

    private fun isNumberWord(w: String) =
        w == "and" || w in units || w in tens || w in scales || numeral.matches(w)
}
