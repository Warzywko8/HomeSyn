package com.example.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "domsync_app_prefs")

enum class AppThemeMode(val key: String, val labelPl: String) {
    SYSTEM("SYSTEM", "Systemowy"),
    LIGHT("LIGHT", "Jasny"),
    DARK("DARK", "Ciemny");

    companion object {
        fun fromKey(key: String?): AppThemeMode {
            return entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }
}

enum class StartTabPreference(val key: String, val labelPl: String) {
    SHOPPING("SHOPPING", "Zakupy"),
    CHORES("CHORES", "Obowiązki"),
    EXPENSES("EXPENSES", "Rozliczenia");

    companion object {
        fun fromKey(key: String?): StartTabPreference {
            return entries.firstOrNull { it.key == key } ?: SHOPPING
        }
    }
}

@Immutable
data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val startTab: StartTabPreference = StartTabPreference.SHOPPING,
    val useCustomWeights: Boolean = false
)

class AppPreferencesStore(private val context: Context) {

    val preferencesFlow: Flow<AppPreferences> = context.appPrefsDataStore.data.map { prefs ->
        AppPreferences(
            themeMode = AppThemeMode.fromKey(prefs[KEY_THEME_MODE]),
            useDynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            startTab = StartTabPreference.fromKey(prefs[KEY_START_TAB]),
            useCustomWeights = prefs[KEY_CUSTOM_WEIGHTS] ?: false
        )
    }

    suspend fun getPreferences(): AppPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setStartTab(tab: StartTabPreference) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[KEY_START_TAB] = tab.key
        }
    }

    suspend fun setUseCustomWeights(enabled: Boolean) {
        context.appPrefsDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_WEIGHTS] = enabled
        }
    }

    suspend fun getSyncedMembersSignature(householdId: String): String? {
        if (householdId.isBlank()) return null
        val key = stringPreferencesKey("synced_members_$householdId")
        return context.appPrefsDataStore.data.first()[key]
    }

    suspend fun setSyncedMembersSignature(householdId: String, signature: String) {
        if (householdId.isBlank()) return
        val key = stringPreferencesKey("synced_members_$householdId")
        context.appPrefsDataStore.edit { prefs ->
            prefs[key] = signature
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val KEY_START_TAB = stringPreferencesKey("start_tab")
        val KEY_CUSTOM_WEIGHTS = booleanPreferencesKey("use_custom_weights")
    }
}
