package com.betteranki

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.betteranki.data.dao.SettingsPresetDao
import com.betteranki.data.preferences.PreferencesRepository
import com.betteranki.data.repository.AnkiRepository
import com.betteranki.ui.completion.CompletionViewModel
import com.betteranki.ui.decklist.DeckListViewModel
import com.betteranki.ui.settings.SettingsViewModel
import com.betteranki.ui.study.StudyViewModel
import com.betteranki.sync.FirebaseProgressSync

class DeckListViewModelFactory(
    private val repository: AnkiRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeckListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeckListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class StudyViewModelFactory(
    private val repository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deckId: Long,
    private val progressSync: FirebaseProgressSync?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(repository, preferencesRepository, deckId, progressSync) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val repository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
    private val settingsPresetDao: SettingsPresetDao,
    private val context: Context,
    private val progressSync: FirebaseProgressSync?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository, preferencesRepository, settingsPresetDao, context, progressSync) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CompletionViewModelFactory(
    private val repository: AnkiRepository,
    private val deckId: Long,
    private val cardsReviewed: Int,
    private val correctCount: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompletionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompletionViewModel(repository, deckId, cardsReviewed, correctCount) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
