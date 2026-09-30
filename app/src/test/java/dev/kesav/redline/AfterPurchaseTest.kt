package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A Pro product left out of the entitlement on the dashboard still takes the money: the store
 * reports the purchase as made and the report stays shut. These pin that the sheet says so, and
 * that it stays quiet when the purchase did what it should.
 */
class AfterPurchaseTest {

    @Test
    fun `pro bought without the entitlement says so and points at Restore`() {
        val note = afterPurchase(Plan.PRO_MONTHLY, entitled = false)
        assertEquals("Payment went through, but Renter Pro did not switch on. Tap Restore.", note)
        assertEquals(note, afterPurchase(Plan.PRO_LIFETIME, entitled = false))
        assertEquals(note, afterPurchase(Plan.PRO_ANNUAL, entitled = false))
    }

    @Test
    fun `pro bought with the entitlement says nothing`() {
        assertNull(afterPurchase(Plan.PRO_LIFETIME, entitled = true))
        assertNull(afterPurchase(Plan.PRO_MONTHLY, entitled = true))
    }

    @Test
    fun `a pass opens by the lease, not the entitlement, so it says nothing`() {
        assertNull(afterPurchase(Plan.PASS, entitled = false))
    }
}
