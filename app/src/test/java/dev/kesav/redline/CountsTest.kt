package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import dev.kesav.redline.ui.severitySplit
import org.junit.Test

/**
 * The two numbers on the results heading have to describe the same population.
 *
 * They did not. The heading counted clauses and the line under it counted findings, so
 * the sample lease rendered "11 of 16 clauses will cost you money" above "13 of them are
 * worth arguing about". Nothing crashed and no test failed; it was only visible by
 * reading the screen.
 */
class CountsTest {

    private fun scanSample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.scan(clauses))
    }

    @Test
    fun `the subheading count can never exceed the heading count`() {
        val scanned = scanSample()
        assertTrue(
            "highClauses ${scanned.highClauses} exceeds flaggedClauses ${scanned.flaggedClauses}",
            scanned.highClauses <= scanned.flaggedClauses,
        )
    }

    @Test
    fun `flagged clauses can never exceed the clauses that were read`() {
        val scanned = scanSample()
        assertTrue(scanned.flaggedClauses <= scanned.clauseCount)
    }

    @Test
    fun `two findings on one clause count as one clause`() {
        val clause = Clause(index = 0, text = "Deposit of ten months rent, returned after ninety days.")
        val scanned = ScanState.Scanned(
            clauseCount = 1,
            findings = listOf(
                Finding(clause, "deposit-size", "Your deposit", "Deposit equal to 10 months rent", "why", Severity.HIGH),
                Finding(clause, "deposit-delay", "Your deposit", "Deposit returned only after 90 days", "why", Severity.HIGH),
            ),
        )
        assertEquals(1, scanned.flaggedClauses)
        assertEquals(1, scanned.highClauses)
    }

    @Test
    fun `the subheading never contradicts the heading`() {
        val scanned = scanSample()
        val line = severitySplit(scanned.highClauses, scanned.flaggedClauses)
        assertEquals("11 clauses flagged in the fixture", 11, scanned.flaggedClauses)
        assertEquals("9 of them are serious.", line)
    }

    @Test
    fun `the subheading never says a flagged clause is free`() {
        // The heading above it reads "N of M clauses will cost you money". Any word in
        // the line beneath that implies some of those N are fine puts the two largest
        // pieces of type on the screen in open disagreement, which is exactly what
        // "9 marked costly, 2 worth checking" did.
        val innocent = listOf("worth checking", "minor", "harmless", "fine", "no problem")
        for (flagged in 1..30) {
            for (high in 0..flagged) {
                val line = severitySplit(high, flagged)
                for (word in innocent) {
                    assertFalse(
                        "$high of $flagged implies a flagged clause is free: $line",
                        line.contains(word),
                    )
                }
            }
        }
    }

    @Test
    fun `the subheading never claims more serious clauses than were flagged`() {
        for (flagged in 1..30) {
            for (high in 0..flagged) {
                val line = severitySplit(high, flagged)
                for (n in Regex("[0-9]+").findAll(line).map { it.value.toInt() }) {
                    assertTrue("$high of $flagged produced $n in: $line", n <= flagged)
                }
            }
        }
    }

    @Test
    fun `the screen and the button count the same thing`() {
        val scanned = scanSample()
        // The heading says "N of M clauses" and the button offers "the other K clauses".
        // K + the one free card has to equal N, or a reader doing the subtraction at the
        // moment of payment gets an answer that makes the app look like it is padding.
        assertEquals(scanned.flaggedClauses, scanned.groups.size)
        assertEquals(scanned.flaggedClauses - 1, scanned.groups.size - 1)
    }

    @Test
    fun `a clause is never shown twice`() {
        val scanned = scanSample()
        val indices = scanned.groups.map { it.clause.index }
        assertEquals("the same clause appeared in two cards", indices.size, indices.distinct().size)
    }

    @Test
    fun `grouping loses no finding`() {
        val scanned = scanSample()
        assertEquals(scanned.findings.size, scanned.groups.sumOf { it.findings.size })
    }

    @Test
    fun `the badge on a mixed clause shows the worst severity`() {
        val clause = Clause(0, "a")
        val group = ClauseGroup(
            clause,
            listOf(
                Finding(clause, "r1", "Your deposit", "m", "why", Severity.MEDIUM),
                Finding(clause, "r2", "Your deposit", "h", "why", Severity.HIGH),
            ),
        )
        assertEquals(Severity.HIGH, group.worst)
    }

    @Test
    fun `a clause with only a medium finding is not counted as costly`() {
        val costly = Clause(index = 0, text = "a")
        val minor = Clause(index = 1, text = "b")
        val scanned = ScanState.Scanned(
            clauseCount = 2,
            findings = listOf(
                Finding(costly, "r1", "Your deposit", "h", "why", Severity.HIGH),
                Finding(minor, "r2", "Moving out", "m", "why", Severity.MEDIUM),
            ),
        )
        assertEquals(2, scanned.flaggedClauses)
        assertEquals(1, scanned.highClauses)
    }
}
