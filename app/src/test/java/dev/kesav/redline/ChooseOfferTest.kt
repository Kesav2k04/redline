package dev.kesav.redline

import com.revenuecat.purchases.PackageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The button sells whatever `chooseOffer` returns, and the list it picks from is in
 * dashboard order. That order is not a contract: reordering the offering, or a product
 * failing to resolve and dropping out of the list, would move a different package into
 * the first slot with no code change at all.
 *
 * These pin the two things that matter: the one-time purchase is chosen wherever it
 * sits, and a recurring package is never chosen by default.
 */
class ChooseOfferTest {

    @Test
    fun `the one-time package is chosen even when it is not first`() {
        assertEquals(
            1,
            chooseOffer(listOf(PackageType.MONTHLY, PackageType.LIFETIME)),
        )
    }

    @Test
    fun `dashboard order does not decide it`() {
        assertEquals(
            0,
            chooseOffer(listOf(PackageType.LIFETIME, PackageType.MONTHLY)),
        )
    }

    @Test
    fun `a subscription is never sold as a substitute`() {
        assertNull(chooseOffer(listOf(PackageType.MONTHLY, PackageType.ANNUAL)))
        assertNull(chooseOffer(listOf(PackageType.WEEKLY)))
        // A package built from a custom identifier reports CUSTOM whatever it sells,
        // so it cannot be assumed to be the one-time purchase either.
        assertNull(chooseOffer(listOf(PackageType.CUSTOM)))
    }

    @Test
    fun `an empty offering sells nothing`() {
        assertNull(chooseOffer(emptyList()))
    }
}
