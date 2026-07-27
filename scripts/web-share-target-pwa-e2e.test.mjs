import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { TURNSTILE_BOOTSTRAP_URL } from './web-browser-network-policy.mjs';
import { shareTargetNetworkDecision } from './web-share-target-network-policy.mjs';

const localOrigin = 'https://127.0.0.1:43123';

test('share target permits only HTTPS-local assets and the exact Turnstile stub', () => {
    assert.equal(
        shareTargetNetworkDecision({ method: 'GET', url: `${localOrigin}/quata-sw.js` }, localOrigin),
        'continue-local',
    );
    assert.equal(
        shareTargetNetworkDecision({ method: 'GET', url: TURNSTILE_BOOTSTRAP_URL }, localOrigin),
        'stub-turnstile',
    );
    for (const request of [
        { method: 'POST', url: TURNSTILE_BOOTSTRAP_URL },
        { method: 'GET', url: 'https://challenges.cloudflare.com/turnstile/v0/api.js' },
        { method: 'GET', url: 'https://example.test/tracker.js' },
        { method: 'POST', url: 'https://project.supabase.co/functions/v1/quata-web-push' },
    ]) {
        assert.equal(shareTargetNetworkDecision(request, localOrigin), 'block-unexpected');
    }
});

test('browser runner wires the policy and cannot pass with unexpected origins', async () => {
    const source = await readFile(new URL('./web-share-target-pwa-e2e.mjs', import.meta.url), 'utf8');
    assert.match(source, /context\.route\("\*\*\/\*"/);
    assert.match(source, /context\.on\("request"/);
    assert.match(source, /createSecureServer/);
    assert.match(source, /https:\/\/127\.0\.0\.1:\$\{address\.port\}/);
    assert.match(source, /--ignore-certificate-errors/);
    assert.match(source, /installAndColdStart/);
    assert.match(source, /navigator\.serviceWorker\.controller !== null/);
    assert.match(source, /https_must_be_a_secure_context/);
    assert.match(source, /single_share_must_create_exactly_one_claimable_entry/);
    assert.match(source, /cold_start_must_retain_exactly_one_claimable_entry/);
    assert.match(source, /discard_must_clean_indexeddb_entry/);
    assert.match(source, /quata-web-share-target-tls-/);
    assert.match(source, /subjectAltName=IP:127\.0\.0\.1,DNS:localhost/);
    assert.match(source, /--proxy-server=http:\/\/127\.0\.0\.1:9/);
    assert.match(source, /--proxy-bypass-list=127\.0\.0\.1;localhost/);
    assert.match(source, /relativeCandidate\.startsWith\(`\.\.\$\{sep\}`\)/);
    assert.match(source, /shareTargetNetworkDecision\(descriptor, server\.origin\)/);
    assert.match(source, /unexpectedNetworkRequests\.push/);
    assert.match(source, /assert\(unexpectedNetworkRequests\.length === 0, "unexpected_external_network_request"\)/);
    assert.match(source, /assert\(turnstileRequests > 0, "turnstile_bootstrap_stub_not_exercised"\)/);
});
