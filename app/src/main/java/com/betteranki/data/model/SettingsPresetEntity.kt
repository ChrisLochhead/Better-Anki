package com.betteranki.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings_presets")
data class SettingsPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val deckId: Long,
    val dailyNewCards: Int,
    val dailyReviewLimit: Int,
    val againIntervalMinutes: Int,
    val hardIntervalMinutes: Int,
    val goodIntervalMinutes: Int,
    val easyIntervalMinutes: Int,
    val easyThresholdSeconds: Int = 3,
    val goodThresholdSeconds: Int = 5,

    // Leniency
    val leniencyModeEnabled: Boolean = true,
    val maxNewCardsAfterSkip: Int = 30,
    val maxReviewCards: Int = 50,
    val dailyReviewsAddable: Int = 20,

    // Notifications
    val notificationsEnabled: Boolean = false,
    val notificationDays: String = "1,2,3,4,5,6,7",
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0,

    // Decay
    val decayModeEnabled: Boolean = true,
    val decayStartDays: Int = 5,
    val decayRatePerDay: Int = 2,
    val decayMinCards: Int = 10,

    // OCR translation defaults
    val ocrSourceLanguage: String = "ja",
    val ocrTargetLanguage: String = "en"
)

fun SettingsPresetEntity.toPreset(): SettingsPreset {
    val parsedDays = notificationDays
        .split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()

    return SettingsPreset(
        id = id,
        name = name,
        settings = StudySettings(
            deckId = deckId,
            dailyNewCards = dailyNewCards,
            dailyReviewLimit = dailyReviewLimit,
            againIntervalMinutes = againIntervalMinutes,
            hardIntervalMinutes = hardIntervalMinutes,
            goodIntervalMinutes = goodIntervalMinutes,
            easyIntervalMinutes = easyIntervalMinutes,
            easyThresholdSeconds = easyThresholdSeconds,
            goodThresholdSeconds = goodThresholdSeconds,

            leniencyModeEnabled = leniencyModeEnabled,
            maxNewCardsAfterSkip = maxNewCardsAfterSkip,
            maxReviewCards = maxReviewCards,
            dailyReviewsAddable = dailyReviewsAddable,

            notificationsEnabled = notificationsEnabled,
            notificationDays = if (parsedDays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else parsedDays,
            notificationHour = notificationHour,
            notificationMinute = notificationMinute,

            decayModeEnabled = decayModeEnabled,
            decayStartDays = decayStartDays,
            decayRatePerDay = decayRatePerDay,
            decayMinCards = decayMinCards,

            ocrSourceLanguage = ocrSourceLanguage,
            ocrTargetLanguage = ocrTargetLanguage
        )
    )
}

fun SettingsPreset.toEntity(): SettingsPresetEntity {
    return SettingsPresetEntity(
        id = id,
        name = name,
        deckId = settings.deckId,
        dailyNewCards = settings.dailyNewCards,
        dailyReviewLimit = settings.dailyReviewLimit,
        againIntervalMinutes = settings.againIntervalMinutes,
        hardIntervalMinutes = settings.hardIntervalMinutes,
        goodIntervalMinutes = settings.goodIntervalMinutes,
        easyIntervalMinutes = settings.easyIntervalMinutes,
        easyThresholdSeconds = settings.easyThresholdSeconds,
        goodThresholdSeconds = settings.goodThresholdSeconds,

        leniencyModeEnabled = settings.leniencyModeEnabled,
        maxNewCardsAfterSkip = settings.maxNewCardsAfterSkip,
        maxReviewCards = settings.maxReviewCards,
        dailyReviewsAddable = settings.dailyReviewsAddable,

        notificationsEnabled = settings.notificationsEnabled,
        notificationDays = settings.notificationDays.joinToString(","),
        notificationHour = settings.notificationHour,
        notificationMinute = settings.notificationMinute,

        decayModeEnabled = settings.decayModeEnabled,
        decayStartDays = settings.decayStartDays,
        decayRatePerDay = settings.decayRatePerDay,
        decayMinCards = settings.decayMinCards,

        ocrSourceLanguage = settings.ocrSourceLanguage,
        ocrTargetLanguage = settings.ocrTargetLanguage
    )
}
