package com.quata.web

import kotlinx.browser.document

/**
 * Public browser configuration injected by the deployment document. Values here are deliberately
 * limited to client-safe settings; service-role keys and VAPID private keys never enter Web.
 */
data class WebRuntimeConfiguration(
    val supabaseUrl: String? = null,
    val supabasePublishableKey: String? = null,
) {
    val isBackendConfigured: Boolean
        get() = !supabaseUrl.isNullOrBlank() && !supabasePublishableKey.isNullOrBlank()

    companion object {
        fun fromDocument(): WebRuntimeConfiguration = WebRuntimeConfiguration(
            supabaseUrl = document.metaContent("quata-supabase-url"),
            supabasePublishableKey = document.metaContent("quata-supabase-publishable-key"),
        )
    }
}

/** Builds the unauthenticated VAPID-key endpoint from already-injected public runtime config. */
fun WebRuntimeConfiguration.webPushBootstrapConfigurationOrNull(): WebPushBootstrapConfiguration? =
    supabaseUrl
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }
        ?.let { WebPushBootstrapConfiguration(vapidEndpoint = "$it/functions/v1/quata-web-push") }

private fun org.w3c.dom.Document.metaContent(name: String): String? =
    querySelector("meta[name='$name']")
        ?.getAttribute("content")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
