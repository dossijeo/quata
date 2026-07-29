import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const [runner, fixture, host, packageJson] = await Promise.all([
  readFile(new URL("./web-authenticated-browser-e2e.mjs", import.meta.url), "utf8"),
  readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebChatE2eFixture.kt", import.meta.url), "utf8"),
  readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt", import.meta.url), "utf8"),
  readFile(new URL("../package.json", import.meta.url), "utf8"),
]);

test("WEB-CHAT-A11Y-E2E-001 remains inside the mandatory Wave2 contract gate", () => {
  assert.match(JSON.parse(packageJson).scripts["test:web-wave2-contracts"], /scripts\/web-chat-a11y-e2e-contract\.test\.mjs/);
});

test("WEB-CHAT-A11Y-E2E-001 drives the real Compose chat host through exact native selectors", () => {
  assert.match(runner, /quata-chat-e2e=1/);
  assert.match(runner, /input\[aria-label="Mensaje"\]/);
  assert.match(runner, /button\[aria-label="Enviar"\]/);
  assert.match(runner, /assertUniqueNativeAx\(page, \{ role: "textbox", name: "Mensaje"/);
  assert.match(runner, /assertUniqueNativeAx\(page, \{ role: "button", name: "Enviar"/);
  assert.match(runner, /await send\.focus\(\)/);
  assert.match(runner, /page\.keyboard\.press\("Enter"\)/);
  assert.match(runner, /await page\.waitForFunction\(marker => \{/);
  assert.match(runner, /value\?\.version === 1 && value\.sends === 1 && value\.text === marker/);
  assert.match(runner, /await page\.waitForTimeout\(250\)/);
  assert.match(runner, /chatFixture\.sends !== 1 \|\| chatFixture\.text !== chatMarker/);
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
  assert.match(host, /WebNativeButton\("Enviar", enabled, onClick/);
});
