package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the rules over the same 30 clauses the embedding approach was measured on,
 * so the two can be compared on identical text. That corpus lives in `eval/` and was
 * committed before either matcher existed.
 *
 * This is a regression fixture, not a claim about unseen leases. It proves the rules
 * fire where they should and stay quiet where they should, on text they were written
 * against. `HeldOutTest` is the one that asks whether they generalise.
 */
class ScannerTest {

    private data class Row(val id: String, val label: String, val category: String, val text: String)

    private fun corpus(path: String): List<Row> =
        File(path).readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
            .map { Row(it[0], it[1], it[2], it[3]) }

    private fun findingsFor(row: Row) = Scanner.scan(listOf(Clause(0, row.text)))

    @Test
    fun `every costly clause is flagged and no benign clause is`() {
        val rows = corpus("../eval/clauses.tsv")
        assertEquals("corpus size changed", 30, rows.size)

        val missed = mutableListOf<String>()
        val falseAlarms = mutableListOf<String>()

        println("%-5s %-7s %-18s %s".format("id", "truth", "category", "findings"))
        for (row in rows) {
            val found = findingsFor(row)
            val names = found.joinToString(", ") { it.ruleId }
            println("%-5s %-7s %-18s %s".format(row.id, row.label, row.category, names.ifEmpty { "-" }))

            if (row.label == "costly" && found.isEmpty()) missed += row.id
            if (row.label == "benign" && found.isNotEmpty()) falseAlarms += "${row.id} ($names)"
        }

        println("\nrules: ${Scanner.ruleCount}")
        println("costly caught : ${20 - missed.size}/20")
        println("benign quiet  : ${10 - falseAlarms.size}/10")

        assertEquals("costly clauses with no finding: $missed", emptyList<String>(), missed)
        assertEquals("benign clauses that fired: $falseAlarms", emptyList<String>(), falseAlarms)
    }

    @Test
    fun `a finding names the number it found`() {
        val clause = Clause(
            0,
            "If the rent remains unpaid after the due date the Tenant shall pay an " +
                "additional charge of ten percent of the monthly rent.",
        )
        val finding = Scanner.scan(listOf(clause)).single { it.ruleId == "late-fee" }

        assertEquals("Late payment penalty of 10 percent", finding.headline)
    }

    @Test
    fun `a quantity is not borrowed from elsewhere in the same clause`() {
        val clause = Clause(
            0,
            "Termination by the Tenant shall require six months notice in writing, " +
                "failing which three months rent shall be payable as liquidated damages " +
                "and shall be adjusted against the deposit.",
        )
        val ids = Scanner.scan(listOf(clause)).map { it.ruleId }.toSet()

        // The only months figure beside "deposit" is three, so no deposit-size claim.
        assertEquals(setOf("long-notice", "liquidated-damages"), ids)
    }

    @Test
    fun `an ordinary sentence produces nothing`() {
        val clause = Clause(0, "The rent shall be paid on or before the fifth day of each calendar month.")
        assertEquals(emptyList<Finding>(), Scanner.scan(listOf(clause)))
    }
}
