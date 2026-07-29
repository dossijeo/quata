import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const smoke = await readFile(new URL('./web-browser-smoke.mjs', import.meta.url), 'utf8');
const index = await readFile(new URL('../web/src/wasmJsMain/resources/index.html', import.meta.url), 'utf8');

test('WEB-UX-001 keeps public deep-link recovery and responsive coverage in the real production smoke', () => {
    for (const fragment of [
        'post-publication-123',
        'official-bulletin-99',
        'chat-sb%3Ateam%2F42?message=msg%209',
        'unknown-route',
    ]) assert.match(smoke, new RegExp(`fragment: '${fragment.replace(/[?]/g, '\\?')}'`));
    assert.match(smoke, /await assertPublicDeepLinkRecovery\(cdp, staticServer\.origin, pageErrors\)/);
    assert.match(smoke, /Page\.reload/);
    assert.match(smoke, /recoveredHash\?\.result\?\.value !== `#\$\{fragment\}`/);
    assert.match(smoke, /waitForNavigationRoute\(cdp, route\)/);
    for (const viewport of ['mobile', 'tablet', 'desktop']) {
        assert.match(smoke, new RegExp(`name: '${viewport}'`));
    }
    assert.match(smoke, /Emulation\.setDeviceMetricsOverride/);
    assert.match(smoke, /requestAnimationFrame\(\(\) => requestAnimationFrame/);
    assert.match(smoke, /requiredStableSamples = 3/);
    assert.match(smoke, /responsiveLayoutsEquivalent\(previous, current\)/);
    assert.match(smoke, /scrollWidth !== viewport\.width/);
    assert.match(smoke, /Responsive \$\{viewport\.name\} Auth layout did not become valid and stable/);
    assert.match(index, /html,\s*body\s*\{\s*overflow:\s*hidden;\s*\}/);
});

test('WEB-UX-001 validates the exact native Auth focus sequence and AX nodes, not global counts or an inert surrogate', () => {
    assert.match(smoke, /controls\?\.phone\?\.tag !== 'INPUT'/);
    assert.match(smoke, /controls\.password\.type !== 'password'/);
    assert.match(smoke, /controls\?\.submit\?\.tag !== 'BUTTON'/);
    assert.match(smoke, /Input\.dispatchKeyEvent/);
    assert.match(smoke, /matches: active === expected/);
    assert.match(smoke, /stableSamples >= 2/);
    assert.match(smoke, /DOM\.getDocument', \{ depth: -1, pierce: true \}/);
    assert.match(smoke, /backendDOMNodeId === node\.backendNodeId/);
    assert.match(smoke, /Accessibility\.getPartialAXTree/);
    assert.match(smoke, /name === identity\.expectedName/);
    assert.match(smoke, /waitForExactAuthFocus\(cdp, 'password'/);
    assert.match(smoke, /waitForExactAuthFocus\(cdp, 'submit'/);
    assert.match(smoke, /focusExactAuthControl\(cdp, 'phone'\)/);
    assert.doesNotMatch(smoke, /Accessibility\.getFullAXTree/);
    assert.doesNotMatch(smoke, /roles\.filter\(role => role === 'textbox'\)/);
    assert.doesNotMatch(smoke, /quata-test-contract[^\n]*focus/);
    assert.match(smoke, /name: 'compact-keyboard', width: 360, height: 320/);
    assert.match(smoke, /scrollIntoView\(\{ block: 'nearest', inline: 'nearest' \}\)/);
    assert.match(smoke, /pageScrollY !== 0/);
    assert.match(smoke, /Compact submit control is not keyboard-visible without page scrolling/);
});
