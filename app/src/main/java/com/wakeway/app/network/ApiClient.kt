package com.wakeway.app.network

import android.content.Context
import com.wakeway.app.config.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiClient(context: Context) {

    private val prefs = context.getSharedPreferences("wakeway_store", Context.MODE_PRIVATE)

    private fun baseUrl(): String =
        prefs.getString("setting_backend_url", null)?.trim()?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: AppConfig.backendUrl

    fun setBackendUrl(url: String) {
        prefs.edit().putString("setting_backend_url", url.trim().trimEnd('/')).apply()
    }

    fun backendUrl(): String = baseUrl()
    fun isConfigured(): Boolean = baseUrl().isNotBlank()

    fun get(
        path: String,
        bearer: String? = null,
        query: Map<String, String> = emptyMap()
    ): JSONObject = request("GET", path, null, bearer, query)

    fun post(
        path: String,
        body: JSONObject = JSONObject(),
        bearer: String? = null
    ): JSONObject = request("POST", path, body, bearer)

    fun delete(path: String, bearer: String? = null): JSONObject =
        request("DELETE", path, null, bearer)

    fun health(): JSONObject =
        if (isConfigured()) get("/health")
        else JSONObject().put("ok", false).put("error", "Backend URL not configured")

    fun config(): JSONObject =
        if (isConfigured()) get("/api/config")
        else JSONObject().put("error", "Backend URL not configured")

    fun searchPlaces(query: String): JSONObject {
        val q = query.trim()
        if (q.length < 2) return JSONObject().put("results", JSONArray())

        if (isConfigured()) {
            val backend = get("/api/place-search", query = mapOf("q" to q))
            val backendResults = backend.optJSONArray("results")
            if (backend.optInt("http_status", 0) in 200..299 && backendResults != null && backendResults.length() > 0) {
                return backend
            }
        }

        val raw = externalGet(
            "https://geocoding-api.open-meteo.com/v1/search",
            mapOf(
                "name" to q,
                "count" to "8",
                "language" to "en",
                "format" to "json"
            )
        )
        val results = JSONArray()
        val rows = raw.optJSONArray("results") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            results.put(
                JSONObject().apply {
                    put(
                        "name",
                        listOf(row.optString("name"), row.optString("admin1"), row.optString("country"))
                            .filter { it.isNotBlank() }.joinToString(", ")
                    )
                    put("shortName", row.optString("name"))
                    put(
                        "address",
                        listOf(row.optString("admin1"), row.optString("country"))
                            .filter { it.isNotBlank() }.joinToString(", ")
                    )
                    put("latitude", row.optDouble("latitude"))
                    put("longitude", row.optDouble("longitude"))
                    put("country", row.optString("country"))
                    put("countryCode", row.optString("country_code"))
                    put("timezone", row.optString("timezone"))
                }
            )
        }
        return JSONObject().put("results", results)
    }

    fun weather(lat: Double, lon: Double): JSONObject =
        if (isConfigured()) {
            get("/api/weather", query = mapOf("lat" to lat.toString(), "lon" to lon.toString()))
        } else {
            externalGet(
                "https://api.open-meteo.com/v1/forecast",
                mapOf(
                    "latitude" to lat.toString(),
                    "longitude" to lon.toString(),
                    "current" to "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,weather_code,wind_speed_10m",
                    "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
                    "forecast_days" to "3",
                    "timezone" to "auto"
                )
            )
        }

    fun sendJourney(journey: JSONObject, bearer: String?): JSONObject =
        post("/api/journeys", journey, bearer)

    fun endJourney(id: String, status: String, bearer: String?): JSONObject =
        post("/api/journeys/" + id, JSONObject().put("status", status), bearer)

    fun savePlace(body: JSONObject, bearer: String?): JSONObject =
        post("/api/places", body, bearer)

    fun savedPlaces(bearer: String?): JSONObject =
        get("/api/places", bearer)

    fun deletePlace(id: String, bearer: String?): JSONObject =
        delete("/api/places/" + id, bearer)

    fun profile(bearer: String?): JSONObject =
        get("/api/profile", bearer)

    fun updateProfile(body: JSONObject, bearer: String?): JSONObject =
        post("/api/profile", body, bearer)

    fun family(action: String, body: JSONObject? = null, bearer: String? = null): JSONObject =
        when (action) {
            "list" -> get("/api/family", bearer)
            "locations" -> get("/api/family/locations", bearer)
            else -> post("/api/family/" + action, body ?: JSONObject(), bearer)
        }

    fun friends(
        action: String,
        body: JSONObject? = null,
        bearer: String? = null,
        query: Map<String, String> = emptyMap()
    ): JSONObject =
        when (action) {
            "search" -> get("/api/friends/search", bearer, query)
            "list" -> get("/api/friends", bearer)
            else -> post("/api/friends/" + action, body ?: JSONObject(), bearer)
        }

    fun chat(
        action: String,
        body: JSONObject? = null,
        bearer: String? = null,
        query: Map<String, String> = emptyMap()
    ): JSONObject =
        when (action) {
            "list" -> get("/api/chat", bearer, query)
            else -> post("/api/chat/" + action, body ?: JSONObject(), bearer)
        }

    fun subscription(bearer: String?): JSONObject =
        get("/api/subscription", bearer)

    fun train(number: String, date: String? = null): JSONObject {
        val query = mutableMapOf("train" to number)
        if (!date.isNullOrBlank()) query["date"] = date
        return get("/api/train", query = query)
    }

    fun trainStations(query: String): JSONObject =
        get("/api/train/stations", query = mapOf("q" to query))

    fun trainsBetween(from: String, to: String, live: Boolean = false): JSONObject =
        get(
            "/api/train/between",
            query = mapOf("from" to from, "to" to to, "live" to live.toString())
        )

    fun trainRoute(number: String, format: String = "geojson", stops: Boolean = true): JSONObject =
        get(
            "/api/train/route",
            query = mapOf(
                "train" to number,
                "format" to format,
                "stops" to stops.toString()
            )
        )

    fun trainSeats(
        number: String,
        source: String,
        destination: String,
        date: String = "",
        classCode: String = "SL",
        quotaCode: String = "GN"
    ): JSONObject =
        get(
            "/api/train/seats",
            query = mapOf(
                "train" to number,
                "source" to source,
                "destination" to destination,
                "journeyDate" to date,
                "classCode" to classCode,
                "quotaCode" to quotaCode
            )
        )

    fun trainCoaches(number: String, station: String): JSONObject =
        get(
            "/api/train/coaches",
            query = mapOf("train" to number, "station" to station)
        )

    fun stationLive(
        code: String,
        hours: String = "4",
        includeIntermediate: Boolean = false
    ): JSONObject =
        get(
            "/api/train/station-live",
            query = mapOf(
                "code" to code,
                "hours" to hours,
                "includeIntermediate" to includeIntermediate.toString()
            )
        )

    fun stationBoard(code: String, includeIntermediate: Boolean = false): JSONObject =
        get(
            "/api/train/station-board",
            query = mapOf(
                "code" to code,
                "includeIntermediate" to includeIntermediate.toString()
            )
        )

    fun stationDirectory(ntes: Boolean = false): JSONObject =
        get(if (ntes) "/api/train/stations-ntes" else "/api/train/stations-directory")

    fun trainDirectory(variant: String = "prs"): JSONObject =
        get(
            when (variant) {
                "ntes" -> "/api/train/directory-ntes"
                "compressed" -> "/api/train/directory-compressed"
                else -> "/api/train/directory"
            }
        )

    fun trainFilter(category: String? = null, type: String? = null): JSONObject {
        val q = mutableMapOf<String, String>()
        if (!category.isNullOrBlank()) q["category"] = category
        if (!type.isNullOrBlank()) q["type"] = type
        return get("/api/train/filter", query = q)
    }

    fun ai(prompt: String): JSONObject =
        post("/api/ai", JSONObject().put("prompt", prompt))

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String?,
        query: Map<String, String> = emptyMap()
    ): JSONObject {
        val base = baseUrl()
        if (base.isBlank()) return JSONObject().put("error", "Backend URL not configured")

        return runCatching {
            val url = URL(buildUrl(base + "/" + path.trimStart('/'), query))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 25_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                if (!bearer.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer " + bearer)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            if (body != null) {
                conn.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            read(conn)
        }.getOrElse {
            JSONObject().put("error", "Network error").put("detail", it.message ?: "Unknown error")
        }
    }

    private fun externalGet(base: String, query: Map<String, String>): JSONObject =
        runCatching {
            val url = URL(buildUrl(base, query))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }
            read(conn)
        }.getOrElse {
            JSONObject().put("error", "Network error").put("detail", it.message ?: "Unknown error")
        }

    private fun buildUrl(base: String, query: Map<String, String>): String {
        if (query.isEmpty()) return base
        val encoded = query.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        return base + "?" + encoded
    }

    private fun read(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val content = stream?.use {
            BufferedReader(InputStreamReader(it)).readText()
        } ?: "{}"

        return runCatching {
            val trimmed = content.trim()
            if (trimmed.startsWith("[")) {
                JSONObject().put("data", JSONArray(trimmed)).put("http_status", code)
            } else if (trimmed.isBlank()) {
                JSONObject().put("http_status", code)
            } else {
                JSONObject(trimmed).put("http_status", code)
            }
        }.getOrElse {
            JSONObject().put("http_status", code).put("raw", content)
        }
    }
}
