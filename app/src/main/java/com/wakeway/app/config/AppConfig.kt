package com.wakeway.app.config

import com.wakeway.app.BuildConfig

object AppConfig {
    private const val PRODUCTION_BACKEND = "https://wakeway-api.shivgarg184.workers.dev"

    val backendUrl: String =
        BuildConfig.BACKEND_URL.trim().trimEnd('/').ifBlank { PRODUCTION_BACKEND }

    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val configured: Boolean
        get() = backendUrl.isNotBlank()

    fun endpoint(path: String): String =
        if (backendUrl.isBlank()) "" else "$backendUrl/${path.trimStart('/')}"
}
