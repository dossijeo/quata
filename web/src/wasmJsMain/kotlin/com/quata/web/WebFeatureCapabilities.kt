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
 * Browser evidence manifest. Most remote capability is configuration-driven, while Profile/SOS
 * deliberately falls back to an explicitly local draft until a real authenticated session is
 * available. The profile gateway must never be advertised as remote merely because a public
 * endpoint is configured.
 */
fun webFeatureCapabilityRegistry(
    configuration: WebRuntimeConfiguration,
    hasAuthenticatedSession: Boolean = false,
): FeatureCapabilityRegistry {
    val remoteRead = configuration.isBackendConfigured
    val remoteOrigin = if (remoteRead) CapabilityStateOrigin.Real else CapabilityStateOrigin.Unsupported
    val remoteProfile = remoteRead && hasAuthenticatedSession
    val profileOrigin = if (remoteProfile) CapabilityStateOrigin.Real else CapabilityStateOrigin.Local
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
                // The Web Profile repository selects PostgREST only when both public runtime
                // metadata and an authenticated session exist; otherwise its local draft is
                // intentionally visible and labelled as such.
                QuataFeature.Profile to capability(profileOrigin, profileOrigin, backend = remoteProfile),
                QuataFeature.Communities to capability(),
                QuataFeature.Official to capability(),
                QuataFeature.Composer to capability(mutation = remoteOrigin),
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
 * Keeps every already-wired route mounted and discoverable.
 *
 * A capability manifest is an honest status projection, not an alternative navigation guard:
 * individual hosts retain their existing configuration/error states.  In particular, suppressing
 * a host here would make a functional local route disappear merely because its remote adapter is
 * not configured yet.
 */
@Composable
fun WebFeatureCapabilityRoute(
    registry: FeatureCapabilityRegistry,
    feature: QuataFeature,
    action: FeatureCapabilityAction = FeatureCapabilityAction.View,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WebFeatureCapabilityNotice(registry, feature, action)
        content()
    }
}
