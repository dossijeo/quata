package com.quata.core.config

import com.quata.BuildConfig

object AppConfig {
    /**
     * Mientras esté true, los repositorios usan MockData y no intentan conectar.
     * Cambia USE_MOCK_BACKEND en app/build.gradle.kts para activar/desactivar.
     */
    val USE_MOCK_BACKEND: Boolean
        get() = BuildConfig.USE_MOCK_BACKEND

    /** WordPress */
    const val WORDPRESS_BASE_URL = "https://your-wordpress-site.com/"
    const val WORDPRESS_JWT_LOGIN_PATH = "wp-json/jwt-auth/v1/token"
    const val WORDPRESS_REGISTER_PATH = "wp-json/quata/v1/register"

    /** Supabase */
    const val SUPABASE_URL = "https://your-project-ref.supabase.co/"
    const val SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY"

    /** Cambia a "supabase" si tu feed principal vive en Supabase. */
    const val FEED_SOURCE = "wordpress" // wordpress | supabase

    const val SUPABASE_TABLE_POSTS = "posts"
    const val SUPABASE_TABLE_CONVERSATIONS = "conversations"
    const val SUPABASE_TABLE_MESSAGES = "messages"
    const val SUPABASE_TABLE_PUSH_TOKENS = "push_tokens"

    /** Google Sign-In: rellena el Web Client ID si integras Credential Manager/Firebase Auth. */
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID"
}
