package com.cloverpi.switcheroo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder

class LightsOutMonitorService : Service() {
    private val chargingReceiver = ChargingReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        registerChargingReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LightsOutServiceController.hasEnabledTasks(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        registerChargingReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(chargingReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerChargingReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                chargingReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(chargingReceiver, filter)
        }

        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lights Out monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps charging-triggered Lights Out tasks active"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Switcheroo")
            .setContentText("Monitoring charging for Lights Out tasks")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lights_out_monitor"
        private const val NOTIFICATION_ID = 1001
    }
}

object LightsOutServiceController {
    fun sync(context: Context) {
        if (hasEnabledTasks(context)) {
            start(context)
        } else {
            stop(context)
        }
    }

    fun hasEnabledTasks(context: Context): Boolean {
        return LightsOutTaskStore.load(context).any { it.enabled }
    }

    private fun start(context: Context) {
        val intent = Intent(context, LightsOutMonitorService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (_: IllegalStateException) {
            // Android can reject background service starts in restricted states.
            // Opening Switcheroo will call sync() again from MainActivity.
        }
    }

    private fun stop(context: Context) {
        context.stopService(Intent(context, LightsOutMonitorService::class.java))
    }
}
