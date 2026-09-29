package dev.kesav.redline

import dev.kesav.redline.ui.barLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * On a phone turned on its side the note above the report's button is dropped, and with it the
 * only sign on screen that the button leads to a payment. The price now moves into the label.
 */
class BarLabelTest {

    @Test
    fun `with the note shown the label is unchanged`() {
        assertEquals("Show the other 10 clauses", barLabel(10, "From $1.99", noteShown = true))
    }

    @Test
    fun `with the note dropped the label keeps the price`() {
        assertEquals("Show the other 10 clauses, from $1.99", barLabel(10, "From $1.99", noteShown = false))
        assertEquals("Show the other clause, $4.99, paid once", barLabel(1, "$4.99, paid once", noteShown = false))
    }

    @Test
    fun `with no price there is nothing to add`() {
        assertEquals("Show the other 10 clauses", barLabel(10, null, noteShown = false))
    }
}
