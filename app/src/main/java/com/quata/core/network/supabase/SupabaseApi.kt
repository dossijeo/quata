package com.quata.core.network.supabase

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {
    @GET("rest/v1/posts?select=*&order=created_at.desc")
    suspend fun getPosts(): List<SupabasePostDto>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/posts")
    suspend fun createPost(@Body request: SupabaseCreatePostRequest): List<SupabasePostDto>

    @GET("rest/v1/conversations?select=*&order=updated_at.desc")
    suspend fun getConversations(): List<SupabaseConversationDto>

    @GET("rest/v1/messages?select=*&order=created_at.asc")
    suspend fun getMessages(
        @Query("conversation_id") conversationFilter: String
    ): List<SupabaseMessageDto>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    suspend fun sendMessage(@Body request: SupabaseSendMessageRequest): List<SupabaseMessageDto>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/push_tokens")
    suspend fun registerPushToken(@Body request: SupabasePushTokenRequest)
}
