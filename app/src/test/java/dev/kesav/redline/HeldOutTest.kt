package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ten clauses written after the rules were finished, in deliberately different
 * language: lessor and lessee rather than landlord and tenant, "demised premises",
 * "surcharge", "discharged by". None of them was available while the patterns were
 * being written.
 *
 * `ScannerTest` only shows the rules work on text they were built against. This is the
 * one that asks whether they work on text they were not. The bar is deliberately lower
 * than the frozen corpus, and whatever it reports is recorded rather than tuned away:
 * editing a rule to pass a held-out clause turns it into a training clause.
 */
class HeldOutTest {

    private data class Row(val id: String, val label: String, val text: String)

    private fun rows(): List<Row> =
        File("../eval/heldout.tsv").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
            .map { Row(it[0], it[1], it[3]) }

    @Test
    fun `rules generalise to unseen phrasing without inventing findings`() {
        val all = rows()
        val costly = all.filter { it.label == "costly" }
        val benign = all.filter { it.label == "benign" }

        val caught = mutableListOf<String>()
        val missed = mutableListOf<String>()
        val falseAlarms = mutableListOf<String>()

        for (row in all) {
            val found = Scanner.scan(listOf(Clause(0, row.text)))
            val names = found.joinToString(", ") { it.ruleId }
            println("%-4s %-7s %s".format(row.id, row.label, names.ifEmpty { "-" }))

            when {
                row.label == "costly" && found.isEmpty() -> missed += row.id
                row.label == "costly" -> caught += row.id
                found.isNotEmpty() -> falseAlarms += "${row.id} ($names)"
            }
        }

        println("\nheld out recall     : ${caught.size}/${costly.size}  missed $missed")
        println("held out precision  : ${benign.size - falseAlarms.size}/${benign.size}  fired $falseAlarms")

        // A false alarm on unseen benign text is the failure that actually hurts a
        // reader, so that one is held at zero. Recall is allowed to be imperfect and
        // the gaps are listed in eval/README.md rather than patched out of existence.
        assertTrue("benign clauses that fired: $falseAlarms", falseAlarms.isEmpty())
        assertTrue("recall collapsed: only ${caught.size}/${costly.size}", caught.size >= 3)
    }
}
