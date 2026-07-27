import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const runner = await readFile(new URL("./web-authenticated-browser-e2e.mjs", import.meta.url), "utf8");
const wrapper = await readFile(new URL("./run-web-authenticated-browser-e2e.ps1", import.meta.url), "utf8");
const bridge = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebAuthE2eBridge.kt", import.meta.url), "utf8");
const main = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url), "utf8");
const workflow = await readFile(new URL("../.github/workflows/web-android-pr.yml", import.meta.url), "utf8");

test("hermetic Auth gate uses a real browser and the product repository/coordinator", () => {
  assert.match(runner, /chromium\.launch\(/);
  assert.match(runner, /__quataAuthE2eProduct\.login/);
  assert.match(runner, /__quataAuthE2eProduct\.restore/);
  assert.match(runner, /__quataAuthE2eProduct\.logout/);
  assert.match(main, /authRepository\.login\(countryCode, phone, password\)/);
  assert.match(main, /preferences\.putString\(WebSessionReadyKey, "true"\)/);
  assert.match(main, /authRepository\.restoreLocalSession\(\)/);
  assert.match(main, /sessionCoordinator\.logoutCurrentSession\(\)/);
  assert.doesNotMatch(bridge, /innerHTML|createElement\(['"]input|addEventListener\(['"]click/);
});

test("fixture fails closed on external network and verifies the complete journey", () => {
  assert.match(runner, /context\.route\("\*\*\/\*"/);
  assert.match(runner, /proxy-server=http:\/\/127\.0\.0\.1:9/);
  assert.match(runner, /unexpected_external_network/);
  assert.match(runner, /fixtureState\.login !== 1/);
  assert.match(runner, /fixtureState\.webLogout !== 1/);
  assert.match(runner, /fixtureState\.globalLogout !== 1/);
});

test("real mode is double opt-in, never provisions an account, and verifies revocation", () => {
  assert.match(wrapper, /\[switch\]\$AllowExistingTestUser/);
  assert.match(wrapper, /\[switch\]\$AcceptSessionRevocation/);
  assert.match(runner, /QUATA_AUTH_E2E_REAL_OPT_IN/);
  assert.match(runner, /I_ACCEPT_SESSION_REVOCATION/);
  assert.match(runner, /route\.fetch\(\)/);
  assert.match(runner, /cleanupSession = captured/);
  assert.match(runner, /grant_type=refresh_token/);
  assert.match(runner, /global_session_revocation_unverified/);
  assert.doesNotMatch(runner, /quata-register|admin\/users|account-lifecycle|createUser|deleteUser/);
});

test("the product bridge is restricted to localhost and an explicit query opt-in", () => {
  assert.match(bridge, /hostname === '127\.0\.0\.1'/);
  assert.match(bridge, /hostname === 'localhost'/);
  assert.match(bridge, /get\('quata-auth-e2e'\) === '1'/);
  assert.match(bridge, /Object\.freeze/);
});

test("PR CI requires both the contract and the hermetic browser journey", () => {
  assert.match(workflow, /npm run test:web-auth-browser-contract/);
  assert.match(workflow, /node scripts\/web-authenticated-browser-e2e\.mjs/);
  assert.match(workflow, /authenticated-browser-e2e\.json/);
  assert.match(workflow, /build\/reports\/web-ci\//);
});
