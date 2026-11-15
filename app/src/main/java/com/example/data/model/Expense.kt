package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ExpenseCategory(val key: String, val displayNamePl: String, val shortNamePl: String) {
    GROCERIES("GROCERIES", "Zakupy spożywcze", "Zakupy"),
    BILLS("BILLS", "Dom i rachunki", "Rachunki"),
    FOOD_OUT("FOOD_OUT", "Jedzenie na mieście", "Jedzenie"),
    TRANSPORT("TRANSPORT", "Transport", "Transport"),
    HOME_HEALTH("HOME_HEALTH", "Zdrowie i chemia", "Zdrowie"),
    OTHER("OTHER", "Rozrywka i inne", "Inne");

    companion object {
        fun fromKey(key: String?): ExpenseCategory {
            return entries.firstOrNull { it.key == key } ?: OTHER
        }
    }
}

@Immutable
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val amountGrosze: Long = 0L,
    val description: String = "",
    val paidByUid: String = "",
    val paidByName: String = "",
    val cycleNumber: Long = 1L,
    val category: String = ExpenseCategory.OTHER.key,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): ExpenseCategory = ExpenseCategory.fromKey(category)
}

@Immutable
data class SettlementTransfer(
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val amountGrosze: Long
)
