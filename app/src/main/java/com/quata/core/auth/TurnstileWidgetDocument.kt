package com.quata.core.auth

internal object TurnstileWidgetDocument {
    private val SiteKeyPattern = Regex("[A-Za-z0-9_-]{8,200}")
    private val ContextPattern = Regex("[A-Za-z0-9-]{16,64}")

    fun render(siteKey: String, contextNonce: String): String {
        require(SiteKeyPattern.matches(siteKey)) { "registration_challenge_site_key_invalid" }
        require(ContextPattern.matches(contextNonce)) { "registration_challenge_context_invalid" }
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy"
              content="default-src 'none'; script-src 'nonce-$contextNonce' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com; img-src data: https://challenges.cloudflare.com; style-src 'unsafe-inline'">
            <script nonce="$contextNonce">
              function quataSuccess(token){
                $TurnstileWebMessageObjectName.postMessage('success:$contextNonce:' + encodeURIComponent(token));
              }
              function quataFailure(code){
                $TurnstileWebMessageObjectName.postMessage('failure:$contextNonce:' + encodeURIComponent(code || 'widget_error'));
              }
              function quataExpired(){ quataFailure('expired'); }
              function quataTimeout(){ quataFailure('interactive_timeout'); }
              function quataRender(){
                if (!window.turnstile || typeof window.turnstile.render !== 'function') {
                  quataFailure('bootstrap_unavailable');
                  return;
                }
                try {
                  window.turnstile.render('#quata-turnstile', {
                    sitekey: '$siteKey',
                    action: '$TurnstileRegisterAction',
                    callback: quataSuccess,
                    'error-callback': quataFailure,
                    'expired-callback': quataExpired,
                    'timeout-callback': quataTimeout
                  });
                } catch (_) {
                  quataFailure('render_error');
                }
              }
              document.addEventListener('DOMContentLoaded', quataRender, { once: true });
            </script>
            <script nonce="$contextNonce" src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" defer></script>
            </head><body>
            <div id="quata-turnstile"></div>
            </body></html>
        """.trimIndent()
    }
}

internal const val TurnstileWebMessageObjectName = "QuataTurnstile"
internal const val TurnstileChallengeTimeoutMillis = 90_000L
