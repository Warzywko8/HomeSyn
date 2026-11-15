package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Household
import com.example.data.model.ShoppingAisle
import com.example.data.model.ShoppingItem
import com.example.domain.PolishShoppingCategorizer
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

class ShoppingRepository(
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

    fun observeShoppingItems(householdId: String, uid: String): Flow<List<ShoppingItem>> {
        return db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ShoppingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    fun allocateItemId(householdId: String): String {
        return db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
            .document()
            .id
    }

    suspend fun addItem(
        household: Household,
        rawInput: String,
        addedByName: String,
        itemId: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val parsed = PolishShoppingCategorizer.parseAndCategorize(rawInput)
        require(parsed.name.isNotBlank()) { "Podaj nazwę produktu." }

        val colRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(household.id)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
        val docRef = if (itemId.isNullOrBlank()) colRef.document() else colRef.document(itemId)

        val payload = mapOf(
            "householdId" to household.id,
            "memberUids" to household.memberUids.ifEmpty { listOf(uid) },
            "name" to parsed.name,
            "quantityText" to parsed.quantityText,
            "category" to parsed.category.key,
            "isPurchased" to false,
            "addedByUid" to uid,
            "addedByName" to addedByName.trim().ifBlank { "Domownik" }.take(60),
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

    suspend fun togglePurchased(
        householdId: String,
        itemId: String,
        purchased: Boolean,
        overflowIdsToDelete: List<String> = emptyList()
    ): Result<Unit> = runCatching {
        requireUserId()
        val colRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
        val docRef = colRef.document(itemId)

        val updateMap = mapOf(
            "isPurchased" to purchased,
            "purchasedAt" to if (purchased) FieldValue.serverTimestamp() else FieldValue.delete(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val idsToPurge = if (purchased) {
            overflowIdsToDelete.filter { it.isNotBlank() && it != itemId }.distinct()
        } else {
            emptyList()
        }

        try {
            if (idsToPurge.isEmpty()) {
                docRef.update(updateMap).await()
            } else {
                val batch = db.batch()
                batch.update(docRef, updateMap)
                idsToPurge.forEach { oldId ->
                    batch.delete(colRef.document(oldId))
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun trimPurchasedOverflow(
        householdId: String,
        overflowIds: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanIds = overflowIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return@runCatching
        val colRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
        val batch = db.batch()
        cleanIds.forEach { oldId ->
            batch.delete(colRef.document(oldId))
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path, auth)
            throw e
        }
    }

    suspend fun updateCategory(
        householdId: String,
        itemId: String,
        category: ShoppingAisle
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
            .document(itemId)

        try {
            docRef.update(
                mapOf(
                    "category" to category.key,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteItem(householdId: String, itemId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
            .document(itemId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun clearPurchasedItems(householdId: String, purchasedItems: List<ShoppingItem>): Result<Unit> = runCatching {
        requireUserId()
        if (purchasedItems.isEmpty()) return@runCatching
        val colRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_SHOPPING)
        val batch = db.batch()
        purchasedItems.forEach { item ->
            if (item.id.isNotBlank()) {
                batch.delete(colRef.document(item.id))
            }
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path, auth)
            throw e
        }
    }
}
