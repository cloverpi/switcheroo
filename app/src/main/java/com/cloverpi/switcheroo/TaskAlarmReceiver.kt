package com.cloverpi.switcheroo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskScheduler.EXTRA_TASK_ID) ?: return
        val powerOn = intent.getBooleanExtra(TaskScheduler.EXTRA_POWER_ON, false)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = ScheduledTaskStore.find(context, taskId) ?: return@launch
                if (!task.enabled) return@launch

                val device = DeviceStateSync.getDevice(context, task.deviceId) ?: return@launch
                val apiKey = context.getSharedPreferences("switcheroo", Context.MODE_PRIVATE)
                    .getString("govee_api_key", "")
                    .orEmpty()
                if (apiKey.isBlank()) return@launch

                GoveeRepository().setPower(apiKey, device, powerOn)

                if (DeviceStateSync.savePowerState(context, device, powerOn)) {
                    WidgetGlanceState.syncDevice(context, device.deviceId)
                }
            } finally {
                ScheduledTaskStore.find(context, taskId)?.let {
                    TaskScheduler.schedule(context, it)
                }
                pendingResult.finish()
            }
        }
    }
}

class TaskRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskScheduler.scheduleAll(context)
        LightsOutServiceController.sync(context)
    }
}
