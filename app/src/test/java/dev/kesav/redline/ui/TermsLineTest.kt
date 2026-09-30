package dev.kesav.redline.ui

import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.TestStoreProduct
import dev.kesav.redline.Offer
import dev.kesav.redline.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for termsLine disclosures, verifying exact store terms and trial honesty. */
class TermsLineTest {

    private fun offer(plan: Plan, type: PackageType, price: String, trial: String? = null) = Offer(
        plan = plan,
        pkg = Package(plan.name, type, TestStoreProduct(plan.name, plan.name, plan.name, plan.name, Price(price, 0L, "USD")), PresentedOfferingContext("default")),
        price = price,
        trial = trial,
    )

    @Test
    fun `null offer shows store currency notice`() {
        assertEquals("Prices come from the store in your own currency.", termsLine(null))
    }

    @Test
    fun `lease pass discloses one payment and single lease scope`() {
        val o = offer(Plan.PASS, PackageType.CUSTOM, "$1.99")
        assertEquals("One payment of $1.99. Opens this lease only, on this phone. No subscription.", termsLine(o))
    }

    @Test
    fun `pro lifetime discloses one payment and no subscription`() {
        val o = offer(Plan.PRO_LIFETIME, PackageType.LIFETIME, "$4.99")
        assertEquals("One payment of $4.99. Every lease you scan. No subscription.", termsLine(o))
    }

    @Test
    fun `monthly plan without trial states recurring price without false trial promise`() {
        val o = offer(Plan.PRO_MONTHLY, PackageType.MONTHLY, "$9.99", trial = null)
        assertEquals("$9.99 a month until you cancel in the store.", termsLine(o))
    }

    @Test
    fun `monthly plan with trial includes trial duration and cancel condition`() {
        val o = offer(Plan.PRO_MONTHLY, PackageType.MONTHLY, "$9.99", trial = "7 days free")
        assertEquals("7 days free, then $9.99 a month until you cancel in the store. Cancel before the trial ends and nothing is charged.", termsLine(o))
    }

    @Test
    fun `annual plan with trial includes trial duration and yearly cancel condition`() {
        val o = offer(Plan.PRO_ANNUAL, PackageType.ANNUAL, "$49.99", trial = "14 days free")
        assertEquals("14 days free, then $49.99 a year until you cancel in the store. Cancel before the trial ends and nothing is charged.", termsLine(o))
    }
}
