package dev.kesav.redline

import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The four areas of a lease the report scores separately. */
enum class Category(val label: String) {
    MONEY("Financial exposure"),
    ENTRY("Privacy & entry"),
    EXIT("Termination traps"),
    UPKEEP("Maintenance shifting"),
}

enum class Tier(val label: String) { LOW("Low risk"), CAUTION("Caution"), TOXIC("Toxic clauses") }

data class CategoryScore(val category: Category, val risk: Int, val flagged: Int, val high: Int, val ruleIds: List<String>)

/**
 * One sum a flagged clause puts on the tenant, in the lease's own terms: a number of months of
 * rent, a stated amount of money, or both. [every] is set for a charge that repeats ("each week
 * rent is late"), which is shown but never added into the total, because a total of charges
 * that may or may not recur would be a guess dressed as a figure.
 */
data class Cost(
    val ruleId: String,
    val clauseIndex: Int,
    val label: String,
    val months: Double,
    val amount: Long,
    val basis: String,
    val every: String? = null,
)

/**
 * The money the flagged clauses write down. [rent] is the monthly rent when the lease states it;
 * without it, months of rent cannot become money until the reader says what the rent is.
 */
data class Exposure(val symbol: String, val rent: Long?, val items: List<Cost>) {
    val oneOff: List<Cost> = items.filter { it.every == null }
    val repeating: List<Cost> = items.filter { it.every != null }

    /** Months of rent at stake in one-off sums. */
    val months: Double = oneOff.sumOf { it.months }

    /** Stated money at stake in one-off sums. */
    val fixed: Long = oneOff.sumOf { it.amount }

    /** The one-off total in money at [monthlyRent], or null when months are owed and no rent is known. */
    fun total(monthlyRent: Long? = rent): Long? = when {
        months == 0.0 -> fixed
        monthlyRent == null -> null
        else -> (months * monthlyRent).roundToLong() + fixed
    }

    /** One cost in money at [monthlyRent], or null when it is in months and no rent is known. */
    fun amountOf(cost: Cost, monthlyRent: Long? = rent): Long? = when {
        cost.months == 0.0 -> cost.amount
        monthlyRent == null -> null
        else -> (cost.months * monthlyRent).roundToLong() + cost.amount
    }
}

data class VoidClause(val ruleId: String, val clauseIndex: Int, val law: String, val why: String)

data class Insight(
    val score: Int,
    val tier: Tier,
    val categories: List<CategoryScore>,
    val exposure: Exposure?,
    val void: List<VoidClause>,
)

object Insights {

    /** Which area each rule belongs to. Every rule in Rules.kt has a line here; InsightTest pins it. */
    private val CATEGORY: Map<String, Category> = buildMap {
        listOf(
            "late-fee", "interest-rate", "dishonour-fee", "deposit-size", "deposit-no-interest",
            "deposit-refund-delay", "sole-discretion", "no-setoff", "rent-escalation", "unilateral-revision",
            "silence-is-consent", "tenant-pays-tax", "future-levies", "charge-increases", "legal-costs",
            "uk-fee", "rent-in-advance", "pet-insurance", "occupant-surcharge",
        ).forEach { put(it, Category.MONEY) }
        listOf("entry-without-notice", "showings", "short-entry-notice", "distraint")
            .forEach { put(it, Category.ENTRY) }
        listOf(
            "lock-in", "unexpired-liability", "long-notice", "liquidated-damages", "auto-renewal",
            "deemed-service", "section-21", "forfeiture", "confession",
        ).forEach { put(it, Category.EXIT) }
        listOf("tenant-structural-repairs", "mandatory-repaint", "restore-original", "as-is", "pro-cleaning", "no-liability")
            .forEach { put(it, Category.UPKEEP) }
    }

    internal val mappedRuleIds: Set<String> get() = CATEGORY.keys

    fun categoryOf(ruleId: String): Category = CATEGORY[ruleId] ?: Category.MONEY

    /**
     * The risk score, 0 to 100, higher is worse.
     *
     * Each flagged clause adds weight, 10 for a serious one and 4 for one worth checking, and the
     * score is how far that weight goes toward a lease that is bad all the way through:
     * 100 × (1 − e^(−weight / 40)). One serious clause scores 22, three score 53, five score 71,
     * so the dial rises fast over the first few problems and slows as a lease runs out of room to
     * get worse. Clauses, not findings, carry the weight, so one paragraph that trips four rules
     * counts once, the same way the report counts it.
     */
    fun of(findings: List<Finding>, clauseCount: Int, place: Place?, clauses: List<Clause> = emptyList()): Insight {
        val weight = clauseWeight(findings)
        val score = curve(weight, 40.0)
        val tier = when {
            score < 30 -> Tier.LOW
            score < 60 -> Tier.CAUTION
            else -> Tier.TOXIC
        }
        val byCategory = findings.groupBy { categoryOf(it.ruleId) }
        val categories = Category.entries.map { c ->
            val fs = byCategory[c].orEmpty()
            CategoryScore(
                category = c,
                risk = curve(clauseWeight(fs), 20.0),
                flagged = fs.map { it.clause.index }.distinct().size,
                high = fs.map { it.clause.index to it.severity }.distinct().count { it.second == Severity.HIGH },
                ruleIds = fs.map { it.ruleId }.distinct(),
            )
        }
        return Insight(score, tier, categories, Money.exposure(findings, clauses), voids(findings, place))
    }

    private fun clauseWeight(findings: List<Finding>): Double =
        findings.groupBy { it.clause.index }.values.sumOf { fs ->
            if (fs.any { it.severity == Severity.HIGH }) 10.0 else 4.0
        }

    private fun curve(weight: Double, scale: Double): Int =
        (100 * (1 - exp(-weight / scale))).roundToInt().coerceIn(0, 100)

    /**
     * Clauses the chosen place's own law already overrides. Only where Places.kt carries a sourced
     * figure for the rule, and only when the rule fired against that place's threshold, so every
     * line here names the statute the reason text already quotes.
     */
    private fun voids(findings: List<Finding>, place: Place?): List<VoidClause> {
        if (place == null) return emptyList()
        val laws = VOID[place] ?: return emptyList()
        return findings
            .mapNotNull { f -> laws[f.ruleId]?.let { (law, why) -> VoidClause(f.ruleId, f.clause.index, law, why) } }
            .distinctBy { it.ruleId to it.clauseIndex }
    }

    private val VOID: Map<Place, Map<String, Pair<String, String>>> = mapOf(
        Place.CALIFORNIA to mapOf(
            "deposit-size" to ("Civil Code 1950.5" to "A deposit over one month's rent, unless the landlord owns no more than two properties."),
            "deposit-refund-delay" to ("Civil Code 1950.5" to "The deposit is due back within 21 days of moving out, whatever the lease says."),
        ),
        Place.NEW_YORK to mapOf(
            "deposit-size" to ("General Obligations Law 7-108" to "A deposit, with any rent paid in advance, is capped at one month's rent."),
            "deposit-refund-delay" to ("General Obligations Law 7-108" to "The deposit is due back within 14 days; a landlord who misses it keeps none of it."),
        ),
        Place.MASSACHUSETTS to mapOf(
            "deposit-size" to ("General Laws ch. 186, s. 15B" to "A deposit is capped at one month's rent."),
            "deposit-no-interest" to ("General Laws ch. 186, s. 15B" to "A deposit must earn interest for the tenant."),
        ),
        Place.ENGLAND to mapOf(
            "deposit-size" to ("Tenant Fees Act 2019" to "A deposit is capped at five weeks' rent, or six at 50,000 pounds a year or more."),
            "uk-fee" to ("Tenant Fees Act 2019" to "Fees beyond rent, deposit and a few named charges are banned."),
            "late-fee" to ("Tenant Fees Act 2019" to "No late fee is allowed, only interest on rent 14 days late."),
            "section-21" to ("Renters' Rights Act 2025" to "No-fault eviction under section 21 ended on 1 May 2026."),
        ),
    )
}

/**
 * Reads the sums of money out of flagged clauses. Nothing is estimated: a cost appears only when
 * the clause itself prints a number of months of rent, a percentage of rent, or an amount of money.
 */
internal object Money {

    private val symbolAmount = Regex("""(₹|rs\.?|inr|\$|usd|£|gbp)\s?(\d[\d,]*(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val amountWord = Regex("""(\d[\d,]*(?:\.\d+)?|(?:[a-z]+[ -]){0,3}[a-z]+)\s+(rupees|dollars|pounds)""", RegexOption.IGNORE_CASE)
    private val monthsOfRent = Regex("""(\d+|[a-z]+(?:[ -][a-z]+)?)\s+(?:calendar\s+)?months?'?s?\s+(?:of\s+(?:the\s+)?(?:monthly\s+)?)?rent""", RegexOption.IGNORE_CASE)
    private val percentOfRent = Regex("""(\d+(?:\.\d+)?|[a-z]+(?:[ -][a-z]+)?)\s*(?:percent|per cent|%)\s+(?:of\s+(?:the\s+)?(?:monthly\s+)?rent)?""", RegexOption.IGNORE_CASE)
    private val rentStated = Regex(
        """(?:monthly rent|rent)\D{0,40}?((?:₹|rs\.?|inr|\$|usd|£|gbp)\s?\d[\d,]*(?:\.\d+)?)\s*(?:per month|a month|each month|every month|monthly|per mensem|/\s?month)""",
        RegexOption.IGNORE_CASE,
    )

    data class Sum(val symbol: String, val amount: Long)

    fun exposure(findings: List<Finding>, clauses: List<Clause>): Exposure? {
        val rent = clauses.firstNotNullOfOrNull { c -> rentStated.find(c.text)?.groupValues?.get(1)?.let(::sum) }
        val items = findings.groupBy { it.clause.index }.mapNotNull { (_, fs) -> costOf(fs) }
        if (items.isEmpty()) return null
        val symbol = rent?.symbol
            ?: items.firstNotNullOfOrNull { c -> sums(findings.first { it.clause.index == c.clauseIndex }.clause.text).firstOrNull()?.symbol }
            ?: ""
        return Exposure(symbol, rent?.amount, items)
    }

    /** The one cost a clause carries, from its most telling rule. */
    private fun costOf(fs: List<Finding>): Cost? {
        val clause = fs.first().clause
        val text = clause.text
        val ids = fs.map { it.ruleId }.toSet()
        fun pick(vararg rule: String) = rule.firstOrNull { it in ids }

        pick("liquidated-damages", "unexpired-liability", "lock-in")?.let { id ->
            monthsIn(text)?.let { (n, shown) -> return Cost(id, clause.index, "Payable if you leave early", n, 0, "\"$shown\" is payable on leaving early.") }
            sums(text).maxByOrNull { it.amount }?.let { return Cost(id, clause.index, "Payable if you leave early", 0.0, it.amount, "The clause names ${show(it)} on leaving early.") }
        }
        pick("deposit-size", "deposit-refund-delay", "deposit-no-interest", "sole-discretion", "forfeiture")?.let { id ->
            if (text.contains("deposit", ignoreCase = true)) {
                monthsIn(text)?.let { (n, shown) ->
                    return Cost(id, clause.index, "Deposit the landlord holds", n, 0, "A deposit of \"$shown\", returned at the landlord's pace and discretion.")
                }
                sums(text).maxByOrNull { it.amount }?.let {
                    return Cost(id, clause.index, "Deposit the landlord holds", 0.0, it.amount, "A deposit of ${show(it)}, returned at the landlord's pace and discretion.")
                }
            }
        }
        pick("rent-in-advance")?.let { id ->
            monthsIn(text)?.let { (n, shown) -> return Cost(id, clause.index, "Rent paid up front", n, 0, "\"$shown\" is paid before moving in.") }
        }
        pick("tenant-structural-repairs", "as-is")?.let { id ->
            sums(text).maxByOrNull { it.amount }?.let {
                return Cost(id, clause.index, "Repairs you pay for", 0.0, it.amount, "Each repair up to ${show(it)} falls on you.", every = "each repair")
            }
        }
        pick("late-fee")?.let { id ->
            percentIn(text)?.let { (p, shown) ->
                return Cost(id, clause.index, "Late fee", p / 100.0, 0, "\"$shown\" of the rent" + if (text.contains("week", true)) ", for each week late." else ".", every = if (text.contains("week", true)) "each week late" else "each late payment")
            }
            sums(text).maxByOrNull { it.amount }?.let {
                return Cost(id, clause.index, "Late fee", 0.0, it.amount, "The clause charges ${show(it)} when rent is late.", every = "each late payment")
            }
        }
        pick("rent-escalation", "charge-increases", "unilateral-revision")?.let { id ->
            percentIn(text)?.let { (p, shown) ->
                return Cost(id, clause.index, "Rent rise", p / 100.0, 0, "Rent goes up by \"$shown\" on the clause's own schedule.", every = "each rise")
            }
        }
        pick("occupant-surcharge")?.let { id ->
            percentIn(text)?.let { (p, shown) ->
                return Cost(id, clause.index, "Extra occupant surcharge", p / 100.0, 0, "Rent rises by \"$shown\" for each extra occupant.", every = "each month")
            }
        }
        pick("dishonour-fee", "legal-costs", "pro-cleaning", "mandatory-repaint", "restore-original", "uk-fee", "pet-insurance")?.let { id ->
            sums(text).maxByOrNull { it.amount }?.let {
                return Cost(id, clause.index, LABELS[id] ?: "Charge", 0.0, it.amount, "The clause names ${show(it)}.")
            }
        }
        return null
    }

    private val LABELS = mapOf(
        "dishonour-fee" to "Bounced payment charge",
        "legal-costs" to "The landlord's legal costs",
        "pro-cleaning" to "Professional cleaning",
        "mandatory-repaint" to "Repainting on leaving",
        "restore-original" to "Restoring the premises",
        "uk-fee" to "Fees on top of rent",
        "pet-insurance" to "Pet charge",
    )

    /** The largest "N months rent" in the text, as a count and the words it was written in. */
    private fun monthsIn(text: String): Pair<Double, String>? =
        monthsOfRent.findAll(text)
            .mapNotNull { m -> tail(m.groupValues[1])?.let { n -> n to m.value.trim() } }
            .maxByOrNull { it.first }

    private fun percentIn(text: String): Pair<Double, String>? =
        percentOfRent.findAll(text)
            .mapNotNull { m -> tail(m.groupValues[1])?.let { n -> n to m.value.trim() } }
            .firstOrNull { it.first in 0.5..100.0 }

    /** A number written in digits or words, taking the longest run of trailing words that reads as one. */
    private fun tail(raw: String): Double? {
        raw.replace(",", "").toDoubleOrNull()?.let { return it }
        val words = raw.lowercase().split(' ', '-').filter { it.isNotBlank() }
        for (start in words.indices) {
            Numbers.parse(words.drop(start))?.let { return it.toDouble() }
        }
        return null
    }

    fun sums(text: String): List<Sum> {
        val out = mutableListOf<Sum>()
        symbolAmount.findAll(text).forEach { m ->
            val amount = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@forEach
            out += Sum(symbolOf(m.groupValues[1]), amount.roundToLong())
        }
        amountWord.findAll(text).forEach { m ->
            val amount = tail(m.groupValues[1]) ?: return@forEach
            out += Sum(symbolOf(m.groupValues[2]), amount.roundToLong())
        }
        return out.filter { it.amount > 0 }
    }

    private fun sum(raw: String): Sum? = sums(raw).firstOrNull()

    private fun symbolOf(raw: String): String = when (raw.lowercase().trimEnd('.')) {
        "₹", "rs", "inr", "rupees" -> "₹"
        "£", "gbp", "pounds" -> "£"
        else -> "$"
    }

    private fun show(s: Sum): String = s.symbol + "%,d".format(s.amount)
}
