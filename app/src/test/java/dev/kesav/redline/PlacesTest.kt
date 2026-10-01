package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The place a reader picks changes legal figures in a letter they send to a landlord, so it
 * is held to the same wording contract as the rules, and each threshold it moves is pinned
 * by a clause on either side of it.
 */
class PlacesTest {

    private fun ids(text: String, place: Place?) =
        Scanner.scan(listOf(Clause(0, text)), place).map { it.ruleId }

    private fun finding(text: String, place: Place?, rule: String) =
        Scanner.scan(listOf(Clause(0, text)), place).single { it.ruleId == rule }

    @Test
    fun `every override names a rule that exists`() {
        for (place in Place.entries) {
            val l = place.limits
            val unknown = (l.floors.keys + l.reasons.keys + l.asks.keys) - Scanner.asks.keys
            assertEquals("${place.label} overrides unknown rules", emptySet<String>(), unknown)
        }
    }

    @Test
    fun `a place's asks read after "Ask for" and never address the reader`() {
        for (place in Place.entries) for ((id, ask) in place.limits.asks) {
            assertTrue("${place.label} $id starts upper-case: $ask", ask.first().isLowerCase() || ask.first().isDigit())
            assertFalse("${place.label} $id ends with a stop: $ask", ask.trimEnd().endsWith("."))
            assertFalse("${place.label} $id says you: $ask", Regex("\\byou(r)?\\b").containsMatchIn(ask))
        }
    }

    @Test
    fun `a two month deposit is over the cap where the cap is one month, and only there`() {
        val clause = "The Tenant shall pay a security deposit equal to two months' rent."
        assertFalse("deposit-size" in ids(clause, null))
        assertFalse("deposit-size" in ids(clause, Place.TEXAS))
        assertFalse("deposit-size" in ids(clause, Place.INDIA))
        val ma = finding(clause, Place.MASSACHUSETTS, "deposit-size")
        assertTrue(ma.ask.contains("one month's rent"))
        assertTrue(ma.reason.contains("chapter 186"))
        assertTrue("deposit-size" in ids(clause, Place.NEW_YORK))
        assertTrue("deposit-size" in ids(clause, Place.CALIFORNIA))
        assertTrue("deposit-size" in ids(clause, Place.ENGLAND))
    }

    @Test
    fun `a legal figure written as words and numeral is not flagged`() {
        val refund = "Landlord shall return the security deposit to Tenant within twenty-one (21) days after Tenant vacates the Premises."
        assertFalse("deposit-refund-delay" in ids(refund, Place.CALIFORNIA))
        assertFalse("deposit-refund-delay" in ids(refund, null))
        val deposit = "Tenant shall pay a security deposit equal to one (1) month's rent."
        assertFalse("deposit-size" in ids(deposit, Place.NEW_YORK))
    }

    @Test
    fun `a deposit back in twenty days is late in New York but not in general`() {
        val clause = "The deposit shall be refunded within twenty days after the Tenant vacates the premises."
        assertFalse("deposit-refund-delay" in ids(clause, null))
        assertFalse("deposit-refund-delay" in ids(clause, Place.CALIFORNIA))
        assertTrue(finding(clause, Place.NEW_YORK, "deposit-refund-delay").ask.contains("14 days"))
    }

    @Test
    fun `a five percent late fee is within the Texas presumption`() {
        val clause = "If rent is paid late, a late fee of five percent of the monthly rent shall be charged."
        assertTrue("late-fee" in ids(clause, null))
        assertFalse("late-fee" in ids(clause, Place.TEXAS))
        assertTrue(finding(clause, Place.MASSACHUSETTS, "late-fee").ask.contains("30 days overdue"))
    }

    @Test
    fun `with no place the wording is the general one`() {
        val clause = "If rent is paid late, a late fee of ten percent of the monthly rent shall be charged."
        assertEquals(Scanner.asks.getValue("late-fee"), finding(clause, null, "late-fee").ask)
    }
}
