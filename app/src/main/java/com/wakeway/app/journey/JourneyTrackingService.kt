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
import com.wakeway.app.model.JourneyStatus
import java.util.Locale
import kotlin.math.max

class JourneyTrackingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var store: LocalStore
    private var tts: TextToSpeech? = null
    private val firedDistanceAlerts = mutableSetOf<String>()
    private var lastGoodLocation: Location? = null
    private var finalAlarmed = false

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        locationManager = getSystemService(LocationManager::class.java)
        tts = TextToSpeech(this) {
            tts?.language = Locale.getDefault()
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

        startForeground(NOTIFICATION_ID, buildNotification("Monitoring ${journey.destination.name}"))

        try {
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

            // Use a recent on-device location immediately when available.
            runCatching {
                val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                last?.let(::onLocationChanged)
            }
        } catch (_: SecurityException) {
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

        lastGoodLocation = location
        val distance = Distance.meters(
            location.latitude,
            location.longitude,
            journey.destination.latitude,
            journey.destination.longitude
        )

        updateNotification(
            "${formatDistance(distance)} • accuracy ±${location.accuracy.toInt()}m"
        )

        val distanceAlerts = journey.alerts
            .filter { it.trigger == AlertTrigger.DISTANCE }
            .sortedByDescending { it.value }

        for (alertRule in distanceAlerts) {
            val meters = alertRule.value * 1000.0
            val key = "${alertRule.value}:${alertRule.label}"
            if (distance <= meters && firedDistanceAlerts.add(key)) {
                val text = when {
                    meters >= 1000.0 ->
                        "Wake up. ${journey.destination.name} is about ${trim(meters / 1000.0)} kilometres away."
                    else ->
                        "Wake up. ${journey.destination.name} is about ${trim(meters)} metres away."
                }
                alert(text)
            }
        }

        val destinationRadius = distanceAlerts
            .minOfOrNull { it.value * 1000.0 }
            ?.coerceAtMost(200.0)
            ?: 150.0

        if (!finalAlarmed && distance <= max(destinationRadius, 75.0)) {
            finalAlarmed = true
            alert(
                "Wake up. You have reached ${journey.destination.name}. Please get ready to exit."
            )
            val completed = journey.copy(
                status = JourneyStatus.COMPLETED,
                acknowledged = false
            )
            store.addHistory(completed)
            store.clearActiveJourney()
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 12_000)
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m to destination"
        else -> "${String.format(Locale.US, "%.2f", meters / 1000.0)} km to destination"
    }

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
            store.addHistory(it.copy(status = JourneyStatus.CANCELLED))
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
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "journey_monitoring"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.wakeway.app.STOP"
    }
}
