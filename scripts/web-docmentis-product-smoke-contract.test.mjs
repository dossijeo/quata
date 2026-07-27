import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const mainSource = await readFile(
    new URL('../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt', import.meta.url),
    'utf8',
);
const serviceSource = await readFile(
    new URL('../web/src/wasmJsMain/kotlin/com/quata/web/WebDocmentisDocumentOpenService.kt', import.meta.url),
    'utf8',
);
const smokeSource = await readFile(new URL('./web-browser-smoke.mjs', import.meta.url), 'utf8');

test('DocMentis smoke bridge delegates to the composition-root product service', () => {
    assert.doesNotMatch(mainSource, /installDocmentisSmokeProbe\(\)/);
    assert.match(mainSource, /platformServices\.documentOpener\.open\(/);
    assert.match(mainSource, /PlatformFile\(/);
    assert.match(serviceSource, /installDocmentisProductSmokeBridge/);
    assert.doesNotMatch(serviceSource, /__quataDocmentisProbe/);
    assert.doesNotMatch(serviceSource, /runWithViewer/);
});

test('browser gate cannot pass on SDK mount alone or a hardcoded file extension', () => {
    assert.match(smokeSource, /__quataDocmentisProductProbe\?\.open/);
    assert.match(smokeSource, /displayName: 'permit-smoke'/);
    assert.match(smokeSource, /mimeType: 'application\/pdf'/);
    assert.match(smokeSource, /trace\?\.mounted !== true/);
    assert.match(smokeSource, /trace\?\.removed !== true/);
    assert.match(smokeSource, /trace\?\.fallbacks\?\.length !== 1/);
    assert.match(smokeSource, /assertDocmentisPermitFlow\(permitRequests\)/);
    assert.doesNotMatch(smokeSource, /expectPermitFailClosed/);
    assert.doesNotMatch(smokeSource, /fixture = '[^']+\.pdf'/);
});

test('DocMentis query opt-in runs only after the stable six-route metric series', () => {
    const routeSeries = smokeSource.indexOf('for (const fragment of routeFragments.slice(1))');
    const docmentisProbe = smokeSource.indexOf('if (options.docmentis)', routeSeries);
    const metricsAssertion = smokeSource.indexOf('assertTurnstileBootstrapFlow', docmentisProbe);
    assert.ok(routeSeries >= 0 && docmentisProbe > routeSeries && metricsAssertion > docmentisProbe);
});
