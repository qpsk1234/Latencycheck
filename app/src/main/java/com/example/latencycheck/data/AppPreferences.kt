package com.example.latencycheck.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val TARGET_URL_KEY = stringPreferencesKey("target_url")
        val INTERVAL_SECONDS_KEY = longPreferencesKey("interval_seconds")
        val IS_RUNNING_KEY = booleanPreferencesKey("is_running")
        val MAX_LATENCY_THRESHOLD_KEY = longPreferencesKey("max_latency_threshold")
        val COLOR_CONFIG_JSON_KEY = stringPreferencesKey("color_config_json")
        val DEBUG_ENABLED_KEY = booleanPreferencesKey("debug_enabled")
        val DISPLAY_COLUMNS_KEY = stringSetPreferencesKey("display_columns")
        val MAP_COLOR_MODE_KEY = stringPreferencesKey("map_color_mode")
        
        val DEFAULT_DISPLAY_COLUMNS = setOf("Time", "Latency", "Type", "Band", "Signal", "Location")

        const val DEFAULT_COLOR_CONFIG = """
            [
                {"threshold": 100, "color": "#4CAF50"},
                {"threshold": 200, "color": "#8BC34A"},
                {"threshold": 300, "color": "#CDDC39"},
                {"threshold": 400, "color": "#FFEB3B"},
                {"threshold": 500, "color": "#FFC107"},
                {"threshold": 600, "color": "#FF9800"},
                {"threshold": 700, "color": "#FF5722"},
                {"threshold": 800, "color": "#F44336"},
                {"threshold": 900, "color": "#D32F2F"},
                {"threshold": 1000, "color": "#B71C1C"}
            ]
        """
    }

    val targetUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TARGET_URL_KEY] ?: "https://www.google.com"
    }

    val intervalSeconds: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[INTERVAL_SECONDS_KEY] ?: 5L
    }

    val isRunning: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_RUNNING_KEY] ?: false
    }

    val maxLatencyThreshold: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[MAX_LATENCY_THRESHOLD_KEY] ?: 1000L
    }

    val colorConfigJson: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[COLOR_CONFIG_JSON_KEY] ?: DEFAULT_COLOR_CONFIG
    }

    val debugEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEBUG_ENABLED_KEY] ?: false
    }

    val displayColumns: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[DISPLAY_COLUMNS_KEY] ?: DEFAULT_DISPLAY_COLUMNS
    }

    val mapColorMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MAP_COLOR_MODE_KEY] ?: "latency"
    }

    suspend fun setTargetUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[TARGET_URL_KEY] = url
        }
    }

    suspend fun setIntervalSeconds(seconds: Long) {
        context.dataStore.edit { preferences ->
            preferences[INTERVAL_SECONDS_KEY] = seconds
        }
    }

    suspend fun setIsRunning(isRunning: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_RUNNING_KEY] = isRunning
        }
    }

    suspend fun setMaxLatencyThreshold(threshold: Long) {
        context.dataStore.edit { preferences ->
            preferences[MAX_LATENCY_THRESHOLD_KEY] = threshold
        }
    }

    suspend fun setColorConfigJson(json: String) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_CONFIG_JSON_KEY] = json
        }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEBUG_ENABLED_KEY] = enabled
        }
    }

    suspend fun setDisplayColumns(columns: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_COLUMNS_KEY] = columns
        }
    }

    suspend fun setMapColorMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[MAP_COLOR_MODE_KEY] = mode
        }
    }
}
