package com.example.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "domsync_widget_cache")

@Serializable
data class PinnedChoreSnapshot(
    val id: String,
    val title: String,
    val iconKey: String,
    val isDone: Boolean,
    val statusText: String
)

@Serializable
data class WidgetSnapshot(
    val householdId: String = "",
    val householdName: String = "HouseSync",
    val remainingShoppingCount: Int = 0,
    val pinnedChores: List<PinnedChoreSnapshot> = emptyList(),
    val currentUserDisplayName: String = "Domownik"
)

class WidgetSnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeSnapshot(): Flow<WidgetSnapshot> {
        return context.widgetDataStore.data.map { prefs ->
            val raw = prefs[KEY_SNAPSHOT_JSON]
            if (raw.isNullOrBlank()) {
                WidgetSnapshot()
            } else {
                runCatching { json.decodeFromString<WidgetSnapshot>(raw) }.getOrDefault(WidgetSnapshot())
            }
        }
    }

    suspend fun getSnapshot(): WidgetSnapshot {
        return observeSnapshot().first()
    }

    suspend fun saveSnapshot(snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_SNAPSHOT_JSON] = json.encodeToString(snapshot)
        }
    }

    suspend fun clear() {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(KEY_SNAPSHOT_JSON)
        }
    }

    companion object {
        private val KEY_SNAPSHOT_JSON = stringPreferencesKey("widget_snapshot_json")
    }
}
