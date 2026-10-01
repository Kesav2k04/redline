package dev.kesav.redline.ui

import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Compare names each lease the way its own report does. */
class CompareLabelTest {

    @Test
    fun `a lease with nothing flagged is not called low risk`() {
        // The report says "Nothing matched", which is not the same as a clean lease.
        assertEquals("Nothing matched", sideLabel(ScanState.Scanned(16, emptyList())))
    }

    @Test
    fun `a lease with flags keeps its risk tier`() {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        val sample = ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
        assertEquals("Toxic clauses", sideLabel(sample))
    }
}
