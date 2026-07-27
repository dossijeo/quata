package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository

/**
 * The minimum payload a future iOS write adapter must bind to the authenticated actor.
 *
 * This is deliberately a transport boundary, not a PostgREST or Storage implementation. The
 * latter may only be introduced once the corresponding RLS and object-ownership contract has
 * been exercised end-to-end.
 */
data class IosActorBoundPublicationRequest(
    val actorId: String,
    val draft: PostComposerDraft,
)

/** A future authenticated iOS publication transport. It has no network implementation yet. */
fun interface IosComposerPublicationTransport {
    suspend fun publish(request: IosActorBoundPublicationRequest): Result<String?>
}

/**
 * Capability gate for iOS writes. There is intentionally no enabled state in this slice.
 *
 * Keeping the only constructible state closed prevents a launcher from accidentally treating a
 * configured URL/key as proof that RLS and Storage object ownership allow publication.
 */
sealed interface IosComposerPublicationCapability {
    data object ContractNotVerified : IosComposerPublicationCapability
}

/**
 * Internal until a dedicated iOS RLS/Storage integration change documents its evidence.
 * It is deliberately not exposed by an iOS host factory or Swift export in this slice.
 */
internal data object VerifiedIosComposerRlsAndStorageContract : IosComposerPublicationCapability

/**
 * Actor-bound repository shape reserved for the verified iOS publication contract.
 *
 * The default capability fails before reading the actor or invoking [IosComposerPublicationTransport].
 * The internal verified branch is exercised only by the module test: no iOS host exposes it and
 * this slice provides no HTTP, PostgREST, or Storage implementation.
 */
class IosActorBoundPostComposerRepository(
    private val authenticatedActorId: suspend () -> String?,
    private val transport: IosComposerPublicationTransport,
    private val capability: IosComposerPublicationCapability = IosComposerPublicationCapability.ContractNotVerified,
) : PostComposerRepository {
    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = when (capability) {
        IosComposerPublicationCapability.ContractNotVerified -> Result.failure(
            IllegalStateException("ios_composer_publication_contract_not_verified"),
        )
        VerifiedIosComposerRlsAndStorageContract -> {
            val actorId = authenticatedActorId()?.trim().orEmpty()
            if (actorId.isEmpty()) {
                Result.failure(IllegalStateException("ios_composer_authenticated_actor_missing"))
            } else {
                transport.publish(IosActorBoundPublicationRequest(actorId = actorId, draft = draft))
            }
        }
    }
}
