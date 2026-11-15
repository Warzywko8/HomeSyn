package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.ChoreIconOption
import com.example.data.model.ChoreResetMode
import com.example.data.model.Household
import com.example.data.model.HouseholdInvite
import com.example.data.model.MemberInfo
import com.example.data.model.ShoppingAisle
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class HouseholdRepository(
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
            ?: throw IllegalStateException("Użytkownik musi być zalogowany, aby korzystać z domu.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return db.collection(COLLECTION_USERS)
            .document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        photoUrl: String?
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection(COLLECTION_USERS).document(uid)
        val snap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            throw e
        }

        val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (existing != null) {
            return@runCatching existing
        }

        val cleanName = displayName.trim().ifBlank { "Domownik" }.take(60)
        val profile = UserProfile(
            id = uid,
            userId = uid,
            displayName = cleanName,
            photoUrl = photoUrl?.take(500)
        )
        try {
            docRef.set(profile.toCreateMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
        profile
    }

    fun observeUserHouseholds(userId: String): Flow<List<Household>> {
        return db.collection(COLLECTION_HOUSEHOLDS)
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Household::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun createHousehold(
        householdName: String,
        ownerDisplayName: String,
        ownerPhotoUrl: String?
    ): Result<Household> = runCatching {
        val uid = requireUserId()
        val cleanHouseName = householdName.trim().ifBlank { "Nasz Dom" }.take(60)
        val cleanOwnerName = ownerDisplayName.trim().ifBlank { "Domownik" }.take(60)
        val inviteCode = generateInviteCode()

        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document()
        val householdId = houseRef.id
        val inviteRef = db.collection(COLLECTION_INVITES).document(inviteCode)
        val userRef = db.collection(COLLECTION_USERS).document(uid)

        val userSnap = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path, auth)
            throw e
        }

        val memberMap = MemberInfo(
            displayName = cleanOwnerName,
            photoUrl = ownerPhotoUrl?.take(500),
            weight = 1.0
        ).toMap()

        val housePayload = mapOf(
            "name" to cleanHouseName,
            "inviteCode" to inviteCode,
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "members" to mapOf(uid to memberMap),
            "aisleOrder" to ShoppingAisle.DEFAULT_ORDER,
            "settlementCycle" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val invitePayload = mapOf(
            "inviteCode" to inviteCode,
            "householdId" to householdId,
            "householdName" to cleanHouseName,
            "ownerId" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(houseRef, housePayload)
        batch.set(inviteRef, invitePayload)

        // Seed 4 default micro-chore tiles (first 2 pinned to widget)
        defaultChores().forEachIndexed { idx, template ->
            val choreRef = houseRef.collection(COLLECTION_CHORES).document()
            batch.set(
                choreRef,
                mapOf(
                    "householdId" to householdId,
                    "memberUids" to listOf(uid),
                    "title" to template.title,
                    "iconKey" to template.icon.key,
                    "isPinnedToWidget" to (idx < 2),
                    "resetMode" to template.resetMode.key,
                    "resetHours" to template.resetHours,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        if (userSnap.exists()) {
            batch.update(
                userRef,
                mapOf(
                    "activeHouseholdId" to householdId,
                    "displayName" to cleanOwnerName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            val userPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to cleanOwnerName,
                "activeHouseholdId" to householdId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!ownerPhotoUrl.isNullOrBlank()) {
                userPayload["photoUrl"] = ownerPhotoUrl.take(500)
            }
            batch.set(userRef, userPayload)
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, houseRef.path, auth)
            throw e
        }

        Household(
            id = householdId,
            name = cleanHouseName,
            inviteCode = inviteCode,
            ownerId = uid,
            memberUids = listOf(uid),
            members = mapOf(uid to MemberInfo(cleanOwnerName, ownerPhotoUrl, 1.0)),
            aisleOrder = ShoppingAisle.DEFAULT_ORDER,
            settlementCycle = 1L
        )
    }

    suspend fun joinHouseholdByCode(
        rawInviteCode: String,
        memberDisplayName: String,
        memberPhotoUrl: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val code = rawInviteCode.trim().uppercase()
        require(INVITE_CODE_REGEX.matches(code)) {
            "Kod zaproszenia musi składać się z 6 znaków (litery i cyfry 2–9)."
        }

        val inviteRef = db.collection(COLLECTION_INVITES).document(code)
        val inviteSnap = try {
            inviteRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, inviteRef.path, auth)
            throw e
        }

        val invite = inviteSnap.toObject(HouseholdInvite::class.java)
            ?: throw IllegalArgumentException("Nie znaleziono domu dla kodu $code.")

        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(invite.householdId)
        val userRef = db.collection(COLLECTION_USERS).document(uid)
        val userSnap = userRef.get().await()
        val cleanName = memberDisplayName.trim().ifBlank { "Domownik" }.take(60)
        val memberMap = MemberInfo(
            displayName = cleanName,
            photoUrl = memberPhotoUrl?.take(500),
            weight = 1.0
        ).toMap()

        try {
            houseRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "members.$uid" to memberMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "activeHouseholdId" to invite.householdId,
                        "displayName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val userPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "activeHouseholdId" to invite.householdId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!memberPhotoUrl.isNullOrBlank()) {
                    userPayload["photoUrl"] = memberPhotoUrl.take(500)
                }
                userRef.set(userPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, houseRef.path, auth)
            throw e
        }

        invite.householdId
    }

    suspend fun setActiveHousehold(householdId: String?): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection(COLLECTION_USERS).document(uid)
        val updateValue: Any = if (householdId.isNullOrBlank()) FieldValue.delete() else householdId
        try {
            userRef.update(
                mapOf(
                    "activeHouseholdId" to updateValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path, auth)
            throw e
        }
    }

    suspend fun updateAisleOrder(householdId: String, aisleOrder: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(householdId)
        try {
            houseRef.update(
                mapOf(
                    "aisleOrder" to aisleOrder,
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

    suspend fun updateMemberWeights(
        household: Household,
        updatedWeights: Map<String, Double>
    ): Result<Unit> = runCatching {
        requireUserId()
        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(household.id)
        val updatedMembersMap = household.members.mapValues { (uid, info) ->
            val newWeight = updatedWeights[uid] ?: info.weight
            info.copy(weight = newWeight.coerceIn(0.1, 100.0)).toMap()
        }
        try {
            houseRef.update(
                mapOf(
                    "members" to updatedMembersMap,
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

    suspend fun updateMonthlyBudget(
        householdId: String,
        monthlyBudgetGrosze: Long?,
        categoryBudgetsGrosze: Map<String, Long>
    ): Result<Unit> = runCatching {
        requireUserId()
        val houseRef = db.collection(HouseholdRepository.COLLECTION_HOUSEHOLDS).document(householdId)
        val cleanCategories = categoryBudgetsGrosze
            .filterValues { it > 0L && it <= 100_000_000L }
            .toMap()
        val budgetFieldValue: Any = if (monthlyBudgetGrosze != null && monthlyBudgetGrosze > 0L) {
            monthlyBudgetGrosze.coerceAtMost(100_000_000L)
        } else {
            FieldValue.delete()
        }
        try {
            houseRef.update(
                mapOf(
                    "monthlyBudgetGrosze" to budgetFieldValue,
                    "categoryBudgetsGrosze" to cleanCategories,
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

    suspend fun updateIdentitySettings(
        household: Household,
        householdName: String,
        memberDisplayName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanHouseName = householdName.trim().take(60)
        val cleanMemberName = memberDisplayName.trim().take(60)
        require(cleanHouseName.isNotBlank()) { "Nazwa domu nie może być pusta." }
        require(cleanMemberName.isNotBlank()) { "Twoje imię nie może być puste." }

        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(household.id)
        val userRef = db.collection(COLLECTION_USERS).document(uid)
        val userSnap = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path, auth)
            throw e
        }

        val currentMember = household.members[uid] ?: MemberInfo(displayName = cleanMemberName, weight = 1.0)
        val updatedMemberMap = currentMember.copy(displayName = cleanMemberName).toMap()

        val batch = db.batch()
        batch.update(
            houseRef,
            mapOf(
                "name" to cleanHouseName,
                "members.$uid" to updatedMemberMap,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (userSnap.exists()) {
            batch.update(
                userRef,
                mapOf(
                    "displayName" to cleanMemberName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                userRef,
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = cleanMemberName,
                    photoUrl = currentMember.photoUrl,
                    activeHouseholdId = household.id
                ).toCreateMap()
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, houseRef.path, auth)
            throw e
        }
    }

    suspend fun syncMemberUidsAcrossSubcollections(household: Household) {
        val uid = auth.currentUser?.uid ?: return
        if (uid !in household.memberUids) return
        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(household.id)
        val targetMembers = household.memberUids

        listOf(COLLECTION_SHOPPING, COLLECTION_CHORES, COLLECTION_EXPENSES).forEach { subName ->
            try {
                val snap = houseRef.collection(subName)
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                val staleDocs = snap.documents.filter { doc ->
                    val current = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    current.toSet() != targetMembers.toSet()
                }
                if (staleDocs.isNotEmpty()) {
                    val batch = db.batch()
                    staleDocs.forEach { doc ->
                        batch.update(
                            doc.reference,
                            mapOf(
                                "memberUids" to targetMembers,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "${houseRef.path}/$subName", auth)
            }
        }
    }

    suspend fun leaveHousehold(household: Household): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid != household.ownerId) { "Właściciel nie może opuścić domu bez jego usunięcia." }
        val houseRef = db.collection(COLLECTION_HOUSEHOLDS).document(household.id)
        val userRef = db.collection(COLLECTION_USERS).document(uid)
        val remainingMembers = household.members.filterKeys { it != uid }.mapValues { it.value.toMap() }
        try {
            houseRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "members" to remainingMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            userRef.set(
                mapOf(
                    "activeHouseholdId" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, houseRef.path, auth)
            throw e
        }
    }

    private data class DefaultChoreTemplate(
        val title: String,
        val icon: ChoreIconOption,
        val resetMode: ChoreResetMode,
        val resetHours: List<Long> = emptyList()
    )

    private fun defaultChores() = listOf(
        DefaultChoreTemplate("Pies nakarmiony", ChoreIconOption.PETS, ChoreResetMode.SCHEDULED_HOURS, listOf(8L, 18L)),
        DefaultChoreTemplate("Zmywarka włączona", ChoreIconOption.KITCHEN, ChoreResetMode.DAILY_MIDNIGHT),
        DefaultChoreTemplate("Śmieci wyniesione", ChoreIconOption.CLEANING, ChoreResetMode.DAILY_MIDNIGHT),
        DefaultChoreTemplate("Kwiaty podlane", ChoreIconOption.PLANTS, ChoreResetMode.NONE)
    )

    private fun generateInviteCode(): String {
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) {
                append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)])
            }
        }
    }

    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_HOUSEHOLDS = "households"
        const val COLLECTION_INVITES = "householdInvites"
        const val COLLECTION_SHOPPING = "shoppingItems"
        const val COLLECTION_CHORES = "choreTiles"
        const val COLLECTION_EXPENSES = "expenses"
        private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val INVITE_CODE_REGEX = Regex("^[A-Z2-9]{6}$")
    }
}
