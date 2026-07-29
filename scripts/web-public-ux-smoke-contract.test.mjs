import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const smoke = await readFile(new URL('./web-browser-smoke.mjs', import.meta.url), 'utf8');

test('WEB-UX-001 keeps public deep-link recovery and responsive coverage in the real production smoke', () => {
    for (const fragment of [
        'post/publication-123',
        'official/bulletin-99',
        'chat-sb%3Ateam%2F42?message=msg%209',
        'unknown-route',
    ]) assert.match(smoke, new RegExp(`fragment: '${fragment.replace(/[?]/g, '\\?')}'`));
    assert.match(smoke, /await assertPublicDeepLinkRecovery\(cdp, staticServer\.origin, pageErrors\)/);
    assert.match(smoke, /Page\.reload/);
    assert.match(smoke, /waitForNavigationRoute\(cdp, route\)/);
    for (const viewport of ['mobile', 'tablet', 'desktop']) {
        assert.match(smoke, new RegExp(`name: '${viewport}'`));
    }
    assert.match(smoke, /Emulation\.setDeviceMetricsOverride/);
    assert.match(smoke, /scrollWidth > viewport\.width/);
    assert.match(smoke, /Responsive \$\{viewport\.name\} Auth layout\/semantic controls failed/);
});

test('WEB-UX-001 validates native keyboard focus and the Chrome AX tree, not an inert test surrogate', () => {
    assert.match(smoke, /document\.querySelector\('input\[aria-label\]'\)/);
    assert.match(smoke, /Input\.dispatchKeyEvent/);
    assert.match(smoke, /Accessibility\.getFullAXTree/);
    assert.match(smoke, /role === 'textbox'/);
    assert.match(smoke, /roles\.includes\('button'\)/);
    assert.doesNotMatch(smoke, /quata-test-contract[^\n]*focus/);
});
