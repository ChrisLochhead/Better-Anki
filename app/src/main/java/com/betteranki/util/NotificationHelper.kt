package com.betteranki.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.betteranki.MainActivity
import com.betteranki.R
import com.betteranki.data.model.StudySettings
import com.betteranki.data.preferences.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "study_reminder"
        const val CHANNEL_NAME = "Study Reminders"
        const val NOTIFICATION_ID = 1001
        const val WORK_TAG = "study_notification"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to study your flashcards"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showStudyReminder() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)  // You can replace with your app icon
            .setContentTitle("Time to Study!")
            .setContentText("You have cards waiting for review")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun scheduleNotifications(settings: StudySettings) {
        val workManager = WorkManager.getInstance(context)
        
        // Cancel existing work
        workManager.cancelAllWorkByTag(WORK_TAG)
        
        if (!settings.notificationsEnabled || settings.notificationDays.isEmpty()) {
            return
        }
        
        // Schedule for each enabled day
        settings.notificationDays.forEach { dayOfWeek ->
            scheduleForDay(workManager, dayOfWeek, settings.notificationHour, settings.notificationMinute)
        }
    }
    
    private fun scheduleForDay(workManager: WorkManager, dayOfWeek: Int, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            // Map our day (1=Mon, 7=Sun) to Calendar (2=Mon, 1=Sun)
            val calendarDay = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
            set(Calendar.DAY_OF_WEEK, calendarDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the time is in the past this week, schedule for next week
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        
        val initialDelay = target.timeInMillis - now.timeInMillis
        
        val workRequest = PeriodicWorkRequestBuilder<StudyNotificationWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .addTag("day_$dayOfWeek")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "notification_day_$dayOfWeek",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
    
    fun cancelAllNotifications() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(WORK_TAG)
    }
}

class StudyNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    
    override fun doWork(): Result {
        // Check if notifications are still enabled
        val preferencesRepository = PreferencesRepository(context)
        val settings = runBlocking { preferencesRepository.currentSettings.first() }
        
        if (!settings.notificationsEnabled) {
            return Result.success()
        }
        
        // Check if today is an enabled day
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        // Convert Calendar day (1=Sun, 2=Mon...) to our format (1=Mon, 7=Sun)
        val ourDay = if (today == Calendar.SUNDAY) 7 else today - 1
        
        if (!settings.notificationDays.contains(ourDay)) {
            return Result.success()
        }
        
        // Show the notification
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showStudyReminder()
        
        return Result.success()
    }
}
