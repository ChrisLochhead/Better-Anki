package com.betteranki.data.dao

import androidx.room.*
import com.betteranki.data.model.Deck
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun getAllDecks(): Flow<List<Deck>>

    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    suspend fun getAllDecksSync(): List<Deck>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun getDeckByIdFlow(deckId: Long): Flow<Deck?>
    
    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): Deck?
    
    @Insert
    suspend fun insertDeck(deck: Deck): Long
    
    @Update
    suspend fun updateDeck(deck: Deck)
    
    @Delete
    suspend fun deleteDeck(deck: Deck)
    
    @Query("SELECT COUNT(*) FROM decks")
    suspend fun getDeckCount(): Int
}
