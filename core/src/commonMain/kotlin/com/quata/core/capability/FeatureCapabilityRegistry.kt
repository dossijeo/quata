package com.quata.core.capability

import kotlinx.serialization.Serializable

/**
 * Stable identifiers shared by launchers, rather than screen class names.  A feature can have a
 * useful read path while its write path is deliberately unavailable, so availability is modelled
 * per action as well as by the migration dimensions.
 */
@Serializable
enum class QuataFeature {
    Auth,
    Feed,
    Chat,
    Profile,
    Communities,
    Official,
    Composer,
}

@Serializable
enum class QuataPlatform { Android, Web, Ios }

/** Where the user-visible state comes from. Local is valid only when the UI says so explicitly. */
@Serializable
enum class CapabilityStateOrigin { Real, Local, Unsupported }

@Serializable
enum class FeatureCapabilityAction { View, Mutate }

/**
 * Versioned, evidence-oriented capability cell. `backendReal` and `e2e` must never be inferred
 * from the other booleans: compiling a host is not evidence of a working remote flow.
 */
@Serializable
data class FeatureCapability(
    val compiles: Boolean,
    val exported: Boolean,
    val composed: Boolean,
    val navigable: Boolean,
    val backendReal: Boolean,
    val e2e: Boolean,
    val stateOrigin: CapabilityStateOrigin,
    val mutationOrigin: CapabilityStateOrigin = CapabilityStateOrigin.Unsupported,
    /** Team/component accountable for refreshing this cell as evidence changes. */
    val owner: String? = null,
    val evidence: String? = null,
)

@Serializable
data class FeatureCapabilityManifest(
    val schemaVersion: Int = CurrentSchemaVersion,
    val platform: QuataPlatform,
    val capabilities: Map<QuataFeature, FeatureCapability>,
) {
    companion object { const val CurrentSchemaVersion = 1 }
}

/** Injectable at platform composition roots; common feature code remains platform-agnostic. */
interface FeatureCapabilityRegistry {
    val manifest: FeatureCapabilityManifest

    fun capability(feature: QuataFeature): FeatureCapability =
        manifest.capabilities[feature] ?: UnsupportedFeatureCapability

    fun projection(feature: QuataFeature, action: FeatureCapabilityAction): FeatureCapabilityProjection {
        val capability = capability(feature)
        val origin = when (action) {
            FeatureCapabilityAction.View -> capability.stateOrigin
            FeatureCapabilityAction.Mutate -> capability.mutationOrigin
        }
        val isNavigable = capability.composed && capability.navigable
        return FeatureCapabilityProjection(
            visible = isNavigable,
            enabled = isNavigable && origin != CapabilityStateOrigin.Unsupported,
            origin = origin,
            backendReal = capability.backendReal,
            e2e = capability.e2e,
            message = when (origin) {
                CapabilityStateOrigin.Real -> if (capability.e2e) null else "La ruta remota est\u00e1 configurada; el recorrido E2E a\u00fan no est\u00e1 verificado."
                CapabilityStateOrigin.Local -> "Los cambios se guardan s\u00f3lo en este dispositivo; no se sincronizan con el servidor."
                CapabilityStateOrigin.Unsupported -> "Esta acci\u00f3n a\u00fan no est\u00e1 disponible en esta plataforma."
            },
        )
    }
}

data class FeatureCapabilityProjection(
    val visible: Boolean,
    val enabled: Boolean,
    val origin: CapabilityStateOrigin,
    val backendReal: Boolean,
    val e2e: Boolean,
    val message: String?,
)

class StaticFeatureCapabilityRegistry(
    override val manifest: FeatureCapabilityManifest,
) : FeatureCapabilityRegistry

val UnsupportedFeatureCapability = FeatureCapability(
    compiles = false,
    exported = false,
    composed = false,
    navigable = false,
    backendReal = false,
    e2e = false,
    stateOrigin = CapabilityStateOrigin.Unsupported,
)
