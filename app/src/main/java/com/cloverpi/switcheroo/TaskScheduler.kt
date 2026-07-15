package com.cloverpi.switcheroo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.time.DayOfWeek
import java.time.ZonedDateTime

object TaskScheduler {
    const val ACTION_ON = "com.cloverpi.switcheroo.TASK_ON"
    const val ACTION_OFF = "com.cloverpi.switcheroo.TASK_OFF"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_POWER_ON = "power_on"

    fun schedule(context: Context, task: ScheduledTask) {
        cancel(context, task.id)
        if (!task.enabled || task.daysMask == 0) return
        scheduleAction(context, task, true)
        scheduleAction(context, task, false)
    }

    fun scheduleAll(context: Context) {
        ScheduledTaskStore.load(context).forEach { schedule(context, it) }
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context, taskId, true))
        alarmManager.cancel(pendingIntent(context, taskId, false))
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun scheduleAction(
        context: Context,
        task: ScheduledTask,
        powerOn: Boolean
    ) {
        val minutes = if (powerOn) task.onMinutes else task.offMinutes
        val triggerAt = nextOccurrence(task.daysMask, minutes)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context, task.id, powerOn)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toInstant().toEpochMilli(),
                intent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toInstant().toEpochMilli(),
                intent
            )
        }
    }

    private fun pendingIntent(
        context: Context,
        taskId: String,
        powerOn: Boolean
    ): PendingIntent {
        val action = if (powerOn) ACTION_ON else ACTION_OFF
        val requestCode = 31 * taskId.hashCode() + if (powerOn) 1 else 2
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_POWER_ON, powerOn)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextOccurrence(daysMask: Int, minutes: Int): ZonedDateTime {
        val now = ZonedDateTime.now()
        val hour = minutes / 60
        val minute = minutes % 60

        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val dayBit = dayBit(date.dayOfWeek)
            if (daysMask and dayBit == 0) continue

            val candidate = date.atTime(hour, minute).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }

        return now.plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    }

    private fun dayBit(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> TaskDays.MONDAY
        DayOfWeek.TUESDAY -> TaskDays.TUESDAY
        DayOfWeek.WEDNESDAY -> TaskDays.WEDNESDAY
        DayOfWeek.THURSDAY -> TaskDays.THURSDAY
        DayOfWeek.FRIDAY -> TaskDays.FRIDAY
        DayOfWeek.SATURDAY -> TaskDays.SATURDAY
        DayOfWeek.SUNDAY -> TaskDays.SUNDAY
    }
}
