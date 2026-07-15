package com.cloverpi.switcheroo

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.cloverpi.switcheroo.ui.theme.SwitcherooTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SwitcherooWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId =
        AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent
            ?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val canceledResult = Intent().putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            appWidgetId
        )

        setResult(
            Activity.RESULT_CANCELED,
            canceledResult
        )

        if (
            appWidgetId ==
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            finish()
            return
        }

        setContent {
            SwitcherooTheme {
                WidgetDevicePicker(
                    onDeviceSelected = {
                        finishConfiguration(it)
                    }
                )
            }
        }
    }

    private fun finishConfiguration(
        device: GoveeDevice
    ) {
        WidgetPreferences.saveDeviceId(
            context = this,
            appWidgetId = appWidgetId,
            deviceId = device.deviceId
        )

        CoroutineScope(Dispatchers.Main).launch {
            val glanceManager =
                GlanceAppWidgetManager(
                    this@SwitcherooWidgetConfigActivity
                )

            val glanceId =
                glanceManager.getGlanceIdBy(
                    appWidgetId
                )

            WidgetGlanceState.saveAndUpdate(
                context = this@SwitcherooWidgetConfigActivity,
                glanceId = glanceId,
                device = device
            )

            val result = Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId
            )

            setResult(
                Activity.RESULT_OK,
                result
            )

            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetDevicePicker(
    onDeviceSelected: (GoveeDevice) -> Unit
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    var devices by remember {
        mutableStateOf<List<GoveeDevice>>(
            emptyList()
        )
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        val apiKey = context
            .getSharedPreferences(
                "switcheroo",
                android.content.Context.MODE_PRIVATE
            )
            .getString(
                "govee_api_key",
                ""
            )
            .orEmpty()

        if (apiKey.isBlank()) {
            error = "Open app and enter API key first"
            loading = false
            return@LaunchedEffect
        }

        try {
            devices =
                GoveeRepository().getDevices(apiKey).also {
                    DeviceStateSync.saveDevices(context, it)
                }
        } catch (exception: Exception) {
            error =
                exception.message ?: "Unable to load devices"
        } finally {
            loading = false
        }
    }

    when {
        loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement =
                    Arrangement.Center,
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement =
                    Arrangement.Center,
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text = error.orEmpty(),
                    color =
                        MaterialTheme.colorScheme.error
                )
            }
        }

        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choose device",
                    style =
                        MaterialTheme.typography.headlineSmall,
                    modifier =
                        Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = devices,
                        key = {
                            it.deviceId
                        }
                    ) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device)
                                }
                        ) {
                            Column(
                                modifier =
                                    Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = device.name,
                                    style =
                                        MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = if (device.isOnline) {
                                        if (device.isOn) {
                                            "On"
                                        } else {
                                            "Off"
                                        }
                                    } else {
                                        "Offline"
                                    },
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}