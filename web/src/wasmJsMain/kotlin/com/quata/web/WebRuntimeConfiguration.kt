package com.quata.web

import kotlinx.browser.document

/**
 * Public browser configuration injected by the deployment document. Values here are deliberately
 * limited to client-safe settings; service-role keys and VAPID private keys never enter Web.
 */
data class WebRuntimeConfiguration(
    val supabaseUrl: String? = null,
    val supabasePublishableKey: String? = null,
    val releaseVersionCode: Long? = null,
    val webRegistrationEnabled: Boolean = false,
) {
    val isBackendConfigured: Boolean
        get() = !supabaseUrl.isNullOrBlank() && !supabasePublishableKey.isNullOrBlank()

    /**
     * Public deployment requirements only. This deliberately reports meta names, never values,
     * so the launcher can explain a local/unconfigured Auth screen without exposing credentials.
     */
    internal fun missingAuthRuntimeRequirements(): List<WebAuthRuntimeRequirement> = buildList {
        if (supabaseUrl.isNullOrBlank()) add(WebAuthRuntimeRequirement.SupabaseUrl)
        if (supabasePublishableKey.isNullOrBlank()) add(WebAuthRuntimeRequirement.SupabasePublishableKey)
    }

    internal fun authRuntimeDiagnosticOrNull(): String? {
        val missing = missingAuthRuntimeRequirements()
        if (missing.isEmpty()) return null
        return "La autenticación remota no está configurada en este despliegue. " +
            "Falta ${missing.joinToString { it.metaName }}. " +
            "Configura únicamente esos metadatos públicos; no uses claves service-role ni VAPID privadas."
    }

    companion object {
        fun fromDocument(): WebRuntimeConfiguration = WebRuntimeConfiguration(
            supabaseUrl = document.metaContent("quata-supabase-url"),
            supabasePublishableKey = document.metaContent("quata-supabase-publishable-key"),
            releaseVersionCode = document.metaContent("quata-release-version-code")?.toLongOrNull(),
            webRegistrationEnabled = document.metaContent("quata-web-registration-enabled") == "true",
        )
    }
}

internal enum class WebAuthRuntimeRequirement(val metaName: String) {
    SupabaseUrl("quata-supabase-url"),
    SupabasePublishableKey("quata-supabase-publishable-key"),
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
