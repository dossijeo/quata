@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.capability.CapabilityStateOrigin
import com.quata.core.capability.FeatureCapability
import com.quata.core.capability.FeatureCapabilityAction
import com.quata.core.capability.FeatureCapabilityManifest
import com.quata.core.capability.FeatureCapabilityRegistry
import com.quata.core.capability.FeatureCapabilityTextCatalog
import com.quata.core.capability.QuataFeature
import com.quata.core.capability.QuataPlatform
import com.quata.core.capability.StaticFeatureCapabilityRegistry
import com.quata.core.ui.components.QuataCard

/**
 * Browser evidence manifest. Profile/SOS uses the same temporary direct authenticated PostgREST
 * access as Android. Actor checks are client-side compatibility guards, not a substitute for the
 * coordinated RLS/bridge rollout tracked in docs/PROFILE_BRIDGE_REQUIREMENTS.md.
 */
fun webFeatureCapabilityRegistry(
    configuration: WebRuntimeConfiguration,
    hasAuthenticatedSession: Boolean = false,
): FeatureCapabilityRegistry {
    val remoteRead = configuration.isBackendConfigured
    val remoteOrigin = if (remoteRead) CapabilityStateOrigin.Real else CapabilityStateOrigin.Unsupported
    val remoteProfile = remoteRead && hasAuthenticatedSession
    val remoteComposer = remoteRead && hasAuthenticatedSession
    // Profile/SOS has a real, authenticated Android-equivalent temporary transport. Do not
    // downgrade it to a local draft merely because the coordinated RLS rollout is pending.
    val profileOrigin = if (remoteProfile) CapabilityStateOrigin.Real else CapabilityStateOrigin.Unsupported
    fun capability(
        source: CapabilityStateOrigin = remoteOrigin,
        mutation: CapabilityStateOrigin = CapabilityStateOrigin.Unsupported,
        backend: Boolean = remoteRead,
    ) = FeatureCapability(
        compiles = true,
        exported = false,
        composed = true,
        navigable = true,
        backendReal = backend,
        e2e = false,
        stateOrigin = source,
        mutationOrigin = mutation,
        owner = "web-launcher",
        evidence = "docs/MULTIPLATFORM_MIGRATION_BOARD.md",
    )
    return StaticFeatureCapabilityRegistry(
        manifest = FeatureCapabilityManifest(
            platform = QuataPlatform.Web,
            capabilities = mapOf(
                QuataFeature.Auth to capability(mutation = remoteOrigin),
                QuataFeature.Feed to capability(),
                QuataFeature.Chat to capability(mutation = remoteOrigin),
                // Cuenta has no local product fallback. A configured signed-in session uses the
                // temporary direct Android-equivalent transport; missing configuration/session
                // remains unavailable.
                QuataFeature.Profile to capability(
                    source = profileOrigin,
                    mutation = profileOrigin,
                    backend = remoteProfile,
                ),
                QuataFeature.Communities to capability(),
                QuataFeature.Official to capability(),
                QuataFeature.Composer to capability(
                    source = if (remoteComposer) CapabilityStateOrigin.Real else CapabilityStateOrigin.Local,
                    mutation = if (remoteComposer) CapabilityStateOrigin.Real else CapabilityStateOrigin.Unsupported,
                    backend = remoteComposer,
                ),
            ),
        ),
        text = FeatureCapabilityTextCatalog.forLanguageTag(browserCapabilityLanguageTag()),
    )
}

internal fun browserCapabilityLanguageTag(): String? = js(
    "globalThis.navigator?.language || globalThis.document?.documentElement?.lang || null",
)

/** Small design-system card used by Web routes so local/unsupported state is never mistaken for sync. */
@Composable
fun WebFeatureCapabilityNotice(
    registry: FeatureCapabilityRegistry,
    feature: QuataFeature,
    action: FeatureCapabilityAction = FeatureCapabilityAction.View,
    modifier: Modifier = Modifier,
) {
    val projection = registry.projection(feature, action)
    val message = projection.message ?: return
    QuataCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(registry.text.title(projection.origin))
            Text(message)
        }
    }
}

/**
 * Keeps every already-wired route mounted and discoverable without changing its product viewport.
 *
 * A capability manifest is an honest status projection, not an alternative navigation guard or a
 * production chrome surface. Diagnostic callers may opt into the notice explicitly; product
 * routes render only their content so they remain visually comparable with Android.
 */
@Composable
fun WebFeatureCapabilityRoute(
    registry: FeatureCapabilityRegistry,
    feature: QuataFeature,
    action: FeatureCapabilityAction = FeatureCapabilityAction.View,
    showCapabilityNotice: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (showCapabilityNotice) {
        Column(Modifier.fillMaxSize()) {
            WebFeatureCapabilityNotice(registry, feature, action)
            content()
        }
    } else {
        content()
    }
}
