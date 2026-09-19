package com.wakeway.app.journey

import android.app.*
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.speech.tts.TextToSpeech
import com.wakeway.app.R
import com.wakeway.app.data.LocalStore
import com.wakeway.app.model.AlertTrigger
import com.wakeway.app.model.Journey
import com.wakeway.app.model.JourneyStatus
import com.wakeway.app.network.ApiClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class JourneyTrackingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var store: LocalStore
    private lateinit var api: ApiClient
    private var tts: TextToSpeech? = null
    private val firedDistanceAlerts = mutableSetOf<String>()
    private val cloudExecutor = Executors.newSingleThreadExecutor()
    private var lastCloudLocationSync = 0L
    private val firedTimeAlerts = mutableSetOf<String>()
    private var finalAlarmed = false
    private var lastDistanceMeters: Double? = null

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        api = ApiClient(this)
        locationManager = getSystemService(LocationManager::class.java)
        tts = TextToSpeech(this) {
            runCatching { tts?.language = Locale.getDefault() }
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopJourney()
            return START_NOT_STICKY
        }

        val journey = store.activeJourney()
        if (journey == null || journey.status != JourneyStatus.ACTIVE) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification("Monitoring " + journey.destination.name))
        } catch (error: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        syncJourney(journey)

        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                alert("WakeWay needs location permission to monitor your journey.")
                return START_STICKY
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5_000L,
                20f,
                this,
                Looper.getMainLooper()
            )

            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10_000L,
                    50f,
                    this,
                    Looper.getMainLooper()
                )
            }

            runCatching {
                val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                last?.let(::onLocationChanged)
            }
        } catch (security: SecurityException) {
            alert("WakeWay needs location permission to monitor your journey.")
        }

        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val journey = store.activeJourney() ?: return
        if (!location.hasAccuracy() || location.accuracy > 250f) {
            updateNotification("GPS weak • waiting for a better signal")
            return
        }

        val distance = Distance.meters(
            location.latitude,
            location.longitude,
            journey.destination.latitude,
            journey.destination.longitude
        )

        val speedKmh = if (location.hasSpeed() && location.speed >= 0f) location.speed * 3.6f else 0f
        val etaText = if (speedKmh >= 3f) {
            val etaMinutes = ((distance / 1000.0) / speedKmh * 60.0).toInt().coerceAtLeast(1)
            " • ETA ~" + etaMinutes + " min"
        } else {
            ""
        }

        updateNotification(
            formatDistance(distance) + etaText + " • accuracy ±" + location.accuracy.toInt() + "m"
        )
        syncLocation(journey, location)

        val distanceAlerts = journey.alerts
            .filter { it.trigger == AlertTrigger.DISTANCE }
            .sortedByDescending { it.value }

        for (alertRule in distanceAlerts) {
            val meters = alertRule.value * 1000.0
            val key = alertRule.value.toString() + ":" + alertRule.label
            if (distance <= meters && firedDistanceAlerts.add(key)) {
                val message = if (meters >= 1000.0) {
                    "Wake up. " + journey.destination.name + " is about " +
                        trim(meters / 1000.0) + " kilometres away."
                } else {
                    "Wake up. " + journey.destination.name + " is about " +
                        trim(meters) + " metres away."
                }
                alert(message)
            }
        }

        val elapsedMinutes = (System.currentTimeMillis() - journey.startedAt) / 60000.0
        journey.alerts
            .filter { it.trigger == AlertTrigger.TIME }
            .forEach { alertRule ->
                val minutes = alertRule.value
                val key = minutes.toString() + ":" + alertRule.label
                if (minutes > 0 && elapsedMinutes >= minutes && firedTimeAlerts.add(key)) {
                    alert(
                        "Wake up. Your WakeWay time backup for " + journey.destination.name +
                            " has reached " + trim(minutes) + " minutes."
                    )
                }
            }

        val previousDistance = lastDistanceMeters
        if (previousDistance != null && distance > previousDistance + 120.0 && previousDistance < 700.0 && !finalAlarmed) {
            alert("WakeWay noticed you may be moving away from " + journey.destination.name + ". Please check your route.")
        }
        lastDistanceMeters = distance

        val destinationRadius = distanceAlerts
            .minOfOrNull { it.value * 1000.0 }
            ?.coerceAtMost(200.0)
            ?: 150.0

        if (!finalAlarmed && distance <= max(destinationRadius, 75.0)) {
            finalAlarmed = true
            alert("Wake up. You have reached " + journey.destination.name + ". Please get ready to exit.")

            val completed = journey.copy(
                status = JourneyStatus.COMPLETED,
                acknowledged = false
            )
            if (store.setting("history", "true") == "true") {
                store.addHistory(completed)
            }
            syncJourney(completed)
            store.clearActiveJourney()
            cloudExecutor.execute {
                runCatching { api.endJourney(journey.id, "completed", store.accessToken()) }
            }
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 12_000)
        }
    }

    private fun syncJourney(journey: Journey) {
        val token = store.accessToken() ?: return
        if (!api.isConfigured()) return

        val body = JSONObject().apply {
            put("id", journey.id)
            put("destination_name", journey.destination.name)
            put("destination_address", journey.destination.address)
            put("destination_lat", journey.destination.latitude)
            put("destination_lon", journey.destination.longitude)
            put("transport_mode", journey.transport.name)
            put("started_at", java.time.Instant.ofEpochMilli(journey.startedAt).toString())
            put("status", journey.status.name.lowercase())
        }

        cloudExecutor.execute {
            runCatching { api.sendJourney(body, token) }
        }
    }

    private fun syncLocation(journey: Journey, location: Location) {
        if (store.setting("auto_share", "false") != "true") return
        val token = store.accessToken() ?: return
        if (!api.isConfigured()) return

        val now = System.currentTimeMillis()
        if (now - lastCloudLocationSync < 30_000L) return
        lastCloudLocationSync = now

        val body = JSONObject()
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("accuracy_m", location.accuracy)

        cloudExecutor.execute {
            runCatching { api.family("location", body, token) }
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m to destination"
        else String.format(Locale.US, "%.2f km to destination", meters / 1000.0)

    private fun alert(message: String) {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.notify(
            (System.currentTimeMillis() % 100000).toInt(),
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("WakeWay")
                .setContentText(message)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(true)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .build()
        )

        if (store.setting("vibration", "true") == "true") {
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 900), -1)
            )
        }

        if (store.setting("voice", "true") == "true") {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "wakeway-alert")
        }
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WakeWay • Journey active")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Stop",
                    PendingIntent.getService(
                        this,
                        77,
                        Intent(this, JourneyTrackingService::class.java).apply {
                            action = ACTION_STOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Journey monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "WakeWay journey monitoring and destination alerts."
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    private fun stopJourney() {
        runCatching { locationManager.removeUpdates(this) }
        store.activeJourney()?.let {
            val cancelled = it.copy(status = JourneyStatus.CANCELLED)
            store.addHistory(cancelled)
            syncJourney(cancelled)
            cloudExecutor.execute {
                runCatching { api.endJourney(it.id, "cancelled", store.accessToken()) }
            }
        }
        store.clearActiveJourney()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?) = null
    override fun onProviderDisabled(provider: String) =
        updateNotification("Location provider disabled • WakeWay waiting")
    override fun onProviderEnabled(provider: String) = Unit
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        cloudExecutor.shutdownNow()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "journey_monitoring"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.wakeway.app.STOP"
    }
}
