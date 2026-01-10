package com.betteranki.data.dao

import androidx.room.*
import com.betteranki.data.model.Card
import com.betteranki.data.model.CardStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun getCardsForDeck(deckId: Long): Flow<List<Card>>
    
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND (status = 'NEW' OR (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime))")
    suspend fun getDueCards(deckId: Long, currentTime: Long): List<Card>
    
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND status = 'NEW' LIMIT :limit")
    suspend fun getNewCards(deckId: Long, limit: Int): List<Card>
    
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime")
    suspend fun getReviewDueCards(deckId: Long, currentTime: Long): List<Card>
    
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND status = :status")
    suspend fun getCardCountByStatus(deckId: Long, status: CardStatus): Int
    
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND (status = 'NEW' OR (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime))")
    suspend fun getDueCardCount(deckId: Long, currentTime: Long): Int
    
    @Insert
    suspend fun insertCard(card: Card): Long
    
    @Insert
    suspend fun insertCards(cards: List<Card>)
    
    @Update
    suspend fun updateCard(card: Card)
    
    @Delete
    suspend fun deleteCard(card: Card)
    
    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): Card?
}
