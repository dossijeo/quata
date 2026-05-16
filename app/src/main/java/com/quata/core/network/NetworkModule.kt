package com.quata.core.network

import com.quata.core.config.AppConfig
import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.wordpress.WordpressApi
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.data.supabase.SupabaseConfig
import com.quata.data.supabase.SupabaseHttpClient
import com.quata.data.supabase.SupabaseRealtimeClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkModule {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val wordpressClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val supabaseClient = OkHttpClient.Builder()
        .addInterceptor(supabaseAuthInterceptor())
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val supabaseHelperConfig = SupabaseConfig(
        projectUrl = AppConfig.SUPABASE_URL,
        anonKey = AppConfig.SUPABASE_ANON_KEY
    )

    val supabaseHttpClient = SupabaseHttpClient(
        config = supabaseHelperConfig,
        okHttp = supabaseClient
    )

    val supabaseCommunityApi = SupabaseCommunityApi(supabaseHttpClient)

    val supabaseRealtimeClient = SupabaseRealtimeClient(
        config = supabaseHelperConfig,
        okHttp = supabaseClient
    )

    val wordpressApi: WordpressApi = Retrofit.Builder()
        .baseUrl(AppConfig.WORDPRESS_BASE_URL)
        .client(wordpressClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WordpressApi::class.java)

    val supabaseApi: SupabaseApi = Retrofit.Builder()
        .baseUrl(AppConfig.SUPABASE_URL)
        .client(supabaseClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SupabaseApi::class.java)

    private fun supabaseAuthInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${AppConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }
}
