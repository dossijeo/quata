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
    configuration: WebRuntimeConfiguration,
    rpcClient: WebPostgrestRpcClient,
    authRepository: WebAuthRepository,
    attachmentUploader: WebChatAttachmentUploader,
    pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : PostgrestChatRepository(
    transport = WebChatPostgrestTransport(rpcClient),
    authenticatedUser = ChatAuthenticatedUserProvider {
        authRepository.sessionForAuthenticatedRequest()?.userId
    },
    attachmentUploader = object : ChatAttachmentUploader {
        override suspend fun upload(profileId: String, file: com.quata.core.platform.PlatformFile): UploadedChatAttachment =
            attachmentUploader.upload(profileId, file).toCommonAttachment()

        override suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean =
            attachmentUploader.delete(uploaded.storagePath)
    },
    pollIntervalMillis = pollIntervalMillis,
    realtimeGateway = WebChatRealtimeGateway(configuration, authRepository),
) {
    private companion object {
        const val DefaultPollIntervalMillis = 30_000L
    }
}

private class WebChatPostgrestTransport(
    private val rpcClient: WebPostgrestRpcClient,
) : ChatPostgrestTransport {
    override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
        if (consumeWebChatMutationFailure(functionName)) {
            return ChatPostgrestResponse.Failure(IllegalStateException("chat_message_mutation_e2e_forced_failure"))
        }
        if (functionName == "quata_chat_set_muted" && webChatMuteEvidenceFailureRequested()) {
            return ChatPostgrestResponse.Failure(IllegalStateException("chat_mute_e2e_failure"))
        }
        return when (val result = rpcClient.post(functionName, body)) {
            is WebPostgrestResult.Success -> ChatPostgrestResponse.Success(result.body)
            is WebPostgrestResult.Failure -> ChatPostgrestResponse.Failure(WebPostgrestReadException(result))
        }
    }
}

@JsFun("""(functionName) => {
  const host = globalThis.location?.hostname;
  if (host !== 'localhost' && host !== '127.0.0.1') return false;
  if (globalThis.__QUATA_CHAT_MUTATION_FAILURE_FIXTURE_OPT_IN__ !== 'I_ACCEPT_WEB_CHAT_MESSAGE_MUTATION_FAILURE_FIXTURE') return false;
  const operation = functionName === 'quata_chat_edit_message' ? 'edit' : functionName === 'quata_chat_delete_messages' ? 'delete' : null;
  if (!operation || globalThis.__QUATA_CHAT_MUTATION_FORCE_FAILURE__ !== operation) return false;
  globalThis.__QUATA_CHAT_MUTATION_FORCE_FAILURE__ = null;
  return true;
}""")
private external fun consumeWebChatMutationFailure(functionName: String): Boolean

@JsFun("""() => ['localhost', '127.0.0.1'].includes(globalThis.location?.hostname) && globalThis.__QUATA_CHAT_MUTE_FORCE_FAILURE__ === true""")
private external fun webChatMuteEvidenceFailureRequested(): Boolean

private fun UploadedWebChatAttachment.toCommonAttachment() = UploadedChatAttachment(
    storagePath = storagePath,
    publicUrl = publicUrl,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    name = name,
    extension = extension,
)
