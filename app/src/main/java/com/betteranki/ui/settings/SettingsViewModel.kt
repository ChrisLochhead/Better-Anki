package com.betteranki.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.dao.SettingsPresetDao
import com.betteranki.data.model.*
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.util.NotificationHelper
import com.betteranki.sync.FirebaseProgressSync
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val availableDecks: List<Deck> = emptyList(),
    val availablePresets: List<SettingsPreset> = emptyList(),
    val currentSettings: StudySettings = StudySettings(),
    val selectedPresetId: Long? = null,
    val showSaveDialog: Boolean = false,
    val firebaseConfigured: Boolean = false,
    val signedInEmail: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authStatus: String? = null,
    val showForgotPassword: Boolean = false,
    val autoSyncAfterReview: Boolean = true
)

class SettingsViewModel(
    private val ankiRepository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val settingsPresetDao: SettingsPresetDao,
    private val context: Context,
    private val progressSync: FirebaseProgressSync?
) : ViewModel() {
    
    private val notificationHelper = NotificationHelper(context)
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // Temporary settings being edited
    private val _editingSettings = MutableStateFlow(StudySettings())
    val editingSettings: StateFlow<StudySettings> = _editingSettings.asStateFlow()
    
    init {
        loadData()
        observeAuthState()
    }

    private fun observeAuthState() {
        _uiState.update {
            it.copy(
                firebaseConfigured = progressSync != null,
                signedInEmail = progressSync?.currentUser?.value?.email,
                authBusy = false,
                authError = null
            )
        }

        progressSync?.let { sync ->
            viewModelScope.launch {
                sync.currentUser.collect { user ->
                    _uiState.update { it.copy(signedInEmail = user?.email, authBusy = false) }
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        val sync = progressSync ?: run {
            _uiState.update { it.copy(authError = "Firebase isn't configured yet") }
            return
        }

        _uiState.update { it.copy(authBusy = true, authError = null, authStatus = null, showForgotPassword = false) }
        viewModelScope.launch {
            runCatching {
                sync.signIn(email, password)
            }.onFailure { err ->
                val (message, showForgot) = humanizeAuthError(err, isSignIn = true)
                _uiState.update {
                    it.copy(
                        authBusy = false,
                        authError = message,
                        authStatus = null,
                        showForgotPassword = showForgot
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(authBusy = false, authError = null, authStatus = null, showForgotPassword = false) }
            }
        }
    }

    fun signUp(email: String, password: String) {
        val sync = progressSync ?: run {
            _uiState.update { it.copy(authError = "Firebase isn't configured yet") }
            return
        }

        _uiState.update { it.copy(authBusy = true, authError = null, authStatus = null, showForgotPassword = false) }
        viewModelScope.launch {
            runCatching {
                sync.signUp(email, password)
            }.onFailure { err ->
                val (message, _) = humanizeAuthError(err, isSignIn = false)
                _uiState.update { it.copy(authBusy = false, authError = message, authStatus = null, showForgotPassword = false) }
            }.onSuccess {
                _uiState.update { it.copy(authBusy = false, authError = null, authStatus = null, showForgotPassword = false) }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val sync = progressSync ?: run {
            _uiState.update { it.copy(authError = "Firebase isn't configured yet", authStatus = null) }
            return
        }

        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(authError = "Enter your email first", authStatus = null) }
            return
        }

        _uiState.update { it.copy(authBusy = true, authError = null, authStatus = null) }
        viewModelScope.launch {
            runCatching {
                sync.sendPasswordResetEmail(trimmed)
            }.onFailure {
                // Avoid leaking whether an account exists.
                _uiState.update {
                    it.copy(
                        authBusy = false,
                        authError = null,
                        authStatus = "If an account exists for that email, you'll receive a reset email shortly."
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        authBusy = false,
                        authError = null,
                        authStatus = "If an account exists for that email, you'll receive a reset email shortly."
                    )
                }
            }
        }
    }

    fun signOut() {
        progressSync?.signOut()
        _uiState.update { it.copy(authError = null, authStatus = null, showForgotPassword = false) }
    }

    private fun humanizeAuthError(err: Throwable, isSignIn: Boolean): Pair<String, Boolean> {
        // Default
        var showForgot = false

        // Firebase often returns: "The supplied auth credential is incorrect, malformed or has expired."
        val rawMessage = (err.message ?: "").lowercase()
        if (isSignIn && rawMessage.contains("supplied auth credential")) {
            return "Wrong username or password" to true
        }

        when (err) {
            is FirebaseAuthInvalidUserException -> {
                // user-not-found
                if (isSignIn) showForgot = true
                return (if (isSignIn) "Wrong username or password" else "Account not found") to showForgot
            }
            is FirebaseAuthInvalidCredentialsException -> {
                // wrong-password / invalid-email
                if (isSignIn) showForgot = true
                return (if (isSignIn) "Wrong username or password" else "Invalid email or password") to showForgot
            }
            is FirebaseAuthException -> {
                if (isSignIn) {
                    val code = err.errorCode
                    if (code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" || code == "ERROR_USER_NOT_FOUND") {
                        return "Wrong username or password" to true
                    }
                }
                return (err.localizedMessage ?: (if (isSignIn) "Sign-in failed" else "Sign-up failed")) to false
            }
        }

        return (err.localizedMessage ?: (if (isSignIn) "Sign-in failed" else "Sign-up failed")) to showForgot
    }

    fun setAutoSyncAfterReview(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoSyncAfterReview(enabled)
        }
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

        viewModelScope.launch {
            preferencesRepository.autoSyncAfterReview.collect { enabled ->
                _uiState.update { it.copy(autoSyncAfterReview = enabled) }
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
