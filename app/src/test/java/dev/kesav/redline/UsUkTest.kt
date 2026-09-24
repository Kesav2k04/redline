package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 94 clauses from US and English leases, statutes and official guidance, each labelled
 * against the law where it applies: 55 from California, New York, Texas, Florida,
 * Illinois and the HUD model lease, and 39 from England.
 *
 * The US and UK rules were written with these clauses open, so this is a regression
 * check in the same sense as `ScannerTest` on the frozen corpus, not a measure of how
 * the rules do on leases they have not seen. The counts are pinned exactly, in both
 * directions. A rule change that catches more should move the numbers here and in
 * eval/README.md together, rather than pass without anyone noticing.
 *
 * Some misses cannot be fixed by any rule that reads one clause. The same sentence can
 * be costly in New York and ordinary in Texas, and nothing in the sentence says which.
 */
class UsUkTest {

    private data class Row(val id: String, val label: String, val text: String, val country: String)

    private fun rows(): List<Row> =
        File("../eval/intl.tsv").readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
            .map { Row(it[0], it[1], it[3], if (it[4] == "ENG") "England" else "US") }

    @Test
    fun `US and English results hold`() {
        val all = rows()
        assertEquals("corpus size changed", 94, all.size)

        val summary = listOf("US", "England").joinToString("; ") { country ->
            val mine = all.filter { it.country == country }
            val costly = mine.filter { it.label == "costly" }
            val benign = mine.filter { it.label == "benign" }
            val fired = mine.associate { it.id to Scanner.scan(listOf(Clause(0, it.text))) }

            val missed = costly.filter { fired.getValue(it.id).isEmpty() }.map { it.id }
            val alarms = benign.filter { fired.getValue(it.id).isNotEmpty() }
                .map { row -> "${row.id} (${fired.getValue(row.id).joinToString(", ") { it.ruleId }})" }
            println("$country missed $missed")
            println("$country benign that fired $alarms")

            "$country ${costly.size - missed.size}/${costly.size} caught, " +
                "${alarms.size}/${benign.size} benign fired"
        }

        assertEquals(
            "US 18/26 caught, 4/29 benign fired; England 13/19 caught, 0/20 benign fired",
            summary,
        )
    }
}
