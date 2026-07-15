package com.cloverpi.switcheroo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class GoveeDevice(
    val sku: String,
    val deviceId: String,
    val name: String,
    val type: String,
    val isOnline: Boolean,
    val isOn: Boolean
)

class GoveeApiException(message: String) : Exception(message)

class GoveeRepository {
    private val baseUrl = "https://openapi.api.govee.com"

    suspend fun getDevices(apiKey: String): List<GoveeDevice> =
        withContext(Dispatchers.IO) {
            val discovery = request(
                method = "GET",
                path = "/router/api/v1/user/devices",
                apiKey = apiKey
            )

            checkResponse(discovery)

            val devices = discovery.optJSONArray("data")
                ?: return@withContext emptyList()

            val result = mutableListOf<GoveeDevice>()

            for (index in 0 until devices.length()) {
                val device = devices.getJSONObject(index)
                val capabilities = device.optJSONArray("capabilities")
                var hasPowerSwitch = false

                if (capabilities != null) {
                    for (capabilityIndex in 0 until capabilities.length()) {
                        val capability = capabilities.getJSONObject(capabilityIndex)

                        if (
                            capability.optString("type") ==
                            "devices.capabilities.on_off" &&
                            capability.optString("instance") == "powerSwitch"
                        ) {
                            hasPowerSwitch = true
                            break
                        }
                    }
                }

                if (!hasPowerSwitch) {
                    continue
                }

                val sku = device.getString("sku")
                val deviceId = device.getString("device")
                val state = getDeviceState(apiKey, sku, deviceId)

                result += GoveeDevice(
                    sku = sku,
                    deviceId = deviceId,
                    name = device.optString("deviceName", sku),
                    type = device.optString("type", ""),
                    isOnline = state.first,
                    isOn = state.second
                )
            }

            result.sortedBy { it.name.lowercase() }
        }

    suspend fun setPower(
        apiKey: String,
        device: GoveeDevice,
        enabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put(
                "payload",
                JSONObject()
                    .put("sku", device.sku)
                    .put("device", device.deviceId)
                    .put(
                        "capability",
                        JSONObject()
                            .put("type", "devices.capabilities.on_off")
                            .put("instance", "powerSwitch")
                            .put("value", if (enabled) 1 else 0)
                    )
            )

        val response = request(
            method = "POST",
            path = "/router/api/v1/device/control",
            apiKey = apiKey,
            body = body
        )

        checkResponse(response)
    }

    private fun getDeviceState(
        apiKey: String,
        sku: String,
        deviceId: String
    ): Pair<Boolean, Boolean> {
        val body = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put(
                "payload",
                JSONObject()
                    .put("sku", sku)
                    .put("device", deviceId)
            )

        val response = request(
            method = "POST",
            path = "/router/api/v1/device/state",
            apiKey = apiKey,
            body = body
        )

        checkResponse(response)

        val capabilities = response
            .optJSONObject("payload")
            ?.optJSONArray("capabilities")
            ?: return false to false

        var online = false
        var poweredOn = false

        for (index in 0 until capabilities.length()) {
            val capability = capabilities.getJSONObject(index)
            val type = capability.optString("type")
            val instance = capability.optString("instance")
            val value = capability
                .optJSONObject("state")
                ?.opt("value")

            if (
                type == "devices.capabilities.online" &&
                instance == "online"
            ) {
                online = value == true
            }

            if (
                type == "devices.capabilities.on_off" &&
                instance == "powerSwitch"
            ) {
                poweredOn = when (value) {
                    is Number -> value.toInt() == 1
                    is Boolean -> value
                    else -> false
                }
            }
        }

        return online to poweredOn
    }

    private fun request(
        method: String,
        path: String,
        apiKey: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection = URL(baseUrl + path)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Govee-API-Key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            if (body != null) {
                connection.doOutput = true

                connection.outputStream.bufferedWriter().use {
                    it.write(body.toString())
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (responseText.isBlank()) {
                throw GoveeApiException(
                    "Govee returned HTTP $statusCode with no response body"
                )
            }

            val response = JSONObject(responseText)

            if (statusCode !in 200..299) {
                throw GoveeApiException(
                    response.optString(
                        "message",
                        response.optString("msg", "HTTP $statusCode")
                    )
                )
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun checkResponse(response: JSONObject) {
        val code = response.optInt("code", 200)

        if (code != 200) {
            throw GoveeApiException(
                response.optString(
                    "message",
                    response.optString("msg", "Govee API error $code")
                )
            )
        }
    }
}