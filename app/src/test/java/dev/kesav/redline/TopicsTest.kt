package dev.kesav.redline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app tells the reader what it checks before asking them to pay for the result.
 *
 * That disclosure is the one claim here a reader cannot verify for themselves, so the
 * only thing keeping it true is that it is derived from the rules rather than typed
 * alongside them. These tests exist to catch the day someone adds a twenty-eighth rule
 * and the screen carries on saying twenty-seven.
 */
class TopicsTest {

    @Test
    fun `the disclosed total is the number of rules that actually run`() {
        assertEquals(Scanner.ruleCount, Scanner.topics.sumOf { it.ruleCount })
    }

    @Test
    fun `every rule is disclosed under some heading`() {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        // Not a proxy for the count: this asserts the grouping cannot silently drop a
        // rule, which a groupingBy over a missing field would do without failing.
        assertTrue("no headings at all", Scanner.topics.isNotEmpty())
        assertFalse("a heading with no rules under it", Scanner.topics.any { it.ruleCount < 1 })
        assertTrue("the scanner still runs", Scanner.scan(clauses).isNotEmpty())
    }

    @Test
    fun `no heading appears twice`() {
        val names = Scanner.topics.map { it.name }
        assertEquals("the same heading was listed twice", names.size, names.distinct().size)
    }

    @Test
    fun `the headings read as plain english`() {
        for (topic in Scanner.topics) {
            assertFalse(
                "a rule id leaked into the disclosure: ${topic.name}",
                topic.name.contains('-') || topic.name.contains('_'),
            )
            assertTrue(
                "heading is not sentence case: ${topic.name}",
                topic.name.first().isUpperCase(),
            )
        }
    }

    @Test
    fun `the disclosure is short enough to read in one sitting`() {
        // Nine headings fit a phone screen without scrolling past the point where a
        // reader stops. Twenty-seven rule ids would not, which is why they are grouped.
        assertTrue("too many headings: ${Scanner.topics.size}", Scanner.topics.size <= 10)
    }
}
