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

    private val token = Regex("""[a-z0-9]+""")

    /**
     * The quantity stated immediately before [unit], or null when there is none.
     *
     * Scans leftwards from the unit and stops at the first word that is not part of a
     * number, so "rent for each of the ten months" yields 10 while "rent for months"
     * yields nothing.
     */
    fun valueBefore(text: String, unit: Regex): Int? = quantities(text, unit).firstOrNull()?.value

    /** A stated quantity, and the character span from its first number word to its unit. */
    data class Quantity(val value: Int, val at: IntRange)

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
            if (!unit.matches(tokens[i].value)) continue
            val run = mutableListOf<String>()
            var j = i - 1
            while (j >= 0 && isNumberWord(tokens[j].value)) {
                run += tokens[j].value
                j--
            }
            val value = parse(run.asReversed()) ?: continue
            found += Quantity(value, tokens[j + 1].range.first..tokens[i].range.last)
        }
        return found
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
                w.all(Char::isDigit) -> { current += w.toInt(); seen = true }
                else -> return if (seen) total + current else null
            }
        }
        return if (seen) total + current else null
    }

    private fun isNumberWord(w: String) =
        w == "and" || w in units || w in tens || w in scales || w.all(Char::isDigit)
}
