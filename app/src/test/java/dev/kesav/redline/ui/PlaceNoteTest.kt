package dev.kesav.redline.ui

import dev.kesav.redline.ClauseSplitter
import dev.kesav.redline.Place
import dev.kesav.redline.ScanState
import dev.kesav.redline.Scanner
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The place sheet says what picking a place changes, and it changes more than the asks. */
class PlaceNoteTest {

    @Test
    fun `the note says a place can move the score`() {
        val clauses = ClauseSplitter.split(File("src/main/assets/sample_lease.txt").readText())
        val general = ScanState.Scanned(clauses.size, Scanner.ranked(clauses))
        val texas = ScanState.Scanned(clauses.size, Scanner.ranked(clauses, Place.TEXAS))
        assertNotEquals("a place moves the sample's score", general.insight.score, texas.insight.score)

        assertTrue(PLACE_NOTE.startsWith("Kept on this phone."))
        assertTrue(PLACE_NOTE.contains("score"))
        assertFalse(PLACE_NOTE.contains("only"))
    }
}
