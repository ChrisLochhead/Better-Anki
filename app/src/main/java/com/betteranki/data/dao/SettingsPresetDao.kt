package com.betteranki.data.dao

import androidx.room.*
import com.betteranki.data.model.SettingsPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsPresetDao {
    @Query("SELECT * FROM settings_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<SettingsPresetEntity>>
    
    @Query("SELECT * FROM settings_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): SettingsPresetEntity?
    
    @Insert
    suspend fun insertPreset(preset: SettingsPresetEntity): Long
    
    @Update
    suspend fun updatePreset(preset: SettingsPresetEntity)
    
    @Delete
    suspend fun deletePreset(preset: SettingsPresetEntity)
}
