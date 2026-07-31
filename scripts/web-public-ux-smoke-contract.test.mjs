import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const smoke = await readFile(new URL('./web-browser-smoke.mjs', import.meta.url), 'utf8');
const routeContract = await readFile(new URL('./web-browser-route-contract.mjs', import.meta.url), 'utf8');
const index = await readFile(new URL('../web/src/wasmJsMain/resources/index.html', import.meta.url), 'utf8');

test('WEB-UX-001 keeps public deep-link recovery and responsive coverage in the real production smoke', () => {
    assert.match(smoke, /import \{ SMOKE_ROUTE_CONTRACTS \} from '\.\/web-browser-route-contract\.mjs';/);
    assert.match(smoke, /const routeContracts = SMOKE_ROUTE_CONTRACTS;/);
    for (const fragment of ['auth', 'feed', 'official', 'chat', 'settings', 'share-target', 'share-target-error', 'notifications', 'profile', 'composer', 'communities', 'whats-new', 'about']) {
        assert.match(routeContract, new RegExp(`fragment: '${fragment}'`));
    }
    assert.match(routeContract, /kind: 'auth'/);
    assert.match(routeContract, /kind: 'public'/);
    assert.match(routeContract, /kind: 'private'/);
    assert.match(smoke, /navigateAndAssertPublicShell\(cdp, staticServer\.origin, contract, pageErrors\)/);
    assert.match(smoke, /navigateAndAssertAuthBoundary\(cdp, staticServer\.origin, contract, pageErrors\)/);
    assert.match(smoke, /navigateAndAssertPrivateAuthBoundary\(cdp, staticServer\.origin, contract, pageErrors\)/);
    assert.doesNotMatch(smoke, /navigateAndAssertShell/);
    for (const fragment of [
        'post-publication-123',
        'official-bulletin-99',
        'unknown-route',
    ]) assert.match(smoke, new RegExp(`fragment: '${fragment.replace(/[?]/g, '\\?')}'`));
    assert.match(smoke, /await assertUnauthenticatedDeepLinkRecovery\(cdp, staticServer\.origin, pageErrors\)/);
    assert.match(smoke, /const privateDeepLinks = \[/);
    assert.match(smoke, /fragment: 'chat-sb%3Ateam%2F42\?message=msg%209'/);
    assert.match(smoke, /waitForPrivateDeepLinkAuthBoundary\(cdp, fragment, returnRoute\)/);
    assert.match(smoke, /lastProbe\?\.hash === ''/);
    assert.match(smoke, /lastProbe\.route === 'feed'/);
    assert.match(smoke, /lastProbe\.shellRoute === 'feed'/);
    assert.match(smoke, /lastProbe\.prompt === 'visible'/);
    assert.match(smoke, /lastProbe\.pendingRoute === fragment/);
    assert.match(smoke, /bridge\.chooseLogin\(\)/);
    assert.match(smoke, /value\?\.hash === '#auth'/);
    assert.match(smoke, /value\.destination === 'login'/);
    assert.match(smoke, /waitForShellMarker\(cdp, contract\.route\)/);
    assert.match(smoke, /assertShellHidden\(cdp, contract\.fragment\)/);
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

test('WEB-UX-001 accepts only a stable viewport-filling Compose canvas when native Login controls are absent', () => {
    assert.match(smoke, /const nativeControls = visibleControls\.length > 0/);
    assert.match(smoke, /const renderer = nativeControls \? 'native_controls' : 'compose_canvas'/);
    assert.match(smoke, /canvas\.width >= viewport\.width - 1 && canvas\.height >= viewport\.height - 1/);
    assert.match(smoke, /!value\.root \|\| value\.root\.width < viewport\.width - 1 \|\| value\.root\.height < viewport\.height - 1/);
    assert.match(smoke, /: !canvasMatchesViewport\);/);
    assert.match(smoke, /left\.renderer !== right\.renderer/);
    assert.match(smoke, /left\.canvases\.length !== right\.canvases\.length/);
    assert.match(smoke, /compactKeyboard: \{ mode: 'compose_canvas', skipped: 'native_controls_absent' \}/);
    assert.match(smoke, /keyboardAndAx = responsiveUx\.nativeControlsPresent/);
});
