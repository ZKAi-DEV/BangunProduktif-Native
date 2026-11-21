package com.ubay.bangunproduktif

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class NativeAlarm(
    val id: String,
    val time: String,
    val reps: Int,
    val label: String,
    val days: List<Int>, // 0=Min .. 6=Sab
    val enabled: Boolean
)

data class NativeTodo(
    val id: String,
    val title: String,
    val tag: String,
    val mandatory: Boolean,
    val completed: Boolean
)

data class NativeGalleryItem(
    val id: String,
    val title: String,
    val date: String,
    val uriString: String
)

data class NativeCalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val source: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val initialChallenge = intent?.getBooleanExtra("TRIGGER_CHALLENGE", false) ?: false
        val initialLabel = intent?.getStringExtra("EXTRA_ALARM_LABEL") ?: "Waktunya Push-Up!"
        val initialReps = intent?.getIntExtra("EXTRA_ALARM_REPS", 10) ?: 10

        setContent {
            BangunProduktifNativeApp(
                initialChallenge = initialChallenge,
                initialLabel = initialLabel,
                initialReps = initialReps
            )
        }
    }
}

@Composable
fun BangunProduktifNativeApp(
    initialChallenge: Boolean,
    initialLabel: String,
    initialReps: Int
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bangunproduktif_prefs", Context.MODE_PRIVATE) }

    // Permissions launcher for Camera & Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Live clock
    var nowStr by remember { mutableStateOf("") }
    var dateHumanStr by remember { mutableStateOf("") }
    var todayIso by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale("id", "ID"))
        val dateFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        while (true) {
            val d = Date()
            nowStr = timeFmt.format(d)
            dateHumanStr = dateFmt.format(d)
            todayIso = isoFmt.format(d)
            delay(1000)
        }
    }

    // App State
    var appTitle by remember { mutableStateOf(prefs.getString("app_title", "BangunProduktif") ?: "BangunProduktif") }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Alarm, 1=To-Do, 2=Galeri, 3=Kalender
    var ringtoneLabel by remember { mutableStateOf(prefs.getString("custom_ringtone_name", "Nada Dering Bawaan HP") ?: "Nada Dering Bawaan HP") }

    // Alarms state
    var alarms by remember { mutableStateOf(loadAlarms(prefs)) }
    var newHour by remember { mutableIntStateOf(5) }
    var newMinute by remember { mutableIntStateOf(0) }
    var newReps by remember { mutableIntStateOf(10) }
    var newLabel by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 0)) }

    // Alarm Challenge State
    var challengeActive by remember { mutableStateOf(initialChallenge) }
    var challengeLabel by remember { mutableStateOf(initialLabel) }
    var challengeTarget by remember { mutableIntStateOf(initialReps) }
    var challengeCurrent by remember { mutableIntStateOf(0) }

    // To-Do State (5 separate Sholat tasks + daily & weekday routines)
    var selectedTodoDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var todoMap by remember { mutableStateOf(loadTodoMap(prefs)) }
    var newTodoText by remember { mutableStateOf("") }

    // Gallery State
    var galleryItems by remember { mutableStateOf(loadGallery(prefs)) }

    // Calendar State
    var events by remember { mutableStateOf(loadEvents(prefs)) }
    var newEventTitle by remember { mutableStateOf("") }
    var newEventDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var newEventTime by remember { mutableStateOf("08:00") }

    // Audio Picker
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio Kustom"
            prefs.edit().putString("custom_ringtone_uri", uri.toString()).putString("custom_ringtone_name", name).apply()
            ringtoneLabel = name
        }
    }

    // Gallery Media Picker
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val item = NativeGalleryItem(
                id = "gal_${System.currentTimeMillis()}",
                title = "Bukti Aktivitas",
                date = selectedTodoDate,
                uriString = uri.toString()
            )
            galleryItems = listOf(item) + galleryItems
            saveGallery(prefs, galleryItems)
        }
    }

    // Full-screen Alarm Challenge Overlay when triggered
    if (challengeActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090C))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0x26EF4444),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(challengeLabel, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Alarm Native Aktif • Selesaikan Push-Up", color = Color(0xFFFECACA), fontSize = 12.sp)
                        }
                        Text(
                            "$challengeCurrent/$challengeTarget",
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141720))
                            .border(3.dp, Color(0xFF3B82F6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Push-Up Challenge",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        "Tekan +1 Rep setiap 1 push-up selesai",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (challengeCurrent < challengeTarget) {
                                challengeCurrent += 1
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("+1 Rep Push-Up", fontWeight = FontWeight.Bold)
                    }

                    val unlocked = challengeCurrent >= challengeTarget
                    Button(
                        enabled = unlocked,
                        onClick = {
                            context.stopService(Intent(context, AlarmSoundService::class.java))
                            challengeActive = false
                            challengeCurrent = 0
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            disabledContainerColor = Color(0xFF1F2937)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            if (unlocked) "Matikan Alarm" else "Kurang ${(challengeTarget - challengeCurrent).coerceAtLeast(0)}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFF08090C),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D0F14)) {
                val tabs = listOf(
                    Triple("Alarm", Icons.Default.Alarm, 0),
                    Triple("To-Do", Icons.Default.CheckBox, 1),
                    Triple("Galeri", Icons.Default.PhotoLibrary, 2),
                    Triple("Kalender", Icons.Default.CalendarMonth, 3)
                )
                tabs.forEach { (title, icon, idx) ->
                    NavigationBarItem(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(appTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Surface(
                    color = Color(0x2610B981),
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        "Native APK",
                        color = Color(0xFF6EE7B7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (activeTab) {
                0 -> AlarmTabContent(
                    nowStr = nowStr,
                    dateHumanStr = dateHumanStr,
                    ringtoneLabel = ringtoneLabel,
                    alarms = alarms,
                    newHour = newHour,
                    newMinute = newMinute,
                    newReps = newReps,
                    newLabel = newLabel,
                    selectedDays = selectedDays,
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onTestAlarm = {
                        challengeLabel = "Tes Alarm Push-Up"
                        challengeTarget = 5
                        challengeCurrent = 0
                        challengeActive = true
                        context.startForegroundService(
                            Intent(context, AlarmSoundService::class.java).apply {
                                putExtra("EXTRA_ALARM_LABEL", "Tes Alarm Push-Up")
                                putExtra("EXTRA_ALARM_REPS", 5)
                            }
                        )
                    },
                    onHourChange = { newHour = it },
                    onMinuteChange = { newMinute = it },
                    onRepsChange = { newReps = it },
                    onLabelChange = { newLabel = it },
                    onToggleDay = { d ->
                        selectedDays = if (selectedDays.contains(d)) selectedDays - d else selectedDays + d
                    },
                    onSaveAlarm = {
                        val timeStr = String.format(Locale.US, "%02d:%02d", newHour, newMinute)
                        val daysList = if (selectedDays.isEmpty()) listOf(1, 2, 3, 4, 5, 6, 0) else selectedDays.toList()
                        val item = NativeAlarm(
                            id = "alm_${System.currentTimeMillis()}",
                            time = timeStr,
                            reps = newReps,
                            label = newLabel.ifBlank { "Bangun & Push-Up" },
                            days = daysList,
                            enabled = true
                        )
                        alarms = listOf(item) + alarms
                        saveAlarms(prefs, alarms)
                        scheduleNativeAlarm(context, item)
                        newLabel = ""
                    },
                    onToggleAlarm = { id ->
                        alarms = alarms.map {
                            if (it.id == id) {
                                val updated = it.copy(enabled = !it.enabled)
                                if (updated.enabled) scheduleNativeAlarm(context, updated)
                                updated
                            } else it
                        }
                        saveAlarms(prefs, alarms)
                    },
                    onDeleteAlarm = { id ->
                        alarms = alarms.filterNot { it.id == id }
                        saveAlarms(prefs, alarms)
                    }
                )

                1 -> TodoTabContent(
                    selectedDate = selectedTodoDate,
                    onDateChange = { selectedTodoDate = it },
                    tasks = ensureTasksForDate(prefs, todoMap, selectedTodoDate) { todoMap = it },
                    newTodoText = newTodoText,
                    onNewTodoTextChange = { newTodoText = it },
                    onAddTodo = {
                        if (newTodoText.isNotBlank()) {
                            val current = todoMap[selectedTodoDate].orEmpty().toMutableList()
                            current.add(
                                NativeTodo(
                                    id = "todo_${System.currentTimeMillis()}",
                                    title = newTodoText.trim(),
                                    tag = "Tambahan",
                                    mandatory = false,
                                    completed = false
                                )
                            )
                            val updatedMap = todoMap.toMutableMap().apply { put(selectedTodoDate, current) }
                            todoMap = updatedMap
                            saveTodoMap(prefs, updatedMap)
                            newTodoText = ""
                        }
                    },
                    onToggleTodo = { taskId ->
                        val current = todoMap[selectedTodoDate].orEmpty().map {
                            if (it.id == taskId) it.copy(completed = !it.completed) else it
                        }
                        val updatedMap = todoMap.toMutableMap().apply { put(selectedTodoDate, current) }
                        todoMap = updatedMap
                        saveTodoMap(prefs, updatedMap)
                    }
                )

                2 -> GalleryTabContent(
                    items = galleryItems,
                    onUploadClick = { galleryPicker.launch(arrayOf("image/*", "video/*")) }
                )

                3 -> CalendarTabContent(
                    events = events,
                    newTitle = newEventTitle,
                    newDate = newEventDate,
                    newTime = newEventTime,
                    onTitleChange = { newEventTitle = it },
                    onDateChange = { newEventDate = it },
                    onTimeChange = { newEventTime = it },
                    onAddEvent = {
                        if (newEventTitle.isNotBlank()) {
                            val ev = NativeCalendarEvent(
                                id = "ev_${System.currentTimeMillis()}",
                                title = newEventTitle.trim(),
                                date = newEventDate,
                                time = newEventTime,
                                source = "Kalender Lokal"
                            )
                            events = listOf(ev) + events
                            saveEvents(prefs, events)
                            newEventTitle = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmTabContent(
    nowStr: String,
    dateHumanStr: String,
    ringtoneLabel: String,
    alarms: List<NativeAlarm>,
    newHour: Int,
    newMinute: Int,
    newReps: Int,
    newLabel: String,
    selectedDays: Set<Int>,
    onPickAudio: () -> Unit,
    onTestAlarm: () -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onLabelChange: (String) -> Unit,
    onToggleDay: (Int) -> Unit,
    onSaveAlarm: () -> Unit,
    onToggleAlarm: (String) -> Unit,
    onDeleteAlarm: (String) -> Unit
) {
    val dayNames = listOf(1 to "Sen", 2 to "Sel", 3 to "Rab", 4 to "Kam", 5 to "Jum", 6 to "Sab", 0 to "Min")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(
                color = Color(0xFF12141B),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("WAKTU SEKARANG", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(nowStr, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text(dateHumanStr, color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                    Button(
                        onClick = onTestAlarm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFFCD34D))
                            Text("Tes Alarm", color = Color(0xFFFCD34D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nada Dering Alarm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(ringtoneLabel, color = Color(0xFF9CA3AF), fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = onPickAudio, shape = RoundedCornerShape(12.dp)) {
                        Text("Pilih Audio", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tambah Alarm Push-Up Baru", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onHourChange((newHour + 1) % 24) }, modifier = Modifier.weight(1f)) {
                            Text("Jam: ${String.format(Locale.US, "%02d", newHour)} (+)")
                        }
                        OutlinedButton(onClick = { onMinuteChange((newMinute + 5) % 60) }, modifier = Modifier.weight(1f)) {
                            Text("Menit: ${String.format(Locale.US, "%02d", newMinute)} (+5)")
                        }
                        OutlinedButton(onClick = {
                            val next = listOf(5, 10, 15, 20, 30)
                            onRepsChange(next[(next.indexOf(newReps) + 1) % next.size])
                        }, modifier = Modifier.weight(1f)) {
                            Text("${newReps}x Push")
                        }
                    }

                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = onLabelChange,
                        placeholder = { Text("Label alarm (misal: Bangun Subuh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        dayNames.forEach { (idx, label) ->
                            val active = selectedDays.contains(idx)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color(0xFF2563EB) else Color(0xFF1A1D26))
                                    .clickable { onToggleDay(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (active) Color.White else Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onSaveAlarm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Simpan Alarm Native", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(alarms, key = { it.id }) { alarm ->
            Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${alarm.time} • ${alarm.reps}x Push-Up", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(alarm.label, color = Color(0xFFD1D5DB), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = alarm.enabled, onCheckedChange = { onToggleAlarm(alarm.id) })
                        IconButton(onClick = { onDeleteAlarm(alarm.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFF9CA3AF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoTabContent(
    selectedDate: String,
    onDateChange: (String) -> Unit,
    tasks: List<NativeTodo>,
    newTodoText: String,
    onNewTodoTextChange: (String) -> Unit,
    onAddTodo: () -> Unit,
    onToggleTodo: (String) -> Unit
) {
    val doneCount = tasks.count { it.completed }
    val total = tasks.size.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tanggal: $selectedDate", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$doneCount/${tasks.size}", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp))
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTodoText,
                onValueChange = onNewTodoTextChange,
                placeholder = { Text("Tambah tugas baru...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddTodo, shape = RoundedCornerShape(14.dp)) {
                Text("Tambah")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Surface(
                    color = Color(0xFF12141B),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onToggleTodo(task.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(checked = task.completed, onCheckedChange = { onToggleTodo(task.id) })
                        Column {
                            Text(
                                task.title,
                                color = if (task.completed) Color(0xFF6B7280) else Color.White,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(task.tag, color = Color(0xFF34D399), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTabContent(items: List<NativeGalleryItem>, onUploadClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Galeri Bukti Aktivitas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onUploadClick, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Upload", fontSize = 12.sp)
            }
        }

        if (items.isEmpty()) {
            Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Belum ada foto/video bukti tersimpan.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(16.dp), modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = item.uriString,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTabContent(
    events: List<NativeCalendarEvent>,
    newTitle: String,
    newDate: String,
    newTime: String,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onAddEvent: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tambah Agenda Kalender", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = newTitle, onValueChange = onTitleChange, placeholder = { Text("Judul agenda...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newDate, onValueChange = onDateChange, label = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = newTime, onValueChange = onTimeChange, label = { Text("Jam") }, singleLine = true, modifier = Modifier.weight(0.7f))
                }
                Button(onClick = onAddEvent, modifier = Modifier.fillMaxWidth()) {
                    Text("Simpan Agenda")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { ev ->
                Surface(color = Color(0xFF12141B), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(ev.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${ev.date} • ${ev.time} • ${ev.source}", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun scheduleNativeAlarm(context: Context, alarm: NativeAlarm) {
    try {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val parts = alarm.time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 5
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_ALARM_ID", alarm.id)
            putExtra("EXTRA_ALARM_LABEL", alarm.label)
            putExtra("EXTRA_ALARM_REPS", alarm.reps)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    } catch (_: Exception) {}
}

private fun ensureTasksForDate(
    prefs: android.content.SharedPreferences,
    currentMap: Map<String, List<NativeTodo>>,
    dateStr: String,
    onUpdateMap: (Map<String, List<NativeTodo>>) -> Unit
): List<NativeTodo> {
    if (currentMap.containsKey(dateStr)) return currentMap[dateStr].orEmpty()
    val list = mutableListOf(
        NativeTodo("${dateStr}_subuh", "Sholat Subuh", "Sholat Wajib", true, false),
        NativeTodo("${dateStr}_dzuhur", "Sholat Dzuhur", "Sholat Wajib", true, false),
        NativeTodo("${dateStr}_ashar", "Sholat Ashar", "Sholat Wajib", true, false),
        NativeTodo("${dateStr}_maghrib", "Sholat Maghrib", "Sholat Wajib", true, false),
        NativeTodo("${dateStr}_isya", "Sholat Isya", "Sholat Wajib", true, false),
        NativeTodo("${dateStr}_shopeefood", "Narik ShopeeFood", "Wajib Harian", true, false),
        NativeTodo("${dateStr}_video", "Bikin Video", "Wajib Harian", true, false),
        NativeTodo("${dateStr}_olahraga", "Olahraga Pemanasan", "Wajib Harian", true, false),
        NativeTodo("${dateStr}_ngaji", "Ngaji", "Wajib Harian", true, false)
    )
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.parse(dateStr)?.let { cal.time = it }
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    if (dow in Calendar.MONDAY..Calendar.FRIDAY) {
        list.add(NativeTodo("${dateStr}_kuliah", "Kuliah", "Senin–Jumat", true, false))
        list.add(NativeTodo("${dateStr}_tugas", "Tugas Kuliah", "Senin–Jumat", true, false))
    }
    val next = currentMap.toMutableMap().apply { put(dateStr, list) }
    onUpdateMap(next)
    saveTodoMap(prefs, next)
    return list
}

private fun loadAlarms(prefs: android.content.SharedPreferences): List<NativeAlarm> {
    val raw = prefs.getString("alarms_json", null) ?: return listOf(
        NativeAlarm("default-1", "04:45", 10, "Bangun Subuh & Push-Up", listOf(1, 2, 3, 4, 5, 6, 0), true)
    )
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val dArr = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until dArr.length()).map { dArr.getInt(it) }
            NativeAlarm(
                id = o.optString("id", "alm_$i"),
                time = o.optString("time", "05:00"),
                reps = o.optInt("reps", 10),
                label = o.optString("label", "Alarm"),
                days = days,
                enabled = o.optBoolean("enabled", true)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveAlarms(prefs: android.content.SharedPreferences, items: List<NativeAlarm>) {
    val arr = JSONArray()
    items.forEach { a ->
        arr.put(JSONObject().apply {
            put("id", a.id)
            put("time", a.time)
            put("reps", a.reps)
            put("label", a.label)
            put("days", JSONArray(a.days))
            put("enabled", a.enabled)
        })
    }
    prefs.edit().putString("alarms_json", arr.toString()).apply()
}

private fun loadTodoMap(prefs: android.content.SharedPreferences): Map<String, List<NativeTodo>> {
    val raw = prefs.getString("todos_json", "{}") ?: "{}"
    return try {
        val root = JSONObject(raw)
        val map = mutableMapOf<String, List<NativeTodo>>()
        root.keys().forEach { dateKey ->
            val arr = root.getJSONArray(dateKey)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NativeTodo(
                    id = o.optString("id", "t_$i"),
                    title = o.optString("title", ""),
                    tag = o.optString("tag", "Tambahan"),
                    mandatory = o.optBoolean("mandatory", false),
                    completed = o.optBoolean("completed", false)
                )
            }
            map[dateKey] = list
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun saveTodoMap(prefs: android.content.SharedPreferences, map: Map<String, List<NativeTodo>>) {
    val root = JSONObject()
    map.forEach { (dateKey, list) ->
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("tag", t.tag)
                put("mandatory", t.mandatory)
                put("completed", t.completed)
            })
        }
        root.put(dateKey, arr)
    }
    prefs.edit().putString("todos_json", root.toString()).apply()
}

private fun loadGallery(prefs: android.content.SharedPreferences): List<NativeGalleryItem> {
    val raw = prefs.getString("gallery_json", "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NativeGalleryItem(
                id = o.optString("id", "g_$i"),
                title = o.optString("title", "Bukti"),
                date = o.optString("date", ""),
                uriString = o.optString("uriString", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveGallery(prefs: android.content.SharedPreferences, items: List<NativeGalleryItem>) {
    val arr = JSONArray()
    items.forEach { g ->
        arr.put(JSONObject().apply {
            put("id", g.id)
            put("title", g.title)
            put("date", g.date)
            put("uriString", g.uriString)
        })
    }
    prefs.edit().putString("gallery_json", arr.toString()).apply()
}

private fun loadEvents(prefs: android.content.SharedPreferences): List<NativeCalendarEvent> {
    val raw = prefs.getString("events_json", "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NativeCalendarEvent(
                id = o.optString("id", "ev_$i"),
                title = o.optString("title", ""),
                date = o.optString("date", ""),
                time = o.optString("time", ""),
                source = o.optString("source", "Kalender Lokal")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveEvents(prefs: android.content.SharedPreferences, items: List<NativeCalendarEvent>) {
    val arr = JSONArray()
    items.forEach { e ->
        arr.put(JSONObject().apply {
            put("id", e.id)
            put("title", e.title)
            put("date", e.date)
            put("time", e.time)
            put("source", e.source)
        })
    }
    prefs.edit().putString("events_json", arr.toString()).apply()
}
