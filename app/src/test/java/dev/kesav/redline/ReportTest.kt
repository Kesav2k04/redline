package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report leaves the phone and lands in somebody else's inbox, which makes it the
 * only output here that a stranger reads without the app around it. It has to stand on
 * its own, quote the lease accurately, and not overstate what the scan actually did.
 */
class ReportTest {

    private fun sample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.scan(clauses))
    }

    @Test
    fun `every finding reaches the message`() {
        val state = sample()
        val text = Report.build(state)
        for (f in state.findings) {
            assertTrue("headline missing: ${f.headline}", text.contains(f.headline))
            assertTrue("reason missing for: ${f.headline}", text.contains(f.reason))
        }
    }

    @Test
    fun `the clause is quoted on one line so it survives the paste`() {
        val clause = Clause(0, "4. Security deposit.\nThe Tenant shall deposit\n  ten months rent.")
        val state = ScanState.Scanned(
            clauseCount = 1,
            findings = listOf(Finding(clause, "r", "Your deposit", "Deposit equal to 10 months rent", "why", Severity.HIGH)),
        )
        val text = Report.build(state)
        assertTrue(
            "hard wrapping survived into the message",
            text.contains("4. Security deposit. The Tenant shall deposit ten months rent."),
        )
        assertFalse("the raw newlines leaked through", text.contains("deposit.\nThe Tenant"))
    }

    @Test
    fun `it never claims the lease is clean`() {
        val text = Report.build(ScanState.Scanned(clauseCount = 12, findings = emptyList()))
        assertTrue(text.contains("matched none of its rules"))
        for (word in listOf("clean", "safe", "fine", "no problems", "legal advice.")) {
            assertFalse("report implies a verdict with '$word': $text", text.contains(" $word "))
        }
    }

    @Test
    fun `the limits of the scan travel with it`() {
        val text = Report.build(sample())
        assertTrue("no disclaimer", text.contains("rather than legal advice"))
        assertTrue("does not say what it matched against", text.contains("fixed set of rules"))
    }

    @Test
    fun `the count in the message matches the count on the screen`() {
        val state = sample()
        val text = Report.build(state)
        assertTrue(
            "header disagrees with the screen",
            text.contains("${state.clauseCount} clauses") &&
                text.contains("${state.flaggedClauses} of them will cost money"),
        )
    }

    @Test
    fun `the message is numbered by clause, not by finding`() {
        val state = sample()
        val text = Report.build(state)
        val groups = Regex("(?m)^\\d+\\) ").findAll(text).count()
        assertEquals(
            "one numbered group per flagged clause",
            state.flaggedClauses,
            groups,
        )
        assertTrue("more findings than groups is the whole point", state.findings.size > groups)
    }

    @Test
    fun `a clause with several findings is quoted once, not once per finding`() {
        val text = Report.build(sample())
        val deposit = "4. Security deposit. The Tenant shall deposit a sum equivalent to ten months rent"
        assertEquals(
            "the deposit paragraph was repeated in the message",
            1,
            Regex(Regex.escape(deposit)).findAll(text).count(),
        )
    }

    @Test
    fun `every finding on a shared clause still reaches the message`() {
        val state = sample()
        val text = Report.build(state)
        val onDeposit = state.findings.filter { it.clause.text.contains("Security deposit") }
        assertTrue("fixture no longer has a multi-finding clause", onDeposit.size >= 3)
        for (f in onDeposit) assertTrue("dropped: ${f.headline}", text.contains(f.headline))
    }
}
