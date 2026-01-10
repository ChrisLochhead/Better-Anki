package com.betteranki.data.model

data class StudySettings(
    val deckId: Long = -1L, // -1 means no specific deck selected
    val dailyNewCards: Int = 20,
    val dailyReviewLimit: Int = 100,
    val againIntervalMinutes: Int = 1,
    val hardIntervalMinutes: Int = 5,
    val goodIntervalMinutes: Int = 1440, // 1 day
    val easyIntervalMinutes: Int = 5760,  // 4 days
    // Response time thresholds (in seconds) for automatic difficulty calculation
    val easyThresholdSeconds: Int = 3,    // < 3 seconds = EASY (very confident)
    val goodThresholdSeconds: Int = 5,   // < 5 seconds = GOOD
    
    // Leniency mode settings - prevents users from getting buried in reviews
    val leniencyModeEnabled: Boolean = true,
    val maxNewCardsAfterSkip: Int = 30,    // Max new cards when days are skipped
    val maxReviewCards: Int = 50,           // Maximum review cards cap
    val dailyReviewsAddable: Int = 20,      // How many reviews can be added per day
    
    // Notification settings
    val notificationsEnabled: Boolean = false,
    val suppressEnableNotificationsPrompt: Boolean = false,
    val notificationDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1=Mon, 7=Sun, all days by default
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,
    
    // Decay mode settings - reduces cards after extended inactivity
    val decayModeEnabled: Boolean = true,
    val decayStartDays: Int = 5,        // Days of inactivity before decay starts
    val decayRatePerDay: Int = 2,       // How many cards to reduce per day after decay starts
    val decayMinCards: Int = 10,        // Minimum floor for both new and review cards
    
    // OCR translation settings
    val ocrSourceLanguage: String = "ja", // Default source language (Japanese)
    val ocrTargetLanguage: String = "en"  // Default target language (English)
)

// Per-deck settings stored separately (for freeze, etc.)
data class DeckSettings(
    val deckId: Long,
    val isFrozen: Boolean = false,
    val freezeUntilDate: Long? = null, // Timestamp when freeze ends
    val lastStudiedDate: Long? = null  // Track last study date for skipped day detection
)

data class SettingsPreset(
    val id: Long = 0,
    val name: String,
    val settings: StudySettings
)
