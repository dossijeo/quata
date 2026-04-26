package com.quata.feature.chat.data

import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.supabase.SupabaseSendMessageRequest

class ChatRemoteDataSource(private val supabaseApi: SupabaseApi) {
    suspend fun getConversations() = supabaseApi.getConversations()
    suspend fun getMessages(conversationId: String) = supabaseApi.getMessages("eq.$conversationId")
    suspend fun sendMessage(userId: String, senderName: String, conversationId: String, text: String) =
        supabaseApi.sendMessage(SupabaseSendMessageRequest(conversationId, userId, senderName, text))
}
