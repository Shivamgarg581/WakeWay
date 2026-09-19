package com.wakeway.app.data

import android.content.Context
import com.wakeway.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("wakeway_store", Context.MODE_PRIVATE)

    fun saveJourney(journey: Journey) {
        prefs.edit().putString("active_journey", journeyToJson(journey).toString()).apply()
    }

    fun activeJourney(): Journey? {
        val raw = prefs.getString("active_journey", null) ?: return null
        return runCatching { journeyFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun clearActiveJourney() {
        prefs.edit().remove("active_journey").apply()
    }

    fun addHistory(journey: Journey) {
        val old = prefs.getString("history", "[]") ?: "[]"
        val arr = JSONArray(old)
        arr.put(journeyToJson(journey))
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun history(): List<Journey> {
        val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
        return buildList {
            for (i in arr.length() - 1 downTo 0) {
                runCatching { add(journeyFromJson(arr.getJSONObject(i))) }
            }
        }
    }


    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun accessToken(): String? = prefs.getString("access_token", null)

    fun clearAccessToken() {
        prefs.edit().remove("access_token").apply()
    }

    fun saveTrackingSnapshot(
        distanceMeters: Double,
        etaMinutes: Int?,
        speedKmh: Double,
        accuracyMeters: Float
    ) {
        val json = JSONObject().apply {
            put("distance_m", distanceMeters)
            if (etaMinutes == null) put("eta_min", JSONObject.NULL) else put("eta_min", etaMinutes)
            put("speed_kmh", speedKmh)
            put("accuracy_m", accuracyMeters)
            put("updated_at", System.currentTimeMillis())
        }
        prefs.edit().putString("tracking_snapshot", json.toString()).apply()
    }

    fun trackingSnapshot(): JSONObject? =
        prefs.getString("tracking_snapshot", null)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }

    fun clearTrackingSnapshot() {
        prefs.edit().remove("tracking_snapshot").apply()
    }

    fun saveSetting(key: String, value: String) {
        prefs.edit().putString("setting_$key", value).apply()
    }

    fun setting(key: String, default: String = ""): String =
        prefs.getString("setting_$key", default) ?: default

    fun updateActiveStatus(status: JourneyStatus, acknowledged: Boolean = false) {
        val j = activeJourney() ?: return
        saveJourney(j.copy(status = status, acknowledged = acknowledged))
    }

    companion object {
        fun newJourney(
            destination: Destination,
            transport: TransportMode,
            alerts: List<JourneyAlert>
        ): Journey = Journey(
            id = UUID.randomUUID().toString(),
            destination = destination,
            transport = transport,
            alerts = alerts,
            startedAt = System.currentTimeMillis()
        )

        private fun journeyToJson(j: Journey): JSONObject = JSONObject().apply {
            put("id", j.id)
            put("destination", JSONObject().apply {
                put("name", j.destination.name)
                put("address", j.destination.address)
                put("lat", j.destination.latitude)
                put("lon", j.destination.longitude)
            })
            put("transport", j.transport.name)
            put("startedAt", j.startedAt)
            put("status", j.status.name)
            put("acknowledged", j.acknowledged)
            put("alerts", JSONArray().apply {
                j.alerts.forEach {
                    put(JSONObject().apply {
                        put("trigger", it.trigger.name)
                        put("value", it.value)
                        put("label", it.label)
                    })
                }
            })
        }

        private fun journeyFromJson(o: JSONObject): Journey {
            val d = o.getJSONObject("destination")
            val alertsJson = o.optJSONArray("alerts") ?: JSONArray()
            val alerts = buildList {
                for (i in 0 until alertsJson.length()) {
                    val a = alertsJson.getJSONObject(i)
                    add(
                        JourneyAlert(
                            AlertTrigger.valueOf(a.getString("trigger")),
                            a.getDouble("value"),
                            a.getString("label")
                        )
                    )
                }
            }
            return Journey(
                id = o.getString("id"),
                destination = Destination(
                    d.getString("name"),
                    d.optString("address", ""),
                    d.getDouble("lat"),
                    d.getDouble("lon")
                ),
                transport = TransportMode.valueOf(o.getString("transport")),
                alerts = alerts,
                startedAt = o.getLong("startedAt"),
                status = JourneyStatus.valueOf(o.getString("status")),
                acknowledged = o.optBoolean("acknowledged", false)
            )
        }
    }
}
