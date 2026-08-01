import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const [runner, authRunner, fixture, host, nativeControls, packageJson, workflow] = await Promise.all([
  readFile(new URL("./web-chat-a11y-browser-e2e.mjs", import.meta.url), "utf8"),
  readFile(new URL("./web-authenticated-browser-e2e.mjs", import.meta.url), "utf8"),
  readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebChatE2eFixture.kt", import.meta.url), "utf8"),
  readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt", import.meta.url), "utf8"),
  readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebNativeAccessibleControls.kt", import.meta.url), "utf8"),
  readFile(new URL("../package.json", import.meta.url), "utf8"),
  readFile(new URL("../.github/workflows/web-android-pr.yml", import.meta.url), "utf8"),
]);

test("WebNativeButton preserves native disabled, label, and selected-state semantics", () => {
  assert.match(nativeControls, /button\.setAttribute\("aria-label", label\)/);
  assert.match(nativeControls, /if \(selected\) button\.setAttribute\("aria-current", "page"\) else button\.removeAttribute\("aria-current"\)/);
  assert.match(nativeControls, /button\.disabled = !enabled/);
  assert.match(nativeControls, /button\.setAttribute\("aria-disabled", \(!enabled\)\.toString\(\)\)/);
  assert.match(nativeControls, /style\.setProperty\("pointer-events", "auto"\)/);
});

test("WEB-CHAT-A11Y-E2E-001 remains inside the mandatory Wave2 contract gate", () => {
  assert.match(JSON.parse(packageJson).scripts["test:web-wave2-contracts"], /scripts\/web-chat-a11y-e2e-contract\.test\.mjs/);
});

test("WEB-CHAT-A11Y-E2E-001 drives the real Compose chat host through exact native selectors", () => {
  assert.match(runner, /quata-chat-e2e=1/);
  assert.match(runner, /localStorage\.getItem\("web\.navigation\.route"\)\?\.startsWith\("chat\/"\)/);
  assert.match(runner, /input\[aria-label="Mensaje"\]/);
  assert.match(runner, /button\[aria-label="Enviar"\]/);
  assert.match(runner, /assertUniqueNativeAx\(page, \{ role: "textbox", name: "Mensaje"/);
  assert.match(runner, /assertUniqueNativeAx\(page, \{ role: "button", name: "Enviar"/);
  assert.match(runner, /ariaDisabled: button\.getAttribute\("aria-disabled"\)/);
  assert.match(runner, /native_chat_send_disabled_a11y_or_focus_invalid/);
  assert.match(runner, /native_chat_send_disabled_callback_fired/);
  assert.match(runner, /await send\.focus\(\)/);
  assert.match(runner, /page\.keyboard\.press\("Enter"\)/);
  assert.match(runner, /await page\.waitForFunction\(marker => \{/);
  assert.match(runner, /value\?\.version === 1 && value\.sends === 1 && value\.text === marker/);
  assert.match(runner, /await page\.waitForTimeout\(250\)/);
  assert.match(runner, /chatFixture\?\.sends !== 1 \|\| chatFixture\.text !== chatMarker/);
  assert.match(workflow, /node scripts\/web-chat-a11y-browser-e2e\.mjs/);
  assert.doesNotMatch(authRunner, /quata-chat-e2e|__quataChatE2eProduct|native_chat_controls/);
});

test("WEB-CHAT-A11Y-E2E-001 accepts only the opt-in product bridge for a stable canvas Auth shell and records a sanitized failure stage", () => {
  assert.match(runner, /resolveAuthSurface\(page\)/);
  assert.match(runner, /authSurface === "native_controls"/);
  assert.match(runner, /loginWithNativeControls\(page\)/);
  assert.match(runner, /loginWithComposeAuthBridge\(page\)/);
  assert.match(runner, /compose_auth_bridge_missing/);
  assert.match(runner, /compose_auth_shell_missing/);
  assert.match(runner, /compose_auth_canvas_missing/);
  assert.match(runner, /bridge\.login\(countryCode, phone, password\)/);
  assert.match(runner, /report\.failureStage = stage/);
  assert.match(runner, /shadowCanvasCount/);
  assert.match(runner, /browserDiagnostics\.slice\(-20\)/);
  assert.match(runner, /stage = "authenticated_chat_route"/);
});

test("WEB-CHAT-A11Y-E2E-001 fixture permits only the exact badge inbox read and continues to block all other REST POSTs", () => {
  assert.match(runner, /url\.pathname === "\/rest\/v1\/rpc\/quata_chat_get_inbox"/);
  assert.match(runner, /request\.method !== "POST"/);
  assert.match(runner, /request\.headers\.authorization !== `Bearer \$\{FIXTURE\.accessToken\}`/);
  assert.match(runner, /body\.p_actor_profile_id !== FIXTURE\.profileId/);
  assert.match(runner, /body\.p_limit !== 100/);
  assert.match(runner, /\{ threads: \[\], messages: \[\], profiles: \[\] \}/);
  assert.match(runner, /if \(request\.method !== "GET"\) return json\(response, 405, \{ error: "fixture_product_mutation_forbidden" \}\)/);
});

test("WEB-CHAT-A11Y-E2E-001 fixture is localhost/query gated, has no network code and publishes only the send evidence", () => {
  assert.match(fixture, /hostname === '127\.0\.0\.1' \|\| location\?\.hostname === 'localhost'/);
  assert.match(fixture, /get\('quata-chat-e2e'\) === '1'/);
  assert.match(fixture, /override suspend fun sendMessage/);
  assert.match(fixture, /const val SyntheticMessage = "mensaje AX local"/);
  assert.match(fixture, /if \(text != SyntheticMessage\) return Result\.failure/);
  assert.match(fixture, /publishChatE2eSend\(sends\)/);
  assert.match(fixture, /text: 'mensaje AX local'/);
  assert.doesNotMatch(fixture, /fetch\(|WebChatRepository|http:\/\//i);
  assert.doesNotMatch(fixture, /accessToken|refreshToken|password|credential/i);
  assert.match(host, /repository: ChatRepository/);
  assert.match(host, /WebNativeInput\(value, onChange, "Mensaje"/);
  assert.match(host, /WebNativeButton\("Enviar", enabled, onClick, modifier\.width\(96\.dp\)\.height\(48\.dp\)\)/);
});
