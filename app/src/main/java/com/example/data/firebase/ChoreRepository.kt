package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ChoreIconOption
import com.example.data.model.ChoreResetMode
import com.example.data.model.ChoreTile
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

class ChoreRepository(
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

    fun observeChores(householdId: String, uid: String): Flow<List<ChoreTile>> {
        return db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_CHORES)
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChoreTile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun toggleCompletion(
        householdId: String,
        choreId: String,
        completedByName: String,
        markDone: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_CHORES)
            .document(choreId)

        val updateMap = if (markDone) {
            mapOf(
                "lastCompletedAt" to FieldValue.serverTimestamp(),
                "lastCompletedByUid" to uid,
                "lastCompletedByName" to completedByName.trim().ifBlank { "Domownik" }.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "lastCompletedAt" to FieldValue.delete(),
                "lastCompletedByUid" to FieldValue.delete(),
                "lastCompletedByName" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        try {
            docRef.update(updateMap).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun addChore(
        household: Household,
        title: String,
        icon: ChoreIconOption,
        resetMode: ChoreResetMode,
        resetHours: List<Long>,
        isPinnedToWidget: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanTitle = title.trim().take(60)
        require(cleanTitle.isNotBlank()) { "Podaj nazwę obowiązku." }

        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(household.id)
            .collection(HouseholdRepository.COLLECTION_CHORES)
            .document()

        val payload = mapOf(
            "householdId" to household.id,
            "memberUids" to household.memberUids.ifEmpty { listOf(uid) },
            "title" to cleanTitle,
            "iconKey" to icon.key,
            "isPinnedToWidget" to isPinnedToWidget,
            "resetMode" to resetMode.key,
            "resetHours" to resetHours.map { it.coerceIn(0L, 23L) }.distinct().sorted().take(6),
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

    suspend fun updateChoreConfig(
        householdId: String,
        choreId: String,
        title: String,
        icon: ChoreIconOption,
        resetMode: ChoreResetMode,
        resetHours: List<Long>,
        isPinnedToWidget: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanTitle = title.trim().take(60)
        require(cleanTitle.isNotBlank()) { "Podaj nazwę obowiązku." }

        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_CHORES)
            .document(choreId)

        try {
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "iconKey" to icon.key,
                    "isPinnedToWidget" to isPinnedToWidget,
                    "resetMode" to resetMode.key,
                    "resetHours" to resetHours.map { it.coerceIn(0L, 23L) }.distinct().sorted().take(6),
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

    suspend fun updatePinnedChores(
        householdId: String,
        chores: List<ChoreTile>,
        pinnedIds: Set<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val targetPinned = pinnedIds.take(2).toSet()
        val colRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_CHORES)
        val changed = chores.filter { (it.id in targetPinned) != it.isPinnedToWidget }
        if (changed.isEmpty()) return@runCatching

        val batch = db.batch()
        changed.forEach { chore ->
            batch.update(
                colRef.document(chore.id),
                mapOf(
                    "isPinnedToWidget" to (chore.id in targetPinned),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, colRef.path, auth)
            throw e
        }
    }

    suspend fun deleteChore(householdId: String, choreId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS)
            .document(householdId)
            .collection(HouseholdRepository.COLLECTION_CHORES)
            .document(choreId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }
}
