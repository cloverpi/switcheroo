package com.cloverpi.switcheroo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            TAG,
            "Charging event received: action=${intent.action}"
        )

        if (intent.action != BatteryManager.ACTION_CHARGING) {
            return
        }

        val chargingType = getChargingType(context)

        if (chargingType == null) {
            Log.d(
                TAG,
                "ACTION_CHARGING received, but charging source unavailable"
            )
            return
        }

        Log.d(
            TAG,
            "Confirmed charging: type=$chargingType"
        )

        val now = LocalTime.now()
        val currentMinutes = now.hour * 60 + now.minute

        val allTasks = LightsOutTaskStore.load(context)

        val matchingTasks = allTasks.filter { task ->
            val chargingTypeMatches = task.matches(chargingType)

            val timeMatches = isInsideWindow(
                currentMinutes = currentMinutes,
                startMinutes = task.startMinutes,
                endMinutes = task.endMinutes
            )

            Log.d(
                TAG,
                "Task ${task.id}: enabled=${task.enabled}, " +
                        "chargingTypeMatches=$chargingTypeMatches, " +
                        "timeMatches=$timeMatches"
            )

            task.enabled &&
                    chargingTypeMatches &&
                    timeMatches
        }

        Log.d(
            TAG,
            "Matching Lights Out tasks=${matchingTasks.size}"
        )

        if (matchingTasks.isEmpty()) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeTasks(
                    context = appContext,
                    tasks = matchingTasks
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeTasks(
        context: Context,
        tasks: List<LightsOutTask>
    ) {
        val apiKey = context
            .getSharedPreferences(
                "switcheroo",
                Context.MODE_PRIVATE
            )
            .getString(
                "govee_api_key",
                ""
            )
            .orEmpty()

        if (apiKey.isBlank()) {
            Log.d(TAG, "Lights Out stopped: API key is blank")
            return
        }

        tasks.forEach { task ->
            val device = DeviceStateSync.getDevice(
                context = context,
                deviceId = task.deviceId
            )

            if (device == null) {
                Log.d(
                    TAG,
                    "Lights Out task ${task.id}: device not found"
                )
                return@forEach
            }

            try {
                Log.d(
                    TAG,
                    "Sending OFF command: task=${task.id}, " +
                            "device=${device.deviceId}"
                )

                GoveeRepository().setPower(
                    apiKey = apiKey,
                    device = device,
                    enabled = false
                )

                Log.d(
                    TAG,
                    "OFF command succeeded: device=${device.deviceId}"
                )

                val stateSaved = DeviceStateSync.savePowerState(
                    context = context,
                    device = device,
                    isOn = false
                )

                Log.d(
                    TAG,
                    "Shared state saved=$stateSaved"
                )

                if (stateSaved) {
                    WidgetGlanceState.syncDevice(
                        context = context,
                        deviceId = device.deviceId
                    )

                    Log.d(
                        TAG,
                        "Widget state synchronized"
                    )
                }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Lights Out failed: task=${task.id}",
                    exception
                )
            }
        }
    }

    private fun getChargingType(
        context: Context
    ): ChargingTrigger? {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val plugged = batteryIntent.getIntExtra(
            BatteryManager.EXTRA_PLUGGED,
            0
        )

        val batteryManager = context.getSystemService(
            BatteryManager::class.java
        )

        Log.d(
            TAG,
            "Charging state: isCharging=${batteryManager.isCharging}, " +
                    "plugged=$plugged"
        )

        if (!batteryManager.isCharging) {
            return null
        }

        return when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 ->
                ChargingTrigger.WIRELESS

            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 ->
                ChargingTrigger.WIRED

            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 ->
                ChargingTrigger.WIRED

            plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 ->
                ChargingTrigger.WIRED

            else -> null
        }
    }

    private fun LightsOutTask.matches(
        actual: ChargingTrigger
    ): Boolean {
        return chargingTrigger == ChargingTrigger.EITHER ||
                chargingTrigger == actual
    }

    private fun isInsideWindow(
        currentMinutes: Int,
        startMinutes: Int,
        endMinutes: Int
    ): Boolean {
        if (startMinutes == endMinutes) {
            return true
        }

        return if (startMinutes < endMinutes) {
            currentMinutes >= startMinutes &&
                    currentMinutes < endMinutes
        } else {
            currentMinutes >= startMinutes ||
                    currentMinutes < endMinutes
        }
    }

    companion object {
        private const val TAG = "Switcheroo"
    }
}