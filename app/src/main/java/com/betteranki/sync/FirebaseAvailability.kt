package com.betteranki.sync

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseAvailability {
    fun isConfigured(context: Context): Boolean {
        return try {
            // If google-services.json is present and properly configured, initializeApp returns non-null.
            // If it's missing, this will return null but won't crash.
            FirebaseApp.initializeApp(context)
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
}
