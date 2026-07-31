package com.quata.web

import com.quata.feature.chat.data.ChatAttachmentUploader
import com.quata.feature.chat.data.ChatAuthenticatedUserProvider
import com.quata.feature.chat.data.ChatPostgrestResponse
import com.quata.feature.chat.data.ChatPostgrestTransport
import com.quata.feature.chat.data.PostgrestChatRepository
import com.quata.feature.chat.data.UploadedChatAttachment

/**
 * WASM boundary for the portable PostgREST chat repository.
 *
 * Browser fetch, session refresh and Blob-to-Storage upload stay here; all RPC payloads, polling,
 * reconciliation and chat state live in feature:chat/commonMain.
 */
class WebChatRepository(
    rpcClient: WebPostgrestRpcClient,
    authRepository: WebAuthRepository,
    attachmentUploader: WebChatAttachmentUploader,
    pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : PostgrestChatRepository(
    transport = WebChatPostgrestTransport(rpcClient),
    authenticatedUser = ChatAuthenticatedUserProvider {
        authRepository.sessionForAuthenticatedRequest()?.userId
    },
    attachmentUploader = ChatAttachmentUploader { profileId, file ->
        attachmentUploader.upload(profileId, file).toCommonAttachment()
    },
    pollIntervalMillis = pollIntervalMillis,
    realtimeGateway = WebChatRealtimeGateway(rpcClient.configuration, authRepository),
) {
    private companion object {
        const val DefaultPollIntervalMillis = 30_000L
    }
}

private class WebChatPostgrestTransport(
    private val rpcClient: WebPostgrestRpcClient,
) : ChatPostgrestTransport {
    override suspend fun post(functionName: String, body: String): ChatPostgrestResponse = when (
        val result = rpcClient.post(functionName, body)
    ) {
        is WebPostgrestResult.Success -> ChatPostgrestResponse.Success(result.body)
        is WebPostgrestResult.Failure -> ChatPostgrestResponse.Failure(WebPostgrestReadException(result))
    }
}

private fun UploadedWebChatAttachment.toCommonAttachment() = UploadedChatAttachment(
    storagePath = storagePath,
    publicUrl = publicUrl,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    name = name,
    extension = extension,
)
