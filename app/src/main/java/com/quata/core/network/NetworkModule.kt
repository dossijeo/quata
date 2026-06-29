package com.quata.core.network

import android.content.Context
import com.quata.core.config.AppConfig
import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.wordpress.WordpressApi
import com.quata.core.session.SessionManager
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.data.supabase.SupabaseConfig
import com.quata.data.supabase.SupabaseHttpClient
import com.quata.data.supabase.SupabaseRealtimeClient
import com.quata.data.supabase.SupabaseResponseCacheStore
import com.quata.wordpress.QuataWordPressClient
import okhttp3.Interceptor
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class NetworkModule(
    context: Context,
    private val sessionManager: SessionManager
) {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val wordpressClient = OkHttpClient.Builder()
        .dns(Ipv4FirstDns)
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

    private val supabaseRealtimeSocketClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val supabaseHelperConfig = SupabaseConfig(
        projectUrl = AppConfig.SUPABASE_URL,
        anonKey = AppConfig.SUPABASE_ANON_KEY
    )
    private val supabaseResponseCacheStore = SupabaseResponseCacheStore(context)

    val supabaseHttpClient = SupabaseHttpClient(
        config = supabaseHelperConfig,
        okHttp = supabaseClient,
        cacheStore = supabaseResponseCacheStore,
        sessionManager = sessionManager
    )

    val supabaseCommunityApi = SupabaseCommunityApi(supabaseHttpClient)

    val supabaseRealtimeClient = SupabaseRealtimeClient(
        config = supabaseHelperConfig,
        okHttp = supabaseRealtimeSocketClient
    )

    val quataWordPressClient = QuataWordPressClient(
        baseUrl = AppConfig.QUATA_WORDPRESS_BASE_URL,
        httpClient = wordpressClient
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
        val bearer = sessionManager.currentSession()
            ?.bearerToken
            ?.takeIf { it.isNotBlank() }
            ?: AppConfig.SUPABASE_ANON_KEY
        val request = chain.request().newBuilder()
            .header("apikey", AppConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $bearer")
            .build()
        chain.proceed(request)
    }

    private object Ipv4FirstDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { address ->
                if (address is Inet4Address) 0 else 1
            }
    }
}
