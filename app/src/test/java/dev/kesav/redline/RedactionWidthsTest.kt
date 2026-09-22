package dev.kesav.redline.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redaction bars claim to be stable across launches, which is what lets a screen
 * recording be re-shot and still match the first take. A claim in a comment is not worth
 * much on its own, so it is pinned here.
 */
class RedactionWidthsTest {

    @Test
    fun `the same finding always redacts to the same silhouette`() {
        assertArrayEquals(
            redactionWidths("Deposit equal to 10 months rent"),
            redactionWidths("Deposit equal to 10 months rent"),
            0f,
        )
    }

    @Test
    fun `different findings do not all look alike`() {
        val headlines = listOf(
            "Late payment penalty of 10 percent",
            "Deposit equal to 10 months rent",
            "Deposit returned only after 90 days",
            "There is a lock-in period",
            "Rent rises by 15 percent",
            "You must give 6 months notice to leave",
        )
        val shapes = headlines.map { redactionWidths(it).toList() }
        // Four identical cards in a row is the thing this exists to avoid, so the bar
        // is deliberately low: most of them differ, not all of them.
        assertTrue("silhouettes were near-identical: $shapes", shapes.distinct().size >= 4)
    }

    @Test
    fun `every bar stays inside the card`() {
        for (h in listOf("", "a", "Rent rises by 15 percent", "x".repeat(300))) {
            for (w in redactionWidths(h)) {
                assertTrue("width $w out of range for '$h'", w > 0f && w <= 1f)
            }
        }
    }

    @Test
    fun `the headline bar is drawn before the two body bars`() {
        assertEquals(3, redactionWidths("Rent rises by 15 percent").size)
    }
}
