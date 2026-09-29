package dev.kesav.redline

/**
 * Where the home is, chosen once by the reader and kept on the phone.
 *
 * Without it every ask ended in "the local legal limit", which sent a reader in Boston off
 * to look the number up and, often, not come back to send the letter. With it the ask says
 * the number. Only figures read in the statute or on an official page are used; where a
 * place's law is silent or the figure could not be confirmed, the general wording stays,
 * because a limit the app has not checked is worse than none.
 */
enum class Place(val label: String) {
    CALIFORNIA("California"),
    NEW_YORK("New York"),
    MASSACHUSETTS("Massachusetts"),
    TEXAS("Texas"),
    ENGLAND("England"),
    INDIA("India");

    internal val limits: Limits get() = LIMITS[this] ?: Limits()

    companion object {
        fun fromName(name: String?): Place? = entries.firstOrNull { it.name == name }
    }
}

/**
 * What one place's law says about the few things a lease most often gets wrong, as the
 * words the app shows. Keyed by rule id, so a rule the place has nothing on is untouched.
 */
internal class Limits(
    /**
     * A lower threshold for a rule, where the place's own limit sits below the general one:
     * a deposit over one month's rent is already too much in New York, and a deposit kept
     * 20 days is already late in California.
     */
    val floors: Map<String, Int> = emptyMap(),
    val reasons: Map<String, String> = emptyMap(),
    val asks: Map<String, String> = emptyMap(),
)

// Every figure below was read at its source on 24 Sep 2026. Places not listed here are
// deliberately not claimed.
//
// California  Civil Code 1950.5 (deposit, as amended by AB 12 from 1 July 2024; return),
//             1954 (entry), 1671 (late fees as liquidated damages)
//             https://leginfo.legislature.ca.gov/faces/codes_displaySection.xhtml?lawCode=CIV&sectionNum=1950.5
// New York    General Obligations Law 7-108 (deposit, return), Real Property Law 238-a (late fee)
//             https://www.nysenate.gov/legislation/laws/GOB/7-108
// Mass.       General Laws c.186 s.15B (deposit, return, interest, late fee)
//             https://malegislature.gov/Laws/GeneralLaws/PartII/TitleI/Chapter186/Section15b
// Texas       Property Code 92.103 (return), 92.019 (late fee); no deposit cap, no entry rule
//             https://texas.public.law/statutes/tex._prop._code_section_92.019
// England     Tenant Fees Act 2019 (deposit, late rent), Housing Act 2004 deposit protection
//             https://www.gov.uk/tenancy-deposit-protection
// India       Model Tenancy Act 2021 is a model the states may adopt; Maharashtra, Karnataka
//             and Delhi set no deposit cap, Tamil Nadu's own 2017 Act sets three months.
private val LIMITS: Map<Place, Limits> = mapOf(
    Place.CALIFORNIA to Limits(
        floors = mapOf("deposit-size" to 2, "deposit-refund-delay" to 22),
        reasons = mapOf(
            "deposit-size" to "California caps a deposit at one month's rent (Civil Code 1950.5). " +
                "Only a landlord with no more than two properties and four homes may ask for two.",
            "deposit-refund-delay" to "California gives the landlord 21 days after you move out " +
                "to return the deposit with an itemised statement (Civil Code 1950.5).",
            "late-fee" to "California sets no fixed late fee, but only enforces one that fairly " +
                "estimates what late payment costs the landlord (Civil Code 1671).",
        ),
        asks = mapOf(
            "deposit-size" to "a deposit of no more than one month's rent, the California limit " +
                "unless the landlord owns no more than two properties",
            "deposit-refund-delay" to "the deposit returned within 21 days of moving out, with an " +
                "itemised statement, as California Civil Code 1950.5 requires",
            "late-fee" to "a late fee no larger than a fair estimate of what late payment costs " +
                "the landlord, the only kind California enforces",
            "short-entry-notice" to "at least 24 hours written notice before any entry, in normal " +
                "business hours, as California Civil Code 1954 presumes reasonable",
            "entry-without-notice" to "at least 24 hours written notice before any entry, in normal " +
                "business hours, as California Civil Code 1954 presumes reasonable",
        ),
    ),
    Place.NEW_YORK to Limits(
        floors = mapOf("deposit-size" to 2, "deposit-refund-delay" to 15),
        reasons = mapOf(
            "deposit-size" to "New York limits a deposit, and any rent paid in advance with it, " +
                "to one month's rent (General Obligations Law 7-108).",
            "deposit-refund-delay" to "New York gives the landlord 14 days after you move out, and " +
                "a landlord who misses it loses the right to keep any of the deposit " +
                "(General Obligations Law 7-108).",
            "late-fee" to "New York allows a late fee only once rent is five days late, and never " +
                "more than 50 dollars or 5 percent of the rent, whichever is less (Real Property Law 238-a).",
        ),
        asks = mapOf(
            "deposit-size" to "a deposit of no more than one month's rent, the New York limit",
            "deposit-refund-delay" to "the deposit returned within 14 days of moving out, with an " +
                "itemised statement, as New York General Obligations Law 7-108 requires",
            "late-fee" to "a late fee of no more than 50 dollars or 5 percent of the rent, whichever " +
                "is less, and only once rent is five days late, as New York Real Property Law 238-a sets",
        ),
    ),
    Place.MASSACHUSETTS to Limits(
        floors = mapOf("deposit-size" to 2),
        reasons = mapOf(
            "deposit-size" to "Massachusetts caps a deposit at one month's rent " +
                "(General Laws chapter 186, section 15B).",
            "deposit-refund-delay" to "Massachusetts gives the landlord 30 days after the tenancy " +
                "ends, and any deduction must be itemised and sworn " +
                "(General Laws chapter 186, section 15B).",
            "late-fee" to "Massachusetts bars any late fee or interest until rent is a full 30 days " +
                "overdue (General Laws chapter 186, section 15B).",
            "deposit-no-interest" to "Massachusetts requires a deposit to earn interest for the " +
                "tenant: 5 percent a year, or the bank's rate if lower " +
                "(General Laws chapter 186, section 15B).",
        ),
        asks = mapOf(
            "deposit-size" to "a deposit of no more than one month's rent, the Massachusetts limit",
            "deposit-refund-delay" to "the deposit returned within 30 days of the tenancy ending, " +
                "with any deduction itemised and sworn, as Massachusetts law requires",
            "late-fee" to "no late fee or interest until rent is 30 days overdue, as Massachusetts " +
                "General Laws chapter 186, section 15B requires",
            "deposit-no-interest" to "the deposit held in a separate Massachusetts bank account, " +
                "with its interest paid to the tenant each year, as state law requires",
        ),
    ),
    Place.TEXAS to Limits(
        floors = mapOf("late-fee" to 11),
        reasons = mapOf(
            "deposit-size" to "Texas sets no limit on the size of a deposit, so the amount is " +
                "whatever the lease says. It is money you cannot touch for the whole term.",
            "deposit-refund-delay" to "Texas gives the landlord 30 days after you move out, once " +
                "you have given a forwarding address in writing (Property Code 92.103).",
            "late-fee" to "Texas presumes a late fee reasonable only up to 12 percent of the rent " +
                "in a building of four homes or fewer, and 10 percent in a larger one, and only " +
                "once rent is two full days late (Property Code 92.019).",
        ),
        asks = mapOf(
            "deposit-size" to "a smaller deposit, since Texas law leaves the amount to what both " +
                "sides agree",
            "deposit-refund-delay" to "the deposit returned within 30 days of moving out, with any " +
                "deduction itemised in writing, as Texas Property Code 92.103 requires",
            "late-fee" to "a late fee of no more than 10 percent of the rent, or 12 percent in a " +
                "building of four homes or fewer, and only once rent is two full days late, as " +
                "Texas Property Code 92.019 sets",
        ),
    ),
    Place.ENGLAND to Limits(
        floors = mapOf("deposit-size" to 2),
        reasons = mapOf(
            "deposit-size" to "In England a deposit may be at most five weeks' rent, or six where " +
                "the year's rent is 50,000 pounds or more (Tenant Fees Act 2019).",
            "deposit-refund-delay" to "In England the deposit must go into a government-approved " +
                "scheme within 30 days, and come back within 10 days of both sides agreeing the amount.",
            "late-fee" to "In England a landlord may not charge a late fee at all, only interest on " +
                "rent 14 days late, at no more than 3 percent above the Bank of England base rate " +
                "(Tenant Fees Act 2019).",
        ),
        asks = mapOf(
            "deposit-size" to "a deposit of no more than five weeks' rent, the cap under the Tenant " +
                "Fees Act 2019",
            "deposit-refund-delay" to "the deposit protected in a government scheme within 30 days, " +
                "and returned within 10 days of agreeing the amount",
            "late-fee" to "the late fee removed, leaving only interest on rent 14 days late at no " +
                "more than 3 percent above the Bank of England base rate",
        ),
    ),
    Place.INDIA to Limits(
        reasons = mapOf(
            "deposit-size" to "Most Indian states set no cap on a deposit. The Model Tenancy Act " +
                "2021 proposes two months for a home, as a model for states to adopt; Tamil " +
                "Nadu's own Act sets three months unless agreed otherwise.",
        ),
        asks = mapOf(
            "deposit-size" to "a deposit of two months' rent, the figure the Model Tenancy Act 2021 proposes",
        ),
    ),
)
