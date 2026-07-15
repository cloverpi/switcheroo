package com.cloverpi.switcheroo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ChargingTrigger(val label: String) {
    WIRELESS("Wireless"),
    WIRED("Wired"),
    EITHER("Either")
}

data class LightsOutTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceId: String,
    val deviceName: String,
    val chargingTrigger: ChargingTrigger,
    val startMinutes: Int,
    val endMinutes: Int,
    val enabled: Boolean = true
)

object LightsOutTaskStore {
    private const val FILE_NAME = "switcheroo_lights_out_tasks"
    private const val KEY_TASKS = "tasks"

    fun load(context: Context): List<LightsOutTask> {
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

    fun save(context: Context, tasks: List<LightsOutTask>): Boolean {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        val saved = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .commit()

        if (saved) {
            LightsOutServiceController.sync(context)
        }

        return saved
    }

    private fun LightsOutTask.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("chargingTrigger", chargingTrigger.name)
        .put("startMinutes", startMinutes)
        .put("endMinutes", endMinutes)
        .put("enabled", enabled)

    private fun JSONObject.toTask() = LightsOutTask(
        id = getString("id"),
        name = getString("name"),
        deviceId = getString("deviceId"),
        deviceName = getString("deviceName"),
        chargingTrigger = runCatching {
            ChargingTrigger.valueOf(getString("chargingTrigger"))
        }.getOrDefault(ChargingTrigger.EITHER),
        startMinutes = getInt("startMinutes"),
        endMinutes = getInt("endMinutes"),
        enabled = optBoolean("enabled", true)
    )
}
