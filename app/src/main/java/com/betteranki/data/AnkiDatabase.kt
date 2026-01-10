package com.betteranki.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.betteranki.data.dao.CardDao
import com.betteranki.data.dao.DeckDao
import com.betteranki.data.dao.ReviewHistoryDao
import com.betteranki.data.dao.SettingsPresetDao
import com.betteranki.data.model.Card
import com.betteranki.data.model.Deck
import com.betteranki.data.model.ReviewHistory
import com.betteranki.data.model.SettingsPresetEntity

@Database(
    entities = [Card::class, Deck::class, ReviewHistory::class, SettingsPresetEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AnkiDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun reviewHistoryDao(): ReviewHistoryDao
    abstract fun settingsPresetDao(): SettingsPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AnkiDatabase? = null

        fun getDatabase(context: Context): AnkiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnkiDatabase::class.java,
                    "anki_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
