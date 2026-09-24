package dev.kesav.redline.ui

import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The share card may carry counts and a link. Nothing from the lease itself. */
class ShareCardTextTest {

    private fun sample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.scan(clauses))
    }

    @Test
    fun `the sample's split adds up`() {
        val state = sample()
        assertEquals(listOf("9 serious", "2 worth checking", "5 clear"), ShareCardText.legend(state))
        assertEquals("of 16 clauses", ShareCardText.ofTotal(state))
    }

    @Test
    fun `no clause text, headline or reason reaches the card`() {
        val state = sample()
        val words = listOf(
            ShareCardText.message(state), ShareCardText.LINE, ShareCardText.FOOT,
            ShareCardText.ofTotal(state),
        ) + ShareCardText.legend(state)
        val card = words.joinToString(" ")
        for (f in state.findings) {
            assertFalse("headline leaked: ${f.headline}", card.contains(f.headline))
            assertFalse("reason leaked: ${f.reason}", card.contains(f.reason))
            val opening = f.clause.text.trim().take(24)
            assertFalse("clause leaked: $opening", card.contains(opening))
        }
    }

    @Test
    fun `offered only for a lease with findings`() {
        val state = sample()
        assertTrue(ShareCardText.offered(state))
        assertFalse(ShareCardText.offered(ScanState.Scanned(clauseCount = 4, findings = emptyList())))
        assertFalse(ShareCardText.offered(state.copy(looksLikeLease = false)))
    }

    @Test
    fun `one clause is singular`() {
        val one = sample().let { s -> s.copy(findings = s.findings.filter { it.clause.index == s.findings.first().clause.index }) }
        assertTrue(ShareCardText.message(one).startsWith("Redline found 1 clause in my lease"))
    }
}
