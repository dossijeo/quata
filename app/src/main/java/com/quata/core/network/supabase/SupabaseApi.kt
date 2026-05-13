package com.quata.core.network.supabase

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
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

    @Headers("Prefer: return=representation")
    @POST("rest/v1/conversations")
    suspend fun createConversation(@Body request: SupabaseCreateConversationRequest): List<SupabaseConversationDto>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/conversations")
    suspend fun updateConversation(
        @Query("id") idFilter: String,
        @Body request: SupabaseConversationUpdateRequest
    ): List<SupabaseConversationDto>

    @GET("rest/v1/messages?select=*&order=created_at.asc")
    suspend fun getMessages(
        @Query("conversation_id") conversationFilter: String
    ): List<SupabaseMessageDto>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    suspend fun sendMessage(@Body request: SupabaseSendMessageRequest): List<SupabaseMessageDto>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/messages")
    suspend fun updateMessages(
        @Query("conversation_id") conversationFilter: String,
        @Query("sender_id") senderFilter: String,
        @Body request: SupabaseMessageUpdateRequest
    )

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/push_tokens")
    suspend fun registerPushToken(@Body request: SupabasePushTokenRequest)

    @GET("rest/v1/profiles?select=*")
    suspend fun getProfiles(
        @Query("id") idFilter: String
    ): List<SupabaseProfileDto>

    @GET("rest/v1/profiles?select=id,email,display_name,neighborhood,phone,avatar_url")
    suspend fun getEmergencyCandidates(): List<SupabaseProfileDto>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body request: SupabaseProfileUpdateRequest
    )
}
