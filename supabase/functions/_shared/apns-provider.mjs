const encoder = new TextEncoder();
const hosts = Object.freeze({
  sandbox: "https://api.sandbox.push.apple.com",
  production: "https://api.push.apple.com",
});

function base64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function encodedJson(value) {
  return base64url(encoder.encode(JSON.stringify(value)));
}

/**
 * Server-only APNs provider. Transport must support HTTP/2 with normal TLS validation.
 * @param {{keyId?: string, teamId?: string, topic?: string, environment: string, privateKeyPem?: string}} configuration
 * @param {{transport?: (url: string, init: RequestInit) => Promise<Response>, cryptoProvider?: Crypto, now?: () => number, timeoutMs?: number}} options
 */
export function createApnsProvider({ keyId, teamId, topic, environment, privateKeyPem }, {
  transport,
  cryptoProvider = globalThis.crypto,
  now = Date.now,
  timeoutMs = 10_000,
} = {}) {
  if (!/^[A-Z0-9]{10}$/.test(keyId ?? "") || !/^[A-Z0-9]{10}$/.test(teamId ?? "") ||
      !/^[A-Za-z0-9][A-Za-z0-9.-]+$/.test(topic ?? "") ||
      !Object.hasOwn(hosts, environment) || typeof transport !== "function" ||
      !Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new Error("apns_configuration_invalid");
  }
  let signingKey;
  let cachedJwt;
  let issuedAt;
  let pendingAuthorization;

  async function authorization() {
    const seconds = Math.floor(now() / 1000);
    // APNs rejects JWTs older than one hour. Reuse within 50 minutes to avoid excessive rotation.
    if (cachedJwt && seconds >= issuedAt && seconds - issuedAt < 3000) return cachedJwt;
    if (pendingAuthorization) return pendingAuthorization;
    pendingAuthorization = signAuthorization(seconds);
    try { return await pendingAuthorization; } finally { pendingAuthorization = undefined; }
  }

  async function signAuthorization(seconds) {
    try {
      if (!signingKey) {
        const match = /^\s*-----BEGIN PRIVATE KEY-----\s+([A-Za-z0-9+/=\s]+)-----END PRIVATE KEY-----\s*$/.exec(privateKeyPem ?? "");
        if (!match) throw new Error();
        const der = Uint8Array.from(atob(match[1].replace(/\s/g, "")), (c) => c.charCodeAt(0));
        signingKey = await cryptoProvider.subtle.importKey(
          "pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"],
        );
      }
      const message = `${encodedJson({ alg: "ES256", kid: keyId })}.${encodedJson({ iss: teamId, iat: seconds })}`;
      const signature = await cryptoProvider.subtle.sign(
        { name: "ECDSA", hash: "SHA-256" }, signingKey, encoder.encode(message),
      );
      cachedJwt = `${message}.${base64url(signature)}`;
      issuedAt = seconds;
      return cachedJwt;
    } catch {
      // Never expose PEM, provider JWTs or low-level crypto diagnostics.
      throw new Error("apns_signing_failed");
    }
  }

  return {
    /** @returns {Promise<{ok: true, code?: never, unregisteredAt?: never} | {ok: false, code: string, unregisteredAt?: number}>} */
    async send({ token, payload }) {
      if (typeof token !== "string" || !/^(?:[a-f0-9]{2})+$/.test(token)) {
        return { ok: false, code: "apns_device_token_invalid" };
      }
      let body;
      try { body = JSON.stringify(payload); } catch { return { ok: false, code: "apns_payload_invalid" }; }
      if (!payload?.aps || typeof payload.aps !== "object" || !body) {
        return { ok: false, code: "apns_payload_invalid" };
      }
      if (encoder.encode(body).byteLength > 4096) return { ok: false, code: "apns_payload_too_large" };
      let jwt;
      try { jwt = await authorization(); } catch { return { ok: false, code: "apns_signing_failed" }; }
      try {
        const response = await transport(`${hosts[environment]}/3/device/${token}`, {
          method: "POST",
          redirect: "error",
          signal: AbortSignal.timeout(timeoutMs),
          headers: {
            authorization: `bearer ${jwt}`,
            "apns-topic": topic,
            "apns-push-type": "alert",
            "apns-priority": "10",
            "apns-expiration": "0",
            "content-type": "application/json",
          },
          body,
        });
        if (response.status === 200) {
          await response.body?.cancel();
          return { ok: true };
        }
        // Whitelist diagnostics: provider bodies must never flow into logs or client responses.
        const error = await response.json().catch(() => ({}));
        const knownReasons = new Set([
          "BadDeviceToken", "DeviceTokenNotForTopic", "Unregistered", "ExpiredProviderToken",
          "InvalidProviderToken", "TooManyProviderTokenUpdates", "TooManyRequests",
          "PayloadTooLarge", "BadTopic", "TopicDisallowed", "Forbidden", "InternalServerError",
          "ServiceUnavailable", "Shutdown", "MissingProviderToken",
        ]);
        const reason = knownReasons.has(error?.reason) ? error.reason : "unknown";
        const result = { ok: false, code: `apns_${response.status}_${reason}` };
        if (reason === "ExpiredProviderToken" && cachedJwt === jwt) cachedJwt = undefined;
        // A caller may retire only a registration no newer than this timestamp (milliseconds).
        // BadDeviceToken can mean the wrong APNs environment; it is not a retirement signal.
        if (response.status === 410 && reason === "Unregistered" &&
            Number.isFinite(error.timestamp) && error.timestamp > 0) {
          result.unregisteredAt = error.timestamp;
        }
        return result;
      } catch {
        return { ok: false, code: "apns_transport_failed" };
      }
    },
  };
}
