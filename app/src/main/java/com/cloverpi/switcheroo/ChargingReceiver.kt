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
    private var previousPlugged: Int? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            "Switcheroo",
            "Battery event received: action=${intent.action}"
        )
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

        val plugged = intent.getIntExtra(
            BatteryManager.EXTRA_PLUGGED,
            0
        )

        val previous = previousPlugged
        previousPlugged = plugged

        if (previous == null) return
        if (previous != 0 || plugged == 0) return

        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_WIRELESS ->
                ChargingTrigger.WIRELESS

            BatteryManager.BATTERY_PLUGGED_AC,
            BatteryManager.BATTERY_PLUGGED_USB,
            BatteryManager.BATTERY_PLUGGED_DOCK ->
                ChargingTrigger.WIRED

            else -> return
        }

        val currentMinutes =
            LocalTime.now().hour * 60 + LocalTime.now().minute

        val matchingTasks = LightsOutTaskStore.load(context).filter { task ->
            task.enabled &&
                    task.matches(chargingType) &&
                    isInsideWindow(
                        currentMinutes,
                        task.startMinutes,
                        task.endMinutes
                    )
        }

        if (matchingTasks.isEmpty()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
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

                if (apiKey.isBlank()) return@launch

                matchingTasks.forEach { task ->
                    val device = DeviceStateSync.getDevice(
                        context,
                        task.deviceId
                    ) ?: return@forEach

                    try {
                        GoveeRepository().setPower(
                            apiKey = apiKey,
                            device = device,
                            enabled = false
                        )

                        if (
                            DeviceStateSync.savePowerState(
                                context,
                                device,
                                false
                            )
                        ) {
                            WidgetGlanceState.syncDevice(
                                context,
                                device.deviceId
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            } finally {
                pendingResult.finish()
            }
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
        if (startMinutes == endMinutes) return true

        return if (startMinutes < endMinutes) {
            currentMinutes >= startMinutes &&
                    currentMinutes < endMinutes
        } else {
            currentMinutes >= startMinutes ||
                    currentMinutes < endMinutes
        }
    }
}
