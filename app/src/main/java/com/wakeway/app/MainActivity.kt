package com.wakeway.app

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.wakeway.app.data.LocalStore
import com.wakeway.app.journey.JourneyTrackingService
import com.wakeway.app.model.AlertTrigger
import com.wakeway.app.model.Destination
import com.wakeway.app.model.Journey
import com.wakeway.app.model.JourneyAlert
import com.wakeway.app.model.JourneyStatus
import com.wakeway.app.model.TransportMode
import com.wakeway.app.network.ApiClient
import com.wakeway.app.ui.WakeWayTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private enum class Screen {
    HOME, SETUP, ACTIVE, HISTORY, EXPLORE, TRAIN, RAIL_EXTRAS, WEATHER, AI, FAMILY, FRIENDS, CHAT, ACCOUNT, SETTINGS, PREMIUM, MAP, SAVED_PLACES
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WakeWayTheme {
                WakeWayApp()
            }
        }
    }
}

@Composable
private fun WakeWayApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val api = remember { ApiClient(context) }
    val executor = remember { Executors.newCachedThreadPool() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val navigationStack = remember { mutableStateListOf(Screen.HOME) }
    val screen: Screen get() = navigationStack.last()
    var journey by remember { mutableStateOf(store.activeJourney()) }
    var selectedDestination by remember { mutableStateOf<Destination?>(null) }
    var selectedTransport by remember { mutableStateOf(TransportMode.TRAIN) }
    var backendOnline by remember { mutableStateOf(false) }
    var backendMessage by remember { mutableStateOf("Checking services…") }
    var darkMode by remember { mutableStateOf(store.setting("dark", "false") == "true") }

    fun navigateTo(newScreen: Screen) {
        if (navigationStack.lastOrNull() != newScreen) navigationStack.add(newScreen)
    }

    fun resetTo(newScreen: Screen) {
        navigationStack.clear()
        navigationStack.add(newScreen)
    }

    fun goBack() {
        if (navigationStack.size > 1) navigationStack.removeAt(navigationStack.lastIndex)
    }

    LaunchedEffect(api.backendUrl()) {
        executor.execute {
            val response = api.health()
            mainHandler.post {
                backendOnline = response.optBoolean("ok", false)
                backendMessage = when {
                    backendOnline -> "Backend connected"
                    api.backendUrl().isBlank() -> "Local-first mode"
                    else -> response.optString("error", "Backend unavailable")
                }
            }
        }
    }

    BackHandler(enabled = navigationStack.size > 1) {
        goBack()
    }

    val locationPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    var pendingJourney by remember { mutableStateOf<Journey?>(null) }

    fun startService(j: Journey) {
        store.saveJourney(j)
        journey = j
        executor.execute {
            val token = store.accessToken()
            val body = JSONObject().apply {
                put("id", j.id)
                put("destination_name", j.destination.name)
                put("destination_address", j.destination.address)
                put("destination_lat", j.destination.latitude)
                put("destination_lon", j.destination.longitude)
                put("transport_mode", j.transport.name)
                put("started_at", java.time.Instant.ofEpochMilli(j.startedAt).toString())
                put("status", "active")
            }
            if (!token.isNullOrBlank() && api.isConfigured()) {
                api.sendJourney(body, token)
            }
        }

        val intent = Intent(context, JourneyTrackingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            navigateTo(Screen.ACTIVE)
        } catch (error: Exception) {
            store.clearActiveJourney()
            journey = null
            Toast.makeText(
                context,
                "WakeWay couldn't start journey tracking. Enable Location and Notifications first.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val notificationGranted =
            Build.VERSION.SDK_INT < 33 ||
                result[Manifest.permission.POST_NOTIFICATIONS] == true

        val pending = pendingJourney
        pendingJourney = null

        if (locationGranted && notificationGranted && pending != null) {
            startService(pending)
        } else if (pending != null) {
            Toast.makeText(
                context,
                "WakeWay needs Location and Notifications permission to monitor your stop.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun beginJourney(j: Journey) {
        val locationManager = context.getSystemService(LocationManager::class.java)
        val locationEnabled = runCatching {
            (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) ||
                (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
        }.getOrDefault(false)

        if (!locationEnabled) {
            pendingJourney = j
            Toast.makeText(
                context,
                "Turn on Location first so WakeWay can monitor your journey.",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }

        val missing = locationPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startService(j)
        } else {
            pendingJourney = j
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun open(newScreen: Screen) {
        navigateTo(newScreen)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = when (screen) {
                            Screen.HOME -> "WakeWay"
                            Screen.SETUP -> "Set your destination"
                            Screen.ACTIVE -> "Journey in progress"
                            Screen.HISTORY -> "Journey history"
                            Screen.EXPLORE -> "Explore"
                            Screen.TRAIN -> "Live trains"
                            Screen.RAIL_EXTRAS -> "Rail extras"
                            Screen.WEATHER -> "Weather"
                            Screen.AI -> "WakeWay AI"
                            Screen.FAMILY -> "Family"
                            Screen.FRIENDS -> "Friends"
                            Screen.CHAT -> "Chat"
                            Screen.ACCOUNT -> "Account"
                            Screen.SETTINGS -> "Settings"
                            Screen.PREMIUM -> "Premium"
                            Screen.MAP -> "Destination map"
                            Screen.SAVED_PLACES -> "Saved places"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (screen == Screen.HOME) 24.sp else 21.sp
                    )
                    if (screen == Screen.HOME) {
                        Text(
                            "Never miss your stop.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = screen == Screen.HOME,
                    onClick = { resetTo(Screen.HOME) },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = screen == Screen.ACTIVE || screen == Screen.SETUP,
                    onClick = { resetTo(if (journey != null) Screen.ACTIVE else Screen.SETUP) },
                    icon = { Icon(Icons.Outlined.Explore, contentDescription = "Journey") },
                    label = { Text("Journey") }
                )
                NavigationBarItem(
                    selected = screen == Screen.HISTORY,
                    onClick = { resetTo(Screen.HISTORY) },
                    icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = screen == Screen.EXPLORE,
                    onClick = { resetTo(Screen.EXPLORE) },
                    icon = { Icon(Icons.Outlined.Map, contentDescription = "Explore") },
                    label = { Text("Explore") }
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { resetTo(Screen.SETTINGS) },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally(initialOffsetX = { it / 5 })) togetherWith
                        (fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 5 }))
                },
                label = "screen_transition"
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.HOME -> HomeScreen(
                        journey = journey,
                        backendOnline = backendOnline,
                        backendMessage = backendMessage,
                        onStart = { navigateTo(Screen.SETUP) },
                        onActive = { navigateTo(Screen.ACTIVE) },
                        onOpen = ::open
                    )
                    Screen.SETUP -> SetupScreen(
                        initialDestination = selectedDestination,
                        initialTransport = selectedTransport,
                        api = api,
                        store = store,
                        onDestination = { selectedDestination = it },
                        onTransport = { selectedTransport = it },
                        onMap = { navigateTo(Screen.MAP) },
                        onStart = { destination, transport, alerts ->
                            beginJourney(LocalStore.newJourney(destination, transport, alerts))
                        }
                    )
                    Screen.ACTIVE -> ActiveScreen(
                        journey = journey,
                        store = store,
                        onEnd = {
                            val stop = Intent(context, JourneyTrackingService::class.java).apply {
                                action = JourneyTrackingService.ACTION_STOP
                            }
                            runCatching { context.startService(stop) }
                            journey = null
                            resetTo(Screen.HOME)
                        },
                        onFamily = { navigateTo(Screen.FAMILY) },
                        onChat = { navigateTo(Screen.CHAT) }
                    )
                    Screen.HISTORY -> HistoryScreen(store.history())
                    Screen.EXPLORE -> ExploreScreen(
                        backendOnline = backendOnline,
                        onOpen = ::open
                    )
                    Screen.TRAIN -> TrainScreen(api)
                    Screen.RAIL_EXTRAS -> RailExtrasScreen(api)
                    Screen.WEATHER -> WeatherScreen(api, selectedDestination)
                    Screen.AI -> AiScreen(api)
                    Screen.FAMILY -> FamilyScreen(api, store)
                    Screen.FRIENDS -> FriendsScreen(api, store)
                    Screen.CHAT -> ChatScreen(api, store)
                    Screen.ACCOUNT -> AccountScreen(
                        api = api,
                        store = store,
                        onProfileUpdated = { }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        api = api,
                        store = store,
                        onAccount = { navigateTo(Screen.ACCOUNT) }
                    )
                    Screen.PREMIUM -> PremiumScreen(api, store)
                    Screen.SAVED_PLACES -> SavedPlacesScreen(api, store) { destination ->
                        selectedDestination = destination
                        navigateTo(Screen.SETUP)
                    }
                    Screen.MAP -> MapScreen(selectedDestination)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    journey: Journey?,
    backendOnline: Boolean,
    backendMessage: String,
    onStart: () -> Unit,
    onActive: () -> Unit,
    onOpen: (Screen) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "WakeWay",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Destination alarm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onOpen(Screen.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Never miss your stop.",
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Tell WakeWay where you're going. We'll keep watch while you sleep.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ),
                        RoundedCornerShape(30.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "WAKE BEFORE ARRIVAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Set a destination, choose your alerts, and relax.",
                                fontSize = 22.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp).size(28.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("SET DESTINATION", fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            label = if (backendOnline) "Cloud connected" else "Local-first",
                            good = backendOnline
                        )
                        StatusPill(
                            label = if (journey != null) "Journey active" else "Ready",
                            good = journey != null
                        )
                    }
                }
            }
        }

        journey?.let { active ->
            item {
                ElevatedCard(
                    onClick = onActive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    Icons.Outlined.Explore,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "JOURNEY ACTIVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    active.destination.name,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                        }
                        Text(
                            active.transport.label + " • " + active.alerts.size + " alerts armed",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { SectionTitle("Everything you need on the way") }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(
                    icon = Icons.Outlined.DirectionsRailway,
                    title = "Trains",
                    subtitle = "Live rail status",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.TRAIN) }
                FeatureCard(
                    icon = Icons.Outlined.Cloud,
                    title = "Weather",
                    subtitle = "At your destination",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.WEATHER) }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI",
                    subtitle = "Ask WakeWay",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.AI) }
                FeatureCard(
                    icon = Icons.Outlined.FamilyRestroom,
                    title = "Family",
                    subtitle = "Private sharing",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.FAMILY) }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(
                    icon = Icons.Outlined.Group,
                    title = "Friends",
                    subtitle = "Requests & chat",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.FRIENDS) }
                FeatureCard(
                    icon = Icons.Outlined.StarOutline,
                    title = "Premium",
                    subtitle = "More control",
                    modifier = Modifier.weight(1f)
                ) { onOpen(Screen.PREMIUM) }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                onClick = { onOpen(Screen.ACCOUNT) }
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(9.dp).size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Quick account access", fontWeight = FontWeight.Bold)
                        Text(
                            "Sign up or sign in to unlock cloud sync, friends and family.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
                )
            ) {
                Row(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Local-first reliability", fontWeight = FontWeight.Bold)
                        Text(
                            "The core destination alarm stays on-device. Cloud features are an enhancement, not a dependency.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, good: Boolean) {
    AssistChip(
        onClick = { },
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                if (good) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp).size(21.dp)
                )
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SetupScreen(
    initialDestination: Destination?,
    initialTransport: TransportMode,
    api: ApiClient,
    store: LocalStore,
    onDestination: (Destination) -> Unit,
    onTransport: (TransportMode) -> Unit,
    onMap: () -> Unit,
    onStart: (Destination, TransportMode, List<JourneyAlert>) -> Unit
) {
    var search by remember { mutableStateOf(initialDestination?.name ?: "") }
    var results by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var selected by remember { mutableStateOf(initialDestination) }
    var transport by remember { mutableStateOf(initialTransport) }
    var firstAlert by remember { mutableStateOf("2.0") }
    var readyAlert by remember { mutableStateOf("0.5") }
    var finalAlert by remember { mutableStateOf("0.15") }
    var timeAlert by remember { mutableStateOf("15") }
    var voice by remember { mutableStateOf(store.setting("voice", "true") == "true") }
    var vibration by remember { mutableStateOf(store.setting("vibration", "true") == "true") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("") }

    fun searchPlaces(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            loading = false
            return
        }
        loading = true
        Executors.newSingleThreadExecutor().execute {
            val response = api.searchPlaces(trimmed)
            val parsed = mutableListOf<Destination>()
            val array = response.optJSONArray("results") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optDouble("latitude", Double.NaN)
                val lon = item.optDouble("longitude", Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    parsed += Destination(
                        item.optString("shortName").ifBlank { item.optString("name") },
                        item.optString("address").ifBlank { item.optString("name") },
                        lat,
                        lon
                    )
                }
            }
            Handler(Looper.getMainLooper()).post {
                loading = false
                if (trimmed == search.trim()) {
                    results = parsed
                    if (parsed.isEmpty() && response.has("error")) {
                        message = apiFriendlyError(response, "No destinations found.")
                    } else {
                        message = ""
                    }
                }
            }
        }
    }

    LaunchedEffect(search) {
        delay(320)
        searchPlaces(search)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Where are you going?",
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Search anything from a railway station to a city, hotel or landmark.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(10.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            message = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("Search destination") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else if (search.isNotBlank()) {
                                IconButton(onClick = {
                                    search = ""
                                    results = emptyList()
                                    message = ""
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    if (results.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        results.take(6).forEach { destination ->
                            ElevatedCard(
                                onClick = {
                                    selected = destination
                                    search = destination.name
                                    results = emptyList()
                                    message = ""
                                    onDestination(destination)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp).size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            destination.name,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            destination.address,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(message, fontSize = 13.sp)
                    }
                }
            }
        }

        selected?.takeIf { it.name.isNotBlank() }?.let { destination ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(10.dp).size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DESTINATION", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(destination.name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(
                            destination.address,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Map, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Map")
                            }
                            OutlinedButton(
                                onClick = {
                                    val token = store.accessToken()
                                    if (token.isNullOrBlank()) {
                                        savedMessage = "Sign in from Account to save destinations."
                                        return@OutlinedButton
                                    }
                                    saving = true
                                    Executors.newSingleThreadExecutor().execute {
                                        val response = api.savePlace(
                                            JSONObject()
                                                .put("name", destination.name)
                                                .put("address", destination.address)
                                                .put("latitude", destination.latitude)
                                                .put("longitude", destination.longitude),
                                            token
                                        )
                                        Handler(Looper.getMainLooper()).post {
                                            saving = false
                                            savedMessage = if (response.has("error")) {
                                                apiFriendlyError(response, "Could not save destination.")
                                            } else {
                                                "Saved to your destinations."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Save")
                            }
                        }
                        if (savedMessage.isNotBlank()) {
                            Text(
                                savedMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle("How will you travel?") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.toList().chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { mode ->
                            FilterChip(
                                selected = transport == mode,
                                onClick = {
                                    transport = mode
                                    onTransport(mode)
                                },
                                label = {
                                    Text(
                                        mode.emoji + " " + mode.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            SectionTitle("Wake-up sequence")
            Text(
                "WakeWay can warn you progressively so you have time to get ready.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlertField("First warning", "km", firstAlert) { firstAlert = it }
                    AlertField("Get ready", "km", readyAlert) { readyAlert = it }
                    AlertField("Final arrival", "km", finalAlert) { finalAlert = it }
                    AlertField("Time backup", "min", timeAlert) { timeAlert = it }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow("Voice announcements", voice) { voice = it }
                    androidx.compose.material3.HorizontalDivider()
                    ToggleRow("Vibration", vibration) { vibration = it }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val destination = selected?.takeIf { it.name.isNotBlank() }
                    if (destination == null) {
                        message = "Choose a destination from the predictions first."
                        return@Button
                    }
                    val first = firstAlert.toDoubleOrNull()
                    val ready = readyAlert.toDoubleOrNull()
                    val finalKm = finalAlert.toDoubleOrNull()
                    val backup = timeAlert.toDoubleOrNull()
                    if (first == null || ready == null || finalKm == null || backup == null ||
                        first <= 0 || ready <= 0 || finalKm <= 0 || backup <= 0
                    ) {
                        message = "Enter valid positive alert values."
                        return@Button
                    }
                    if (finalKm >= ready || ready >= first) {
                        message = "Use descending distances: first warning > get ready > final arrival."
                        return@Button
                    }
                    store.saveSetting("voice", voice.toString())
                    store.saveSetting("vibration", vibration.toString())
                    onStart(
                        destination,
                        transport,
                        listOf(
                            JourneyAlert(AlertTrigger.DISTANCE, first, "First warning"),
                            JourneyAlert(AlertTrigger.DISTANCE, ready, "Get ready"),
                            JourneyAlert(AlertTrigger.DISTANCE, finalKm, "Final arrival"),
                            JourneyAlert(AlertTrigger.TIME, backup, "Time backup")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(19.dp)
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ARM DESTINATION ALARM", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AlertField(
    title: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(title) },
            leadingIcon = {
                Icon(
                    if (suffix == "km") Icons.Outlined.Explore else Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        Box(
            Modifier
                .width(76.dp)
                .height(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(suffix, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ActiveScreen(
    journey: Journey?,
    store: LocalStore,
    onEnd: () -> Unit,
    onFamily: () -> Unit,
    onChat: () -> Unit
) {
    if (journey == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(8.dp))
                Text("No active journey", fontWeight = FontWeight.Bold)
                Text("Create a destination alarm from Journey.")
            }
        }
        return
    }

    var snapshot by remember(journey.id) { mutableStateOf(store.trackingSnapshot()) }

    LaunchedEffect(journey.id) {
        while (true) {
            snapshot = store.trackingSnapshot()
            delay(1000)
        }
    }

    val distanceText = snapshot?.let {
        val meters = it.optDouble("distance_m", Double.NaN)
        when {
            meters.isNaN() -> "Waiting for GPS…"
            meters >= 1000 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
            else -> "${meters.toInt()} m"
        }
    } ?: "Waiting for GPS…"

    val etaText = snapshot?.let {
        val eta = it.optInt("eta_min", -1)
        if (eta > 0) "≈ ${eta} min" else "Calculating"
    } ?: "Calculating"

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                        ) {
                            Icon(
                                Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("JOURNEY ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                journey.destination.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        MetricCard("Distance", distanceText, Modifier.weight(1f))
                        MetricCard("ETA", etaText, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(journey.transport.label, true)
                        StatusPill(
                            snapshot?.let { "GPS ±${it.optDouble("accuracy_m", 0.0).toInt()} m" } ?: "GPS waiting",
                            snapshot != null
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Wake-up sequence", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    journey.alerts.forEach { rule ->
                        val valueText = if (rule.trigger == AlertTrigger.DISTANCE) {
                            if (rule.value >= 1.0)
                                String.format(Locale.US, "%.1f km", rule.value)
                            else
                                String.format(Locale.US, "%.0f m", rule.value * 1000.0)
                        } else {
                            String.format(Locale.US, "%.0f min after start", rule.value)
                        }
                        AlertRow(
                            valueText,
                            rule.label,
                            if (rule.trigger == AlertTrigger.DISTANCE) "DISTANCE" else "TIME"
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Reliability", fontWeight = FontWeight.Bold)
                    Text("Keep Location enabled and allow WakeWay to run as a foreground service.", fontSize = 13.sp)
                    Text("For long journeys, remove battery restrictions if your phone applies them.", fontSize = 13.sp)
                    Text("The alarm stays on-device even when cloud sync is unavailable.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onFamily, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FamilyRestroom, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Family")
                }
                OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f)) {
                    Text("Chat")
                }
            }
        }

        item {
            Button(
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("END JOURNEY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AlertRow(distance: String, label: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                if (icon == "DISTANCE") Icons.Outlined.Explore else Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(distance, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryScreen(history: List<Journey>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Your journeys", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Completed and cancelled destination alarms stay on-device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (history.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗺", fontSize = 42.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No journeys yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Start your first WakeWay journey and it will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(history) { item ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.transport.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.destination.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(
                                    item.destination.address,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(item.status.name.lowercase().replaceFirstChar { it.uppercase() }, item.status == JourneyStatus.COMPLETED)
                        }
                        Text(
                            SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(item.startedAt)),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreScreen(
    backendOnline: Boolean,
    onOpen: (Screen) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Explore", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Your journey, tools and travel support in one place.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (backendOnline)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (backendOnline) Icons.Outlined.CheckCircle else Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (backendOnline) "Cloud features connected" else "Local-first mode",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (backendOnline) "AI, account, social and rail services are available from the backend."
                            else "Your destination alarm still works without cloud services.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { SectionTitle("Travel") }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(Icons.Outlined.DirectionsRailway, "Trains", "Live rail", Modifier.weight(1f)) {
                    onOpen(Screen.TRAIN)
                }
                FeatureCard(Icons.Outlined.Cloud, "Weather", "Forecast", Modifier.weight(1f)) {
                    onOpen(Screen.WEATHER)
                }
            }
        }

        item { SectionTitle("People & assistant") }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(Icons.Outlined.AutoAwesome, "AI", "Travel co-pilot", Modifier.weight(1f)) {
                    onOpen(Screen.AI)
                }
                FeatureCard(Icons.Outlined.FamilyRestroom, "Family", "Private sharing", Modifier.weight(1f)) {
                    onOpen(Screen.FAMILY)
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(Icons.Outlined.Group, "Friends", "Requests", Modifier.weight(1f)) {
                    onOpen(Screen.FRIENDS)
                }
                FeatureCard(Icons.Outlined.Person, "Account", "Profile & sync", Modifier.weight(1f)) {
                    onOpen(Screen.ACCOUNT)
                }
            }
        }

        item { SectionTitle("Rail extras") }
        item {
            FeatureCard(
                Icons.Outlined.DirectionsRailway,
                "Fare & PNR",
                "Advanced rail tools",
                Modifier.fillMaxWidth()
            ) {
                onOpen(Screen.RAIL_EXTRAS)
            }
        }

        item { SectionTitle("Your places") }
        item {
            FeatureCard(Icons.Outlined.LocationOn, "Saved places", "Quick destinations", Modifier.fillMaxWidth()) {
                onOpen(Screen.SAVED_PLACES)
            }
        }

        item { SectionTitle("More") }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FeatureCard(Icons.Outlined.StarOutline, "Premium", "Plan-ready", Modifier.weight(1f)) {
                    onOpen(Screen.PREMIUM)
                }
                FeatureCard(Icons.Outlined.Map, "Map", "Destination", Modifier.weight(1f)) {
                    onOpen(Screen.MAP)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    icon: String,
    title: String,
    subtitle: String,
    screen: Screen,
    onOpen: (Screen) -> Unit
) {
    ElevatedCard(
        onClick = { onOpen(screen) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 26.sp)
        }
    }
}

@Composable
private fun TrainScreen(api: ApiClient) {
    var trainNumber by remember { mutableStateOf("") }
    var stationQuery by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("") }
    var fromCode by remember { mutableStateOf("") }
    var toCode by remember { mutableStateOf("") }
    var stationSuggestions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(stationQuery) {
        delay(320)
        val q = stationQuery.trim()
        if (q.length < 2) {
            stationSuggestions = emptyList()
            return@LaunchedEffect
        }
        Executors.newSingleThreadExecutor().execute {
            val response = api.trainStations(q)
            val data = response.optJSONArray("data") ?: JSONArray()
            val parsed = mutableListOf<Pair<String, String>>()
            for (i in 0 until data.length()) {
                val row = data.optJSONObject(i) ?: continue
                parsed += (row.optString("code") to row.optString("name"))
            }
            Handler(Looper.getMainLooper()).post {
                stationSuggestions = parsed.take(8)
                if (parsed.isEmpty() && response.has("error")) {
                    message = apiFriendlyError(response, "No station matches found.")
                }
            }
        }
    }

    fun runRequest(block: () -> JSONObject) {
        loading = true
        message = ""
        Executors.newSingleThreadExecutor().execute {
            val response = block()
            Handler(Looper.getMainLooper()).post {
                result = response
                loading = false
                if (response.has("error")) {
                    message = apiFriendlyError(response, "Rail service returned an error.")
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Trains", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Live rail tools powered through WakeWay's backend.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Train live", "Station", "Between").forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            result = null
                            message = ""
                        },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (selectedTab) {
                        0 -> {
                            OutlinedTextField(
                                value = trainNumber,
                                onValueChange = { trainNumber = it.filter(Char::isDigit).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Train number") },
                                leadingIcon = { Icon(Icons.Outlined.DirectionsRailway, contentDescription = null) },
                                placeholder = { Text("e.g. 12919") }
                            )
                            Button(
                                onClick = {
                                    if (trainNumber.length < 4) {
                                        message = "Enter a valid train number."
                                        return@Button
                                    }
                                    runRequest { api.train(trainNumber) }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else {
                                    Icon(Icons.Outlined.Search, contentDescription = null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("CHECK LIVE STATUS")
                                }
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                value = stationQuery,
                                onValueChange = { stationQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search station") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                placeholder = { Text("Delhi, KOTA, Mumbai…") }
                            )

                            if (stationSuggestions.isNotEmpty()) {
                                stationSuggestions.forEach { (code, name) ->
                                    ElevatedCard(
                                        onClick = {
                                            stationCode = code
                                            stationQuery = code + " • " + name
                                            stationSuggestions = emptyList()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                                            Spacer(Modifier.width(9.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(name, fontWeight = FontWeight.Bold)
                                                Text(code, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                            }

                            if (stationCode.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { runRequest { api.stationLive(stationCode) } },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) { Text("LIVE") }
                                    OutlinedButton(
                                        onClick = { runRequest { api.stationBoard(stationCode) } },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) { Text("TIMETABLE") }
                                }
                            }
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = fromCode,
                                    onValueChange = { fromCode = it.uppercase(Locale.US).take(5) },
                                    label = { Text("From code") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = toCode,
                                    onValueChange = { toCode = it.uppercase(Locale.US).take(5) },
                                    label = { Text("To code") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Text(
                                "Tip: use station codes such as KOTA → JP.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    if (fromCode.isBlank() || toCode.isBlank()) {
                                        message = "Enter both station codes."
                                        return@Button
                                    }
                                    runRequest { api.trainsBetween(fromCode, toCode, live = true) }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("FIND TRAINS")
                            }
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) item { ApiPlainCard(message) }

        result?.let { response ->
            item { ApiResultCard(response) }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RailRadar coverage", fontWeight = FontWeight.Bold)
                    Text("Live train • station boards • station autocomplete • trains between stations", fontSize = 12.sp)
                    Text("Routes • seats • coach position • directories • filters are available in the backend.", fontSize = 12.sp)
                    Text("Free sandbox quota: 1,000 requests/month.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RailExtrasScreen(api: ApiClient) {
    var tab by remember { mutableIntStateOf(0) }
    var train by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var pnr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun run(request: () -> JSONObject) {
        loading = true
        message = ""
        Executors.newSingleThreadExecutor().execute {
            val response = request()
            Handler(Looper.getMainLooper()).post {
                loading = false
                result = response
                if (response.has("error")) {
                    message = apiFriendlyError(response, "Rail service returned an error.")
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Rail extras", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Fare, PNR prediction and refund information.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Fare", "PNR prediction", "PNR refund").forEachIndexed { index, label ->
                    FilterChip(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            result = null
                            message = ""
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tab == 0) {
                        OutlinedTextField(
                            value = train,
                            onValueChange = { train = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("5-digit train number") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = from,
                                onValueChange = { from = it.uppercase(Locale.US).take(5) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("From") }
                            )
                            OutlinedTextField(
                                value = to,
                                onValueChange = { to = it.uppercase(Locale.US).take(5) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("To") }
                            )
                        }
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Journey date (YYYY-MM-DD)") }
                        )
                        Button(
                            onClick = {
                                if (train.length != 5 || from.isBlank() || to.isBlank()) {
                                    message = "Enter a train number and both station codes."
                                    return@Button
                                }
                                run { api.trainFare(train, from, to, date) }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("GET FARE")
                        }
                    } else {
                        OutlinedTextField(
                            value = pnr,
                            onValueChange = { pnr = it.filter(Char::isDigit).take(10) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("10-digit PNR") }
                        )
                        Text(
                            if (tab == 1)
                                "Check the documented PNR confirmation prediction service."
                            else
                                "Check documented refund information for a PNR.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                if (pnr.length != 10) {
                                    message = "Enter a valid 10-digit PNR."
                                    return@Button
                                }
                                run {
                                    if (tab == 1) api.pnrPrediction(pnr) else api.pnrRefund(pnr)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(if (tab == 1) "CHECK PREDICTION" else "CHECK REFUND")
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) item { ApiPlainCard(message) }
        result?.let { response ->
            item { ApiResultCard(response) }
        }
    }
}

@Composable
private fun WeatherScreen(api: ApiClient, destination: Destination?) {
    var lat by remember { mutableStateOf(destination?.latitude?.toString() ?: "25.2138") }
    var lon by remember { mutableStateOf(destination?.longitude?.toString() ?: "75.8648") }
    var locationName by remember { mutableStateOf(destination?.name ?: "Kota") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun loadWeather() {
        val latitude = lat.toDoubleOrNull()
        val longitude = lon.toDoubleOrNull()
        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            message = "Enter valid coordinates."
            return
        }
        loading = true
        message = ""
        Executors.newSingleThreadExecutor().execute {
            val response = api.weather(latitude, longitude)
            Handler(Looper.getMainLooper()).post {
                result = response
                loading = false
                if (response.has("error")) {
                    message = apiFriendlyError(response, "Weather service unavailable.")
                }
            }
        }
    }

    LaunchedEffect(destination?.latitude, destination?.longitude) {
        if (destination != null) {
            lat = destination.latitude.toString()
            lon = destination.longitude.toString()
            locationName = destination.name
            loadWeather()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Weather", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Destination conditions and a short forecast.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    ) {
                        Icon(
                            Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(11.dp).size(27.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DESTINATION", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(locationName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            "Live conditions + 3-day forecast",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { loadWeather() }) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        result?.let { response ->
            val current = response.optJSONObject("current")
            if (current != null) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val temp = current.optDouble("temperature_2m", Double.NaN)
                                    val feels = current.optDouble("apparent_temperature", Double.NaN)
                                    Text(
                                        if (temp.isNaN()) "—" else String.format(Locale.US, "%.0f°", temp),
                                        fontSize = 52.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        if (feels.isNaN()) "Feels like —"
                                        else "Feels like " + String.format(Locale.US, "%.0f°", feels)
                                    )
                                }
                                Text(weatherEmoji(current.optInt("weather_code", -1)), fontSize = 42.sp)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                WeatherMetric(
                                    "Humidity",
                                    current.optInt("relative_humidity_2m", 0).toString() + "%",
                                    Modifier.weight(1f)
                                )
                                WeatherMetric(
                                    "Wind",
                                    String.format(Locale.US, "%.0f km/h", current.optDouble("wind_speed_10m", 0.0)),
                                    Modifier.weight(1f)
                                )
                                WeatherMetric(
                                    "Rain",
                                    String.format(Locale.US, "%.1f mm", current.optDouble("precipitation", 0.0)),
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            val daily = response.optJSONObject("daily")
            if (daily != null) {
                item { SectionTitle("Next 3 days") }
                val dates = daily.optJSONArray("time") ?: JSONArray()
                val codes = daily.optJSONArray("weather_code") ?: JSONArray()
                val highs = daily.optJSONArray("temperature_2m_max") ?: JSONArray()
                val lows = daily.optJSONArray("temperature_2m_min") ?: JSONArray()
                for (i in 0 until minOf(3, dates.length())) {
                    item {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Row(
                                Modifier.padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(weatherEmoji(codes.optInt(i, -1)), fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(dates.optString(i), fontWeight = FontWeight.Bold)
                                    val hi = highs.optDouble(i, Double.NaN)
                                    val lo = lows.optDouble(i, Double.NaN)
                                    Text(
                                        "High " + (if (hi.isNaN()) "—" else String.format(Locale.US, "%.0f°", hi)) +
                                            " • Low " + (if (lo.isNaN()) "—" else String.format(Locale.US, "%.0f°", lo)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Coordinates", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lat,
                            onValueChange = { lat = it },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lon,
                            onValueChange = { lon = it },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { loadWeather() }, modifier = Modifier.fillMaxWidth()) {
                        Text("REFRESH WEATHER")
                    }
                }
            }
        }

        if (message.isNotBlank()) item { ApiPlainCard(message) }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67 -> "🌧️"
    71, 73, 75, 77 -> "🌨️"
    80, 81, 82 -> "⛈️"
    85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌦️"
}

@Composable
private fun AiScreen(api: ApiClient) {
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("I'm ready. Ask about your destination, timing, packing, routes or how WakeWay works.") }
    var loading by remember { mutableStateOf(false) }
    val examples = listOf(
        "What should I pack?",
        "How early should I leave?",
        "Help me plan this journey"
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp).size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("WakeWay AI", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Your travel co-pilot, behind the secure backend.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("Ask WakeWay anything") },
                        placeholder = { Text("e.g. Is this destination good for an evening arrival?") },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.forEach { example ->
                            AssistChip(
                                onClick = { prompt = example },
                                label = { Text(example, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val question = prompt.trim()
                            if (question.isBlank()) return@Button
                            loading = true
                            Executors.newSingleThreadExecutor().execute {
                                val response = api.ai(question)
                                Handler(Looper.getMainLooper()).post {
                                    loading = false
                                    answer = if (response.optString("answer").isNotBlank()) {
                                        response.optString("answer")
                                    } else {
                                        apiFriendlyError(response, "No answer returned. Check the backend AI provider.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("ASK WAKEWAY")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Assistant", fontWeight = FontWeight.Bold)
                    }
                    Text(answer)
                }
            }
        }
    }
}

@Composable
private fun FamilyScreen(api: ApiClient, store: LocalStore) {
    val token = store.accessToken()
    var familyName by remember { mutableStateOf("My Family") }
    var inviteCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var locations by remember { mutableStateOf<JSONArray?>(null) }

    if (token.isNullOrBlank()) {
        AuthRequiredCard("Sign in to use Family Tracking.")
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Family tracking", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Private sharing is off by default and only syncs when you enable it.")

        OutlinedTextField(
            familyName,
            { familyName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Family name") }
        )

        Button(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val response = api.family(
                    "create",
                    JSONObject().put("name", familyName),
                    token
                )
                Handler(Looper.getMainLooper()).post {
                    result = if (response.has("invite_code")) {
                        "Invite code: " + response.optString("invite_code")
                    } else {
                        response.optString("error", response.toString())
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("CREATE FAMILY")
        }

        OutlinedTextField(
            inviteCode,
            { inviteCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Invite code") }
        )

        OutlinedButton(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val response = api.family(
                    "join",
                    JSONObject().put("invite_code", inviteCode),
                    token
                )
                Handler(Looper.getMainLooper()).post {
                    result = response.optString("message", response.optString("error", response.toString()))
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("JOIN FAMILY")
        }

        OutlinedButton(onClick = {
            Executors.newSingleThreadExecutor().execute {
                val response = api.family("locations", bearer = token)
                Handler(Looper.getMainLooper()).post {
                    locations = response.optJSONArray("locations")
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("REFRESH FAMILY LOCATIONS")
        }

        if (result.isNotBlank()) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text(result, Modifier.padding(16.dp))
            }
        }

        locations?.let { arr ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latest shared locations", fontWeight = FontWeight.Bold)
                    if (arr.length() == 0) Text("No family locations yet.")
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONObject(i) ?: continue
                        Text(
                            row.optString("user_id") + " • " +
                                String.format(Locale.US, "%.4f, %.4f", row.optDouble("latitude"), row.optDouble("longitude"))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsScreen(api: ApiClient, store: LocalStore) {
    val token = store.accessToken()
    var query by remember { mutableStateOf("") }
    var requestUsername by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var pending by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var friends by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    if (token.isNullOrBlank()) {
        AuthRequiredCard("Sign in to use Friends.")
        return
    }

    fun loadFriends() {
        loading = true
        Executors.newSingleThreadExecutor().execute {
            val response = api.friends("list", bearer = token)
            val pendingArray = response.optJSONArray("pending") ?: JSONArray()
            val allFriends = mutableListOf<JSONObject>()
            val pendingRows = mutableListOf<JSONObject>()
            for (i in 0 until pendingArray.length()) {
                pendingRows += pendingArray.optJSONObject(i) ?: continue
            }

            val friendObject = response.optJSONObject("friends")
            val sent = friendObject?.optJSONArray("sent") ?: JSONArray()
            val received = friendObject?.optJSONArray("received") ?: JSONArray()
            for (i in 0 until sent.length()) {
                sent.optJSONObject(i)?.let(allFriends::add)
            }
            for (i in 0 until received.length()) {
                received.optJSONObject(i)?.let(allFriends::add)
            }

            Handler(Looper.getMainLooper()).post {
                pending = pendingRows
                friends = allFriends
                loading = false
                if (response.has("error")) {
                    message = apiFriendlyError(response, "Could not load your friends.")
                }
            }
        }
    }

    fun searchPeople(value: String) {
        val q = value.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            return
        }
        Executors.newSingleThreadExecutor().execute {
            val response = api.friends("search", bearer = token, query = mapOf("q" to q))
            val array = response.optJSONArray("results") ?: JSONArray()
            val rows = mutableListOf<JSONObject>()
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let(rows::add)
            }
            Handler(Looper.getMainLooper()).post {
                searchResults = rows
                if (response.has("error")) {
                    message = apiFriendlyError(response, "People search failed.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFriends()
    }

    LaunchedEffect(query) {
        delay(280)
        searchPeople(query)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Friends", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Find people, manage requests and keep unwanted accounts blocked.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Find someone", fontWeight = FontWeight.Bold)
                            Text(
                                "Search by username or display name.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(17.dp),
                        label = { Text("Search people") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = {
                                    query = ""
                                    searchResults = emptyList()
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    searchResults.forEach { person ->
                        ElevatedCard(
                            onClick = {
                                requestUsername = person.optString("username")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(17.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(13.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(9.dp).size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        person.optString("display_name").ifBlank { person.optString("username") },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "@" + person.optString("username"),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send a request", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = requestUsername,
                        onValueChange = { requestUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") }
                    )
                    Button(
                        onClick = {
                            if (requestUsername.isBlank()) {
                                message = "Choose a username first."
                                return@Button
                            }
                            loading = true
                            Executors.newSingleThreadExecutor().execute {
                                val response = api.friends(
                                    "request",
                                    JSONObject().put("username", requestUsername.trim()),
                                    token
                                )
                                Handler(Looper.getMainLooper()).post {
                                    loading = false
                                    message = if (response.has("error")) {
                                        apiFriendlyError(response, "Friend request failed.")
                                    } else {
                                        "Friend request sent."
                                    }
                                    loadFriends()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("SEND REQUEST")
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Requests", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { loadFriends() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (pending.isEmpty()) {
            item {
                Text(
                    "No pending requests.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            pending.forEach { row ->
                item {
                    val requestId = row.optString("id")
                    val profile = row.optJSONObject("profiles")
                    val name = profile?.optString("display_name").orEmpty().ifBlank {
                        profile?.optString("username").orEmpty()
                    }
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name.ifBlank { "Someone" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "wants to connect with you",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                Executors.newSingleThreadExecutor().execute {
                                    val response = api.friends(
                                        "accept",
                                        JSONObject().put("request_id", requestId),
                                        token
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        message = response.optString("message", response.optString("error", "Done"))
                                        loadFriends()
                                    }
                                }
                            }) { Text("Accept") }
                            TextButton(onClick = {
                                Executors.newSingleThreadExecutor().execute {
                                    val response = api.friends(
                                        "reject",
                                        JSONObject().put("request_id", requestId),
                                        token
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        message = response.optString("message", response.optString("error", "Done"))
                                        loadFriends()
                                    }
                                }
                            }) { Text("Reject") }
                        }
                    }
                }
            }
        }

        item {
            Text("Connections", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (friends.isEmpty()) {
            item {
                Text(
                    "Your accepted connections will appear here.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            friends.forEach { row ->
                item {
                    val profile = row.optJSONObject("profile")
                    val name = profile?.optString("display_name").orEmpty().ifBlank {
                        profile?.optString("username").orEmpty()
                    }
                    Card(shape = RoundedCornerShape(19.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    Icons.Outlined.Person,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp).size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name.ifBlank { "Friend" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "@" + profile?.optString("username").orEmpty(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                requestUsername = profile?.optString("username").orEmpty()
                            }) {
                                Icon(Icons.Outlined.ArrowForward, contentDescription = "Open chat")
                            }
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item { ApiPlainCard(message) }
        }
    }
}

@Composable
private fun ChatScreen(api: ApiClient, store: LocalStore) {
    val token = store.accessToken()
    var toUsername by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var conversationId by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf<JSONArray?>(null) }
    var currentUserId by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Start a conversation with a username.") }
    var loading by remember { mutableStateOf(false) }

    if (token.isNullOrBlank()) {
        AuthRequiredCard("Sign in to use Chat.")
        return
    }

    fun refreshMessages() {
        if (conversationId.isBlank()) return
        Executors.newSingleThreadExecutor().execute {
            val response = api.chat(
                "list",
                bearer = token,
                query = mapOf("conversation_id" to conversationId)
            )
            Handler(Looper.getMainLooper()).post {
                val array = response.optJSONArray("messages")
                transcript = array
                if (response.has("error")) status = apiFriendlyError(response, "Could not load messages.")
            }
        }
    }

    LaunchedEffect(Unit) {
        Executors.newSingleThreadExecutor().execute {
            val profile = api.profile(token)
            Handler(Looper.getMainLooper()).post {
                currentUserId = profile.optString("id")
            }
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isBlank()) return@LaunchedEffect
        while (true) {
            refreshMessages()
            delay(5000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Chat", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Simple, private one-to-one messaging through the WakeWay backend.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = toUsername,
                        onValueChange = {
                            toUsername = it.lowercase().replace(" ", "_")
                            if (conversationId.isNotBlank()) {
                                conversationId = ""
                                transcript = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Friend username") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                    )
                    if (conversationId.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Text("Conversation connected", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { refreshMessages() }) { Text("Refresh") }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (transcript == null || transcript?.length == 0) {
                    Box(
                        Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(status, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Column(
                        Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val array = transcript
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val row = array.optJSONObject(i) ?: continue
                                val mine = row.optString("sender_id") == currentUserId
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (mine) 16.dp else 4.dp,
                                            bottomEnd = if (mine) 4.dp else 16.dp
                                        )
                                    ) {
                                        Column(Modifier.padding(11.dp)) {
                                            Text(row.optString("body"), fontSize = 14.sp)
                                            Text(
                                                row.optString("created_at"),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4,
                    label = { Text("Message") }
                )
                Button(
                    onClick = {
                        if (message.isBlank()) return@Button
                        if (conversationId.isBlank() && toUsername.isBlank()) {
                            status = "Enter a friend username first."
                            return@Button
                        }
                        loading = true
                        val outgoing = message
                        Executors.newSingleThreadExecutor().execute {
                            val body = JSONObject().put("message", outgoing)
                            if (conversationId.isBlank()) body.put("to_username", toUsername)
                            else body.put("conversation_id", conversationId)
                            val response = api.chat("send", body, token)
                            Handler(Looper.getMainLooper()).post {
                                loading = false
                                if (response.has("error")) {
                                    status = apiFriendlyError(response, "Message could not be sent.")
                                } else {
                                    conversationId = response.optString("conversation_id", conversationId)
                                    message = ""
                                    status = "Sent."
                                    refreshMessages()
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("➤", fontSize = 20.sp)
                }
            }
        }

        if (status.isNotBlank()) {
            item { ApiPlainCard(status) }
        }
    }
}

@Composable
private fun AccountScreen(
    api: ApiClient,
    store: LocalStore,
    onProfileUpdated: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(!store.accessToken().isNullOrBlank()) }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            Executors.newSingleThreadExecutor().execute {
                val profile = api.profile(store.accessToken())
                Handler(Looper.getMainLooper()).post {
                    username = profile.optString("username")
                    displayName = profile.optString("display_name")
                    if (profile.has("error")) message = apiFriendlyError(profile, "Could not load your profile.")
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp).size(28.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (signedIn) "You're signed in" else "Your WakeWay account",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Cloud sync, friends and family sharing stay behind the Worker.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!signedIn) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (email.isBlank() || password.length < 6) {
                                        message = "Enter a valid email and a password with at least 6 characters."
                                        return@Button
                                    }
                                    busy = true
                                    Executors.newSingleThreadExecutor().execute {
                                        val response = api.post(
                                            "/api/auth/signup",
                                            JSONObject().put("email", email.trim()).put("password", password)
                                        )
                                        Handler(Looper.getMainLooper()).post {
                                            busy = false
                                            val token = response.optString("access_token")
                                            if (token.isNotBlank()) {
                                                store.saveAccessToken(token)
                                                signedIn = true
                                                message = "Account created."
                                            } else {
                                                message = apiFriendlyError(
                                                    response,
                                                    "Account created. Check your email if confirmation is enabled."
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Create account")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (email.isBlank() || password.isBlank()) {
                                        message = "Enter your email and password."
                                        return@OutlinedButton
                                    }
                                    busy = true
                                    Executors.newSingleThreadExecutor().execute {
                                        val response = api.post(
                                            "/api/auth/signin",
                                            JSONObject().put("email", email.trim()).put("password", password)
                                        )
                                        Handler(Looper.getMainLooper()).post {
                                            busy = false
                                            val token = response.optString("access_token")
                                            if (token.isNotBlank()) {
                                                store.saveAccessToken(token)
                                                signedIn = true
                                                message = "Signed in."
                                            } else {
                                                message = apiFriendlyError(response, "Could not sign in.")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) { Text("Sign in") }
                        }
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.lowercase().replace(" ", "_") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Username") }
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Display name") }
                        )
                        Button(
                            onClick = {
                                busy = true
                                Executors.newSingleThreadExecutor().execute {
                                    val response = api.updateProfile(
                                        JSONObject()
                                            .put("username", username.trim())
                                            .put("display_name", displayName.trim()),
                                        store.accessToken()
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        busy = false
                                        message = if (response.has("error")) {
                                            apiFriendlyError(response, "Could not save profile.")
                                        } else {
                                            "Profile saved."
                                        }
                                        onProfileUpdated()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("SAVE PROFILE")
                        }
                        OutlinedButton(
                            onClick = {
                                store.clearAccessToken()
                                signedIn = false
                                username = ""
                                displayName = ""
                                message = "Signed out."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SIGN OUT")
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) item { ApiPlainCard(message) }
    }
}

@Composable
private fun SettingsScreen(
    api: ApiClient,
    store: LocalStore,
    onAccount: () -> Unit
) {
    val context = LocalContext.current
    var backendUrl by remember { mutableStateOf(api.backendUrl()) }
    var status by remember { mutableStateOf("") }
    var providers by remember { mutableStateOf<JSONObject?>(null) }
    var checking by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(store.setting("voice", "true") == "true") }
    var vibration by remember { mutableStateOf(store.setting("vibration", "true") == "true") }
    var autoShare by remember { mutableStateOf(store.setting("auto_share", "false") == "true") }
    var history by remember { mutableStateOf(store.setting("history", "true") == "true") }

    fun checkBackend() {
        checking = true
        Executors.newSingleThreadExecutor().execute {
            val health = api.health()
            val config = if (health.optBoolean("ok")) api.config() else JSONObject()
            Handler(Looper.getMainLooper()).post {
                checking = false
                providers = config.optJSONObject("providers")
                status = if (health.optBoolean("ok")) {
                    "WakeWay backend is online."
                } else {
                    apiFriendlyError(health, "Backend is unavailable.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkBackend()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Control centre", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Make WakeWay reliable for long journeys.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.contains("online", ignoreCase = true))
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (status.contains("online", ignoreCase = true))
                                Icons.Outlined.CheckCircle
                            else Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Service status", fontWeight = FontWeight.Bold)
                            Text(
                                status.ifBlank { "Checking services…" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { checkBackend() }) {
                            if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    }
                    providers?.let { p ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProviderPill("Supabase", p.optBoolean("supabase"))
                            ProviderPill("Gemini", p.optBoolean("gemini"))
                            ProviderPill("RailRadar", p.optBoolean("railradar"))
                        }
                    }
                }
            }
        }

        item { SectionTitle("Alarm") }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    ToggleRow("Voice announcements", voice) {
                        voice = it
                        store.saveSetting("voice", it.toString())
                    }
                    androidx.compose.material3.HorizontalDivider()
                    ToggleRow("Vibration", vibration) {
                        vibration = it
                        store.saveSetting("vibration", it.toString())
                    }
                    androidx.compose.material3.HorizontalDivider()
                    ToggleRow("Save history", history) {
                        history = it
                        store.saveSetting("history", it.toString())
                    }
                }
            }
        }

        item { SectionTitle("Sharing") }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ToggleRow("Family location sharing", autoShare) {
                        autoShare = it
                        store.saveSetting("auto_share", it.toString())
                    }
                    Text(
                        "When enabled, an active journey can sync your latest GPS location approximately every 30 seconds.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionTitle("Backend") }

        item {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WakeWay backend URL") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        api.setBackendUrl(backendUrl.trim())
                        checkBackend()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("CHECK & SAVE")
                }
                OutlinedButton(
                    onClick = {
                        backendUrl = "https://wakeway-api.shivgarg184.workers.dev"
                        api.setBackendUrl(backendUrl)
                        checkBackend()
                    },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("USE PRODUCTION")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Permissions", fontWeight = FontWeight.Bold)
                    Text("Notifications and battery settings are important for a reliable destination alarm.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { testLocalAlarm(context, store) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("TEST ALARM NOW")
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Notification settings") }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Battery optimisation") }
                }
            }
        }

        item {
            OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Account & profile")
            }
        }
    }
}

@Composable
private fun ProviderPill(label: String, enabled: Boolean) {
    AssistChip(
        onClick = { },
        leadingIcon = {
            Icon(
                if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
        },
        label = { Text(label, fontSize = 11.sp) }
    )
}

@Composable
private fun SavedPlacesScreen(
    api: ApiClient,
    store: LocalStore,
    onSelect: (Destination) -> Unit
) {
    val token = store.accessToken()
    var places by remember { mutableStateOf(JSONArray()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    if (token.isNullOrBlank()) {
        AuthRequiredCard("Sign in to save and manage destinations.")
        return
    }

    fun load() {
        loading = true
        Executors.newSingleThreadExecutor().execute {
            val response = api.savedPlaces(token)
            Handler(Looper.getMainLooper()).post {
                loading = false
                if (response.has("error")) {
                    message = apiFriendlyError(response, "Could not load saved places.")
                } else {
                    places = if (response.optJSONArray("data") != null) {
                        response.optJSONArray("data")!!
                    } else if (response.optJSONArray("places") != null) {
                        response.optJSONArray("places")!!
                    } else {
                        response.optJSONArray("results") ?: JSONArray()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Saved places", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Keep the destinations you use most often one tap away.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { load() }) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (message.isNotBlank()) {
            item { ApiPlainCard(message) }
        }

        if (places.length() == 0 && !loading) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(46.dp))
                        Text("No saved places yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Open Set destination, choose a prediction, then tap Save.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            for (i in 0 until places.length()) {
                val row = places.optJSONObject(i) ?: continue
                val destination = Destination(
                    row.optString("name"),
                    row.optString("address"),
                    row.optDouble("latitude"),
                    row.optDouble("longitude")
                )
                item {
                    ElevatedCard(
                        onClick = { onSelect(destination) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(9.dp).size(21.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(destination.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(
                                    destination.address,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("›", fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun testLocalAlarm(context: Context, store: LocalStore) {
    val channelId = "wakeway_test"
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            channelId,
            "WakeWay test alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Test notifications for WakeWay alarm settings."
            enableVibration(true)
        }
    )
    val notification = Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("WakeWay test alarm")
        .setContentText("Alarm, voice and vibration settings are ready to test.")
        .setCategory(Notification.CATEGORY_ALARM)
        .setPriority(Notification.PRIORITY_MAX)
        .setAutoCancel(true)
        .build()
    manager.notify(8401, notification)

    if (store.setting("vibration", "true") == "true") {
        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(
            android.os.VibrationEffect.createWaveform(longArrayOf(0, 450, 180, 800), -1)
        )
    }
    Toast.makeText(context, "Test alert sent.", Toast.LENGTH_SHORT).show()
}

@Composable
private fun PremiumScreen(api: ApiClient, store: LocalStore) {
    var result by remember { mutableStateOf("Premium is subscription-ready but not falsely marked as purchased.") }

    val token = store.accessToken()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("WAKEWAY PREMIUM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("More control. More context.", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Billing can be activated later with server verification.")
                }
            }
        }

        item { PremiumFeature("✦", "Advanced alert profiles", "More distance/time combinations") }
        item { PremiumFeature("✦", "Enhanced AI", "Longer, context-aware travel sessions") }
        item { PremiumFeature("✦", "Family controls", "Additional sharing controls") }
        item { PremiumFeature("✦", "Journey analytics", "More detailed statistics and exports") }

        item {
            OutlinedButton(
                onClick = {
                    if (token.isNullOrBlank()) {
                        result = "Sign in first to read your subscription status."
                        return@OutlinedButton
                    }
                    Executors.newSingleThreadExecutor().execute {
                        val response = api.subscription(token)
                        Handler(Looper.getMainLooper()).post {
                            result = response.optJSONObject("subscription")?.toString()
                                ?: response.optString("error", response.toString())
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CHECK SUBSCRIPTION")
            }
        }

        item { ApiPlainCard(result) }
    }
}

@Composable
private fun PremiumFeature(icon: String, title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AuthRequiredCard(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("👤", fontSize = 42.sp)
                Text(message, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Open Account from Settings or Home and sign in.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun apiFriendlyError(response: JSONObject, fallback: String): String {
    val status = response.optInt("http_status", 0)
    val error = response.optString("error").ifBlank { response.optString("message") }
    return when {
        status == 401 -> "Your session has expired. Please sign in again."
        status == 403 -> "This action is not allowed for this account."
        status == 404 -> "WakeWay endpoint was not found. Check the backend URL and deployment."
        status == 429 -> "This service is temporarily rate-limited. Try again shortly."
        status >= 500 -> "WakeWay server is having trouble. Try again shortly."
        error.isNotBlank() -> error
        else -> fallback
    }
}

@Composable
private fun ApiResultCard(response: JSONObject, skipKeys: Set<String> = emptySet()) {
    val copy = JSONObject(response.toString())
    skipKeys.forEach { copy.remove(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (response.has("error")) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (response.has("error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (response.has("error")) "Service response" else "Live response",
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                copy.toString(2),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun ApiPlainCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(message, Modifier.padding(16.dp))
    }
}

@Composable
private fun MapScreen(destination: Destination?) {
    val context = LocalContext.current
    var mapLoading by remember { mutableStateOf(true) }
    var mapError by remember { mutableStateOf(false) }

    val webView = remember {
        android.webkit.WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    mapLoading = false
                    view?.evaluateJavascript(
                        "try { if (window.map) setTimeout(function(){ map.invalidateSize(); }, 250); } catch(e) {}",
                        null
                    )
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        mapLoading = false
                        mapError = true
                    }
                }
            }
        }
    }

    val lat = destination?.latitude ?: 25.2138
    val lon = destination?.longitude ?: 75.8648
    val label = (destination?.name?.takeIf { it.isNotBlank() } ?: "Destination")
        .replace("\\", "\\\\")
        .replace("'", "\\'")

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                mapLoading = true
                mapError = false
                val html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
                      <style>
                        html,body,#map{height:100%;width:100%;margin:0;background:#f4f6f9}
                        .leaflet-control-attribution{font-size:10px}
                      </style>
                    </head>
                    <body>
                      <div id="map"></div>
                      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                      <script>
                        window.map = L.map('map', { zoomControl: true }).setView([${lat},${lon}], 13);
                        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                          maxZoom: 19,
                          attribution: '© OpenStreetMap contributors'
                        }).addTo(window.map);
                        L.marker([${lat},${lon}]).addTo(window.map).bindPopup('${label}').openPopup();
                        setTimeout(function(){ window.map.invalidateSize(); }, 500);
                      </script>
                    </body>
                    </html>
                """.trimIndent()
                view.loadDataWithBaseURL(
                    "https://wakeway-map.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )

        if (mapLoading) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("Loading map…", fontSize = 12.sp)
                }
            }
        }

        if (destination != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(destination.name, fontWeight = FontWeight.Bold)
                        Text(
                            destination.address,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val uri = android.net.Uri.parse(
                                "geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}(" +
                                    java.net.URLEncoder.encode(destination.name, "UTF-8") + ")"
                            )
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open in maps")
                    }
                }
            }
        }

        if (mapError) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Map tiles couldn't load", fontWeight = FontWeight.Bold)
                    Text(
                        "The destination is still valid. Use the maps button to open your maps app.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
