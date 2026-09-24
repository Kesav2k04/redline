package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A whole US lease, written after the rules, in the wording US leases use.
 *
 * Its first run found six of its nine costly clauses for the right reason, read the
 * renewal clause as a twelve month notice period, and missed the tenant paying for all
 * repairs and the rent rising at any time. The anchors were widened to that wording, so
 * from here on this is a regression check, not a measure of unseen leases.
 */
class UsLeaseTest {

    @Test
    fun `the US lease reads as it did when the rules were widened`() {
        val clauses = ClauseSplitter.split(File("../eval/us-lease.txt").readText())
        val findings = Scanner.ranked(clauses)

        assertEquals("clauses", 16, clauses.size)
        assertEquals("flagged clauses", 9, findings.map { it.clause.index }.distinct().size)
        assertEquals("findings", 14, findings.size)
        assertTrue("the renewal term was read as a notice period", findings.none { it.ruleId == "long-notice" })
    }
}
