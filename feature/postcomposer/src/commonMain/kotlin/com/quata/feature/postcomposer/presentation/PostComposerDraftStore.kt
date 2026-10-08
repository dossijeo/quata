package com.quata.feature.postcomposer.presentation

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
    private var activeActorProfileId: String? = null
    private var actorGeneration: Long = 0
    private var actorResolved: Boolean = false

    suspend fun activateActor(actorProfileId: String?): PostComposerDraftActorLease? = mutationLock.withLock {
        val normalized = actorProfileId.normalizedActorIdOrNull()
        if (!actorResolved || activeActorProfileId != normalized) {
            activeActorProfileId = normalized
            actorGeneration += 1
            actorResolved = true
        }
        val previous = preferences.getString(ActiveActorKey)?.normalizedActorIdOrNull()
        if (previous != null && previous != normalized) preferences.remove(draftKey(previous))
        if (normalized == null) preferences.remove(ActiveActorKey)
        else preferences.putString(ActiveActorKey, normalized)
        normalized?.let { PostComposerDraftActorLease(it, actorGeneration) }
    }

    suspend fun save(lease: PostComposerDraftActorLease, draft: PostComposerDraftSnapshot): Boolean = mutationLock.withLock {
        if (!lease.isCurrentLocked()) return@withLock false
        val actor = lease.actorProfileId
        val sanitized = draft.sanitizedForPersistence()
        if (!sanitized.isMeaningful()) preferences.remove(draftKey(actor))
        else preferences.putString(draftKey(actor), PostComposerDraftEnvelopeCodec.encode(sanitized))
        preferences.putString(ActiveActorKey, actor)
        true
    }

    suspend fun restore(
        actorProfileId: String,
        mediaReferenceAvailable: suspend (String) -> Boolean,
    ): PostComposerDraftRestoration? {
        val lease = activateActor(actorProfileId) ?: return null
        return mutationLock.withLock {
            if (!lease.isCurrentLocked()) return@withLock null
            val actor = lease.actorProfileId
            val key = draftKey(actor)
            val raw = preferences.getString(key) ?: return@withLock null
            val decoded = PostComposerDraftEnvelopeCodec.decode(raw)
            if (decoded == null) {
                preferences.remove(key)
                return@withLock null
            }
            val image = decoded.imageUri?.takeIf { mediaReferenceAvailable(it) }
            val video = decoded.videoUri?.takeIf { mediaReferenceAvailable(it) }
            decoded.copy(imageUri = image, videoUri = video).also { restored ->
                if (restored != decoded) preferences.putString(key, PostComposerDraftEnvelopeCodec.encode(restored))
            }.let { PostComposerDraftRestoration(lease, it) }
        }
    }

    suspend fun isCurrent(lease: PostComposerDraftActorLease): Boolean = mutationLock.withLock {
        lease.isCurrentLocked()
    }

    suspend fun clear(actorProfileId: String?): PostComposerDraftActorLease? = mutationLock.withLock {
        val actor = actorProfileId.normalizedActorIdOrNull() ?: return@withLock null
        preferences.remove(draftKey(actor))
        if (preferences.getString(ActiveActorKey) == actor) preferences.remove(ActiveActorKey)
        if (actorResolved && activeActorProfileId == actor) {
            actorGeneration += 1
            PostComposerDraftActorLease(actor, actorGeneration)
        } else {
            null
        }
    }

    private fun PostComposerDraftActorLease.isCurrentLocked(): Boolean =
        actorResolved && activeActorProfileId == actorProfileId && actorGeneration == generation

    private fun draftKey(actorProfileId: String): String = "$DraftKeyPrefix$actorProfileId"

    private companion object {
        const val DraftKeyPrefix = "post-composer.draft.v1."
        const val ActiveActorKey = "post-composer.draft.active-actor.v1"
    }
}

class PostComposerDraftActorLease internal constructor(
    val actorProfileId: String,
    internal val generation: Long,
)

class PostComposerDraftRestoration internal constructor(
    val actorLease: PostComposerDraftActorLease,
    val snapshot: PostComposerDraftSnapshot,
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
