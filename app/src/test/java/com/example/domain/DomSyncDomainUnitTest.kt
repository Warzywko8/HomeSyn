package com.example.domain

import com.example.data.model.ChoreIconOption
import com.example.data.model.ChoreResetMode
import com.example.data.model.ChoreTile
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Household
import com.example.data.model.MemberInfo
import com.example.data.model.ShoppingAisle
import com.example.data.model.ShoppingItem
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

class DomSyncDomainUnitTest {

    @Test
    fun categorizer_parsesNaturalPolishQuantitiesAndAisles() {
        val milk = PolishShoppingCategorizer.parseAndCategorize("mleko 2l")
        assertEquals("Mleko", milk.name)
        assertEquals("2l", milk.quantityText)
        assertEquals(ShoppingAisle.DAIRY, milk.category)

        val bananas = PolishShoppingCategorizer.parseAndCategorize("banany 1 kg")
        assertEquals("Banany", bananas.name)
        assertEquals("1 kg", bananas.quantityText)
        assertEquals(ShoppingAisle.VEG_FRUIT, bananas.category)

        val bread = PolishShoppingCategorizer.parseAndCategorize("chleb żytni")
        assertEquals("Chleb żytni", bread.name)
        assertEquals("", bread.quantityText)
        assertEquals(ShoppingAisle.BAKERY, bread.category)

        val tabs = PolishShoppingCategorizer.parseAndCategorize("tabletki do zmywarki 30 szt.")
        assertEquals(ShoppingAisle.HOME_CHEM, tabs.category)
    }

    @Test
    fun choreResetEvaluator_respectsScheduledHoursBoundary() {
        val zone = ZoneId.of("Europe/Warsaw")
        val doneAt10 = LocalDateTime.of(2026, 11, 13, 10, 15).atZone(zone).toInstant()
        val tile = ChoreTile(
            id = "c1",
            title = "Pies nakarmiony",
            resetMode = ChoreResetMode.SCHEDULED_HOURS.key,
            resetHours = listOf(8L, 18L),
            lastCompletedAt = Timestamp(Date.from(doneAt10)),
            lastCompletedByName = "Marek"
        )

        val check14 = LocalDateTime.of(2026, 11, 13, 14, 0).atZone(zone).toInstant().toEpochMilli()
        val eval14 = ChoreResetEvaluator.evaluate(tile, check14, zone)
        assertTrue(eval14.isDoneForCurrentPeriod)
        assertTrue(eval14.statusTextPl.contains("Marek"))

        val check19 = LocalDateTime.of(2026, 11, 13, 19, 0).atZone(zone).toInstant().toEpochMilli()
        val eval19 = ChoreResetEvaluator.evaluate(tile, check19, zone)
        assertFalse(eval19.isDoneForCurrentPeriod)
    }

    @Test
    fun expenseBalanceCalculator_handlesMultiMemberEqualSplit() {
        val household = Household(
            id = "h1",
            name = "Dom",
            memberUids = listOf("u1", "u2", "u3"),
            members = mapOf(
                "u1" to MemberInfo("Ania"),
                "u2" to MemberInfo("Marek"),
                "u3" to MemberInfo("Kuba")
            )
        )
        val expenses = listOf(
            Expense(id = "e1", amountGrosze = 30000L, paidByUid = "u1", paidByName = "Ania")
        )
        val summary = ExpenseBalanceCalculator.calculate(household, expenses)
        assertEquals(30000L, summary.totalCycleGrosze)
        assertEquals(2, summary.transfers.size)
        assertTrue(summary.transfers.all { it.toUid == "u1" && it.amountGrosze == 10000L })
    }

    @Test
    fun monthlyBudgetCalculator_tracksCurrentMonthAcrossCyclesAndCategories() {
        val zone = ZoneId.systemDefault()
        val nowMillis = LocalDateTime.of(2026, 11, 15, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val sameMonthCycle1 = Timestamp(Date.from(LocalDateTime.of(2026, 11, 2, 10, 0).atZone(zone).toInstant()))
        val sameMonthCycle2 = Timestamp(Date.from(LocalDateTime.of(2026, 11, 12, 18, 0).atZone(zone).toInstant()))
        val prevMonth = Timestamp(Date.from(LocalDateTime.of(2026, 10, 28, 12, 0).atZone(zone).toInstant()))

        val household = Household(
            id = "h1",
            name = "Dom",
            settlementCycle = 2L,
            monthlyBudgetGrosze = 100_000L, // 1000 zł
            categoryBudgetsGrosze = mapOf(
                ExpenseCategory.GROCERIES.key to 50_000L,
                ExpenseCategory.BILLS.key to 40_000L
            )
        )

        val expenses = listOf(
            Expense(id = "e1", amountGrosze = 45_000L, cycleNumber = 1L, category = ExpenseCategory.GROCERIES.key, createdAt = sameMonthCycle1),
            Expense(id = "e2", amountGrosze = 40_000L, cycleNumber = 2L, category = ExpenseCategory.BILLS.key, createdAt = sameMonthCycle2),
            Expense(id = "e_old", amountGrosze = 90_000L, cycleNumber = 1L, category = ExpenseCategory.GROCERIES.key, createdAt = prevMonth)
        )

        val summary = MonthlyBudgetCalculator.calculate(household, expenses, nowMillis)
        assertEquals(85_000L, summary.totalSpentGrosze) // Ignores Oct, sums cycle 1 + cycle 2 in Nov
        assertEquals(15_000L, summary.remainingGrosze)
        assertEquals(85, summary.percentUsed)
        assertEquals(BudgetStatus.WARNING_80, summary.status)

        val groceries = summary.categoryBreakdown.first { it.category == ExpenseCategory.GROCERIES }
        assertEquals(45_000L, groceries.spentGrosze)
        assertEquals(BudgetStatus.WARNING_80, groceries.status)

        val bills = summary.categoryBreakdown.first { it.category == ExpenseCategory.BILLS }
        assertEquals(40_000L, bills.spentGrosze)
        assertEquals(BudgetStatus.EXCEEDED, bills.status)
    }

    @Test
    fun expenseCategorySuggester_matchesPolishKeywords() {
        assertEquals(ExpenseCategory.GROCERIES, ExpenseCategorySuggester.suggestCategory("Biedronka zakupy"))
        assertEquals(ExpenseCategory.BILLS, ExpenseCategorySuggester.suggestCategory("Czynsz za mieszkanie"))
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategorySuggester.suggestCategory("Paliwo Orlen"))
        assertEquals(ExpenseCategory.HOME_HEALTH, ExpenseCategorySuggester.suggestCategory("Apteka leki"))
        assertEquals(ExpenseCategory.FOOD_OUT, ExpenseCategorySuggester.suggestCategory("Pizza z dostawą"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategorySuggester.suggestCategory("Prezent urodzinowy"))
    }

    @Test
    fun shoppingHistoryLimiter_identifiesOldestOverflowBeyondTen() {
        val items = (1..12).map { idx ->
            ShoppingItem(
                id = "item_$idx",
                name = "Produkt $idx",
                isPurchased = true,
                purchasedAt = Timestamp(Date(idx * 10_000L))
            )
        }
        val overflow = ShoppingHistoryLimiter.overflowIdsToDelete(items)
        // Newest 10 are item_12 down to item_3; oldest 2 (item_2, item_1) must be deleted
        assertEquals(listOf("item_2", "item_1"), overflow)

        val incomingOverflow = ShoppingHistoryLimiter.overflowIdsToDelete(
            purchasedItems = items.take(10),
            incomingItemId = "new_item"
        )
        assertEquals(listOf("item_1"), incomingOverflow)
    }

    @Test
    fun choreIconOption_mapsLegacyAndGeneralCategories() {
        assertEquals(ChoreIconOption.KITCHEN, ChoreIconOption.fromKey("kitchen"))
        assertEquals(ChoreIconOption.KITCHEN, ChoreIconOption.fromKey("dishwasher"))
        assertEquals(ChoreIconOption.CLEANING, ChoreIconOption.fromKey("cleaning"))
        assertEquals(ChoreIconOption.CLEANING, ChoreIconOption.fromKey("trash"))
        assertEquals(ChoreIconOption.CLEANING, ChoreIconOption.fromKey("vacuum"))
        assertEquals(ChoreIconOption.HOME, ChoreIconOption.fromKey("home"))
        assertEquals(ChoreIconOption.HOME, ChoreIconOption.fromKey("lock"))
        assertEquals(ChoreIconOption.OTHER, ChoreIconOption.fromKey("sparkle"))
        assertEquals(ChoreIconOption.OTHER, ChoreIconOption.fromKey("unknown_key"))
    }
}
