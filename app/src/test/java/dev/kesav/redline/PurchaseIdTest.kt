package dev.kesav.redline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore after a reinstall is the whole reason this function exists, and it works only
 * if the same device produces the same identifier every time the app is installed. That
 * property is worth pinning, because nothing else in the app would notice if it broke:
 * the purchase would still succeed, and only a reinstall weeks later would show it.
 */
class PurchaseIdTest {

    @Test
    fun `the same device always produces the same identifier`() {
        val first = Billing.purchaseId("a1b2c3d4e5f60718")
        val second = Billing.purchaseId("a1b2c3d4e5f60718")

        assertEquals(first, second)
    }

    @Test
    fun `different devices do not collide`() {
        assertNotEquals(
            Billing.purchaseId("a1b2c3d4e5f60718"),
            Billing.purchaseId("a1b2c3d4e5f60719"),
        )
    }

    @Test
    fun `the raw value never survives into the identifier`() {
        val raw = "a1b2c3d4e5f60718"
        val id = Billing.purchaseId(raw)!!

        assertTrue("the device value leaked into the id", !id.contains(raw))
        assertEquals("expected 16 bytes of hex", 32, id.length)
        assertTrue("expected lowercase hex only", id.all { it in "0123456789abcdef" })
    }

    @Test
    fun `a missing or useless device value falls back to anonymous`() {
        assertNull(Billing.purchaseId(null))
        assertNull(Billing.purchaseId(""))
        assertNull(Billing.purchaseId("   "))
        // Shipped identically on a batch of early devices, so it identifies nobody.
        assertNull(Billing.purchaseId("9774d56d682e549c"))
    }
}
