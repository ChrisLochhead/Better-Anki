package com.betteranki.util

import android.content.Context
import java.io.File

/**
 * Helper for managing media files imported from .apkg files
 */
object MediaHelper {
    
    /**
     * Get the media directory for a specific deck
     */
    fun getMediaDir(context: Context, deckId: Long): File {
        return File(context.filesDir, "media/deck_$deckId")
    }
    
    /**
     * Delete all media files for a specific deck
     */
    fun deleteMediaForDeck(context: Context, deckId: Long): Boolean {
        return try {
            val mediaDir = getMediaDir(context, deckId)
            if (mediaDir.exists()) {
                mediaDir.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get the total size of media files for a deck in bytes
     */
    fun getMediaSize(context: Context, deckId: Long): Long {
        return try {
            val mediaDir = getMediaDir(context, deckId)
            if (mediaDir.exists()) {
                mediaDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
    
    /**
     * Get count of media files for a deck
     */
    fun getMediaFileCount(context: Context, deckId: Long): Int {
        return try {
            val mediaDir = getMediaDir(context, deckId)
            if (mediaDir.exists()) {
                mediaDir.listFiles()?.size ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
    
    /**
     * Format bytes to human-readable size
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
