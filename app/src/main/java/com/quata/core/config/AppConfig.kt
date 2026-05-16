package com.quata.core.config

import com.quata.BuildConfig

object AppConfig {
    /**
     * Mientras este true, los repositorios usan MockData y no intentan conectar.
     * Se genera desde -Pquata.useMockBackend=true en Gradle.
     */
    val USE_MOCK_BACKEND: Boolean
        get() = BuildConfig.USE_MOCK_BACKEND

    /** WordPress */
    const val WORDPRESS_BASE_URL = "https://egquata.com/"
    const val WORDPRESS_JWT_LOGIN_PATH = "wp-json/jwt-auth/v1/token"
    const val WORDPRESS_REGISTER_PATH = "wp-json/quata/v1/register"
    const val QUATA_WORDPRESS_BASE_URL = "https://egquata.com/"
    const val BETTER_MESSAGES_BASE_URL = QUATA_WORDPRESS_BASE_URL

    /** Supabase */
    const val SUPABASE_URL = "https://yrrlankpwmhluexshxnw.supabase.co/"
    const val SUPABASE_ANON_KEY = "sb_publishable_dQILq4zEe6xW1TpJPQwMHw_gk6ZlaX3"

    const val FEED_SOURCE = "supabase"

    const val SUPABASE_TABLE_POSTS = "community_posts"
    const val SUPABASE_TABLE_CONVERSATIONS = "community_private_chats"
    const val SUPABASE_TABLE_MESSAGES = "community_private_messages"
    const val SUPABASE_TABLE_PUSH_TOKENS = "push_tokens"

    /** Google Sign-In: rellena el Web Client ID si integras Credential Manager/Firebase Auth. */
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_GOOGLE_WEB_CLIENT_ID"
}
