package com.wakeway.app.config

import com.wakeway.app.BuildConfig

object AppConfig {
    val backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val configured: Boolean
        get() = backendUrl.isNotBlank()

    fun endpoint(path: String): String =
        if (backendUrl.isBlank()) "" else "$backendUrl/${path.trimStart('/')}"
}
