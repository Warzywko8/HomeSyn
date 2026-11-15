package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Household
import com.example.data.model.ShoppingItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class BudgetStatus {
    UNCONFIGURED,
    SAFE,
    WARNING_80,
    EXCEEDED
}

@Immutable
data class CategoryBudgetProgress(
    val category: ExpenseCategory,
    val spentGrosze: Long,
    val limitGrosze: Long?,
    val progressFraction: Float,
    val status: BudgetStatus
)

@Immutable
data class MonthlyBudgetSummary(
    val monthLabelPl: String,
    val totalSpentGrosze: Long,
    val totalLimitGrosze: Long?,
    val remainingGrosze: Long?,
    val progressFraction: Float,
    val percentUsed: Int,
    val status: BudgetStatus,
    val categoryBreakdown: List<CategoryBudgetProgress>,
    val hasAnyBudgetConfigured: Boolean
)

object ExpenseCategorySuggester {
    private val rules: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.GROCERIES to listOf(
            "biedronka", "lidl", "kaufland", "auchan", "carrefour", "żabka", "zabka",
            "dino", "netto", "aldi", "lewiatan", "stokrotka", "piekarnia", "warzyw",
            "zakup", "spożyw", "spozyw", "market", "sklep", "chleb", "mleko"
        ),
        ExpenseCategory.BILLS to listOf(
            "czynsz", "prąd", "prad", "gaz", "woda", "internet", "światłowód", "swiatlowod",
            "telefon", "abonament", "rachun", "energia", "ogrzewanie", "ścieki", "scieki",
            "śmieci", "smieci", "ubezpieczenie", "media", "kredyt", "najem", "wynajem"
        ),
        ExpenseCategory.FOOD_OUT to listOf(
            "pizza", "kebab", "obiad", "kolacja", "śniadanie", "sniadanie", "restaurac",
            "kawa", "kawiarnia", "bar", "burger", "sushi", "wolt", "pyszne", "glovo",
            "uber eats", "lody", "bistro", "lunch"
        ),
        ExpenseCategory.TRANSPORT to listOf(
            "paliwo", "orlen", "bp", "shell", "circle", "lotos", "moya", "benzyna",
            "diesel", "uber", "bolt", "taxi", "bilet", "pkp", "mpk", "ztm", "kolej",
            "parking", "autostrada", "myjnia", "mechanik"
        ),
        ExpenseCategory.HOME_HEALTH to listOf(
            "apteka", "leki", "lek", "rossmann", "hebe", "drogeria", "chemia", "proszek",
            "kapsułki", "dentysta", "lekarz", "wizyta", "ikea", "castorama", "leroy",
            "obi", "żarówk", "zarowk", "papier", "karma", "weterynarz"
        )
    )

    fun suggestCategory(description: String): ExpenseCategory {
        val normalized = description.trim().lowercase(Locale.forLanguageTag("pl-PL"))
        if (normalized.isBlank()) return ExpenseCategory.OTHER
        for ((category, keywords) in rules) {
            if (keywords.any { normalized.contains(it) }) {
                return category
            }
        }
        return ExpenseCategory.OTHER
    }
}

object MonthlyBudgetCalculator {
    private val polishLocale = Locale.forLanguageTag("pl-PL")

    fun calculate(
        household: Household,
        allExpenses: List<Expense>,
        nowMillis: Long = System.currentTimeMillis()
    ): MonthlyBudgetSummary {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)

        val monthFormatter = SimpleDateFormat("LLLL yyyy", polishLocale)
        val monthLabel = monthFormatter.format(Date(nowMillis)).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(polishLocale) else it.toString()
        }

        // Filter expenses belonging to the current calendar month regardless of settlementCycle
        val expCal = Calendar.getInstance()
        val currentMonthExpenses = allExpenses.filter { expense ->
            val createdMs = expense.createdAt?.toDate()?.time ?: nowMillis
            expCal.timeInMillis = createdMs
            expCal.get(Calendar.YEAR) == targetYear && expCal.get(Calendar.MONTH) == targetMonth
        }

        val totalSpentGrosze = currentMonthExpenses.sumOf { it.amountGrosze }
        val totalLimitGrosze = household.monthlyBudgetGrosze?.takeIf { it > 0L }

        val overallStatus: BudgetStatus
        val overallProgress: Float
        val percentUsed: Int
        val remainingGrosze: Long?

        if (totalLimitGrosze != null) {
            val ratio = totalSpentGrosze.toDouble() / totalLimitGrosze.toDouble()
            overallProgress = ratio.toFloat().coerceIn(0f, 1f)
            percentUsed = (ratio * 100.0).toInt()
            remainingGrosze = totalLimitGrosze - totalSpentGrosze
            overallStatus = when {
                totalSpentGrosze >= totalLimitGrosze -> BudgetStatus.EXCEEDED
                ratio >= 0.80 -> BudgetStatus.WARNING_80
                else -> BudgetStatus.SAFE
            }
        } else {
            overallProgress = 0f
            percentUsed = 0
            remainingGrosze = null
            overallStatus = BudgetStatus.UNCONFIGURED
        }

        val spentByCategory = currentMonthExpenses
            .groupBy { it.categoryEnum() }
            .mapValues { (_, list) -> list.sumOf { it.amountGrosze } }

        val categoryBreakdown = ExpenseCategory.entries.mapNotNull { cat ->
            val spent = spentByCategory[cat] ?: 0L
            val limit = household.categoryBudgetsGrosze[cat.key]?.takeIf { it > 0L }
            if (spent == 0L && limit == null) {
                null
            } else {
                val catStatus: BudgetStatus
                val catProgress: Float
                if (limit != null) {
                    val ratio = spent.toDouble() / limit.toDouble()
                    catProgress = ratio.toFloat().coerceIn(0f, 1f)
                    catStatus = when {
                        spent >= limit -> BudgetStatus.EXCEEDED
                        ratio >= 0.80 -> BudgetStatus.WARNING_80
                        else -> BudgetStatus.SAFE
                    }
                } else {
                    catProgress = 0f
                    catStatus = BudgetStatus.UNCONFIGURED
                }
                CategoryBudgetProgress(
                    category = cat,
                    spentGrosze = spent,
                    limitGrosze = limit,
                    progressFraction = catProgress,
                    status = catStatus
                )
            }
        }

        val hasAnyBudget = totalLimitGrosze != null || household.categoryBudgetsGrosze.values.any { it > 0L }

        return MonthlyBudgetSummary(
            monthLabelPl = monthLabel,
            totalSpentGrosze = totalSpentGrosze,
            totalLimitGrosze = totalLimitGrosze,
            remainingGrosze = remainingGrosze,
            progressFraction = overallProgress,
            percentUsed = percentUsed,
            status = overallStatus,
            categoryBreakdown = categoryBreakdown,
            hasAnyBudgetConfigured = hasAnyBudget
        )
    }
}

object ShoppingHistoryLimiter {
    const val MAX_RECENTLY_PURCHASED = 10

    fun sortPurchasedNewestFirst(items: List<ShoppingItem>): List<ShoppingItem> {
        return items
            .filter { it.isPurchased }
            .sortedWith(
                compareByDescending<ShoppingItem> {
                    it.purchasedAt?.toDate()?.time
                        ?: it.updatedAt?.toDate()?.time
                        ?: it.createdAt?.toDate()?.time
                        ?: 0L
                }.thenBy { it.id }
            )
    }

    fun overflowIdsToDelete(
        purchasedItems: List<ShoppingItem>,
        incomingItemId: String? = null,
        maxItems: Int = MAX_RECENTLY_PURCHASED
    ): List<String> {
        val sorted = sortPurchasedNewestFirst(purchasedItems)
            .filterNot { it.id == incomingItemId }
        val keepExisting = if (incomingItemId != null) (maxItems - 1).coerceAtLeast(0) else maxItems
        return sorted.drop(keepExisting).map { it.id }.filter { it.isNotBlank() }
    }
}
