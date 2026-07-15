package com.cloverpi.switcheroo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val chargingType = currentChargingType(context) ?: return
        val currentMinutes = LocalTime.now().hour * 60 + LocalTime.now().minute
        val matchingTasks = LightsOutTaskStore.load(context).filter { task ->
            task.enabled &&
                task.matches(chargingType) &&
                isInsideWindow(currentMinutes, task.startMinutes, task.endMinutes)
        }

        if (matchingTasks.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = context.getSharedPreferences("switcheroo", Context.MODE_PRIVATE)
                    .getString("govee_api_key", "")
                    .orEmpty()
                if (apiKey.isBlank()) return@launch

                matchingTasks.forEach { task ->
                    val device = DeviceStateSync.getDevice(context, task.deviceId)
                        ?: return@forEach

                    try {
                        GoveeRepository().setPower(
                            apiKey = apiKey,
                            device = device,
                            enabled = false
                        )

                        if (DeviceStateSync.savePowerState(context, device, false)) {
                            WidgetGlanceState.syncDevice(context, device.deviceId)
                        }
                    } catch (_: Exception) {
                        // One failed task must not prevent the remaining tasks from running.
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun currentChargingType(context: Context): ChargingTrigger? {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        return when (batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingTrigger.WIRELESS
            BatteryManager.BATTERY_PLUGGED_AC,
            BatteryManager.BATTERY_PLUGGED_USB,
            BatteryManager.BATTERY_PLUGGED_DOCK -> ChargingTrigger.WIRED
            else -> null
        }
    }

    private fun LightsOutTask.matches(actual: ChargingTrigger): Boolean {
        return chargingTrigger == ChargingTrigger.EITHER || chargingTrigger == actual
    }

    private fun isInsideWindow(
        currentMinutes: Int,
        startMinutes: Int,
        endMinutes: Int
    ): Boolean {
        if (startMinutes == endMinutes) return true

        return if (startMinutes < endMinutes) {
            currentMinutes >= startMinutes && currentMinutes < endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
