package com.quata.feature.feed.data

import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.wordpress.WordpressApi

class FeedRemoteDataSource(
    private val wordpressApi: WordpressApi,
    private val supabaseApi: SupabaseApi
) {
    suspend fun getWordpressPosts() = wordpressApi.getPosts()
    suspend fun getSupabasePosts() = supabaseApi.getPosts()
}
