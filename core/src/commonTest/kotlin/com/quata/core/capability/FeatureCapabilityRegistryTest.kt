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

    private fun registry(feature: QuataFeature, capability: FeatureCapability) = StaticFeatureCapabilityRegistry(
        FeatureCapabilityManifest(platform = QuataPlatform.Web, capabilities = mapOf(feature to capability)),
    )

    private fun localCapability(
        backendReal: Boolean = false,
        stateOrigin: CapabilityStateOrigin = CapabilityStateOrigin.Local,
        mutationOrigin: CapabilityStateOrigin = CapabilityStateOrigin.Local,
    ) = FeatureCapability(true, false, true, true, backendReal, false, stateOrigin, mutationOrigin)
}
