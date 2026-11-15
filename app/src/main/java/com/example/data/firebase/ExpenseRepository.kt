package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Expense
import com.example.data.model.Household
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ExpenseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Użytkownik musi być zalogowany.")
    }

    fun observeExpenses(householdId: String, uid: String): Flow<List<Expense>> {
        return db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_EXPENSES)
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun addExpense(
        household: Household,
        amountGrosze: Long,
        description: String,
        paidByUid: String,
        paidByName: String,
        category: com.example.data.model.ExpenseCategory = com.example.data.model.ExpenseCategory.OTHER
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(amountGrosze in 1L..100_000_000L) { "Kwota musi być większa od 0 zł." }
        val cleanDesc = description.trim().take(100)
        require(cleanDesc.isNotBlank()) { "Podaj krótki opis wydatku." }

        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(household.id)
            .collection(HouseholdRepository.COLLECTION_EXPENSES)
            .document()

        val payload = mapOf(
            "householdId" to household.id,
            "memberUids" to household.memberUids.ifEmpty { listOf(uid) },
            "amountGrosze" to amountGrosze,
            "description" to cleanDesc,
            "paidByUid" to paidByUid,
            "paidByName" to paidByName.trim().ifBlank { "Domownik" }.take(60),
            "cycleNumber" to household.settlementCycle.coerceAtLeast(1L),
            "category" to category.key,
            "createdByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
        docRef.id
    }

    suspend fun deleteExpense(householdId: String, expenseId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_EXPENSES)
            .document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun settleCycle(householdId: String): Result<Unit> = runCatching {
        requireUserId()
        val houseRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS).document(householdId)
        try {
            houseRef.update(
                mapOf(
                    "settlementCycle" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, houseRef.path, auth)
            throw e
        }
    }
}
