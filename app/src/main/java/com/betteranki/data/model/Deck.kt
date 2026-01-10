package com.betteranki.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class DeckWithStats(
    val deck: Deck,
    val totalCards: Int,
    val newCards: Int,
    val hardCards: Int,      // Previously learningCards
    val easyCards: Int,      // Previously reviewCards
    val masteredCards: Int,
    val dueForReview: Int
)
