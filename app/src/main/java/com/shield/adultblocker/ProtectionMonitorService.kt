package com.shield.adultblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

class ProtectionMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wasActiveLastCheck = true

    private val checkRunnable = object : Runnable {
        override fun run() {
            val isCurrentlyActive = isAccessibilityEnabled()
            val prefs = getSharedPreferences("achivity_prefs", Context.MODE_PRIVATE)

            if (!isCurrentlyActive && wasActiveLastCheck) {
                // Service was just turned off! Log tamper event and reset streak
                val tamperCount = prefs.getInt("tamper_count", 0) + 1
                prefs.edit()
                    .putInt("tamper_count", tamperCount)
                    .putLong("streak_start_time", System.currentTimeMillis())
                    .apply()

                sendTamperWarningNotification(tamperCount)
            } else if (isCurrentlyActive && !wasActiveLastCheck) {
                // Restored
                updateForegroundNotification("Achivity Protection Active 🛡️", "Focus streak is running.")
            }

            wasActiveLastCheck = isCurrentlyActive
            handler.postDelayed(this, 3000) // Check every 3 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val prefs = getSharedPreferences("achivity_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("streak_start_time")) {
            prefs.edit().putLong("streak_start_time", System.currentTimeMillis()).apply()
        }
        startForeground(101, buildNotification("Achivity Protection Active 🛡️", "Monitoring focus goal..."))
        handler.post(checkRunnable)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedComponent = ComponentName(this, BlockerAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { ComponentName.unflattenFromString(it) == expectedComponent }
    }

    private fun sendTamperWarningNotification(tamperCount: Int) {
        updateForegroundNotification(
            "⚠️ Accessibility Turned Off! (Tamper #$tamperCount)",
            "Your focus streak was reset. Tap here to re-enable protection immediately."
        )
    }

    private fun updateForegroundNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(101, buildNotification(title, message))
    }

    private fun buildNotification(title: String, message: String): Notification {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "achivity_monitor_channel")
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "achivity_monitor_channel",
                "Achivity Accountability Monitor",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
