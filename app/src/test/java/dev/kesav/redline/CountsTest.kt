package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                Finding(clause, "deposit-size", "Deposit equal to 10 months rent", "why", Severity.HIGH),
                Finding(clause, "deposit-delay", "Deposit returned only after 90 days", "why", Severity.HIGH),
            ),
        )
        assertEquals(1, scanned.flaggedClauses)
        assertEquals(1, scanned.highClauses)
    }

    @Test
    fun `a clause with only a medium finding is not counted as costly`() {
        val costly = Clause(index = 0, text = "a")
        val minor = Clause(index = 1, text = "b")
        val scanned = ScanState.Scanned(
            clauseCount = 2,
            findings = listOf(
                Finding(costly, "r1", "h", "why", Severity.HIGH),
                Finding(minor, "r2", "m", "why", Severity.MEDIUM),
            ),
        )
        assertEquals(2, scanned.flaggedClauses)
        assertEquals(1, scanned.highClauses)
    }
}
