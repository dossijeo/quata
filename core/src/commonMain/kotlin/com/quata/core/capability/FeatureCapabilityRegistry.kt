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
 * Small, injectable text contract for capability notices. Platform launchers may replace this
 * later with localized resources; the Spanish implementation is the explicit shared fallback.
 */
interface FeatureCapabilityText {
    fun title(origin: CapabilityStateOrigin): String
    fun message(origin: CapabilityStateOrigin, e2eVerified: Boolean): String?
}

/**
 * Deliberately small locale set for capability notices.  Feature-specific catalogues can grow
 * independently; this contract must not depend on an Android or browser resource API.
 */
enum class FeatureCapabilityLocale {
    Spanish,
    English,
}

object SpanishFeatureCapabilityText : FeatureCapabilityText {
    override fun title(origin: CapabilityStateOrigin): String = when (origin) {
        CapabilityStateOrigin.Real -> "Estado de integraci\u00f3n"
        CapabilityStateOrigin.Local -> "Datos locales"
        CapabilityStateOrigin.Unsupported -> "Funci\u00f3n no disponible"
    }

    override fun message(origin: CapabilityStateOrigin, e2eVerified: Boolean): String? = when (origin) {
        CapabilityStateOrigin.Real -> if (e2eVerified) null else
            "La ruta remota est\u00e1 configurada; el recorrido E2E a\u00fan no est\u00e1 verificado."
        CapabilityStateOrigin.Local ->
            "Los cambios se guardan s\u00f3lo en este dispositivo; no se sincronizan con el servidor."
        CapabilityStateOrigin.Unsupported ->
            "Esta acci\u00f3n a\u00fan no est\u00e1 disponible en esta plataforma."
    }
}

object EnglishFeatureCapabilityText : FeatureCapabilityText {
    override fun title(origin: CapabilityStateOrigin): String = when (origin) {
        CapabilityStateOrigin.Real -> "Integration status"
        CapabilityStateOrigin.Local -> "Local data"
        CapabilityStateOrigin.Unsupported -> "Feature unavailable"
    }

    override fun message(origin: CapabilityStateOrigin, e2eVerified: Boolean): String? = when (origin) {
        CapabilityStateOrigin.Real -> if (e2eVerified) null else
            "The remote route is configured; the E2E journey has not been verified yet."
        CapabilityStateOrigin.Local ->
            "Changes are stored only on this device and are not synchronized with the server."
        CapabilityStateOrigin.Unsupported ->
            "This action is not available on this platform yet."
    }
}

/**
 * Common catalogue for the capability-notice surface. Unknown or absent language tags retain
 * the established Spanish fallback until a launcher deliberately supplies another catalogue.
 */
object FeatureCapabilityTextCatalog {
    fun forLocale(locale: FeatureCapabilityLocale): FeatureCapabilityText = when (locale) {
        FeatureCapabilityLocale.Spanish -> SpanishFeatureCapabilityText
        FeatureCapabilityLocale.English -> EnglishFeatureCapabilityText
    }

    fun forLanguageTag(languageTag: String?): FeatureCapabilityText = when (
        languageTag?.trim()?.substringBefore('-')?.lowercase()
    ) {
        "en" -> EnglishFeatureCapabilityText
        "es" -> SpanishFeatureCapabilityText
        else -> SpanishFeatureCapabilityText
    }
}

/** Default retained until a platform composition root installs localized capability text. */
val DefaultFeatureCapabilityText: FeatureCapabilityText = SpanishFeatureCapabilityText

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
    /**
     * Platform composition roots can supply their own resource-backed implementation without
     * making the common capability model depend on a platform resource system.
     */
    val text: FeatureCapabilityText get() = DefaultFeatureCapabilityText

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
            message = text.message(origin, capability.e2e),
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
    override val text: FeatureCapabilityText = DefaultFeatureCapabilityText,
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
