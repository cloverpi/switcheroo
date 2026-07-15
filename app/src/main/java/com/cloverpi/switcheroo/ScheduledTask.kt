package com.cloverpi.switcheroo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceId: String,
    val deviceName: String,
    val daysMask: Int,
    val onMinutes: Int,
    val offMinutes: Int,
    val enabled: Boolean = true
)

object TaskDays {
    const val MONDAY = 1 shl 0
    const val TUESDAY = 1 shl 1
    const val WEDNESDAY = 1 shl 2
    const val THURSDAY = 1 shl 3
    const val FRIDAY = 1 shl 4
    const val SATURDAY = 1 shl 5
    const val SUNDAY = 1 shl 6
    const val EVERY_DAY = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY or SATURDAY or SUNDAY

    val ordered = listOf(
        MONDAY to "M",
        TUESDAY to "T",
        WEDNESDAY to "W",
        THURSDAY to "T",
        FRIDAY to "F",
        SATURDAY to "S",
        SUNDAY to "S"
    )

    fun label(mask: Int): String {
        if (mask == EVERY_DAY) return "Every day"
        if (mask == (MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY)) return "Weekdays"
        if (mask == (SATURDAY or SUNDAY)) return "Weekends"

        val names = listOf(
            MONDAY to "Mon",
            TUESDAY to "Tue",
            WEDNESDAY to "Wed",
            THURSDAY to "Thu",
            FRIDAY to "Fri",
            SATURDAY to "Sat",
            SUNDAY to "Sun"
        )
        return names.filter { mask and it.first != 0 }.joinToString(", ") { it.second }
    }
}

object ScheduledTaskStore {
    private const val FILE_NAME = "switcheroo_tasks"
    private const val KEY_TASKS = "tasks"

    fun load(context: Context): List<ScheduledTask> {
        val raw = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toTask())
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, tasks: List<ScheduledTask>): Boolean {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .commit()
    }

    fun find(context: Context, taskId: String): ScheduledTask? =
        load(context).firstOrNull { it.id == taskId }

    private fun ScheduledTask.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("daysMask", daysMask)
        .put("onMinutes", onMinutes)
        .put("offMinutes", offMinutes)
        .put("enabled", enabled)

    private fun JSONObject.toTask() = ScheduledTask(
        id = getString("id"),
        name = getString("name"),
        deviceId = getString("deviceId"),
        deviceName = getString("deviceName"),
        daysMask = getInt("daysMask"),
        onMinutes = getInt("onMinutes"),
        offMinutes = getInt("offMinutes"),
        enabled = optBoolean("enabled", true)
    )
}
