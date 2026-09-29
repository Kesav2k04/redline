package dev.kesav.redline

import com.revenuecat.purchases.PurchasesErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Purchase and restore failures used to reach the reader as the SDK's own text, in a snackbar the
 * paywall sheet draws over. A pending payment read as a failure, and a network drop after the
 * charge read as a payment that never happened. These pin what the sheet now says instead.
 */
class StoreMessagesTest {

    @Test
    fun `a pending payment says it is pending, not failed`() {
        assertEquals(
            "Payment pending. The report opens when the store confirms it.",
            Billing.purchaseProblem(PurchasesErrorCode.PaymentPendingError),
        )
    }

    @Test
    fun `a dropped connection says what happens if money moved`() {
        assertEquals(
            "The connection dropped. If you were charged, the report opens when you are back online, or tap Restore.",
            Billing.purchaseProblem(PurchasesErrorCode.NetworkError),
        )
    }

    @Test
    fun `any other failure says to try again or restore`() {
        val other = "The purchase did not go through. Try again, or tap Restore if you were charged."
        assertEquals(other, Billing.purchaseProblem(PurchasesErrorCode.StoreProblemError))
        assertEquals(other, Billing.purchaseProblem(PurchasesErrorCode.UnknownError))
        assertEquals(other, Billing.purchaseProblem(null))
    }

    @Test
    fun `a restore offline says the store could not be reached`() {
        assertEquals(Billing.STORE_UNREACHABLE, Billing.restoreProblem(PurchasesErrorCode.NetworkError))
        assertEquals("Restore did not finish. Try again in a moment.", Billing.restoreProblem(PurchasesErrorCode.UnknownBackendError))
    }

    @Test
    fun `no message is the store's developer text`() {
        for (code in PurchasesErrorCode.entries) {
            assertFalse(code.name, Billing.purchaseProblem(code) == code.description)
            assertFalse(code.name, Billing.restoreProblem(code) == code.description)
        }
    }

    @Test
    fun `nothing to restore no longer speaks of an account`() {
        assertFalse(Billing.NOTHING_TO_RESTORE.contains("this account"))
    }
}
