import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const worker = await source("../web/src/wasmJsMain/resources/quata-sw.js");
const shareStore = await source("../web/src/wasmJsMain/kotlin/com/quata/web/WebIncomingShareStore.kt");
const shareContract = await source("../web/src/commonMain/kotlin/com/quata/web/WebIncomingShareTargetContract.kt");
const auth = await source("../web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt");
const coordinator = await source("../web/src/wasmJsMain/kotlin/com/quata/web/WebPushSessionCoordinator.kt");
const subscription = await source("../web/src/wasmJsMain/kotlin/com/quata/web/BrowserWebPushSubscriptionService.kt");
const workflow = await source("../.github/workflows/web-android-pr.yml");

test("incoming PWA shares remain local, one-shot discardable payloads", () => {
  assert.match(worker, /request\.method === "POST"/);
  assert.match(worker, /requestUrl\.origin === self\.location\.origin/);
  assert.match(worker, /INCOMING_SHARE_STORE = "incoming-shares"/);
  assert.match(worker, /blob: file/);
  assert.match(worker, /objectStore\(INCOMING_SHARE_STORE\)\.put\(payload\)/);
  assert.doesNotMatch(worker, /access[_-]?token|refresh[_-]?token|web[_-]?session[_-]?token/i);

  assert.match(shareContract, /takeIf \{ it\.startsWith\("blob:"\) \}/);
  assert.ok(shareContract.includes('filter { it.startsWith("blob:") }.distinct()'));
  assert.match(shareStore, /URL\.revokeObjectURL\(reference\)/);
  assert.match(shareStore, /objectStore\('incoming-shares'\)\.delete\(payloadId\)/);
  assert.doesNotMatch(shareStore, /fetch\(|localStorage\.(?:setItem|getItem)/);
});

test("logout clears credentials after server revocation and browser unsubscribe even on a failure", () => {
  const lifecycle = auth.slice(auth.indexOf("suspend fun logoutWithBrowserUnsubscribe"));
  const server = lifecycle.indexOf("val serverFailure = runCatching { notifyServerLogout() }.exceptionOrNull()");
  const browserTimeout = lifecycle.indexOf("withTimeoutOrNull(WebBrowserUnsubscribeTimeoutMillis)");
  const cleared = lifecycle.indexOf("WebAuthStorage.clear(preferences)");
  assert.ok(server >= 0 && browserTimeout > server && cleared > browserTimeout, "logout_lifecycle_order_must_be_server_browser_clear");
  assert.match(lifecycle, /val browserResult = withTimeoutOrNull\(WebBrowserUnsubscribeTimeoutMillis\)[\s\S]*runCatching \{ browserUnsubscribe\(\)\.getOrThrow\(\) \}[\s\S]*Result\.failure\(IllegalStateException\("web_push_unsubscribe_timeout"\)\)/);
  assert.match(lifecycle, /val browserFailure = browserResult\.exceptionOrNull\(\)/);
  assert.match(lifecycle, /val failure = serverFailure \?: browserFailure/);
  assert.match(coordinator, /logoutWithBrowserUnsubscribe\(::unsubscribeBrowserPush\)/);
  assert.match(coordinator, /suspendCancellableCoroutine/);
  assert.match(coordinator, /if \(continuation\.isActive\) continuation\.resume\(Result\.success\(Unit\)\)/);
  assert.match(coordinator, /if \(continuation\.isActive\) continuation\.resume\(Result\.failure\(IllegalStateException\(reason\)\)\)/);
  assert.match(coordinator, /pushManager\?\.getSubscription\(\)/);
  assert.match(coordinator, /subscription \? subscription\.unsubscribe\(\) : true/);
});

test("subscription renewal remains session-bound and CI executes executable browser tests", async () => {
  assert.match(worker, /pushsubscriptionchange/);
  assert.match(worker, /quata:push-subscription-change/);
  assert.match(coordinator, /operations\.currentCredentials\(\)/);
  assert.match(coordinator, /operations\.subscribeServer/);
  assert.match(subscription, /PushSubscription\.toJSON\(\)/);
  assert.doesNotMatch(workflow, /\bpaths:/,
    "the PR workflow must not lose this contract behind a path filter");
  assert.match(workflow, /npm run test:web-wave2-contracts/);
  assert.match(workflow, /wasmJsBrowserTest/);
  const smoke = await source("../scripts/web-browser-smoke.mjs");
  assert.match(smoke, /assertPushConsentUsesTrustedSettingsClick/);
  assert.match(smoke, /navigator\?\.userActivation\?\.isActive === true/);
  assert.match(smoke, /Input\.dispatchMouseEvent/);
});

test("share-target smoke creates the IndexedDB store on clean browser profiles", async () => {
  const e2e = await source("../scripts/web-share-target-pwa-e2e.mjs");
  const boundary = e2e.slice(e2e.indexOf("async function addShareInspectionBoundary"));
  assert.match(boundary, /onupgradeneeded/);
  assert.match(boundary, /objectStoreNames\.contains\("incoming-shares"\)/);
  assert.match(boundary, /createObjectStore\("incoming-shares", \{ keyPath: "id" \}\)/);
  assert.match(boundary, /database\.transaction\("incoming-shares", "readonly"\)/);
  assert.match(boundary, /database\.transaction\("incoming-shares", "readwrite"\)/);
});

async function source(relativePath) {
  return readFile(new URL(relativePath, import.meta.url), "utf8");
}
