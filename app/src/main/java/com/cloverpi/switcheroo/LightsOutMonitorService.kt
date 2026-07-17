package com.cloverpi.switcheroo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log

class LightsOutMonitorService : Service() {

    private val chargingReceiver = ChargingReceiver()
    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "LightsOutMonitorService.onCreate()")

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        registerChargingReceiver()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        Log.d(TAG, "LightsOutMonitorService.onStartCommand()")

        if (!LightsOutServiceController.hasEnabledTasks(this)) {
            Log.d(
                TAG,
                "No enabled Lights Out tasks; stopping service"
            )

            stopSelf()
            return START_NOT_STICKY
        }

        registerChargingReceiver()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "LightsOutMonitorService.onDestroy()")

        if (receiverRegistered) {
            try {
                unregisterReceiver(chargingReceiver)
                Log.d(TAG, "Charging receiver unregistered")
            } catch (exception: IllegalArgumentException) {
                Log.e(
                    TAG,
                    "Charging receiver was not registered",
                    exception
                )
            }

            receiverRegistered = false
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun registerChargingReceiver() {
        if (receiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(BatteryManager.ACTION_CHARGING)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    chargingReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(
                    chargingReceiver,
                    filter
                )
            }

            receiverRegistered = true

            Log.d(
                TAG,
                "Charging receiver registered for " +
                        BatteryManager.ACTION_CHARGING
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to register charging receiver",
                exception
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(
            NotificationManager::class.java
        )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lights Out monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                "Keeps charging-triggered Lights Out tasks active"

            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(
                this,
                MainActivity::class.java
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            Notification.Builder(
                this,
                CHANNEL_ID
            )
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_charging
            )
            .setContentTitle("Switcheroo")
            .setContentText(
                "Monitoring charging for Lights Out tasks"
            )
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "Switcheroo"
        private const val CHANNEL_ID =
            "lights_out_monitor"

        private const val NOTIFICATION_ID =
            1001
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

    fun hasEnabledTasks(
        context: Context
    ): Boolean {
        return LightsOutTaskStore
            .load(context)
            .any { task ->
                task.enabled
            }
    }

    private fun start(context: Context) {
        val appContext = context.applicationContext

        val intent = Intent(
            appContext,
            LightsOutMonitorService::class.java
        )

        try {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (exception: IllegalStateException) {
            Log.e(
                "Switcheroo",
                "Android rejected foreground-service start",
                exception
            )
        } catch (exception: SecurityException) {
            Log.e(
                "Switcheroo",
                "Foreground-service permission failure",
                exception
            )
        }
    }

    private fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(
                context.applicationContext,
                LightsOutMonitorService::class.java
            )
        )
    }
}