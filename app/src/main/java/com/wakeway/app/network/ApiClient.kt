package com.wakeway.app.network

import com.wakeway.app.config.AppConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {
    fun get(path: String, bearer: String? = null, query: Map<String, String> = emptyMap()): JSONObject {
        val base = AppConfig.endpoint(path)
        if (base.isBlank()) return JSONObject().put("error", "Backend not configured")
        val url = URL(buildUrl(base, query))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        return read(conn)
    }

    fun post(
        path: String,
        body: JSONObject,
        bearer: String? = null
    ): JSONObject {
        val base = AppConfig.endpoint(path)
        if (base.isBlank()) return JSONObject().put("error", "Backend not configured")
        val conn = (URL(base).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return read(conn)
    }

    private fun buildUrl(base: String, query: Map<String, String>): String {
        if (query.isEmpty()) return base
        val encoded = query.entries.joinToString("&") {
            "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        return "$base?$encoded"
    }

    private fun read(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.use {
            BufferedReader(InputStreamReader(it)).readText()
        } ?: "{}"
        return runCatching { JSONObject(text) }.getOrElse {
            JSONObject().put("status", code).put("raw", text)
        }
    }
}
