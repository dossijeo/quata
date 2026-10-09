package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.AtomicPreferenceStore
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Durable, actor-bound storage for the existing post-composer draft.
 *
 * The envelope is deliberately small and versioned. It stores references to platform-owned media,
 * never media bytes or credentials. Callers must validate those references before restoring them.
 */
class PostComposerDraftStore(
    private val preferences: PreferenceStore,
) {
    private val mutationLock = Mutex()

    suspend fun activateActor(actorProfileId: String?): PostComposerDraftActorLease? {
        val normalized = actorProfileId.normalizedActorIdOrNull()
        if (actorProfileId != null && normalized == null) return null
        val mutation = mutateState { current ->
            val next = if (normalized == null || current.actorProfileId != normalized) {
                PostComposerDraftPersistentState(
                    actorProfileId = normalized,
                    generation = current.generation + 1,
                    revision = current.revision + 1,
                    encodedDraft = null,
                )
            } else {
                current
            }
            next to normalized?.let { PostComposerDraftActorLease(it, next.generation) }
        }
        return mutation.result.takeIf { mutation.committed }
    }

    suspend fun save(lease: PostComposerDraftActorLease, draft: PostComposerDraftSnapshot): Boolean {
        val sanitized = draft.sanitizedForPersistence()
        return mutateState { current ->
            if (!current.matches(lease)) current to false
            else current.copy(
                revision = current.revision + 1,
                encodedDraft = sanitized.takeIf { it.isMeaningful() }
                    ?.let(PostComposerDraftEnvelopeCodec::encode),
            ) to true
        }.let { it.committed && it.result == true }
    }

    suspend fun restore(
        actorProfileId: String,
        mediaReferenceAvailable: suspend (String) -> Boolean,
    ): PostComposerDraftRestoration? {
        val lease = activateActor(actorProfileId) ?: return null
        val state = readStateConsistently()
        if (!state.matches(lease)) return null
        val restoredRevision = state.revision
        val encodedDraft = state.encodedDraft ?: return null
        val decoded = PostComposerDraftEnvelopeCodec.decode(encodedDraft)
        if (decoded == null) {
            mutateState { current ->
                if (current.matches(lease) && current.revision == restoredRevision) {
                    current.copy(revision = current.revision + 1, encodedDraft = null) to Unit
                } else {
                    current to Unit
                }
            }
            return null
        }
        val image = decoded.imageUri?.takeIf { mediaReferenceAvailable(it) }
        val video = decoded.videoUri?.takeIf { mediaReferenceAvailable(it) }
        val restored = decoded.copy(imageUri = image, videoUri = video)
        val finalRevision = if (restored != decoded) {
            val repair = mutateState { current ->
                if (current.matches(lease) && current.revision == restoredRevision) {
                    val next = current.copy(
                        revision = current.revision + 1,
                        encodedDraft = PostComposerDraftEnvelopeCodec.encode(restored),
                    )
                    next to next.revision
                } else {
                    current to null
                }
            }
            repair.result.takeIf { repair.committed } ?: return null
        } else {
            restoredRevision
        }
        val restoration = PostComposerDraftRestoration(lease, restored, finalRevision)
        return restoration.takeIf { isCurrent(it) }
    }

    suspend fun isCurrent(lease: PostComposerDraftActorLease): Boolean =
        readStateConsistently().matches(lease)

    suspend fun isCurrent(restoration: PostComposerDraftRestoration): Boolean =
        readStateConsistently().let { state ->
            state.matches(restoration.actorLease) && state.revision == restoration.revision
        }

    suspend fun clear(actorProfileId: String?): PostComposerDraftActorLease? {
        val actor = actorProfileId.normalizedActorIdOrNull() ?: return null
        val current = readStateConsistently()
        if (current.actorProfileId != actor) return null
        return clear(PostComposerDraftActorLease(actor, current.generation))
    }

    suspend fun clear(lease: PostComposerDraftActorLease): PostComposerDraftActorLease? {
        val mutation = mutateState { current ->
            if (!current.matches(lease)) {
                current to null
            } else {
                val next = current.copy(
                    generation = current.generation + 1,
                    revision = current.revision + 1,
                    encodedDraft = null,
                )
                next to PostComposerDraftActorLease(lease.actorProfileId, next.generation)
            }
        }
        return mutation.result.takeIf { mutation.committed }
    }

    private suspend fun readState(): PostComposerDraftPersistentState =
        PostComposerDraftPersistentStateCodec.decode(preferences.getString(StateKey))

    private suspend fun readStateConsistently(): PostComposerDraftPersistentState =
        mutateState { current -> current to current }.result ?: PostComposerDraftPersistentStateCodec.Empty

    private suspend fun <T> mutateState(
        transform: (PostComposerDraftPersistentState) -> Pair<PostComposerDraftPersistentState, T>,
    ): PostComposerDraftStateMutation<T> {
        val atomic = preferences as? AtomicPreferenceStore
        if (atomic != null) {
            var result: T? = null
            var hasResult = false
            val committed = atomic.updateStringAtomically(StateKey) { raw ->
                val (next, value) = transform(PostComposerDraftPersistentStateCodec.decode(raw))
                result = value
                hasResult = true
                PostComposerDraftPersistentStateCodec.encode(next)
            }
            return PostComposerDraftStateMutation(committed && hasResult, result)
        }
        return mutationLock.withLock {
            val (next, result) = transform(readState())
            preferences.putString(StateKey, PostComposerDraftPersistentStateCodec.encode(next))
            PostComposerDraftStateMutation(true, result)
        }
    }

    private fun PostComposerDraftPersistentState.matches(lease: PostComposerDraftActorLease): Boolean =
        actorProfileId == lease.actorProfileId && generation == lease.generation

    private companion object {
        const val StateKey = "post-composer.draft.state.v1"
    }
}

private class PostComposerDraftStateMutation<T>(
    val committed: Boolean,
    val result: T?,
)

private data class PostComposerDraftPersistentState(
    val actorProfileId: String?,
    val generation: Long,
    val revision: Long,
    val encodedDraft: String?,
)

private object PostComposerDraftPersistentStateCodec {
    private const val Version = "QPCS1"

    fun encode(state: PostComposerDraftPersistentState): String = buildString {
        append(Version)
        appendField(state.actorProfileId)
        appendField(state.generation.toString())
        appendField(state.revision.toString())
        appendField(state.encodedDraft)
    }

    fun decode(raw: String?): PostComposerDraftPersistentState = runCatching {
        if (raw == null) return Empty
        require(raw.startsWith(Version))
        var cursor = Version.length
        fun next(): String? {
            val colon = raw.indexOf(':', cursor)
            require(colon >= cursor)
            val length = raw.substring(cursor, colon).toInt()
            cursor = colon + 1
            if (length == -1) return null
            require(length >= 0 && cursor + length <= raw.length)
            return raw.substring(cursor, cursor + length).also { cursor += length }
        }
        val actor = next()?.normalizedActorIdOrNull()
        val generation = requireNotNull(next()).toLong().also { require(it >= 0) }
        val revision = requireNotNull(next()).toLong().also { require(it >= 0) }
        val draft = next()
        require(cursor == raw.length)
        PostComposerDraftPersistentState(actor, generation, revision, draft)
    }.getOrElse { Empty }

    val Empty = PostComposerDraftPersistentState(null, 0, 0, null)

    private fun StringBuilder.appendField(value: String?) {
        if (value == null) append("-1:")
        else append(value.length).append(':').append(value)
    }
}

class PostComposerDraftActorLease internal constructor(
    val actorProfileId: String,
    internal val generation: Long,
)

class PostComposerDraftRestoration internal constructor(
    val actorLease: PostComposerDraftActorLease,
    val snapshot: PostComposerDraftSnapshot,
    internal val revision: Long,
)

private fun PostComposerDraftSnapshot.isMeaningful(): Boolean =
    step != CreatePostStep.TypePicker ||
        text.isNotBlank() ||
        imageUri != null ||
        videoUri != null ||
        locationLabel != null ||
        latitude != null ||
        longitude != null ||
        selectedDestinationWallId != null ||
        textPatternId != DEFAULT_TEXT_CANVAS_PATTERN_ID

private fun String?.normalizedActorIdOrNull(): String? = this
    ?.trim()
    ?.takeIf { it.length in 1..128 && it.all { character -> character.isLetterOrDigit() || character in "._-" } }

private fun PostComposerDraftSnapshot.sanitizedForPersistence(): PostComposerDraftSnapshot = copy(
    text = text.take(CreatePostTextLimit),
    textPatternId = textPatternId.take(128),
    imageUri = imageUri.durableMediaReferenceOrNull(),
    videoUri = videoUri.durableMediaReferenceOrNull(),
    locationLabel = locationLabel?.take(512),
    latitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 },
    longitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 },
    selectedDestinationWallId = selectedDestinationWallId?.take(128),
)

private fun String?.durableMediaReferenceOrNull(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() && it.length <= 4096 && !it.startsWith("data:", ignoreCase = true) }

internal object PostComposerDraftEnvelopeCodec {
    private const val Version = "QPCD1"
    private const val FieldCount = 11
    private const val MaximumEnvelopeLength = 24_576

    fun encode(snapshot: PostComposerDraftSnapshot): String = buildString {
        append(Version)
        listOf(
            snapshot.step.name,
            snapshot.text,
            snapshot.textPatternId,
            snapshot.imageUri,
            snapshot.videoUri,
            snapshot.locationLabel,
            snapshot.latitude?.toString(),
            snapshot.longitude?.toString(),
            snapshot.locationOrigin?.name,
            snapshot.selectedDestinationWallId,
            FieldCount.toString(),
        ).forEach { value -> appendField(value) }
    }

    fun decode(raw: String): PostComposerDraftSnapshot? = runCatching {
        require(raw.length <= MaximumEnvelopeLength && raw.startsWith(Version))
        var cursor = Version.length
        fun next(): String? {
            val colon = raw.indexOf(':', cursor)
            require(colon >= cursor)
            val length = raw.substring(cursor, colon).toInt()
            cursor = colon + 1
            if (length == -1) return null
            require(length >= 0 && cursor + length <= raw.length)
            return raw.substring(cursor, cursor + length).also { cursor += length }
        }
        val step = enumValueOf<CreatePostStep>(requireNotNull(next()))
        val text = requireNotNull(next())
        val pattern = requireNotNull(next())
        val image = next()
        val video = next()
        val location = next()
        val latitude = next()?.toDouble()
        val longitude = next()?.toDouble()
        val origin = next()?.let { enumValueOf<CreatePostLocationOrigin>(it) }
        val destination = next()
        require(next()?.toInt() == FieldCount && cursor == raw.length)
        PostComposerDraftSnapshot(
            step = step,
            text = text,
            textPatternId = pattern,
            imageUri = image,
            videoUri = video,
            locationLabel = location,
            latitude = latitude,
            longitude = longitude,
            locationOrigin = origin,
            selectedDestinationWallId = destination,
        ).sanitizedForPersistence()
    }.getOrNull()

    private fun StringBuilder.appendField(value: String?) {
        if (value == null) append("-1:")
        else append(value.length).append(':').append(value)
    }
}
