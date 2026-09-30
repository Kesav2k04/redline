package dev.kesav.redline

import dev.kesav.redline.ui.viewerLocks
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marked-up lease used to lock every clause after the first whenever the report was shut,
 * without asking whether the text was a lease at all. A privacy policy that tripped three rules
 * then showed "What it says is in the full report." with a button to the paywall. Text that is
 * not a lease is never sold, and these pin that the viewer keeps to the same rule as the report.
 */
class ViewerLocksTest {

    private val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())

    private fun scanned(lease: Boolean) =
        ScanState.Scanned(clauses.size, Scanner.ranked(clauses), looksLikeLease = lease, clauses = clauses)

    @Test
    fun `a shut lease report locks every clause after the first`() {
        val state = scanned(lease = true)
        val locked = viewerLocks(state, unlocked = false)
        assertEquals(state.groups.drop(1).map { it.clause.index }.toSet(), locked)
        assertTrue(locked.isNotEmpty())
    }

    @Test
    fun `the same findings over text that is not a lease lock nothing`() {
        val state = scanned(lease = false)
        assertTrue("more than one flagged clause, so only the lease check decides", state.groups.size > 1)
        assertEquals(emptySet<Int>(), viewerLocks(state, unlocked = false))
    }

    @Test
    fun `an open report locks nothing`() {
        assertEquals(emptySet<Int>(), viewerLocks(scanned(lease = true), unlocked = true))
    }
}
