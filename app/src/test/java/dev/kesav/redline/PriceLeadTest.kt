package dev.kesav.redline

import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PresentedOfferingContext
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.TestStoreProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The price line outside the paywall may only say "from" and "paid once" when they are true. */
class PriceLeadTest {

    private fun offer(plan: Plan, type: PackageType, price: String, micros: Long) = Offer(
        plan = plan,
        pkg = Package(plan.name, type, TestStoreProduct(plan.name, plan.name, plan.name, plan.name, Price(price, micros, "USD")), PresentedOfferingContext("default")),
        price = price,
        trial = null,
    )

    private val lifetime = offer(Plan.PRO_LIFETIME, PackageType.LIFETIME, "$4.99", 4_990_000)
    private val pass = offer(Plan.PASS, PackageType.CUSTOM, "$1.99", 1_990_000)
    private val monthly = offer(Plan.PRO_MONTHLY, PackageType.MONTHLY, "$2.99", 2_990_000)

    @Test
    fun `one one-time plan is its price, paid once`() {
        assertEquals("$4.99, paid once", priceLead(listOf(lifetime))!!.text)
    }

    @Test
    fun `two one-time plans start at the cheaper, paid once`() {
        assertEquals("From $1.99, paid once", priceLead(listOf(pass, lifetime))!!.text)
    }

    @Test
    fun `a subscription beside a one-time plan drops paid once`() {
        // The cheapest is the monthly plan, which is not paid once.
        assertEquals("From $2.99", priceLead(listOf(lifetime, monthly))!!.text)
    }

    @Test
    fun `a lone subscription names its period`() {
        assertEquals("$2.99 a month", priceLead(listOf(monthly))!!.text)
    }

    @Test
    fun `no offers, no line`() {
        assertNull(priceLead(emptyList()))
    }
}
