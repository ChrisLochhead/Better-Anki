package com.betteranki.data.dao

import androidx.room.*
import com.betteranki.data.model.ReviewHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewHistoryDao {
    @Query("SELECT * FROM review_history WHERE deckId = :deckId ORDER BY date DESC LIMIT 30")
    fun getReviewHistory(deckId: Long): Flow<List<ReviewHistory>>
    
    @Query("SELECT * FROM review_history WHERE deckId = :deckId AND date = :date")
    suspend fun getHistoryForDate(deckId: Long, date: Long): ReviewHistory?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHistory(history: ReviewHistory)
    
    @Update
    suspend fun updateHistory(history: ReviewHistory)
}
