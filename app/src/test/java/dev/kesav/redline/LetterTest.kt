package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The letter goes to the landlord, so it is held to a different bar from the report: it
 * asks, it never accuses, and it never quotes the scanner's verdicts back at the person
 * who wrote the lease.
 */
class LetterTest {

    private fun sample(): ScanState.Scanned {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        return ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
    }

    @Test
    fun `a narrowed letter asks only about the chosen clauses`() {
        val state = sample()
        val kept = state.groups.first()
        val dropped = state.groups.last()
        val letter = Report.letter(state, setOf(kept.clause.index))!!
        for (f in kept.findings) {
            assertTrue("kept ask missing: ${f.ask}", letter.contains(f.ask.replaceFirstChar { it.uppercase() }))
        }
        for (f in dropped.findings) {
            assertFalse("dropped ask sent: ${f.ask}", letter.contains(f.ask.replaceFirstChar { it.uppercase() }))
        }
        // One clause is "one change", not "a few".
        assertTrue(letter.contains("one change:"))
    }

    @Test
    fun `choosing nothing writes no letter`() {
        assertNull(Report.letter(sample(), emptySet()))
    }

    @Test
    fun `every ask found reaches the letter`() {
        val state = sample()
        val letter = Report.letter(state)!!
        for (f in state.findings) {
            assertTrue("ask missing: ${f.ask}", letter.contains(f.ask.replaceFirstChar { it.uppercase() }))
        }
    }

    @Test
    fun `the letter carries no verdicts`() {
        val state = sample()
        val letter = Report.letter(state)!!
        for (f in state.findings) {
            assertFalse("reason leaked: ${f.reason}", letter.contains(f.reason))
        }
        assertFalse(letter.contains("SERIOUS"))
        assertFalse(letter.contains("Redline"))
    }

    @Test
    fun `nothing found means no letter`() {
        assertNull(Report.letter(ScanState.Scanned(clauseCount = 5, findings = emptyList())))
    }

    @Test
    fun `a clause is named by its own number when it has one`() {
        assertEquals("Clause 4.2", Report.where("4.2 The deposit shall be refunded within ninety days."))
    }

    @Test
    fun `an unnumbered clause is named by its opening words`() {
        assertEquals(
            "The clause starting \"The Tenant shall not sublet the...\"",
            Report.where("The Tenant shall not sublet the premises without consent."),
        )
        assertEquals("The clause \"No pets\"", Report.where("No pets."))
    }

    @Test
    fun `one clause asks for one change`() {
        val clause = Clause(0, "7. The Landlord may enter the premises without notice.")
        val finding = Scanner.scan(listOf(clause)).single()
        val letter = Report.letter(ScanState.Scanned(1, listOf(finding)))!!
        assertTrue(letter.contains("I would like to ask for one change:"))
        assertTrue(letter.contains("Clause 7\n- At least 24 hours written notice"))
    }
}
