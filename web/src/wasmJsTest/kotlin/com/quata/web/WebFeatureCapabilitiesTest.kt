package com.quata.web

import com.quata.core.capability.CapabilityStateOrigin
import com.quata.core.capability.FeatureCapabilityAction
import com.quata.core.capability.QuataFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebFeatureCapabilitiesTest {
    private val configured = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "publishable-key",
    )

    @Test
    fun mapsAuditedWebFeaturesWithoutClaimingE2e() {
        val registry = webFeatureCapabilityRegistry(configured)

        listOf(
            QuataFeature.Feed,
            QuataFeature.Communities,
            QuataFeature.Profile,
            QuataFeature.Composer,
            QuataFeature.Official,
            QuataFeature.Chat,
        ).forEach { feature ->
            assertTrue(registry.projection(feature, FeatureCapabilityAction.View).visible, "$feature route")
            assertFalse(registry.capability(feature).e2e, "$feature E2E is not evidenced")
        }
    }

    @Test
    fun marksLocalProfileAndUnsupportedReadOnlyMutationsHonestly() {
        val registry = webFeatureCapabilityRegistry(configured)

        assertEquals(
            CapabilityStateOrigin.Local,
            registry.projection(QuataFeature.Profile, FeatureCapabilityAction.Mutate).origin,
        )
        assertFalse(registry.projection(QuataFeature.Feed, FeatureCapabilityAction.Mutate).enabled)
        assertFalse(registry.projection(QuataFeature.Communities, FeatureCapabilityAction.Mutate).enabled)
        assertFalse(registry.projection(QuataFeature.Official, FeatureCapabilityAction.Mutate).enabled)
    }

    @Test
    fun disablesRemoteRoutesWhenPublicBackendConfigurationIsAbsent() {
        val registry = webFeatureCapabilityRegistry(WebRuntimeConfiguration())

        assertFalse(registry.projection(QuataFeature.Auth, FeatureCapabilityAction.View).enabled)
        assertTrue(registry.projection(QuataFeature.Profile, FeatureCapabilityAction.View).enabled)
    }
}
