package com.betteranki.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.betteranki.data.model.DeckSettings
import com.betteranki.data.model.StudySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {
    
    private object PreferenceKeys {
        val CURRENT_DECK_ID = longPreferencesKey("current_deck_id")
        val DAILY_NEW_CARDS = intPreferencesKey("daily_new_cards")
        val DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val AGAIN_INTERVAL = intPreferencesKey("again_interval")
        val HARD_INTERVAL = intPreferencesKey("hard_interval")
        val GOOD_INTERVAL = intPreferencesKey("good_interval")
        val EASY_INTERVAL = intPreferencesKey("easy_interval")
        val EASY_THRESHOLD = intPreferencesKey("easy_threshold")
        val GOOD_THRESHOLD = intPreferencesKey("good_threshold")
        val HARD_THRESHOLD = intPreferencesKey("hard_threshold")
        // Debug: simulated days offset
        val DEBUG_DAY_OFFSET = intPreferencesKey("debug_day_offset")
        // Leniency mode settings
        val LENIENCY_MODE_ENABLED = booleanPreferencesKey("leniency_mode_enabled")
        val MAX_NEW_CARDS_AFTER_SKIP = intPreferencesKey("max_new_cards_after_skip")
        val MAX_REVIEW_CARDS = intPreferencesKey("max_review_cards")
        val DAILY_REVIEWS_ADDABLE = intPreferencesKey("daily_reviews_addable")
        // Notification settings
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SUPPRESS_ENABLE_NOTIFICATIONS_PROMPT = booleanPreferencesKey("suppress_enable_notifications_prompt")
        val NOTIFICATION_DAYS = stringPreferencesKey("notification_days") // comma-separated
        val NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
        val NOTIFICATION_MINUTE = intPreferencesKey("notification_minute")
        // Decay mode settings
        val DECAY_MODE_ENABLED = booleanPreferencesKey("decay_mode_enabled")
        val DECAY_START_DAYS = intPreferencesKey("decay_start_days")
        val DECAY_RATE_PER_DAY = intPreferencesKey("decay_rate_per_day")
        val DECAY_MIN_CARDS = intPreferencesKey("decay_min_cards")
        // OCR translation settings
        val OCR_SOURCE_LANGUAGE = stringPreferencesKey("ocr_source_language")
        val OCR_TARGET_LANGUAGE = stringPreferencesKey("ocr_target_language")
    }
    
    // Debug day offset for testing
    val debugDayOffset: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
    }
    
    suspend fun incrementDebugDay() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
            preferences[PreferenceKeys.DEBUG_DAY_OFFSET] = current + 1
        }
    }
    
    suspend fun resetDebugDay() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEBUG_DAY_OFFSET] = 0
        }
    }
    
    val currentSettings: Flow<StudySettings> = context.dataStore.data.map { preferences ->
        val notificationDaysStr = preferences[PreferenceKeys.NOTIFICATION_DAYS] ?: "1,2,3,4,5,6,7"
        val notificationDays = notificationDaysStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        
        StudySettings(
            deckId = preferences[PreferenceKeys.CURRENT_DECK_ID] ?: -1L,
            dailyNewCards = preferences[PreferenceKeys.DAILY_NEW_CARDS] ?: 20,
            dailyReviewLimit = preferences[PreferenceKeys.DAILY_REVIEW_LIMIT] ?: 100,
            againIntervalMinutes = preferences[PreferenceKeys.AGAIN_INTERVAL] ?: 1,
            hardIntervalMinutes = preferences[PreferenceKeys.HARD_INTERVAL] ?: 5,
            goodIntervalMinutes = preferences[PreferenceKeys.GOOD_INTERVAL] ?: 1440,
            easyIntervalMinutes = preferences[PreferenceKeys.EASY_INTERVAL] ?: 5760,
            easyThresholdSeconds = preferences[PreferenceKeys.EASY_THRESHOLD] ?: 60,
            goodThresholdSeconds = preferences[PreferenceKeys.GOOD_THRESHOLD] ?: 300,
            hardThresholdSeconds = preferences[PreferenceKeys.HARD_THRESHOLD] ?: 86400,
            leniencyModeEnabled = preferences[PreferenceKeys.LENIENCY_MODE_ENABLED] ?: true,
            maxNewCardsAfterSkip = preferences[PreferenceKeys.MAX_NEW_CARDS_AFTER_SKIP] ?: 30,
            maxReviewCards = preferences[PreferenceKeys.MAX_REVIEW_CARDS] ?: 50,
            dailyReviewsAddable = preferences[PreferenceKeys.DAILY_REVIEWS_ADDABLE] ?: 20,
            notificationsEnabled = preferences[PreferenceKeys.NOTIFICATIONS_ENABLED] ?: false,
            suppressEnableNotificationsPrompt = preferences[PreferenceKeys.SUPPRESS_ENABLE_NOTIFICATIONS_PROMPT] ?: false,
            notificationDays = notificationDays,
            notificationHour = preferences[PreferenceKeys.NOTIFICATION_HOUR] ?: 9,
            notificationMinute = preferences[PreferenceKeys.NOTIFICATION_MINUTE] ?: 0,
            decayModeEnabled = preferences[PreferenceKeys.DECAY_MODE_ENABLED] ?: true,
            decayStartDays = preferences[PreferenceKeys.DECAY_START_DAYS] ?: 5,
            decayRatePerDay = preferences[PreferenceKeys.DECAY_RATE_PER_DAY] ?: 2,
            decayMinCards = preferences[PreferenceKeys.DECAY_MIN_CARDS] ?: 10,
            ocrSourceLanguage = preferences[PreferenceKeys.OCR_SOURCE_LANGUAGE] ?: "ja",
            ocrTargetLanguage = preferences[PreferenceKeys.OCR_TARGET_LANGUAGE] ?: "en"
        )
    }
    
    suspend fun saveSettings(settings: StudySettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.CURRENT_DECK_ID] = settings.deckId
            preferences[PreferenceKeys.DAILY_NEW_CARDS] = settings.dailyNewCards
            preferences[PreferenceKeys.DAILY_REVIEW_LIMIT] = settings.dailyReviewLimit
            preferences[PreferenceKeys.AGAIN_INTERVAL] = settings.againIntervalMinutes
            preferences[PreferenceKeys.HARD_INTERVAL] = settings.hardIntervalMinutes
            preferences[PreferenceKeys.GOOD_INTERVAL] = settings.goodIntervalMinutes
            preferences[PreferenceKeys.EASY_INTERVAL] = settings.easyIntervalMinutes
            preferences[PreferenceKeys.EASY_THRESHOLD] = settings.easyThresholdSeconds
            preferences[PreferenceKeys.GOOD_THRESHOLD] = settings.goodThresholdSeconds
            preferences[PreferenceKeys.HARD_THRESHOLD] = settings.hardThresholdSeconds
            preferences[PreferenceKeys.LENIENCY_MODE_ENABLED] = settings.leniencyModeEnabled
            preferences[PreferenceKeys.MAX_NEW_CARDS_AFTER_SKIP] = settings.maxNewCardsAfterSkip
            preferences[PreferenceKeys.MAX_REVIEW_CARDS] = settings.maxReviewCards
            preferences[PreferenceKeys.DAILY_REVIEWS_ADDABLE] = settings.dailyReviewsAddable
            preferences[PreferenceKeys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            preferences[PreferenceKeys.SUPPRESS_ENABLE_NOTIFICATIONS_PROMPT] = settings.suppressEnableNotificationsPrompt
            preferences[PreferenceKeys.NOTIFICATION_DAYS] = settings.notificationDays.joinToString(",")
            preferences[PreferenceKeys.NOTIFICATION_HOUR] = settings.notificationHour
            preferences[PreferenceKeys.NOTIFICATION_MINUTE] = settings.notificationMinute
            preferences[PreferenceKeys.DECAY_MODE_ENABLED] = settings.decayModeEnabled
            preferences[PreferenceKeys.DECAY_START_DAYS] = settings.decayStartDays
            preferences[PreferenceKeys.DECAY_RATE_PER_DAY] = settings.decayRatePerDay
            preferences[PreferenceKeys.DECAY_MIN_CARDS] = settings.decayMinCards
            preferences[PreferenceKeys.OCR_SOURCE_LANGUAGE] = settings.ocrSourceLanguage
            preferences[PreferenceKeys.OCR_TARGET_LANGUAGE] = settings.ocrTargetLanguage
        }
    }
    
    // Deck-specific settings (freeze, last studied date)
    fun getDeckSettings(deckId: Long): Flow<DeckSettings> = context.dataStore.data.map { preferences ->
        val frozenKey = booleanPreferencesKey("deck_${deckId}_frozen")
        val freezeUntilKey = longPreferencesKey("deck_${deckId}_freeze_until")
        val lastStudiedKey = longPreferencesKey("deck_${deckId}_last_studied")
        
        DeckSettings(
            deckId = deckId,
            isFrozen = preferences[frozenKey] ?: false,
            freezeUntilDate = preferences[freezeUntilKey],
            lastStudiedDate = preferences[lastStudiedKey]
        )
    }
    
    suspend fun saveDeckSettings(settings: DeckSettings) {
        context.dataStore.edit { preferences ->
            val frozenKey = booleanPreferencesKey("deck_${settings.deckId}_frozen")
            val freezeUntilKey = longPreferencesKey("deck_${settings.deckId}_freeze_until")
            val lastStudiedKey = longPreferencesKey("deck_${settings.deckId}_last_studied")
            
            preferences[frozenKey] = settings.isFrozen
            if (settings.freezeUntilDate != null) {
                preferences[freezeUntilKey] = settings.freezeUntilDate
            } else {
                preferences.remove(freezeUntilKey)
            }
            if (settings.lastStudiedDate != null) {
                preferences[lastStudiedKey] = settings.lastStudiedDate
            }
        }
    }
    
    suspend fun freezeDeck(deckId: Long, days: Int) {
        val freezeUntil = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
        context.dataStore.edit { preferences ->
            val frozenKey = booleanPreferencesKey("deck_${deckId}_frozen")
            val freezeUntilKey = longPreferencesKey("deck_${deckId}_freeze_until")
            preferences[frozenKey] = true
            preferences[freezeUntilKey] = freezeUntil
        }
    }
    
    suspend fun unfreezeDeck(deckId: Long) {
        context.dataStore.edit { preferences ->
            val frozenKey = booleanPreferencesKey("deck_${deckId}_frozen")
            val freezeUntilKey = longPreferencesKey("deck_${deckId}_freeze_until")
            preferences[frozenKey] = false
            preferences.remove(freezeUntilKey)
        }
    }
    
    suspend fun updateLastStudiedDate(deckId: Long) {
        context.dataStore.edit { preferences ->
            val lastStudiedKey = longPreferencesKey("deck_${deckId}_last_studied")
            preferences[lastStudiedKey] = System.currentTimeMillis()
        }
    }
    
    // Track new cards studied today per deck
    fun getNewCardsStudiedToday(deckId: Long): Flow<Int> = context.dataStore.data.map { preferences ->
        val dayOffset = preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
        val key = stringPreferencesKey("new_cards_studied_${deckId}_day_$dayOffset")
        preferences[key]?.toIntOrNull() ?: 0
    }
    
    suspend fun incrementNewCardsStudied(deckId: Long) {
        context.dataStore.edit { preferences ->
            val dayOffset = preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
            val key = stringPreferencesKey("new_cards_studied_${deckId}_day_$dayOffset")
            val current = preferences[key]?.toIntOrNull() ?: 0
            preferences[key] = (current + 1).toString()
        }
    }
    
    // Check if study session already done today
    fun hasStudiedToday(deckId: Long): Flow<Boolean> = context.dataStore.data.map { preferences ->
        val dayOffset = preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
        val key = stringPreferencesKey("studied_today_${deckId}_day_$dayOffset")
        preferences[key] == "true"
    }
    
    suspend fun markStudiedToday(deckId: Long) {
        context.dataStore.edit { preferences ->
            val dayOffset = preferences[PreferenceKeys.DEBUG_DAY_OFFSET] ?: 0
            val key = stringPreferencesKey("studied_today_${deckId}_day_$dayOffset")
            preferences[key] = "true"
        }
    }
    
    // OCR translation language settings
    suspend fun updateOcrSourceLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OCR_SOURCE_LANGUAGE] = language
        }
    }
    
    suspend fun updateOcrTargetLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.OCR_TARGET_LANGUAGE] = language
        }
    }
}
