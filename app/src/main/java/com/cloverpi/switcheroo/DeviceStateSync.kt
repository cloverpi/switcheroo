package com.cloverpi.switcheroo

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

data class DevicePowerUpdate(
    val deviceId: String,
    val isOn: Boolean
)

object DeviceStateSync {
    private const val FILE_NAME = "switcheroo_device_state"

    private val mutableUpdates = MutableSharedFlow<DevicePowerUpdate>(
        extraBufferCapacity = 16
    )

    val updates = mutableUpdates.asSharedFlow()

    fun saveDevices(
        context: Context,
        devices: List<GoveeDevice>
    ): Boolean {
        val editor = context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()

        devices.forEach { device ->
            editor.putString(
                device.deviceId,
                device.toJson().toString()
            )
        }

        return editor.commit()
    }

    fun saveDevice(
        context: Context,
        device: GoveeDevice
    ): Boolean {
        return context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(device.deviceId, device.toJson().toString())
            .commit()
    }

    fun savePowerState(
        context: Context,
        device: GoveeDevice,
        isOn: Boolean
    ): Boolean {
        val updated = device.copy(isOn = isOn)
        val saved = saveDevice(context, updated)

        if (saved) {
            publish(updated.deviceId, updated.isOn)
        }

        return saved
    }

    fun getAllDevices(context: Context): List<GoveeDevice> {
        return context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .all
            .values
            .mapNotNull { value ->
                val json = value as? String ?: return@mapNotNull null
                try {
                    JSONObject(json).toGoveeDevice()
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getDevice(
        context: Context,
        deviceId: String
    ): GoveeDevice? {
        val json = context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(deviceId, null)
            ?: return null

        return try {
            JSONObject(json).toGoveeDevice()
        } catch (_: Exception) {
            null
        }
    }

    fun publish(
        deviceId: String,
        isOn: Boolean
    ) {
        mutableUpdates.tryEmit(
            DevicePowerUpdate(
                deviceId = deviceId,
                isOn = isOn
            )
        )
    }

    private fun GoveeDevice.toJson(): JSONObject = JSONObject()
        .put("sku", sku)
        .put("deviceId", deviceId)
        .put("name", name)
        .put("type", type)
        .put("isOnline", isOnline)
        .put("isOn", isOn)

    private fun JSONObject.toGoveeDevice(): GoveeDevice = GoveeDevice(
        sku = getString("sku"),
        deviceId = getString("deviceId"),
        name = getString("name"),
        type = optString("type", ""),
        isOnline = optBoolean("isOnline", false),
        isOn = optBoolean("isOn", false)
    )
}
