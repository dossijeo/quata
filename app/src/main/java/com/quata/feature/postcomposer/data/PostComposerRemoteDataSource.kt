package com.quata.feature.postcomposer.data

import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.supabase.SupabaseCreatePostRequest
import com.quata.core.network.wordpress.WordpressApi
import com.quata.core.network.wordpress.WordpressCreatePostRequest

class PostComposerRemoteDataSource(
    private val wordpressApi: WordpressApi,
    private val supabaseApi: SupabaseApi
) {
    suspend fun createWordpressPost(token: String, text: String) = wordpressApi.createPost(
        bearerToken = "Bearer $token",
        request = WordpressCreatePostRequest(title = text.take(48).ifBlank { "Qüata" }, content = text)
    )

    suspend fun createSupabasePost(userId: String, text: String, imageUrl: String?) = supabaseApi.createPost(
        SupabaseCreatePostRequest(userId = userId, text = text, imageUrl = imageUrl)
    )
}
