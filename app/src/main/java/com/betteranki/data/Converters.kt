package com.betteranki.data

import androidx.room.TypeConverter
import com.betteranki.data.model.CardStatus

class Converters {
    @TypeConverter
    fun fromCardStatus(status: CardStatus): String {
        return status.name
    }

    @TypeConverter
    fun toCardStatus(status: String): CardStatus {
        return CardStatus.valueOf(status)
    }
}
