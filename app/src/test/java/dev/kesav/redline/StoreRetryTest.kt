package dev.kesav.redline

import dev.kesav.redline.ui.askStoreAgain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed first entitlement read used to be final for the session: a Pro buyer who reinstalled
 * and opened the app offline saw a locked report, and it stayed locked after the signal came back.
 * The report now asks again for the entitlement as it already did for the price.
 */
class StoreRetryTest {

    @Test
    fun `a locked report asks again after a failed entitlement read`() {
        assertTrue(askStoreAgain(pricesMissing = false, readFailed = true, locked = true))
    }

    @Test
    fun `a locked report still asks again for missing prices`() {
        assertTrue(askStoreAgain(pricesMissing = true, readFailed = false, locked = true))
    }

    @Test
    fun `nothing to ask for once prices and entitlement are in`() {
        assertFalse(askStoreAgain(pricesMissing = false, readFailed = false, locked = true))
    }

    @Test
    fun `an open report never polls`() {
        assertFalse(askStoreAgain(pricesMissing = true, readFailed = true, locked = false))
    }
}
