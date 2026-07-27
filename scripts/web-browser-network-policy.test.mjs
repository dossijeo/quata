import assert from 'node:assert/strict';
import test from 'node:test';
import {
    classifyBrowserRequest,
    DOCMENTIS_PERMIT_URL,
    TURNSTILE_BOOTSTRAP_URL,
    validateDocmentisPermitRequest,
} from './web-browser-network-policy.mjs';

const localOrigin = 'http://127.0.0.1:40123';
const storageOrigin = 'http://127.0.0.1:40124';

test('allows only the pinned Turnstile bootstrap request', () => {
    assert.equal(
        classifyBrowserRequest({ method: 'GET', url: TURNSTILE_BOOTSTRAP_URL }, localOrigin, storageOrigin),
        'turnstile-bootstrap',
    );
    for (const request of [
        { method: 'POST', url: TURNSTILE_BOOTSTRAP_URL },
        { method: 'GET', url: 'https://challenges.cloudflare.com/turnstile/v0/api.js' },
        { method: 'GET', url: `${TURNSTILE_BOOTSTRAP_URL}&extra=true` },
        { method: 'GET', url: 'https://challenges.cloudflare.com/turnstile/v0/siteverify' },
        { method: 'GET', url: 'https://evil.example/turnstile/v0/api.js?render=explicit' },
    ]) {
        assert.equal(classifyBrowserRequest(request, localOrigin, storageOrigin), 'unexpected');
    }
});

test('recognises only the exact DocMentis permit preflight and POST', () => {
    assert.equal(
        classifyBrowserRequest({ method: 'OPTIONS', url: DOCMENTIS_PERMIT_URL }, localOrigin, storageOrigin),
        'docmentis-permit-preflight',
    );
    assert.equal(
        classifyBrowserRequest({ method: 'POST', url: DOCMENTIS_PERMIT_URL }, localOrigin, storageOrigin),
        'docmentis-permit',
    );
    for (const request of [
        { method: 'GET', url: DOCMENTIS_PERMIT_URL },
        { method: 'POST', url: `${DOCMENTIS_PERMIT_URL}/` },
        { method: 'POST', url: `${DOCMENTIS_PERMIT_URL}?debug=true` },
        { method: 'POST', url: 'https://docmentis.com/api/udoc-viewer/permit' },
    ]) {
        assert.equal(classifyBrowserRequest(request, localOrigin, storageOrigin), 'unexpected');
    }
});

test('permit payload cannot include document data or bypass the pinned SDK contract', () => {
    const valid = {
        distinct_id: '123e4567-e89b-42d3-a456-426614174000',
        host: '127.0.0.1',
        nonce: '0123456789abcdef0123456789abcdef',
        viewer_version: '0.7.9',
    };
    assert.deepEqual(validateDocmentisPermitRequest(JSON.stringify(valid)), []);
    assert.match(
        validateDocmentisPermitRequest(JSON.stringify({ ...valid, document_url: 'secret.pdf' }))[0],
        /fields changed/,
    );
    assert.deepEqual(validateDocmentisPermitRequest('{'), ['permit body is not JSON']);
    assert.match(
        validateDocmentisPermitRequest(JSON.stringify({ ...valid, viewer_version: '0.8.0' }))[0],
        /pinned SDK/,
    );
});

test('loopback assets stay local and all other origins fail closed', () => {
    assert.equal(
        classifyBrowserRequest({ method: 'GET', url: `${localOrigin}/web.js` }, localOrigin, storageOrigin),
        'local',
    );
    assert.equal(
        classifyBrowserRequest({ method: 'GET', url: `${storageOrigin}/authenticated/document.docx` }, localOrigin, storageOrigin),
        'local',
    );
    assert.equal(
        classifyBrowserRequest({ method: 'GET', url: 'https://example.test/document.pdf' }, localOrigin, storageOrigin),
        'unexpected',
    );
});
