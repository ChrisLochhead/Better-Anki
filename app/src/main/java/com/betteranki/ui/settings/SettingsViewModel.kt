package com.betteranki.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.dao.SettingsPresetDao
import com.betteranki.data.model.*
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.util.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val availableDecks: List<Deck> = emptyList(),
    val availablePresets: List<SettingsPreset> = emptyList(),
    val currentSettings: StudySettings = StudySettings(),
    val selectedPresetId: Long? = null,
    val showSaveDialog: Boolean = false
)

class SettingsViewModel(
    private val ankiRepository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val settingsPresetDao: SettingsPresetDao,
    private val context: Context
) : ViewModel() {
    
    private val notificationHelper = NotificationHelper(context)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // Temporary settings being edited
    private val _editingSettings = MutableStateFlow(StudySettings())
    val editingSettings: StateFlow<StudySettings> = _editingSettings.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            // Load decks
            ankiRepository.getAllDecksWithStats().collect { decksWithStats ->
                val decks = decksWithStats.map { it.deck }
                _uiState.update { it.copy(availableDecks = decks) }
            }
        }
        
        viewModelScope.launch {
            // Load current settings
            preferencesRepository.currentSettings.collect { settings ->
                _uiState.update { it.copy(currentSettings = settings) }
                _editingSettings.value = settings
            }
        }
        
        viewModelScope.launch {
            // Load presets
            settingsPresetDao.getAllPresets().collect { entities ->
                val presets = entities.map { it.toPreset() }
                _uiState.update { it.copy(availablePresets = presets) }
            }
        }
    }
    
    fun updateDeckId(deckId: Long) {
        _editingSettings.update { it.copy(deckId = deckId) }
    }
    
    fun updateDailyNewCards(count: Int) {
        _editingSettings.update { it.copy(dailyNewCards = count) }
    }
    
    fun updateDailyReviewLimit(limit: Int) {
        _editingSettings.update { it.copy(dailyReviewLimit = limit) }
    }
    
    fun updateAgainInterval(minutes: Int) {
        _editingSettings.update { it.copy(againIntervalMinutes = minutes) }
    }
    
    fun updateHardInterval(minutes: Int) {
        _editingSettings.update { it.copy(hardIntervalMinutes = minutes) }
    }
    
    fun updateGoodInterval(minutes: Int) {
        _editingSettings.update { it.copy(goodIntervalMinutes = minutes) }
    }
    
    fun updateEasyInterval(minutes: Int) {
        _editingSettings.update { it.copy(easyIntervalMinutes = minutes) }
    }
    
    fun updateEasyThreshold(seconds: Int) {
        _editingSettings.update { it.copy(easyThresholdSeconds = seconds) }
    }
    
    fun updateGoodThreshold(seconds: Int) {
        _editingSettings.update { it.copy(goodThresholdSeconds = seconds) }
    }
    
    // Leniency Mode Settings
    fun updateLeniencyModeEnabled(enabled: Boolean) {
        _editingSettings.update { it.copy(leniencyModeEnabled = enabled) }
    }
    
    fun updateMaxNewCardsAfterSkip(count: Int) {
        _editingSettings.update { it.copy(maxNewCardsAfterSkip = count) }
    }
    
    fun updateMaxReviewCards(count: Int) {
        _editingSettings.update { it.copy(maxReviewCards = count) }
    }
    
    fun updateDailyReviewsAddable(count: Int) {
        _editingSettings.update { it.copy(dailyReviewsAddable = count) }
    }
    
    // Notification Settings
    fun updateNotificationsEnabled(enabled: Boolean) {
        _editingSettings.update { it.copy(notificationsEnabled = enabled) }
    }
    
    fun updateNotificationDays(days: Set<Int>) {
        _editingSettings.update { it.copy(notificationDays = days) }
    }
    
    fun updateNotificationHour(hour: Int) {
        _editingSettings.update { it.copy(notificationHour = hour) }
    }
    
    fun updateNotificationMinute(minute: Int) {
        _editingSettings.update { it.copy(notificationMinute = minute) }
    }
    
    // Decay Mode Settings
    fun updateDecayModeEnabled(enabled: Boolean) {
        _editingSettings.update { it.copy(decayModeEnabled = enabled) }
    }
    
    fun updateDecayStartDays(days: Int) {
        _editingSettings.update { it.copy(decayStartDays = days) }
    }
    
    fun updateDecayRatePerDay(rate: Int) {
        _editingSettings.update { it.copy(decayRatePerDay = rate) }
    }
    
    fun updateDecayMinCards(min: Int) {
        _editingSettings.update { it.copy(decayMinCards = min) }
    }
    
    fun updateOcrSourceLanguage(language: String) {
        _editingSettings.update { it.copy(ocrSourceLanguage = language) }
    }
    
    fun updateOcrTargetLanguage(language: String) {
        _editingSettings.update { it.copy(ocrTargetLanguage = language) }
    }
    
    fun selectPreset(presetId: Long) {
        val preset = _uiState.value.availablePresets.find { it.id == presetId }
        preset?.let {
            _editingSettings.value = it.settings
            _uiState.update { state -> state.copy(selectedPresetId = presetId) }
        }
    }
    
    fun showSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }
    
    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            val settings = _editingSettings.value
            preferencesRepository.saveSettings(settings)
            
            // Schedule or cancel notifications based on settings
            if (settings.notificationsEnabled) {
                notificationHelper.scheduleNotifications(settings)
            } else {
                notificationHelper.cancelAllNotifications()
            }
        }
    }
    
    fun saveAsPreset(name: String, overwritePresetId: Long? = null) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                dismissSaveDialog()
                return@launch
            }

            if (overwritePresetId != null) {
                val entity = SettingsPreset(
                    id = overwritePresetId,
                    name = trimmed,
                    settings = _editingSettings.value
                ).toEntity()
                settingsPresetDao.updatePreset(entity)
                _uiState.update { it.copy(selectedPresetId = overwritePresetId) }
            } else {
                val preset = SettingsPreset(
                    name = trimmed,
                    settings = _editingSettings.value
                )
                val newId = settingsPresetDao.insertPreset(preset.toEntity())
                _uiState.update { it.copy(selectedPresetId = newId) }
            }
            dismissSaveDialog()
        }
    }
}
