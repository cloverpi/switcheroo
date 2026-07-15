package com.cloverpi.switcheroo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

private val widgetDeviceIdActionKey =
    ActionParameters.Key<String>("device_id")

class SwitcherooWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        provideContent {
            val state = currentState<Preferences>()
            val available = state[WidgetGlanceState.availableKey] ?: false
            val deviceId = state[WidgetGlanceState.deviceIdKey]
            val deviceName = state[WidgetGlanceState.deviceNameKey]
            val isOn = state[WidgetGlanceState.isOnKey]

            RockerWidgetContent(
                deviceId = if (available) deviceId else null,
                deviceName = if (available) deviceName else null,
                isOn = if (available) isOn else null
            )
        }
    }
}

class SwitcherooWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget =
        SwitcherooWidget()
}

@Composable
private fun RockerWidgetContent(
    deviceId: String?,
    deviceName: String?,
    isOn: Boolean?
) {
    val widgetSize = LocalSize.current
    val reservedLabelSpace = 24.dp
    val rockerHeight = maxOf(
        40.dp,
        widgetSize.height - reservedLabelSpace
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment =
            Alignment.Horizontal.CenterHorizontally
    ) {
        if (deviceId == null || deviceName == null || isOn == null) {
            Image(
                provider = ImageProvider(
                    R.drawable.widget_rocker_unavailable
                ),
                contentDescription = "Device unavailable",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(rockerHeight)
            )

            WidgetDeviceLabel("Unavailable")
        } else {
            val drawable = if (isOn) {
                R.drawable.widget_rocker_on
            } else {
                R.drawable.widget_rocker_off
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(rockerHeight),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(drawable),
                    contentDescription =
                        "$deviceName: ${if (isOn) "on" else "off"}",
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(
                            actionRunCallback<ToggleWidgetDeviceAction>(
                                actionParametersOf(
                                    widgetDeviceIdActionKey to deviceId
                                )
                            )
                        )
                )
            }

            WidgetDeviceLabel(deviceName)
        }
    }
}

@Composable
private fun WidgetDeviceLabel(
    name: String
) {
    Text(
        text = name,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(
                day = Color(0xFF202124),
                night = Color(0xFFF1F3F4)
            ),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                top = 2.dp,
                end = 2.dp,
                bottom = 1.dp
            )
    )
}

class ToggleWidgetDeviceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val deviceId =
            parameters[widgetDeviceIdActionKey] ?: return

        val sharedDevice = DeviceStateSync.getDevice(
            context = context,
            deviceId = deviceId
        ) ?: return

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
            return
        }

        try {
            val newState = !sharedDevice.isOn

            GoveeRepository().setPower(
                apiKey = apiKey,
                device = sharedDevice,
                enabled = newState
            )

            val stateSaved = DeviceStateSync.savePowerState(
                context = context,
                device = sharedDevice,
                isOn = newState
            )

            if (!stateSaved) {
                return
            }

            val updatedDevice = sharedDevice.copy(isOn = newState)
            WidgetGlanceState.saveAndUpdate(
                context = context,
                glanceId = glanceId,
                device = updatedDevice
            )
        } catch (_: Exception) {
            return
        }
    }
}

object WidgetPreferences {
    private const val FILE_NAME =
        "switcheroo_widgets"

    private fun deviceKey(
        appWidgetId: Int
    ): String {
        return "device_$appWidgetId"
    }

    fun saveDeviceId(
        context: Context,
        appWidgetId: Int,
        deviceId: String
    ) {
        context
            .getSharedPreferences(
                FILE_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                deviceKey(appWidgetId),
                deviceId
            )
            .apply()
    }

    fun getDeviceId(
        context: Context,
        appWidgetId: Int
    ): String? {
        return context
            .getSharedPreferences(
                FILE_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                deviceKey(appWidgetId),
                null
            )
    }

    fun delete(
        context: Context,
        appWidgetId: Int
    ) {
        context
            .getSharedPreferences(
                FILE_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(
                deviceKey(appWidgetId)
            )
            .apply()
    }
}
