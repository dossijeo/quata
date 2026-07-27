package com.quata.core.capability

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FeatureCapabilityRegistryTest {
    @Test
    fun localStateIsActionableButNeverAdvertisedAsRemotePersistence() {
        val registry = registry(QuataFeature.Profile, localCapability())

        val projection = registry.projection(QuataFeature.Profile, FeatureCapabilityAction.Mutate)

        assertTrue(projection.visible)
        assertTrue(projection.enabled)
        assertFalse(projection.backendReal)
        assertEquals(CapabilityStateOrigin.Local, projection.origin)
        assertTrue(projection.message!!.contains("s\u00f3lo en este dispositivo"))
    }

    @Test
    fun unsupportedMutationRemainsDisabledWhenReadPathIsReal() {
        val registry = registry(QuataFeature.Feed, localCapability(
            backendReal = true,
            stateOrigin = CapabilityStateOrigin.Real,
            mutationOrigin = CapabilityStateOrigin.Unsupported,
        ))

        assertTrue(registry.projection(QuataFeature.Feed, FeatureCapabilityAction.View).enabled)
        assertFalse(registry.projection(QuataFeature.Feed, FeatureCapabilityAction.Mutate).enabled)
    }

    @Test
    fun configuredRemoteAdapterIsNotDescribedAsAnE2eSuccess() {
        val projection = registry(
            QuataFeature.Chat,
            localCapability(backendReal = true, stateOrigin = CapabilityStateOrigin.Real),
        ).projection(QuataFeature.Chat, FeatureCapabilityAction.View)

        assertTrue(projection.enabled)
        assertFalse(projection.e2e)
        assertTrue(projection.message!!.contains("E2E"))
        assertFalse(projection.message.contains("Conectado"))
    }

    @Test
    fun unknownFeatureIsNotVisible() {
        assertFalse(registry(QuataFeature.Auth, localCapability()).projection(QuataFeature.Chat, FeatureCapabilityAction.View).visible)
    }

    @Test
    fun spanishFallbackTextIsUsedForCapabilityNotices() {
        assertEquals("Funci\u00f3n no disponible", DefaultFeatureCapabilityText.title(CapabilityStateOrigin.Unsupported))
        assertEquals(
            "Esta acci\u00f3n a\u00fan no est\u00e1 disponible en esta plataforma.",
            DefaultFeatureCapabilityText.message(CapabilityStateOrigin.Unsupported, e2eVerified = false),
        )
    }

    @Test
    fun capabilityTextCatalogueSelectsSpanishAndEnglishFromLanguageTags() {
        assertEquals(
            "Datos locales",
            FeatureCapabilityTextCatalog.forLanguageTag("es-ES").title(CapabilityStateOrigin.Local),
        )
        assertEquals(
            "Local data",
            FeatureCapabilityTextCatalog.forLanguageTag("EN-us").title(CapabilityStateOrigin.Local),
        )
    }

    @Test
    fun capabilityTextCatalogueKeepsSpanishForUnknownOrMissingLanguageTags() {
        assertEquals(
            "Funci\u00f3n no disponible",
            FeatureCapabilityTextCatalog.forLanguageTag("fr-FR").title(CapabilityStateOrigin.Unsupported),
        )
        assertEquals(
            "Funci\u00f3n no disponible",
            FeatureCapabilityTextCatalog.forLanguageTag(null).title(CapabilityStateOrigin.Unsupported),
        )
    }

    @Test
    fun capabilityTextCatalogueLocalizesFeedMediaAvailabilityWithSpanishFallback() {
        assertEquals(
            "Media content is not available on this platform yet.",
            FeatureCapabilityTextCatalog.forLanguageTag("en-GB").mediaUnavailable(),
        )
        assertEquals(
            "El contenido multimedia a\u00fan no est\u00e1 disponible en esta plataforma.",
            FeatureCapabilityTextCatalog.forLanguageTag("fr-FR").mediaUnavailable(),
        )
    }

    @Test
    fun registryUsesInjectedCapabilityText() {
        val text = object : FeatureCapabilityText {
            override fun title(origin: CapabilityStateOrigin) = "custom-title"
            override fun message(origin: CapabilityStateOrigin, e2eVerified: Boolean) = "custom-message"
            override fun mediaUnavailable() = "custom-media-unavailable"
        }
        val registry = StaticFeatureCapabilityRegistry(
            manifest = FeatureCapabilityManifest(
                platform = QuataPlatform.Web,
                capabilities = mapOf(QuataFeature.Feed to localCapability()),
            ),
            text = text,
        )

        assertEquals("custom-message", registry.projection(QuataFeature.Feed, FeatureCapabilityAction.View).message)
    }

    private fun registry(feature: QuataFeature, capability: FeatureCapability) = StaticFeatureCapabilityRegistry(
        FeatureCapabilityManifest(platform = QuataPlatform.Web, capabilities = mapOf(feature to capability)),
    )

    private fun localCapability(
        backendReal: Boolean = false,
        stateOrigin: CapabilityStateOrigin = CapabilityStateOrigin.Local,
        mutationOrigin: CapabilityStateOrigin = CapabilityStateOrigin.Local,
    ) = FeatureCapability(true, false, true, true, backendReal, false, stateOrigin, mutationOrigin)
}
