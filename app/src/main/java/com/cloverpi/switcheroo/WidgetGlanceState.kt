package com.cloverpi.switcheroo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

object WidgetGlanceState {
    val deviceIdKey = stringPreferencesKey("device_id")
    val deviceNameKey = stringPreferencesKey("device_name")
    val isOnKey = booleanPreferencesKey("is_on")
    val availableKey = booleanPreferencesKey("available")

    suspend fun save(
        context: Context,
        glanceId: GlanceId,
        device: GoveeDevice?
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            if (device == null) {
                preferences.clearWidgetState()
            } else {
                preferences[deviceIdKey] = device.deviceId
                preferences[deviceNameKey] = device.name
                preferences[isOnKey] = device.isOn
                preferences[availableKey] = true
            }
        }
    }

    suspend fun saveAndUpdate(
        context: Context,
        glanceId: GlanceId,
        device: GoveeDevice?
    ) {
        save(context, glanceId, device)
        SwitcherooWidget().update(context, glanceId)
    }

    suspend fun syncAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(SwitcherooWidget::class.java)

        glanceIds.forEach { glanceId ->
            val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId
            val selectedDeviceId = appWidgetId?.let {
                WidgetPreferences.getDeviceId(context, it)
            }
            val device = selectedDeviceId?.let {
                DeviceStateSync.getDevice(context, it)
            }

            save(context, glanceId, device)
        }

        SwitcherooWidget().updateAll(context)
    }

    suspend fun syncDevice(
        context: Context,
        deviceId: String
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(SwitcherooWidget::class.java)
        val device = DeviceStateSync.getDevice(context, deviceId)

        glanceIds.forEach { glanceId ->
            val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId
            val selectedDeviceId = appWidgetId?.let {
                WidgetPreferences.getDeviceId(context, it)
            }

            if (selectedDeviceId == deviceId) {
                save(context, glanceId, device)
                SwitcherooWidget().update(context, glanceId)
            }
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.clearWidgetState() {
        remove(deviceIdKey)
        remove(deviceNameKey)
        remove(isOnKey)
        this[availableKey] = false
    }
}
