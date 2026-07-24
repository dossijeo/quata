package com.quata.core.platform

/** Opens a selected or remote document through the platform's real document handling surface. */
interface DocumentOpenService {
    suspend fun open(file: PlatformFile): PlatformResult<Unit>
}

object UnsupportedDocumentOpenService : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> = PlatformResult.Unsupported
}
