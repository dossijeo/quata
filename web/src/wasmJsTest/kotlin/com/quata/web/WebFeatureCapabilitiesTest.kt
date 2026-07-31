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
    fun keepsProfileUnavailableUntilAnAuthenticatedSessionCanUseTheRemoteGateway() {
        val registry = webFeatureCapabilityRegistry(configured)

        assertEquals(
            CapabilityStateOrigin.Unsupported,
            registry.projection(QuataFeature.Profile, FeatureCapabilityAction.Mutate).origin,
        )
        assertFalse(registry.projection(QuataFeature.Feed, FeatureCapabilityAction.Mutate).enabled)
        assertFalse(registry.projection(QuataFeature.Communities, FeatureCapabilityAction.Mutate).enabled)
        assertFalse(registry.projection(QuataFeature.Official, FeatureCapabilityAction.Mutate).enabled)
    }

    @Test
    fun keepsComposerVisibleAsLocalButFailsClosedForPublication() {
        val registry = webFeatureCapabilityRegistry(configured, hasAuthenticatedSession = true)

        assertEquals(
            CapabilityStateOrigin.Real,
            registry.projection(QuataFeature.Composer, FeatureCapabilityAction.View).origin,
        )
        assertFalse(registry.capability(QuataFeature.Composer).backendReal)
        assertEquals(
            CapabilityStateOrigin.Unsupported,
            registry.projection(QuataFeature.Composer, FeatureCapabilityAction.Mutate).origin,
        )
        assertFalse(registry.projection(QuataFeature.Composer, FeatureCapabilityAction.Mutate).enabled)
    }

    @Test
    fun marksProfileRemoteOnlyWithConfiguredBackendAndAuthenticatedSession() {
        val authenticated = webFeatureCapabilityRegistry(
            configuration = configured,
            hasAuthenticatedSession = true,
        )

        assertTrue(authenticated.capability(QuataFeature.Profile).backendReal)
        assertEquals(
            CapabilityStateOrigin.Real,
            authenticated.projection(QuataFeature.Profile, FeatureCapabilityAction.View).origin,
        )
        assertEquals(
            CapabilityStateOrigin.Local,
            authenticated.projection(QuataFeature.Profile, FeatureCapabilityAction.Mutate).origin,
        )
        assertFalse(authenticated.capability(QuataFeature.Profile).e2e)
    }

    @Test
    fun disablesRemoteRoutesWhenPublicBackendConfigurationIsAbsent() {
        val unconfigured = WebRuntimeConfiguration()
        val registry = webFeatureCapabilityRegistry(unconfigured)

        assertFalse(registry.projection(QuataFeature.Auth, FeatureCapabilityAction.View).enabled)
        assertTrue(registry.projection(QuataFeature.Profile, FeatureCapabilityAction.View).enabled)
        assertFalse(registry.capability(QuataFeature.Auth).backendReal)
        assertEquals(
            listOf(
                WebAuthRuntimeRequirement.SupabaseUrl,
                WebAuthRuntimeRequirement.SupabasePublishableKey,
            ),
            unconfigured.missingAuthRuntimeRequirements(),
        )
        val diagnostic = requireNotNull(unconfigured.authRuntimeDiagnosticOrNull())
        assertTrue(diagnostic.contains("quata-supabase-url"))
        assertTrue(diagnostic.contains("quata-supabase-publishable-key"))
        assertTrue(diagnostic.contains("no uses claves service-role"))
    }

    @Test
    fun reportsOnlyTheMissingPublicAuthSetting() {
        val configuration = WebRuntimeConfiguration(supabaseUrl = "https://project.supabase.co")

        assertEquals(
            listOf(WebAuthRuntimeRequirement.SupabasePublishableKey),
            configuration.missingAuthRuntimeRequirements(),
        )
        assertTrue(requireNotNull(configuration.authRuntimeDiagnosticOrNull()).contains("quata-supabase-publishable-key"))
        assertEquals(null, configured.authRuntimeDiagnosticOrNull())
    }
}
