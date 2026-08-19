package com.quata.feature.postcomposer.data

class FailInsertAfterUploadComposerTransport(
    private val delegate: ActorBoundComposerTransport,
    private val failureMessage: String = "post_composer_e2e_forced_insert_after_upload_failure",
) : ActorBoundComposerTransport by delegate {
    private var failed = false

    override suspend fun insertPost(request: ComposerPostInsert): Result<String?> {
        if (!failed && (request.imageUrl != null || request.videoUrl != null)) {
            failed = true
            return Result.failure(IllegalStateException(failureMessage))
        }
        return delegate.insertPost(request)
    }
}
