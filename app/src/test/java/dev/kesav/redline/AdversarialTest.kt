package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lease clauses built to catch the scanner reading the wrong thing.
 *
 * Every rule used to match its trigger words anywhere inside another word, and six of the
 * seven rules that read a number took the first one in the clause. Between them, "The rent
 * shall be escalated by ten percent" came out as a ten percent late fee: "late" sat inside
 * "escalated", and the only percentage in the clause was taken as the fee. The same shape
 * turned "residential" into a second occupant and a lease term into a notice period.
 *
 * The frozen corpus scored 20 of 20 through all of it, because none of its clauses happened
 * to be phrased like these. That is the reason this file exists separately: the corpus says
 * the rules find what they should, and these say they do not find what they should not.
 */
class AdversarialTest {

    private fun rules(text: String): List<String> =
        Scanner.scan(ClauseSplitter.split(text)).map { it.ruleId }

    private fun finding(text: String, rule: String): Finding? =
        Scanner.scan(ClauseSplitter.split(text)).firstOrNull { it.ruleId == rule }

    // ----------------------------------------------------------------- word boundaries

    @Test
    fun `late inside another word is not lateness`() {
        val clauses = listOf(
            "The rent shall be escalated by ten percent after the first year.",
            "Maintenance, as calculated by the association, shall increase by eight percent each year.",
            "All sums related to the common area shall be shared, and the Tenant bears twenty percent.",
            "The rent for the premises stipulated herein shall rise by twelve percent.",
        )
        for (c in clauses) {
            assertTrue("late-fee fired on: $c", "late-fee" !in rules(c))
        }
    }

    @Test
    fun `residential is not a second occupant`() {
        val c = "The residential premises shall be used by the Tenant's family only, and the " +
            "rent includes ten percent towards maintenance."
        assertTrue("occupant-surcharge fired on: $c", "occupant-surcharge" !in rules(c))
    }

    @Test
    fun `blocking a gate is not a lock-in`() {
        val c = "The Tenant shall not park any vehicle so as to be blocking the gate of the premises."
        assertTrue("lock-in fired on: $c", "lock-in" !in rules(c))
    }

    // --------------------------------------------------------------- nearest quantity

    @Test
    fun `an interest free deposit does not make a rent rise into an interest rate`() {
        val c = "The deposit shall be interest free, and the rent shall be revised annually by " +
            "fifteen percent."
        val found = rules(c)
        assertTrue("interest-rate fired on: $c", "interest-rate" !in found)
        // The rise is real and should still be caught under its own name.
        assertTrue("the rent rise was lost: $found", "rent-escalation" in found)
    }

    @Test
    fun `the length of the lease is not its notice period`() {
        val c = "The term of this lease is eleven months. Either party may terminate it by " +
            "giving one month's notice."
        assertTrue("long-notice fired on: $c", "long-notice" !in rules(c))
    }

    @Test
    fun `the late fee is the percentage beside the charge, not the first one in the clause`() {
        val c = "The rent shall be escalated by ten percent annually, and any rent unpaid after " +
            "the due date attracts a late charge of four percent."
        val fee = finding(c, "late-fee")
        assertTrue("the real late fee was missed", fee != null)
        assertEquals("Late payment penalty of 4 percent", fee!!.headline)
    }

    // ------------------------------------------------------ recall on plain phrasing

    @Test
    fun `tightening the rules did not stop them firing on plain clauses`() {
        val expected = mapOf(
            "If rent remains unpaid after the fifth day, a late charge of five percent of " +
                "the monthly rent shall be payable." to "late-fee",
            "The Tenant shall give three months' notice in writing before vacating." to "long-notice",
            "Arrears shall carry interest at eighteen percent per annum." to "interest-rate",
            "If any person other than the Tenant resides in the premises, the rent shall " +
                "increase by ten percent." to "occupant-surcharge",
            "The rent shall be increased by ten percent every year." to "rent-escalation",
            "The deposit shall be refunded within ninety days after the Tenant vacates the " +
                "premises." to "deposit-refund-delay",
        )
        for ((clause, rule) in expected) {
            assertTrue("$rule no longer fires on: $clause", rule in rules(clause))
        }
    }
}
