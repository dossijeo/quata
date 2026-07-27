export const TURNSTILE_BOOTSTRAP_URL =
    'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
export const DOCMENTIS_PERMIT_URL =
    'https://www.docmentis.com/api/udoc-viewer/permit';

export function classifyBrowserRequest(request, localOrigin, authenticatedStorageOrigin) {
    if (request.method === 'GET' && request.url === TURNSTILE_BOOTSTRAP_URL) {
        return 'turnstile-bootstrap';
    }
    if (request.url === DOCMENTIS_PERMIT_URL && request.method === 'OPTIONS') {
        return 'docmentis-permit-preflight';
    }
    if (request.url === DOCMENTIS_PERMIT_URL && request.method === 'POST') {
        return 'docmentis-permit';
    }
    try {
        const parsed = new URL(request.url);
        if (
            parsed.protocol === 'data:' ||
            parsed.protocol === 'blob:' ||
            parsed.origin === localOrigin ||
            parsed.origin === authenticatedStorageOrigin
        ) {
            return 'local';
        }
    } catch {
        // Invalid URLs are external by default.
    }
    return 'unexpected';
}

export function validateDocmentisPermitRequest(postData) {
    let payload;
    try {
        payload = JSON.parse(postData ?? '');
    } catch {
        return ['permit body is not JSON'];
    }
    if (!payload || Array.isArray(payload) || typeof payload !== 'object') {
        return ['permit body is not an object'];
    }
    const expectedKeys = ['distinct_id', 'host', 'nonce', 'viewer_version'];
    const actualKeys = Object.keys(payload).sort();
    if (actualKeys.join('\0') !== [...expectedKeys].sort().join('\0')) {
        return [`permit body fields changed: ${actualKeys.join(', ')}`];
    }
    const failures = [];
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(payload.distinct_id)) {
        failures.push('distinct_id is not a random UUID v4');
    }
    if (!['127.0.0.1', 'localhost', '::1'].includes(payload.host)) {
        failures.push('permit host is not loopback');
    }
    if (!/^[0-9a-f]{32}$/i.test(payload.nonce)) {
        failures.push('permit nonce is not 16 random bytes');
    }
    if (payload.viewer_version !== '0.7.9') {
        failures.push('viewer_version no longer matches the pinned SDK');
    }
    return failures;
}
