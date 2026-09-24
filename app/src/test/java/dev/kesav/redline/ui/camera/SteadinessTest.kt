package dev.kesav.redline.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scanner's cue, judged on made-up frames.
 *
 * On a phone the recogniser drops and regains blocks at the edge of the frame from one pass
 * to the next, so these check that the flicker alone never reads as a moving hand, and that
 * a real move always sends the cue back to "hold steady".
 */
class SteadinessTest {

    private val diagonal = 2600f
    private val page = listOf(block(100f, 300f, "a"), block(100f, 500f, "b"), block(100f, 700f, "c"))

    @Test
    fun aFewWordsAreNotAPage() {
        val s = Steadiness()
        repeat(4) { assertEquals(Framing.NONE, s.next(page, chars = 12, diagonal = diagonal)) }
    }

    @Test
    fun aStillPageBecomesSteadyAfterTheHold() {
        val s = Steadiness()
        assertEquals(Framing.FOUND, s.next(page, 300, diagonal))
        assertEquals(Framing.FOUND, s.next(page, 300, diagonal))
        assertEquals(Framing.STEADY, s.next(page, 300, diagonal))
    }

    @Test
    fun aMoveStartsTheHoldAgain() {
        val s = Steadiness()
        repeat(3) { s.next(page, 300, diagonal) }
        // Exactly one paragraph's spacing: by position alone every block lands on a neighbour.
        val moved = page.map { it.copy(top = it.top + 200f, bottom = it.bottom + 200f) }
        assertEquals(Framing.FOUND, s.next(moved, 300, diagonal))
    }

    @Test
    fun withoutTextToMatchThePositionDecides() {
        val s = Steadiness()
        val unread = page.map { it.copy(key = "") }
        repeat(3) { s.next(unread, 300, diagonal) }
        val moved = unread.map { it.copy(left = it.left + 120f, right = it.right + 120f) }
        assertEquals(Framing.FOUND, s.next(moved, 300, diagonal))
    }

    @Test
    fun aBlockLostAtTheEdgeIsNotAMove() {
        val s = Steadiness()
        repeat(3) { s.next(page, 300, diagonal) }
        // The bottom block drops out and a new one appears far away. The blocks seen in
        // both frames have not moved.
        val flicker = page.dropLast(1) + block(900f, 1900f, "z")
        assertEquals(Framing.STEADY, s.next(flicker, 300, diagonal))
    }

    @Test
    fun losingTheTextResetsTheHold() {
        val s = Steadiness()
        repeat(3) { s.next(page, 300, diagonal) }
        assertEquals(Framing.NONE, s.next(emptyList(), 0, diagonal))
        assertEquals(Framing.FOUND, s.next(page, 300, diagonal))
    }

    @Test
    fun aNewBlockFarFromAnyShownOneFadesInInPlace() {
        val paired = pair(shown = page, next = listOf(block(110f, 305f, "a"), block(900f, 1900f, "z")), reach = 56f)
        assertEquals(page[0], paired[0])
        assertNull(paired[1])
    }

    @Test
    fun driftIsTheMedianMove() {
        val after = listOf(block(100f, 310f, "a"), block(100f, 510f, "b"), block(100f, 1700f, "c"))
        assertEquals(10f, drift(page, after)!!, 0.01f)
    }

    private fun block(left: Float, top: Float, key: String) = Block(left, top, left + 600f, top + 120f, key)
}
