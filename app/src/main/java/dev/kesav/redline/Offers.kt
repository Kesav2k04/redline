package dev.kesav.redline

import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.models.Period
import java.security.MessageDigest

/**
 * The two things a reader can buy.
 *
 * A pass opens the full report for the one lease on screen, which is what someone choosing
 * between two flats this weekend needs and nothing more. Pro opens every lease scanned on this
 * phone, plus the side-by-side comparison, for someone who is going to read several. Pro can be
 * sold three ways (once, yearly, monthly) and the paywall offers whichever the dashboard holds.
 */
enum class Plan { PASS, PRO_LIFETIME, PRO_ANNUAL, PRO_MONTHLY }

/** A package from the current offering, read into the terms the paywall shows. */
data class Offer(
    val plan: Plan,
    val pkg: Package,
    /** Localised by the store: the reader's own currency, at the price set for their country. */
    val price: String,
    /** "7 days free" when the store attaches a free phase, otherwise null. */
    val trial: String?,
)

/**
 * Which plan a package sells, decided by its type and never by its position.
 *
 * The list arrives in dashboard order, so reading "the first package" would slide a
 * subscription into the pass's place the day someone reorders the offering. A pass is a custom
 * package whose identifier names it; every standard type maps to its own plan; anything else is
 * ignored, because selling the wrong thing is worse than selling nothing.
 */
internal fun planOf(type: PackageType, identifier: String): Plan? = when (type) {
    PackageType.LIFETIME -> Plan.PRO_LIFETIME
    PackageType.ANNUAL -> Plan.PRO_ANNUAL
    PackageType.MONTHLY -> Plan.PRO_MONTHLY
    PackageType.CUSTOM -> Plan.PASS.takeIf { PASS_ID.containsMatchIn(identifier) }
    else -> null
}

private val PASS_ID = Regex("pass|single|one_lease|lease_pass", RegexOption.IGNORE_CASE)

/** Every plan the offering can sell, one package per plan, pass first. */
internal fun offersFrom(offering: Offering?): List<Offer> {
    val packages = offering?.availablePackages.orEmpty()
    return packages
        .mapNotNull { pkg -> planOf(pkg.packageType, pkg.identifier)?.let { it to pkg } }
        .distinctBy { it.first }
        .sortedBy { it.first.ordinal }
        .map { (plan, pkg) ->
            val free = pkg.product.defaultOption?.freePhase?.billingPeriod
            Offer(plan, pkg, pkg.product.price.formatted, free?.let(::trialText))
        }
}

/**
 * The price as the lines outside the paywall state it. "From" only when there is more than one
 * way to pay, and "paid once" only when every way is one payment: a monthly plan beside a
 * lifetime one made "From $9.99, paid once" untrue.
 */
internal data class PriceLead(val price: String, val from: Boolean, val once: Boolean, val per: String?) {
    val text: String get() = buildString {
        if (from) append("From ")
        append(price)
        per?.let { append(" a $it") }
        if (once) append(", paid once")
    }
}

internal fun priceLead(offers: List<Offer>): PriceLead? {
    val cheapest = offers.minByOrNull { it.pkg.product.price.amountMicros } ?: return null
    val once = offers.all { it.plan == Plan.PASS || it.plan == Plan.PRO_LIFETIME }
    val per = when {
        offers.size > 1 -> null
        cheapest.plan == Plan.PRO_ANNUAL -> "year"
        cheapest.plan == Plan.PRO_MONTHLY -> "month"
        else -> null
    }
    return PriceLead(cheapest.price, from = offers.size > 1, once = once, per = per)
}

internal fun trialText(period: Period): String {
    val n = period.value
    val unit = when (period.unit) {
        Period.Unit.DAY -> "day"
        Period.Unit.WEEK -> "week"
        Period.Unit.MONTH -> "month"
        Period.Unit.YEAR -> "year"
        else -> "day"
    }
    return "$n ${if (n == 1) unit else unit + "s"} free"
}

/**
 * A lease's identity for a pass: a hash of its words, so the same lease opened again (pasted,
 * shared or re-imported) is recognised and a different one is not. Case, spacing and line breaks
 * are ignored, because a PDF and a paste of the same agreement differ in exactly those.
 */
internal fun leaseFingerprint(text: String): String {
    val normal = text.lowercase().replace(Regex("\\s+"), " ").trim()
    val digest = MessageDigest.getInstance("SHA-256").digest(normal.toByteArray(Charsets.UTF_8))
    return digest.take(12).joinToString("") { "%02x".format(it) }
}
