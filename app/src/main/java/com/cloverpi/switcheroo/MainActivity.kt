package com.cloverpi.switcheroo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cloverpi.switcheroo.ui.theme.SwitcherooTheme
import kotlinx.coroutines.launch

private enum class AppTab(
    val title: String
) {
    Devices("Devices"),
    Tasks("Tasks"),
    Settings("Settings")
}

class MainActivity : ComponentActivity() {
    private val repository = GoveeRepository()
    private var resumeCounter by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCounter++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = getSharedPreferences(
            "switcheroo",
            MODE_PRIVATE
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        LightsOutServiceController.sync(this)

        setContent {
            SwitcherooTheme {
                var apiKey by rememberSaveable {
                    mutableStateOf(
                        preferences.getString("govee_api_key", "").orEmpty()
                    )
                }

                var selectedTab by rememberSaveable {
                    mutableStateOf(
                        if (apiKey.isBlank()) {
                            AppTab.Settings
                        } else {
                            AppTab.Devices
                        }
                    )
                }

                SwitcherooApp(
                    apiKey = apiKey,
                    selectedTab = selectedTab,
                    repository = repository,
                    resumeCounter = resumeCounter,
                    onTabSelected = { selectedTab = it },
                    onSaveApiKey = { enteredKey ->
                        preferences.edit()
                            .putString("govee_api_key", enteredKey)
                            .apply()

                        apiKey = enteredKey
                        selectedTab = AppTab.Devices
                    },
                    onClearApiKey = {
                        preferences.edit()
                            .remove("govee_api_key")
                            .apply()

                        apiKey = ""
                        selectedTab = AppTab.Settings
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitcherooApp(
    apiKey: String,
    selectedTab: AppTab,
    repository: GoveeRepository,
    resumeCounter: Int,
    onTabSelected: (AppTab) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedTab.title)
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Devices,
                    onClick = {
                        onTabSelected(AppTab.Devices)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Devices,
                            contentDescription = "Devices"
                        )
                    },
                    label = null,
                    alwaysShowLabel = false
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.Tasks,
                    onClick = {
                        onTabSelected(AppTab.Tasks)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Tasks"
                        )
                    },
                    label = null,
                    alwaysShowLabel = false
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings,
                    onClick = {
                        onTabSelected(AppTab.Settings)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = null,
                    alwaysShowLabel = false
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Devices -> {
                DevicesScreen(
                    apiKey = apiKey,
                    repository = repository,
                    resumeCounter = resumeCounter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            AppTab.Tasks -> {
                TasksScreen(
                    apiKey = apiKey,
                    resumeCounter = resumeCounter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            AppTab.Settings -> {
                SettingsScreen(
                    currentApiKey = apiKey,
                    onSaveApiKey = onSaveApiKey,
                    onClearApiKey = onClearApiKey,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun TasksScreen(
    apiKey: String,
    resumeCounter: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>() }
    val lightsOutTasks = remember { mutableStateListOf<LightsOutTask>() }
    var editorTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var lightsOutEditorTask by remember { mutableStateOf<LightsOutTask?>(null) }
    var showScheduleEditor by remember { mutableStateOf(false) }
    var showLightsOutEditor by remember { mutableStateOf(false) }
    var showTaskTypePicker by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(TaskScheduler.canScheduleExact(context)) }

    fun reload() {
        scheduledTasks.clear()
        scheduledTasks.addAll(ScheduledTaskStore.load(context))
        lightsOutTasks.clear()
        lightsOutTasks.addAll(LightsOutTaskStore.load(context))
        exactAllowed = TaskScheduler.canScheduleExact(context)
    }

    LaunchedEffect(resumeCounter) {
        reload()
        TaskScheduler.scheduleAll(context)
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!exactAllowed && scheduledTasks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Exact timing is not enabled",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Schedules will still run, but Android may delay them.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = {
                                TaskScheduler.exactAlarmSettingsIntent(context)?.let {
                                    context.startActivity(it)
                                }
                            }
                        ) {
                            Text("Enable exact timing")
                        }
                    }
                }
            }

            if (apiKey.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add your Govee API key in Settings")
                }
            } else if (scheduledTasks.isEmpty() && lightsOutTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scheduledTasks, key = { "schedule_${it.id}" }) { task ->
                        ScheduledTaskCard(
                            task = task,
                            onEnabledChanged = { enabled ->
                                val index = scheduledTasks.indexOfFirst { it.id == task.id }
                                if (index == -1) return@ScheduledTaskCard
                                val updated = task.copy(enabled = enabled)
                                scheduledTasks[index] = updated
                                if (ScheduledTaskStore.save(context, scheduledTasks.toList())) {
                                    TaskScheduler.schedule(context, updated)
                                }
                            },
                            onEdit = {
                                editorTask = task
                                showScheduleEditor = true
                            },
                            onDelete = {
                                TaskScheduler.cancel(context, task.id)
                                scheduledTasks.removeAll { it.id == task.id }
                                ScheduledTaskStore.save(context, scheduledTasks.toList())
                            }
                        )
                    }

                    items(lightsOutTasks, key = { "lights_out_${it.id}" }) { task ->
                        LightsOutTaskCard(
                            task = task,
                            onEnabledChanged = { enabled ->
                                val index = lightsOutTasks.indexOfFirst { it.id == task.id }
                                if (index == -1) return@LightsOutTaskCard
                                lightsOutTasks[index] = task.copy(enabled = enabled)
                                LightsOutTaskStore.save(context, lightsOutTasks.toList())
                            },
                            onEdit = {
                                lightsOutEditorTask = task
                                showLightsOutEditor = true
                            },
                            onDelete = {
                                lightsOutTasks.removeAll { it.id == task.id }
                                LightsOutTaskStore.save(context, lightsOutTasks.toList())
                            }
                        )
                    }
                }
            }
        }

        if (apiKey.isNotBlank()) {
            FloatingActionButton(
                onClick = { showTaskTypePicker = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add task"
                )
            }
        }
    }

    if (showTaskTypePicker) {
        AlertDialog(
            onDismissRequest = { showTaskTypePicker = false },
            title = { Text("New task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showTaskTypePicker = false
                            editorTask = null
                            showScheduleEditor = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Schedule")
                    }
                    OutlinedButton(
                        onClick = {
                            showTaskTypePicker = false
                            lightsOutEditorTask = null
                            showLightsOutEditor = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lights out")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTaskTypePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScheduleEditor) {
        ScheduledTaskEditor(
            existingTask = editorTask,
            devices = DeviceStateSync.getAllDevices(context),
            onDismiss = { showScheduleEditor = false },
            onSave = { savedTask ->
                val existingIndex = scheduledTasks.indexOfFirst { it.id == savedTask.id }
                if (existingIndex == -1) {
                    scheduledTasks.add(savedTask)
                } else {
                    scheduledTasks[existingIndex] = savedTask
                }
                if (ScheduledTaskStore.save(context, scheduledTasks.toList())) {
                    TaskScheduler.schedule(context, savedTask)
                }
                showScheduleEditor = false
            }
        )
    }

    if (showLightsOutEditor) {
        LightsOutTaskEditor(
            existingTask = lightsOutEditorTask,
            devices = DeviceStateSync.getAllDevices(context),
            onDismiss = { showLightsOutEditor = false },
            onSave = { savedTask ->
                val existingIndex = lightsOutTasks.indexOfFirst { it.id == savedTask.id }
                if (existingIndex == -1) {
                    lightsOutTasks.add(savedTask)
                } else {
                    lightsOutTasks[existingIndex] = savedTask
                }
                LightsOutTaskStore.save(context, lightsOutTasks.toList())
                showLightsOutEditor = false
            }
        )
    }
}

@Composable
private fun LightsOutTaskCard(
    task: LightsOutTask,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = task.deviceName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Lights out • ${task.chargingTrigger.label} charging",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${formatMinutes(task.startMinutes)} – ${formatMinutes(task.endMinutes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = task.enabled,
                onCheckedChange = onEnabledChanged
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete lights-out task"
                )
            }
        }
    }
}

@Composable
private fun LightsOutTaskEditor(
    existingTask: LightsOutTask?,
    devices: List<GoveeDevice>,
    onDismiss: () -> Unit,
    onSave: (LightsOutTask) -> Unit
) {
    val context = LocalContext.current
    var name by remember(existingTask) { mutableStateOf(existingTask?.name.orEmpty()) }
    var selectedDevice by remember(existingTask, devices) {
        mutableStateOf(
            existingTask?.let { task -> devices.firstOrNull { it.deviceId == task.deviceId } }
                ?: devices.firstOrNull()
        )
    }
    var chargingTrigger by remember(existingTask) {
        mutableStateOf(existingTask?.chargingTrigger ?: ChargingTrigger.EITHER)
    }
    var startMinutes by remember(existingTask) {
        mutableIntStateOf(existingTask?.startMinutes ?: 22 * 60)
    }
    var endMinutes by remember(existingTask) {
        mutableIntStateOf(existingTask?.endMinutes ?: 6 * 60)
    }
    var deviceMenuOpen by remember { mutableStateOf(false) }
    var triggerMenuOpen by remember { mutableStateOf(false) }

    fun openTimePicker(current: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected(hour * 60 + minute) },
            current / 60,
            current % 60,
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingTask == null) "New lights-out task" else "Edit lights-out task")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (devices.isEmpty()) {
                    Text("Open Devices once so available devices can be loaded.")
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box {
                        OutlinedButton(
                            onClick = { deviceMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedDevice?.name ?: "Choose device")
                        }
                        DropdownMenu(
                            expanded = deviceMenuOpen,
                            onDismissRequest = { deviceMenuOpen = false }
                        ) {
                            devices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.name) },
                                    onClick = {
                                        selectedDevice = device
                                        deviceMenuOpen = false
                                        if (name.isBlank()) name = "${device.name} lights out"
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { triggerMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${chargingTrigger.label} charging")
                        }
                        DropdownMenu(
                            expanded = triggerMenuOpen,
                            onDismissRequest = { triggerMenuOpen = false }
                        ) {
                            ChargingTrigger.entries.forEach { trigger ->
                                DropdownMenuItem(
                                    text = { Text(trigger.label) },
                                    onClick = {
                                        chargingTrigger = trigger
                                        triggerMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "Active time window",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                openTimePicker(startMinutes) { startMinutes = it }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("From ${formatMinutes(startMinutes)}")
                        }
                        OutlinedButton(
                            onClick = {
                                openTimePicker(endMinutes) { endMinutes = it }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Until ${formatMinutes(endMinutes)}")
                        }
                    }

                    Text(
                        text = "The window may cross midnight.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val device = selectedDevice ?: return@TextButton
                    val finalName = name.trim().ifBlank { "${device.name} lights out" }
                    onSave(
                        LightsOutTask(
                            id = existingTask?.id ?: java.util.UUID.randomUUID().toString(),
                            name = finalName,
                            deviceId = device.deviceId,
                            deviceName = device.name,
                            chargingTrigger = chargingTrigger,
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            enabled = existingTask?.enabled ?: true
                        )
                    )
                },
                enabled = selectedDevice != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = task.deviceName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = TaskDays.label(task.daysMask),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "On ${formatMinutes(task.onMinutes)}  •  Off ${formatMinutes(task.offMinutes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = task.enabled,
                onCheckedChange = onEnabledChanged
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete schedule"
                )
            }
        }
    }
}

@Composable
private fun ScheduledTaskEditor(
    existingTask: ScheduledTask?,
    devices: List<GoveeDevice>,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    val context = LocalContext.current
    var name by remember(existingTask) { mutableStateOf(existingTask?.name.orEmpty()) }
    var selectedDevice by remember(existingTask, devices) {
        mutableStateOf(
            existingTask?.let { task -> devices.firstOrNull { it.deviceId == task.deviceId } }
                ?: devices.firstOrNull()
        )
    }
    var daysMask by remember(existingTask) {
        mutableIntStateOf(existingTask?.daysMask ?: TaskDays.EVERY_DAY)
    }
    var onMinutes by remember(existingTask) {
        mutableIntStateOf(existingTask?.onMinutes ?: 6 * 60)
    }
    var offMinutes by remember(existingTask) {
        mutableIntStateOf(existingTask?.offMinutes ?: 8 * 60)
    }
    var deviceMenuOpen by remember { mutableStateOf(false) }

    fun openTimePicker(current: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected(hour * 60 + minute) },
            current / 60,
            current % 60,
            android.text.format.DateFormat.is24HourFormat(context)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingTask == null) "New schedule" else "Edit schedule")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (devices.isEmpty()) {
                    Text("Open Devices once so available devices can be loaded.")
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box {
                        OutlinedButton(
                            onClick = { deviceMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedDevice?.name ?: "Choose device")
                        }
                        DropdownMenu(
                            expanded = deviceMenuOpen,
                            onDismissRequest = { deviceMenuOpen = false }
                        ) {
                            devices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.name) },
                                    onClick = {
                                        selectedDevice = device
                                        deviceMenuOpen = false
                                        if (name.isBlank()) name = device.name
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openTimePicker(onMinutes) { onMinutes = it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("On ${formatMinutes(onMinutes)}")
                        }
                        OutlinedButton(
                            onClick = { openTimePicker(offMinutes) { offMinutes = it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Off ${formatMinutes(offMinutes)}")
                        }
                    }

                    Text("Days", style = MaterialTheme.typography.labelLarge)
                    TaskDays.ordered.chunked(4).forEach { rowDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowDays.forEach { (bit, label) ->
                                FilterChip(
                                    selected = daysMask and bit != 0,
                                    onClick = {
                                        daysMask = if (daysMask and bit != 0) {
                                            daysMask and bit.inv()
                                        } else {
                                            daysMask or bit
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val device = selectedDevice ?: return@TextButton
                    val finalName = name.trim().ifBlank { device.name }
                    onSave(
                        ScheduledTask(
                            id = existingTask?.id ?: java.util.UUID.randomUUID().toString(),
                            name = finalName,
                            deviceId = device.deviceId,
                            deviceName = device.name,
                            daysMask = daysMask,
                            onMinutes = onMinutes,
                            offMinutes = offMinutes,
                            enabled = existingTask?.enabled ?: true
                        )
                    )
                },
                enabled = selectedDevice != null && daysMask != 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatMinutes(minutes: Int): String {
    val hour = minutes / 60
    val minute = minutes % 60
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(displayHour, minute, suffix)
}

@Composable
private fun SettingsScreen(
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKey by rememberSaveable(currentApiKey) {
        mutableStateOf(currentApiKey)
    }

    Column(
        modifier = modifier
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Govee API",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it.trim()
            },
            label = {
                Text("API key")
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveApiKey(apiKey)
            },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API key")
        }

        if (currentApiKey.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    apiKey = ""
                    onClearApiKey()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove API key")
            }
        }
    }
}

@Composable
private fun DevicesScreen(
    apiKey: String,
    repository: GoveeRepository,
    resumeCounter: Int,
    modifier: Modifier = Modifier
) {
    if (apiKey.isBlank()) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Add your Govee API key in Settings",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    val devices = remember {
        mutableStateListOf<GoveeDevice>()
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var refreshCounter by remember {
        mutableIntStateOf(0)
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        DeviceStateSync.updates.collect { update ->
            val index = devices.indexOfFirst {
                it.deviceId == update.deviceId
            }

            if (index != -1) {
                devices[index] = devices[index].copy(
                    isOn = update.isOn
                )
            }
        }
    }

    LaunchedEffect(apiKey, refreshCounter, resumeCounter) {
        loading = true
        error = null

        try {
            val loadedDevices = repository.getDevices(apiKey)
            devices.clear()
            devices.addAll(loadedDevices)

            val stateSaved = DeviceStateSync.saveDevices(
                context,
                loadedDevices
            )

            if (!stateSaved) {
                throw IllegalStateException("Unable to save device state")
            }

            WidgetGlanceState.syncAll(context)
        } catch (exception: Exception) {
            error = exception.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    when {
        loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Column(
                modifier = modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )

                Button(
                    onClick = {
                        refreshCounter++
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Retry")
                }
            }
        }

        devices.isEmpty() -> {
            Column(
                modifier = modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No switchable Govee devices found")

                Button(
                    onClick = {
                        refreshCounter++
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Refresh")
                }
            }
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = modifier,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = devices,
                    key = {
                        it.deviceId
                    }
                ) { device ->
                    DeviceSwitch(
                        device = device,
                        onChanged = { enabled ->
                            val index = devices.indexOfFirst {
                                it.deviceId == device.deviceId
                            }

                            if (index == -1) {
                                return@DeviceSwitch
                            }

                            val previous = devices[index]
                            devices[index] = previous.copy(
                                isOn = enabled
                            )

                            scope.launch {
                                try {
                                    repository.setPower(
                                        apiKey = apiKey,
                                        device = previous,
                                        enabled = enabled
                                    )

                                    val stateSaved = DeviceStateSync.savePowerState(
                                        context = context,
                                        device = previous,
                                        isOn = enabled
                                    )

                                    if (!stateSaved) {
                                        throw IllegalStateException(
                                            "Unable to save device state"
                                        )
                                    }

                                    WidgetGlanceState.syncDevice(
                                        context = context,
                                        deviceId = previous.deviceId
                                    )
                                } catch (exception: Exception) {
                                    devices[index] = previous
                                    error = exception.message
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSwitch(
    device: GoveeDevice,
    onChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = if (device.isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.bodySmall,
                color = if (device.isOnline) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            WallSwitch(
                checked = device.isOn,
                enabled = device.isOnline,
                onCheckedChange = onChanged,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = if (device.isOn) "On" else "Off",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun WallSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val plateColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val rockerColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .width(72.dp)
            .height(104.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = plateColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(72.dp)
                .shadow(
                    elevation = if (checked) 2.dp else 6.dp,
                    shape = RoundedCornerShape(6.dp)
                )
                .background(
                    color = rockerColor,
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(
                        if (checked) {
                            Alignment.TopCenter
                        } else {
                            Alignment.BottomCenter
                        }
                    )
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        color = Color.Black.copy(
                            alpha = if (checked) 0.08f else 0.16f
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
        }
    }
}
