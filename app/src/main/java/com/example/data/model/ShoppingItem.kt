package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Immutable
@IgnoreExtraProperties
data class ShoppingItem(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val quantityText: String = "",
    val category: String = ShoppingAisle.OTHER.key,
    @get:PropertyName("isPurchased")
    @field:PropertyName("isPurchased")
    val isPurchased: Boolean = false,
    val addedByUid: String = "",
    val addedByName: String = "",
    val purchasedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun aisle(): ShoppingAisle = ShoppingAisle.fromKey(category)
}
