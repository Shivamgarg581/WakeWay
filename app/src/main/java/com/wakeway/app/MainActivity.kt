package com.wakeway.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wakeway.app.data.LocalStore
import com.wakeway.app.journey.JourneyTrackingService
import com.wakeway.app.model.*
import com.wakeway.app.network.ApiClient
import com.wakeway.app.ui.WakeWayTheme
import org.json.JSONObject
import java.util.concurrent.Executors

enum class Screen { HOME, SETUP, ACTIVE, HISTORY, SETTINGS, FAMILY, CHAT, SOCIAL, AI, WEATHER, TRAIN, ACCOUNT, PREMIUM, MAP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WakeWayTheme { WakeWayApp() } }
    }
}

@Composable
fun WakeWayApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { LocalStore(context) }
    val api = remember { ApiClient() }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var journey by remember { mutableStateOf(store.activeJourney()) }
    var selectedDestination by remember { mutableStateOf<Destination?>(null) }
    var selectedTransport by remember { mutableStateOf(TransportMode.TRAIN) }

    val locationPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    var pendingJourney by remember { mutableStateOf<Journey?>(null) }

    fun launchJourneyService(j: Journey) {
        store.saveJourney(j)
        journey = j
        val intent = Intent(context, JourneyTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        screen = Screen.ACTIVE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val requiredGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val notificationsGranted = Build.VERSION.SDK_INT < 33 || result[Manifest.permission.POST_NOTIFICATIONS] == true
        val pending = pendingJourney
        pendingJourney = null
        if (requiredGranted && notificationsGranted && pending != null) {
            launchJourneyService(pending)
        }
    }

    fun startJourney(j: Journey) {
        val missing = locationPermissions.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingJourney = j
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            launchJourneyService(j)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME }, label = { Text("Home") }, icon = { Text("⌂") })
                NavigationBarItem(screen == Screen.ACTIVE, { screen = Screen.ACTIVE }, label = { Text("Journey") }, icon = { Text("🧭") })
                NavigationBarItem(screen == Screen.HISTORY, { screen = Screen.HISTORY }, label = { Text("History") }, icon = { Text("◷") })
                NavigationBarItem(screen == Screen.SETTINGS, { screen = Screen.SETTINGS }, label = { Text("Settings") }, icon = { Text("⚙") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    journey = journey,
                    onStart = { screen = Screen.SETUP },
                    onDestination = { selectedDestination = it; screen = Screen.SETUP },
                    onOpen = { screen = it }
                )
                Screen.SETUP -> JourneySetupScreen(
                    initialDestination = selectedDestination,
                    initialTransport = selectedTransport,
                    onBack = { screen = Screen.HOME },
                    onStart = { d, t, alerts -> startJourney(LocalStore.newJourney(d, t, alerts)) },
                    onOpenMap = { screen = Screen.MAP },
                    onSelectedTransport = { selectedTransport = it }
                )
                Screen.ACTIVE -> ActiveJourneyScreen(
                    journey = journey,
                    onEnd = {
                        val svc = Intent(context, JourneyTrackingService::class.java).apply {
                            action = JourneyTrackingService.ACTION_STOP
                        }
                        context.startService(svc)
                        journey = store.activeJourney()
                        screen = Screen.HOME
                    },
                    onFamily = { screen = Screen.FAMILY },
                    onChat = { screen = Screen.CHAT }
                )
                Screen.HISTORY -> HistoryScreen(store.history())
                Screen.SETTINGS -> SettingsScreen(store, onBack = { screen = Screen.HOME }, onAccount = { screen = Screen.ACCOUNT })
                Screen.FAMILY -> FamilyScreen(api)
                Screen.CHAT -> ChatScreen(api)
                Screen.SOCIAL -> SocialScreen(api)
                Screen.AI -> AiScreen(api)
                Screen.WEATHER -> WeatherScreen(api)
                Screen.TRAIN -> TrainScreen(api)
                Screen.ACCOUNT -> AccountScreen(api)
                Screen.PREMIUM -> PremiumScreen()
                Screen.MAP -> MapScreen(destination = selectedDestination)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    journey: Journey?,
    onStart: () -> Unit,
    onDestination: (Destination) -> Unit,
    onOpen: (Screen) -> Unit
) {
    val saved = listOf(
        Destination("Kota Junction", "Kota, Rajasthan", 25.2138, 75.8648),
        Destination("Jaipur Junction", "Jaipur, Rajasthan", 26.9196, 75.7878)
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Good afternoon 👋", fontSize = 15.sp)
            Text("WakeWay", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Sleep. We'll wake you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("START JOURNEY", fontWeight = FontWeight.Bold)
            }
        }
        journey?.let {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("🔔 Active journey", fontWeight = FontWeight.Bold)
                        Text("${it.destination.name} • ${it.transport.label}")
                        Spacer(Modifier.height(8.dp))
                        Text("Alarm armed")
                    }
                }
            }
        }
        item { Text("Quick destinations", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(saved) { d ->
            OutlinedButton(
                onClick = { onDestination(d) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("⭐ ${d.name}", modifier = Modifier.weight(1f))
                Text("Set")
            }
        }
        item { Text("Tools", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTool("🚆", "Train") { onOpen(Screen.TRAIN) }
                SmallTool("☁️", "Weather") { onOpen(Screen.WEATHER) }
                SmallTool("🤖", "AI") { onOpen(Screen.AI) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTool("👨‍👩‍👧", "Family") { onOpen(Screen.FAMILY) }
                SmallTool("💬", "Chat") { onOpen(Screen.CHAT) }
                SmallTool("👤", "Account") { onOpen(Screen.ACCOUNT) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTool("🌐", "Social") { onOpen(Screen.SOCIAL) }
                SmallTool("⭐", "Premium") { onOpen(Screen.PREMIUM) }
                SmallTool("🔔", "Alerts") { onOpen(Screen.SETTINGS) }
            }
        }
    }
}

@Composable
private fun RowScope.SmallTool(icon: String, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 25.sp)
            Spacer(Modifier.height(5.dp))
            Text(label)
        }
    }
}

@Composable
private fun JourneySetupScreen(
    initialDestination: Destination?,
    initialTransport: TransportMode,
    onBack: () -> Unit,
    onStart: (Destination, TransportMode, List<JourneyAlert>) -> Unit,
    onOpenMap: () -> Unit,
    onSelectedTransport: (TransportMode) -> Unit
) {
    var name by remember { mutableStateOf(initialDestination?.name ?: "") }
    var address by remember { mutableStateOf(initialDestination?.address ?: "") }
    var lat by remember { mutableStateOf(initialDestination?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(initialDestination?.longitude?.toString() ?: "") }
    var transport by remember { mutableStateOf(initialTransport) }
    var wakeDistance by remember { mutableStateOf("2") }
    var finalDistance by remember { mutableStateOf("0.15") }
    var voice by remember { mutableStateOf(true) }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("←", fontSize = 28.sp, modifier = Modifier.padding(end = 10.dp))
                Text("Set destination", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedTextField(name, { name = it }, label = { Text("Destination name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(address, { address = it }, label = { Text("Address / station") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                Text("📍 Pick destination on map")
            }
        }
        item { Text("Travel mode", fontWeight = FontWeight.Bold) }
        item {
            LazyColumn(
                Modifier.height(170.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TransportMode.entries.toList()) { mode ->
                    FilterChip(
                        selected = mode == transport,
                        onClick = { transport = mode; onSelectedTransport(mode) },
                        label = { Text("${mode.emoji} ${mode.label}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item { Text("Wake-up rules", fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    wakeDistance,
                    { wakeDistance = it },
                    label = { Text("First alert km") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    finalDistance,
                    { finalDistance = it },
                    label = { Text("Final km") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice announcement", Modifier.weight(1f))
                Switch(voice, { voice = it })
            }
        }
        item {
            Button(
                onClick = {
                    val d = Destination(
                        name = name.ifBlank { "My destination" },
                        address = address,
                        latitude = lat.toDoubleOrNull() ?: 0.0,
                        longitude = lon.toDoubleOrNull() ?: 0.0
                    )
                    onStart(
                        d,
                        transport,
                        listOf(
                            JourneyAlert(AlertTrigger.DISTANCE, wakeDistance.toDoubleOrNull() ?: 2.0, "${wakeDistance} km warning"),
                            JourneyAlert(AlertTrigger.DISTANCE, finalDistance.toDoubleOrNull() ?: 0.15, "${finalDistance} km final")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) { Text("🔔 START JOURNEY", fontWeight = FontWeight.Bold) }
        }
        item {
            Text(
                "For a real trip, confirm the destination coordinates before starting. The alarm engine runs on-device so it can continue when the network disappears.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ActiveJourneyScreen(
    journey: Journey?,
    onEnd: () -> Unit,
    onFamily: () -> Unit,
    onChat: () -> Unit
) {
    if (journey == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active journey")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("🧭 ACTIVE JOURNEY", fontWeight = FontWeight.Bold)
            Text(journey.destination.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("${journey.transport.emoji} ${journey.transport.label}")
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("🔔 Alarm armed", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("2 km → wake-up warning")
                    Text("500 m → get ready")
                    Text("150 m → final alarm")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onFamily, modifier = Modifier.weight(1f)) { Text("👨‍👩‍👧 Share") }
                OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f)) { Text("💬 Chat") }
            }
        }
        item {
            OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                Text("END JOURNEY")
            }
        }
        item {
            Text("Keep location enabled and allow background/foreground location operation on your device for reliable tracking.", fontSize = 12.sp)
        }
    }
}

@Composable
private fun HistoryScreen(history: List<Journey>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("History", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Past journey and alarm results")
        }
        if (history.isEmpty()) {
            item { Text("No journeys yet.") }
        } else {
            items(history) { j ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${j.transport.emoji} ${j.destination.name}", fontWeight = FontWeight.Bold)
                        Text(j.destination.address)
                        Text("Status: ${j.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: LocalStore, onBack: () -> Unit, onAccount: () -> Unit) {
    val context = LocalContext.current

    var sleep by remember { mutableStateOf(store.setting("sleep", "true") == "true") }
    var vibration by remember { mutableStateOf(store.setting("vibration", "true") == "true") }
    var voice by remember { mutableStateOf(store.setting("voice", "true") == "true") }
    var announceEta by remember { mutableStateOf(store.setting("announce_eta", "true") == "true") }
    var repeatFinal by remember { mutableStateOf(store.setting("repeat_final", "true") == "true") }
    var autoShare by remember { mutableStateOf(store.setting("auto_share", "false") == "true") }
    var cloudSync by remember { mutableStateOf(store.setting("cloud_sync", "false") == "true") }
    var ai by remember { mutableStateOf(store.setting("ai", "true") == "true") }
    var weather by remember { mutableStateOf(store.setting("weather", "true") == "true") }
    var trains by remember { mutableStateOf(store.setting("trains", "true") == "true") }
    var history by remember { mutableStateOf(store.setting("history", "true") == "true") }
    var dark by remember { mutableStateOf(store.setting("dark", "false") == "true") }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item { Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item { Text("Every important WakeWay control stays in one place.") }

        item { Text("Alarm & sleep", fontWeight = FontWeight.Bold) }
        item { SettingRow("😴 Sleep Mode", "Stronger progressive alerts while you sleep.", sleep) { sleep = it; store.saveSetting("sleep", it.toString()) } }
        item { SettingRow("📳 Vibration", "Vibrate on wake-up and final alerts.", vibration) { vibration = it; store.saveSetting("vibration", it.toString()) } }
        item { SettingRow("🔊 Voice", "Speak the destination alert aloud.", voice) { voice = it; store.saveSetting("voice", it.toString()) } }
        item { SettingRow("🕐 ETA announcements", "Allow travel progress announcements.", announceEta) { announceEta = it; store.saveSetting("announce_eta", it.toString()) } }
        item { SettingRow("🔁 Repeat final alarm", "Repeat the final alarm when enabled.", repeatFinal) { repeatFinal = it; store.saveSetting("repeat_final", it.toString()) } }

        item { Text("Journey & sharing", fontWeight = FontWeight.Bold) }
        item { SettingRow("👨‍👩‍👧 Auto-share journey", "Use your configured family sharing preferences.", autoShare) { autoShare = it; store.saveSetting("auto_share", it.toString()) } }
        item { SettingRow("☁️ Cloud sync", "Sync journeys and saved places when signed in.", cloudSync) { cloudSync = it; store.saveSetting("cloud_sync", it.toString()) } }
        item { SettingRow("🗂 Save history", "Keep completed journey results locally.", history) { history = it; store.saveSetting("history", it.toString()) } }

        item { Text("Smart services", fontWeight = FontWeight.Bold) }
        item { SettingRow("🤖 AI assistant", "Enable AI travel help when configured.", ai) { ai = it; store.saveSetting("ai", it.toString()) } }
        item { SettingRow("🌦 Weather", "Enable weather-aware journey information.", weather) { weather = it; store.saveSetting("weather", it.toString()) } }
        item { SettingRow("🚆 Live trains", "Enable live train integration when available.", trains) { trains = it; store.saveSetting("trains", it.toString()) } }

        item { Text("Appearance", fontWeight = FontWeight.Bold) }
        item { SettingRow("🌙 Dark mode", "Switch the app appearance preference.", dark) { dark = it; store.saveSetting("dark", it.toString()) } }

        item { Text("System access", fontWeight = FontWeight.Bold) }
        item {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔔 Notification settings") }
        }
        item {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔋 Battery optimisation settings") }
        }
        item {
            OutlinedButton(
                onClick = {
                    android.widget.Toast.makeText(context, "WakeWay test alert", android.widget.Toast.LENGTH_SHORT).show()
                    if (vibration) {
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(
                            android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 500), -1)
                        )
                    }
            },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🧪 Test wake-up alert") }
        }

        item { Text("Account & privacy", fontWeight = FontWeight.Bold) }
        item { OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("👤 Account / Login") } }
        item {
            Text(
                "Core destination monitoring is designed to work on-device. Cloud features remain optional and require the configured backend.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(value, onChange)
        }
    }
}

@Composable
private fun AccountScreen(api: ApiClient) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Use email/password for the zero-cost starter. Google OAuth can be enabled in Supabase later.")
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val body = JSONObject().put("email", email).put("password", password)
                Executors.newSingleThreadExecutor().execute {
                    val r = api.post("/api/auth/signup", body)
                    r.optString("access_token").takeIf { it.isNotBlank() }?.let { store.saveAccessToken(it) }
                    result = r.optString("message", r.optString("error", r.toString()))
                }
            }) { Text("Sign up") }
            OutlinedButton(onClick = {
                val body = JSONObject().put("email", email).put("password", password)
                Executors.newSingleThreadExecutor().execute {
                    val r = api.post("/api/auth/signin", body)
                    r.optString("access_token").takeIf { it.isNotBlank() }?.let { store.saveAccessToken(it) }
                    result = r.optString("message", r.optString("error", r.toString()))
                }
            }) { Text("Sign in") }
        }
        if (result.isNotBlank()) Text(result)
        Text("Cloud login is disabled until the backend URL is configured in local.properties.", fontSize = 12.sp)
    }
}

@Composable
private fun FamilyScreen(api: ApiClient) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Family Tracking", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Invite family with a code instead of paying for SMS.")
        Button(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val r = api.post("/api/family/create", JSONObject().put("name", "My Family"), store.accessToken())
                result = r.optString("invite_code", r.optString("error", "Unable to create family"))
            }
        }) { Text("Create Family") }
        OutlinedTextField(code, { code = it }, label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val r = api.post("/api/family/join", JSONObject().put("invite_code", code), store.accessToken())
                result = r.optString("message", r.optString("error", r.toString()))
            }
        }) { Text("Join Family") }
        Text("Status: $result")
    }
}

@Composable
private fun ChatScreen(api: ApiClient) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var message by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Chat uses your cloud backend when configured.") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chat", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Card { Text(result, Modifier.padding(16.dp)) }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(message, { message = it }, label = { Text("Message") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                Executors.newSingleThreadExecutor().execute {
                    val r = api.post("/api/chat/send", JSONObject().put("message", message), store.accessToken())
                    result = r.optString("message", r.optString("error", r.toString()))
                }
            }) { Text("➤") }
        }
    }
}

@Composable
private fun AiScreen(api: ApiClient) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Ask me to plan a journey, explain your alerts, or simplify travel information.") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI Journey Assistant", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Ask WakeWay AI") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val r = api.post("/api/ai", JSONObject().put("prompt", prompt))
                answer = r.optString("answer", r.optString("error", "AI unavailable; basic app still works."))
            }
        }) { Text("ASK AI") }
        Card { Text(answer, Modifier.padding(16.dp)) }
    }
}

@Composable
private fun WeatherScreen(api: ApiClient) {
    var lat by remember { mutableStateOf("25.2138") }
    var lon by remember { mutableStateOf("75.8648") }
    var result by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Weather", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
            OutlinedTextField(lon, { lon = it }, label = { Text("Lon") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val r = api.get("/api/weather", query = mapOf("lat" to lat, "lon" to lon))
                result = r.toString(2)
            }
        }) { Text("GET WEATHER") }
        LazyColumn { item { Text(result) } }
    }
}

@Composable
private fun TrainScreen(api: ApiClient) {
    var train by remember { mutableStateOf("12919") }
    var result by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Live Train", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(train, { train = it }, label = { Text("Train number") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val r = api.get("/api/train", query = mapOf("train" to train))
                result = r.toString(2)
            }
        }) { Text("CHECK TRAIN") }
        LazyColumn { item { Text(result) } }
    }
}

@Composable
private fun SocialScreen(api: ApiClient) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var username by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Social features: friends, requests, privacy, block/report and activity are part of the app structure. Cloud sync is enabled after backend setup.") }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Social", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Friends, requests and journey sharing without making location public by default.")
        OutlinedTextField(
            username,
            { username = it },
            label = { Text("Friend username") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            result = if (username.isBlank()) "Enter a username first." else "Friend request prepared for $username. Connect the social endpoint after backend setup."
        }) {
            Text("Send friend request")
        }
        OutlinedButton(onClick = {
            store.saveSetting("social_share", "true")
            result = "Journey sharing preference enabled locally. No location is shared until a supported backend session is active."
        }) {
            Text("Enable journey sharing")
        }
        Card { Text(result, Modifier.padding(16.dp)) }
    }
}

@Composable
private fun PremiumScreen() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WakeWay Premium", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Premium architecture is included now so paid features can be added without rebuilding the app.")
        listOf(
            "Advanced alert profiles",
            "Expanded saved places",
            "Enhanced AI travel help",
            "Premium map experience",
            "Advanced family controls",
            "Detailed journey statistics"
        ).forEach {
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(it)
                }
            }
        }
        Text(
            "Google Play Billing is intentionally kept server-verifiable. This starter does not pretend a subscription exists until billing products and verification are configured.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MapScreen(destination: Destination?) {
    val context = LocalContext.current
    val webView = remember {
        android.webkit.WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
    }
    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize(),
        update = { wv ->
            val lat = destination?.latitude ?: 25.2138
            val lon = destination?.longitude ?: 75.8648
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <style>html,body,#map{height:100%;margin:0}</style>
                </head><body><div id="map"></div>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <script>
                const map=L.map('map').setView([$lat,$lon],13);
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
                    maxZoom:19, attribution:'© OpenStreetMap contributors'
                }).addTo(map);
                L.marker([$lat,$lon]).addTo(map).bindPopup('${(destination?.name ?: "Destination").replace("'", "\\'")}').openPopup();
                </script></body></html>
            """.trimIndent()
            wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
        }
    )
}
