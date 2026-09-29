package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClauseSplitterTest {

    @Test
    fun `blank input yields no clauses`() {
        assertTrue(ClauseSplitter.split("   \n\n  ").isEmpty())
    }

    @Test
    fun `blank lines separate clauses`() {
        val text = """
            The Tenant shall pay rent on the first day of each month.

            The Tenant shall keep the premises in good repair at all times.
        """.trimIndent()

        assertEquals(2, ClauseSplitter.split(text).size)
    }

    @Test
    fun `numbered items split without a blank line between them`() {
        val text = """
            1. The Tenant shall pay a late fee of five percent of the monthly rent.
            2. The Landlord may enter the premises upon twenty four hours notice.
            3. The security deposit shall equal two months of the monthly rent.
        """.trimIndent()

        assertEquals(3, ClauseSplitter.split(text).size)
    }

    @Test
    fun `hard wrapped lines rejoin into one clause`() {
        val text = """
            The Tenant shall be responsible for all
            repairs and maintenance of the premises
            during the term of this agreement.
        """.trimIndent()

        val clauses = ClauseSplitter.split(text)
        assertEquals(1, clauses.size)
        assertTrue(clauses[0].text.contains("all repairs and maintenance"))
    }

    @Test
    fun `hyphen at a line break rejoins the word`() {
        val text = "The Tenant shall carry out all mainten-\nance of the premises at their own cost."

        assertTrue(ClauseSplitter.split(text)[0].text.contains("maintenance"))
    }

    @Test
    fun `an amount ending in slash dash does not swallow the next clause`() {
        // Indian leases close an amount with "/-". Read as a word broken by the wrap, it
        // glued the next numbered clause on: "Rs. 25,000/4. Deposit ...".
        val text = """
            3. Rent. The monthly rent is Rs. 25,000/-
            4. Deposit. The security deposit is Rs. 1,00,000/-
            5. Notice. Either party may end this agreement on one month's notice.
        """.trimIndent()

        val clauses = ClauseSplitter.split(text)
        assertEquals(3, clauses.size)
        assertEquals("3. Rent. The monthly rent is Rs. 25,000/-", clauses[0].text)
    }

    @Test
    fun `fragments shorter than twenty characters are dropped`() {
        val text = """
            SCHEDULE A

            The Tenant shall pay all utility charges billed to the premises.
        """.trimIndent()

        val clauses = ClauseSplitter.split(text)
        assertEquals(1, clauses.size)
        assertTrue(clauses[0].text.startsWith("The Tenant shall pay all utility"))
    }

    @Test
    fun `a long block splits on sentence boundaries`() {
        val sentence = "The Tenant shall observe every covenant set out in this agreement without exception. "
        val clauses = ClauseSplitter.split(sentence.repeat(6))

        assertTrue("expected a long block to split, got ${clauses.size}", clauses.size > 1)
    }

    @Test
    fun `clause indices are sequential from zero`() {
        val text = """
            The Tenant shall pay rent on the first day of each month.

            The Landlord shall maintain the structure of the building.

            The Tenant shall not sublet the premises without written consent.
        """.trimIndent()

        assertEquals(listOf(0, 1, 2), ClauseSplitter.split(text).map { it.index })
    }
}
