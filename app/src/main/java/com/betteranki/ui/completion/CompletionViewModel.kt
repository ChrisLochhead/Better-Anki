package com.betteranki.ui.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betteranki.data.model.DeckWithStats
import com.betteranki.data.model.ReviewHistory
import com.betteranki.data.repository.AnkiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompletionState(
    val cardsReviewed: Int = 0,
    val correctCount: Int = 0,
    val deckStats: DeckWithStats? = null,
    val reviewHistory: List<ReviewHistory> = emptyList(),
    val percentLearned: Float = 0f
)

class CompletionViewModel(
    private val repository: AnkiRepository,
    private val deckId: Long,
    cardsReviewed: Int,
    correctCount: Int
) : ViewModel() {
    
    private val _state = MutableStateFlow(
        CompletionState(
            cardsReviewed = cardsReviewed,
            correctCount = correctCount
        )
    )
    val state: StateFlow<CompletionState> = _state.asStateFlow()
    
    init {
        loadStats()
    }
    
    private fun loadStats() {
        viewModelScope.launch {
            val stats = repository.getDeckWithStats(deckId)
            val history = repository.getReviewHistory(deckId)
            
            history.collect { historyList ->
                val percentLearned = if (stats != null && stats.totalCards > 0) {
                    ((stats.learningCards + stats.masteredCards).toFloat() / stats.totalCards) * 100
                } else 0f
                
                _state.value = _state.value.copy(
                    deckStats = stats,
                    reviewHistory = historyList,
                    percentLearned = percentLearned
                )
            }
        }
    }
}
