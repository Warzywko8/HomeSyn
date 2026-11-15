package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class ChoreResetMode(val key: String, val labelPl: String) {
    NONE("NONE", "Bez auto-resetu"),
    DAILY_MIDNIGHT("DAILY_MIDNIGHT", "Codziennie o 00:00"),
    SCHEDULED_HOURS("SCHEDULED_HOURS", "O wybranych godzinach");

    companion object {
        fun fromKey(key: String?): ChoreResetMode {
            return entries.firstOrNull { it.key == key } ?: DAILY_MIDNIGHT
        }
    }
}

enum class ChoreIconOption(val key: String, val labelPl: String) {
    KITCHEN("kitchen", "Kuchnia"),
    CLEANING("cleaning", "Sprzątanie"),
    LAUNDRY("laundry", "Pranie"),
    PETS("pets", "Zwierzęta"),
    PLANTS("plants", "Rośliny"),
    HOME("home", "Dom"),
    OTHER("other", "Inne");

    companion object {
        fun fromKey(key: String?): ChoreIconOption {
            return when (key) {
                "kitchen", "dishwasher" -> KITCHEN
                "cleaning", "vacuum", "trash" -> CLEANING
                "laundry" -> LAUNDRY
                "pets" -> PETS
                "plants" -> PLANTS
                "home", "lock" -> HOME
                "other", "sparkle" -> OTHER
                else -> entries.firstOrNull { it.key == key } ?: OTHER
            }
        }
    }
}

@Immutable
@IgnoreExtraProperties
data class ChoreTile(
    @DocumentId val id: String = "",
    val householdId: String = "",
    val memberUids: List<String> = emptyList(),
    val title: String = "",
    val iconKey: String = ChoreIconOption.OTHER.key,
    @get:PropertyName("isPinnedToWidget")
    @field:PropertyName("isPinnedToWidget")
    val isPinnedToWidget: Boolean = false,
    val resetMode: String = ChoreResetMode.DAILY_MIDNIGHT.key,
    val resetHours: List<Long> = emptyList(),
    val lastCompletedAt: Timestamp? = null,
    val lastCompletedByUid: String? = null,
    val lastCompletedByName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun mode(): ChoreResetMode = ChoreResetMode.fromKey(resetMode)
    fun icon(): ChoreIconOption = ChoreIconOption.fromKey(iconKey)
}
