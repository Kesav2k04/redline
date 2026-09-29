package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample lease is what a first-time user sees and what the demo shows, so it is
 * worth a test of its own. If a rule changes and the sample stops producing findings,
 * the app opens on an empty screen for everyone who has not pasted anything yet.
 */
class SampleLeaseTest {

    @Test
    fun `the bundled sample produces a readable report`() {
        val text = File("src/main/assets/sample_lease.txt").readText()
        val clauses = ClauseSplitter.split(text)
        val findings = Scanner.ranked(clauses)

        println("clauses: ${clauses.size}")
        println("findings: ${findings.size} over ${findings.map { it.clause.index }.distinct().size} clauses")
        println()
        for (f in findings) {
            println("  [${f.severity}] ${f.headline}   (${f.ruleId}, clause ${f.clause.index})")
        }

        assertTrue("sample split into too few clauses: ${clauses.size}", clauses.size >= 10)
        assertTrue("sample produced too few findings: ${findings.size}", findings.size >= 8)
        // The first card is the only one a non-payer reads, so it has to be both
        // serious and concrete.
        assertTrue(
            "the first card should be high severity",
            findings.first().severity == Severity.HIGH,
        )
        assertTrue(
            "the first card should name a figure, got: ${findings.first().headline}",
            findings.first().headline.any(Char::isDigit),
        )
        assertTrue(
            "every finding must name its clause",
            findings.all { it.clause.text.isNotBlank() && it.reason.isNotBlank() },
        )
    }

    @Test
    fun `the sample report keeps the numbers the demo shows`() {
        // The demo video shows these figures. A rule change that moves any of them has to
        // be a decision, not a side effect.
        val text = File("src/main/assets/sample_lease.txt").readText()
        val clauses = ClauseSplitter.split(text)
        val scanned = ScanState.Scanned(clauses.size, Scanner.ranked(clauses), LeaseCheck.looksLikeLease(text), null, clauses)

        assertEquals("clauses", 16, scanned.clauseCount)
        assertEquals("flagged", 11, scanned.flaggedClauses)
        assertEquals("serious", 9, scanned.highClauses)
        assertEquals("worth checking", 2, scanned.flaggedClauses - scanned.highClauses)
        assertEquals("clear", 5, scanned.clauseCount - scanned.flaggedClauses)
        assertEquals("score", 91, scanned.insight.score)
        assertEquals("months of rent at stake", 13.0, scanned.insight.exposure!!.months, 0.001)
    }
}
