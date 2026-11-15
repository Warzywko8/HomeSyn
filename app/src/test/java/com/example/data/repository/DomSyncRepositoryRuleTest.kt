package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.ChoreRepository
import com.example.data.firebase.ExpenseRepository
import com.example.data.firebase.HouseholdRepository
import com.example.data.firebase.ShoppingRepository
import com.example.data.model.ExpenseCategory
import com.example.data.model.ShoppingAisle
import com.example.domain.ExpenseBalanceCalculator
import com.example.domain.ShoppingHistoryLimiter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DomSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun householdLifecycle_createJoinShoppingChoresExpenses_succeedsWithRealtimeSync() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val householdRepo = HouseholdRepository(firestore, auth)
        val shoppingRepo = ShoppingRepository(firestore, auth)
        val choreRepo = ChoreRepository(firestore, auth)
        val expenseRepo = ExpenseRepository(firestore, auth)

        val createdHousehold = withTimeout(TIMEOUT_MS) {
            householdRepo.createHousehold("Testowy Dom", "Ania", null).getOrThrow()
        }
        assertTrue(createdHousehold.id.isNotBlank())
        assertEquals(6, createdHousehold.inviteCode.length)

        // Default chores should be observable by Alice with 2 pinned by default
        val aliceChores = withTimeout(TIMEOUT_MS) {
            choreRepo.observeChores(createdHousehold.id, aliceUid).first { it.size == 4 }
        }
        assertEquals(4, aliceChores.size)
        assertEquals(2, aliceChores.count { it.isPinnedToWidget })

        // Update pinned chores to the last 2 chores and verify deserialization
        val newPinnedIds = aliceChores.takeLast(2).map { it.id }.toSet()
        withTimeout(TIMEOUT_MS) {
            choreRepo.updatePinnedChores(createdHousehold.id, aliceChores, newPinnedIds).getOrThrow()
        }
        val updatedChores = withTimeout(TIMEOUT_MS) {
            choreRepo.observeChores(createdHousehold.id, aliceUid).first { list ->
                list.filter { it.isPinnedToWidget }.map { it.id }.toSet() == newPinnedIds
            }
        }
        assertEquals(newPinnedIds, updatedChores.filter { it.isPinnedToWidget }.map { it.id }.toSet())

        // Bob joins using the 6-char code
        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val joinedHouseId = withTimeout(TIMEOUT_MS) {
            householdRepo.joinHouseholdByCode(createdHousehold.inviteCode, "Marek", null).getOrThrow()
        }
        assertEquals(createdHousehold.id, joinedHouseId)

        val bobHouse = withTimeout(TIMEOUT_MS) {
            householdRepo.observeUserHouseholds(bobUid).first { houses ->
                houses.any { it.id == joinedHouseId && bobUid in it.memberUids }
            }.first { it.id == joinedHouseId }
        }

        // Bob adds "mleko 2l" -> auto-parsed and categorized into DAIRY
        val itemId = withTimeout(TIMEOUT_MS) {
            shoppingRepo.addItem(bobHouse, "mleko 2l", "Marek").getOrThrow()
        }
        val items = withTimeout(TIMEOUT_MS) {
            shoppingRepo.observeShoppingItems(bobHouse.id, bobUid).first { list -> list.any { it.id == itemId } }
        }
        val milk = items.first { it.id == itemId }
        assertEquals("Mleko", milk.name)
        assertEquals("2l", milk.quantityText)
        assertEquals(ShoppingAisle.DAIRY.key, milk.category)
        assertEquals(false, milk.isPurchased)

        // Check off milk (isPurchased = true) and verify Firestore deserializes true
        withTimeout(TIMEOUT_MS) {
            shoppingRepo.togglePurchased(bobHouse.id, itemId, true).getOrThrow()
        }
        val purchasedMilk = withTimeout(TIMEOUT_MS) {
            shoppingRepo.observeShoppingItems(bobHouse.id, bobUid)
                .first { list -> list.firstOrNull { it.id == itemId }?.isPurchased == true }
                .first { it.id == itemId }
        }
        assertTrue(purchasedMilk.isPurchased)

        // Uncheck milk (isPurchased = false) and verify Firestore deserializes false
        withTimeout(TIMEOUT_MS) {
            shoppingRepo.togglePurchased(bobHouse.id, itemId, false).getOrThrow()
        }
        val restoredMilk = withTimeout(TIMEOUT_MS) {
            shoppingRepo.observeShoppingItems(bobHouse.id, bobUid)
                .first { list -> list.firstOrNull { it.id == itemId }?.isPurchased == false }
                .first { it.id == itemId }
        }
        assertEquals(false, restoredMilk.isPurchased)

        // Bob adds expense 148 zł with category GROCERIES -> balance shows Ania owes Marek 74,00 zł
        withTimeout(TIMEOUT_MS) {
            expenseRepo.addExpense(
                bobHouse,
                14800L,
                "Biedronka",
                bobUid,
                "Marek",
                ExpenseCategory.GROCERIES
            ).getOrThrow()
        }
        val expenses = withTimeout(TIMEOUT_MS) {
            expenseRepo.observeExpenses(bobHouse.id, bobUid).first { it.isNotEmpty() }
        }
        assertEquals(ExpenseCategory.GROCERIES, expenses.first().categoryEnum())
        val summary = ExpenseBalanceCalculator.calculate(bobHouse, expenses)
        assertEquals(14800L, summary.totalCycleGrosze)
        assertEquals(1, summary.transfers.size)
        assertEquals(7400L, summary.transfers.first().amountGrosze)
        assertEquals("Ania", summary.transfers.first().fromName)
        assertEquals("Marek", summary.transfers.first().toName)

        // Household budget planning persists monthlyBudgetGrosze & categoryBudgetsGrosze
        withTimeout(TIMEOUT_MS) {
            householdRepo.updateMonthlyBudget(
                householdId = bobHouse.id,
                monthlyBudgetGrosze = 400_000L,
                categoryBudgetsGrosze = mapOf(
                    ExpenseCategory.GROCERIES.key to 180_000L,
                    ExpenseCategory.BILLS.key to 120_000L
                )
            ).getOrThrow()
        }
        val budgetedHouse = withTimeout(TIMEOUT_MS) {
            householdRepo.observeUserHouseholds(bobUid).first { houses ->
                houses.any { it.id == bobHouse.id && it.monthlyBudgetGrosze == 400_000L }
            }.first { it.id == bobHouse.id }
        }
        assertEquals(400_000L, budgetedHouse.monthlyBudgetGrosze)
        assertEquals(180_000L, budgetedHouse.categoryBudgetsGrosze[ExpenseCategory.GROCERIES.key])

        // Member updates household name and own display name atomically
        withTimeout(TIMEOUT_MS) {
            householdRepo.updateIdentitySettings(
                household = budgetedHouse,
                householdName = "Nowy Dom na Mokotowie",
                memberDisplayName = "Marek Pro"
            ).getOrThrow()
        }
        val renamedHouse = withTimeout(TIMEOUT_MS) {
            householdRepo.observeUserHouseholds(bobUid).first { houses ->
                houses.any { it.id == bobHouse.id && it.name == "Nowy Dom na Mokotowie" }
            }.first { it.id == bobHouse.id }
        }
        assertEquals("Nowy Dom na Mokotowie", renamedHouse.name)
        assertEquals("Marek Pro", renamedHouse.memberName(bobUid))

        // Check off 11 items -> oldest purchased item is purged so at most 10 remain
        val extraIds = (1..11).map { idx ->
            withTimeout(TIMEOUT_MS) {
                shoppingRepo.addItem(bobHouse, "Produkt $idx", "Marek").getOrThrow()
            }
        }
        extraIds.forEach { id ->
            val currentList = withTimeout(TIMEOUT_MS) {
                shoppingRepo.observeShoppingItems(bobHouse.id, bobUid).first { list -> list.any { it.id == id } }
            }
            val overflow = ShoppingHistoryLimiter.overflowIdsToDelete(currentList, incomingItemId = id)
            withTimeout(TIMEOUT_MS) {
                shoppingRepo.togglePurchased(bobHouse.id, id, true, overflow).getOrThrow()
            }
        }
        val afterTrim = withTimeout(TIMEOUT_MS) {
            shoppingRepo.observeShoppingItems(bobHouse.id, bobUid).first { list ->
                list.count { it.isPurchased } == 10 && list.any { it.id == extraIds.last() }
            }
        }
        assertEquals(10, afterTrim.count { it.isPurchased })
    }

    @Test
    fun outsider_cannotReadOrWriteAnotherHousehold() = runBlocking {
        signInTestUser("owner_${UUID.randomUUID()}@test.com")
        val householdRepo = HouseholdRepository(firestore, auth)
        val house = withTimeout(TIMEOUT_MS) {
            householdRepo.createHousehold("Prywatny Dom", "Ania", null).getOrThrow()
        }

        val outsiderUid = signInTestUser("outsider_${UUID.randomUUID()}@test.com")
        val shoppingRepo = ShoppingRepository(firestore, auth)

        val addResult = withTimeout(TIMEOUT_MS) {
            shoppingRepo.addItem(house.copy(memberUids = listOf(outsiderUid)), "chleb", "Intruz")
        }
        assertTrue(addResult.isFailure)
        val fsEx = addResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)

        val renameResult = withTimeout(TIMEOUT_MS) {
            householdRepo.updateIdentitySettings(house, "Przejęty Dom", "Intruz")
        }
        assertTrue(renameResult.isFailure)
        val renameEx = renameResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(renameEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, renameEx?.code)
    }

    @Test
    fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val householdRepo = HouseholdRepository(firestore, auth)

        val thrown = runCatching {
            withTimeout(TIMEOUT_MS) {
                householdRepo.observeUserHouseholds("some_uid_123").first()
            }
        }.exceptionOrNull()

        assertNotNull(thrown)
        val fsEx = thrown?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
    }
}
