package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ShoppingAisle(val key: String, val displayNamePl: String) {
    VEG_FRUIT("VEG_FRUIT", "Warzywa i owoce"),
    BAKERY("BAKERY", "Pieczywo"),
    DAIRY("DAIRY", "Nabiał"),
    MEAT_FISH("MEAT_FISH", "Mięso i ryby"),
    DRY_GOODS("DRY_GOODS", "Produkty suche"),
    HOME_CHEM("HOME_CHEM", "Chemia i dom"),
    OTHER("OTHER", "Inne");

    companion object {
        val DEFAULT_ORDER: List<String> = entries.map { it.key }

        fun fromKey(key: String?): ShoppingAisle {
            return entries.firstOrNull { it.key == key } ?: OTHER
        }
    }
}

@Immutable
@IgnoreExtraProperties
data class MemberInfo(
    val displayName: String = "",
    val photoUrl: String? = null,
    val weight: Double = 1.0
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "displayName" to displayName.ifBlank { "Domownik" }.take(60),
            "weight" to weight.coerceIn(0.1, 100.0)
        )
        if (!photoUrl.isNullOrBlank()) {
            map["photoUrl"] = photoUrl.take(500)
        }
        return map
    }
}

@Immutable
@IgnoreExtraProperties
data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val members: Map<String, MemberInfo> = emptyMap(),
    val aisleOrder: List<String> = ShoppingAisle.DEFAULT_ORDER,
    val settlementCycle: Long = 1L,
    val monthlyBudgetGrosze: Long? = null,
    val categoryBudgetsGrosze: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun orderedAisles(): List<ShoppingAisle> {
        val normalized = aisleOrder.mapNotNull { key ->
            ShoppingAisle.entries.firstOrNull { it.key == key }
        }.distinct()
        val missing = ShoppingAisle.entries.filter { it !in normalized }
        return normalized + missing
    }

    fun memberName(uid: String): String {
        return members[uid]?.displayName?.takeIf { it.isNotBlank() } ?: "Domownik"
    }
}

@IgnoreExtraProperties
data class HouseholdInvite(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val householdId: String = "",
    val householdName: String = "",
    val ownerId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
