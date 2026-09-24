package dev.kesav.redline

// Placeholder with the agreed surface, replaced wholesale by the insight branch at merge.

enum class Category(val label: String) {
    MONEY("Financial exposure"),
    ENTRY("Privacy & entry"),
    EXIT("Termination traps"),
    UPKEEP("Maintenance shifting"),
}

enum class Tier(val label: String) { LOW("Low risk"), CAUTION("Caution"), TOXIC("Toxic clauses") }

data class CategoryScore(val category: Category, val risk: Int, val flagged: Int, val high: Int, val ruleIds: List<String>)

data class Cost(val ruleId: String, val clauseIndex: Int, val label: String, val amount: Long, val basis: String)

data class Exposure(val symbol: String, val total: Long, val items: List<Cost>)

data class VoidClause(val ruleId: String, val clauseIndex: Int, val law: String, val why: String)

data class Insight(
    val score: Int,
    val tier: Tier,
    val categories: List<CategoryScore>,
    val exposure: Exposure?,
    val void: List<VoidClause>,
)

object Insights {
    fun categoryOf(ruleId: String): Category = when (ruleId) {
        "entry-without-notice", "showings", "occupant-surcharge", "short-entry-notice" -> Category.ENTRY
        "lock-in", "unexpired-liability", "long-notice", "liquidated-damages", "auto-renewal",
        "deemed-service", "section-21", "forfeiture", "confession", "distraint" -> Category.EXIT
        "tenant-structural-repairs", "mandatory-repaint", "restore-original", "as-is",
        "pro-cleaning", "no-liability" -> Category.UPKEEP
        else -> Category.MONEY
    }

    fun of(findings: List<Finding>, clauseCount: Int, place: Place?): Insight {
        val byCategory = findings.groupBy { categoryOf(it.ruleId) }
        val categories = Category.entries.map { c ->
            val fs = byCategory[c].orEmpty()
            val clauses = fs.map { it.clause.index }.distinct().size
            val high = fs.count { it.severity == Severity.HIGH }
            CategoryScore(c, (high * 22 + (fs.size - high) * 9).coerceAtMost(100), clauses, high, fs.map { it.ruleId }.distinct())
        }
        val high = findings.filter { it.severity == Severity.HIGH }.map { it.clause.index }.distinct().size
        val medium = findings.map { it.clause.index }.distinct().size - high
        val score = (high * 9 + medium * 4).coerceAtMost(100)
        val tier = when {
            score < 30 -> Tier.LOW
            score < 60 -> Tier.CAUTION
            else -> Tier.TOXIC
        }
        return Insight(score, tier, categories, null, emptyList())
    }
}
