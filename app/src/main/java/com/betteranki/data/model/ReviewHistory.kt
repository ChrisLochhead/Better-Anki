package com.betteranki.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_history")
data class ReviewHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val date: Long, // Timestamp truncated to day
    val cardsReviewed: Int,
    val learningCards: Int,
    val masteredCards: Int,
    val newCards: Int = 0,
    val reviewCards: Int = 0
)
