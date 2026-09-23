package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ask is shown as "Ask for <ask>." on the card and "I would like to ask for <ask>." in
 * the report, so its shape is a contract: one lower-case noun phrase, no closing stop, no
 * "you" or "your", which would point at the landlord once the tenant sends it.
 */
class AsksTest {
    private val asks = Scanner.asks

    @Test
    fun `every rule has an ask`() {
        assertEquals(Scanner.ruleCount, asks.size)
        val blank = asks.filterValues { it.isBlank() }.keys
        assertEquals("rules with no ask: $blank", emptySet<String>(), blank)
    }

    @Test
    fun `an ask reads after "Ask for"`() {
        for ((id, ask) in asks) {
            assertTrue("$id starts upper-case: $ask", ask.first().isLowerCase() || ask.first().isDigit())
            assertTrue("$id ends with a stop: $ask", !ask.trimEnd().endsWith("."))
        }
    }

    @Test
    fun `an ask never addresses the reader`() {
        val you = Regex("\\byou(r|rs)?\\b", RegexOption.IGNORE_CASE)
        val offenders = asks.filterValues { you.containsMatchIn(it) }.keys
        assertEquals("asks that say you: $offenders", emptySet<String>(), offenders)
    }

    @Test
    fun `a scanned finding carries its rule's ask`() {
        val clause = Clause(0, "A late payment penalty of 10 percent per month shall be charged on overdue rent.")
        val finding = Scanner.scan(listOf(clause)).single { it.ruleId == "late-fee" }
        assertEquals(asks.getValue("late-fee"), finding.ask)
    }
}
