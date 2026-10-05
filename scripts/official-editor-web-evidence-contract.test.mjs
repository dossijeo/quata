import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  isExpectedFixtureRealtimeConsoleError,
  officialFeedPageFixtureResponse,
} from "./official-editor-web-evidence-policy.mjs";

const runner = await readFile(new URL("./official-editor-web-evidence.mjs", import.meta.url), "utf8");
const browserFeedMedia = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/BrowserFeedMediaContent.kt", import.meta.url), "utf8");
const mediaRecovery = await readFile(new URL("../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataMediaPlaybackRecoveryContent.kt", import.meta.url), "utf8");
const officialMediaFrame = await readFile(new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostMediaFrameContent.kt", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

test("Official editor Web evidence keeps the permission fixture hermetic and mutation-free", () => {
  assert.match(runner, /OFFICIAL-EDITOR-WEB-CTA-001/);
  assert.match(runner, /assertDistributionRevision/);
  assert.match(runner, /gitMetadata/);
  assert.match(runner, /--require-pr-identity/);
  assert.match(runner, /assertPullRequestIdentity/);
  assert.match(runner, /pr_identity_checkout_not_merge/);
  assert.match(runner, /quata_web_access_token/);
  assert.match(runner, /localStorage\.setItem\("quata_web_is_official", "true"\)/);
  assert.match(runner, /currentUgcTermsVersion\(\)/);
  assert.match(runner, /CurrentUgcTermsVersion/);
  assert.match(runner, /localStorage\.setItem\(`ugc_terms:accepted:\$\{profileId\}:\$\{ugcTermsVersion\}`, "true"\)/);
  assert.match(runner, /community_profiles/);
  assert.match(
    runner,
    /request\.method === "POST" && table === "rpc\/quata_chat_get_inbox_page"\)[\s\S]*?if \(!authenticated\) return observedJson\(response, observed, 401,[\s\S]*?threads: \[\],[\s\S]*?messages: \[\],[\s\S]*?profiles: \[\],[\s\S]*?has_more: false,[\s\S]*?next_cursor: null/,
  );
  assert.match(runner, /url\.searchParams\.get\("id"\) !== `in\.\(\$\{PROFILE_ID\}\)`/);
  assert.match(runner, /request\.headers\.authorization === `Bearer \$\{ACCESS_TOKEN\}`/);
  assert.match(
    runner,
    /table === "rpc\/quata_official_feed_page"[\s\S]*?officialFeedPageFixtureResponse[\s\S]*?authorization: request\.headers\.authorization[\s\S]*?fixture\.status, fixture\.body/,
  );
  assert.match(
    runner,
    /entry\.table === "rpc\/quata_official_feed_page"[\s\S]*?entry\.method === "GET"[\s\S]*?entry\.authenticated === false[\s\S]*?entry\.authorizationPresent === false[\s\S]*?entry\.query\?\.p_limit === "50"[\s\S]*?entry\.statusCode === 200/,
  );
  assert.match(runner, /official_feed_public_rpc_fixture_not_exercised/);
  assert.match(runner, /is_official: "true"/);
  assert.match(runner, /quata-auth-e2e=1&quata-official-editor-e2e=1#official/);
  assert.match(runner, /__quataOfficialFeedE2eProduct\.create\(\)/);
  assert.match(runner, /officialEditorSemanticClick\(page, "official-editor-publish"\)/);
  assert.doesNotMatch(runner, /__quataOfficialEditorE2eProduct\.setBodyHtml/);
  assert.match(runner, /fillRichTextBodyThroughProductUi\(page, "Official editor reversible evidence"\)/);
  assert.match(runner, /__quataOfficialRichTextEditorE2eProduct/);
  assert.match(runner, /page\.locator\("#quata-portable-rich-text-field"\)/);
  assert.match(runner, /const box = await waitForVisibleBoundingBox\(field\)/);
  assert.match(runner, /async function waitForVisibleBoundingBox\(locator, timeoutMs = 15_000\)/);
  assert.match(runner, /box && box\.width > 0 && box\.height > 0/);
  assert.doesNotMatch(runner, /const box = await field\.boundingBox\(\)/);
  assert.match(runner, /waitForRichTextFieldText\(page, value\)/);
  assert.match(runner, /official_editor_body_input_not_committed/);
  assert.match(runner, /official_editor_body_field_text_timeout/);
  assert.match(runner, /page\.keyboard\.insertText\(value\)/);
  assert.match(runner, /__quataOfficialEditorE2eProduct\.skipTranslation\(\)/);
  assert.match(runner, /waitForOfficialEditorState/);
  assert.match(runner, /data-quata-official-editor-e2e/);
  assert.doesNotMatch(runner, /getByLabel\(\/Crear comunicado\|Create notice\|Créer un communiqué/);
  assert.match(runner, /empty_publish_shows_shared_validation_feedback_without_mutation/);
  assert.match(runner, /official_editor_invalid_draft_mutated/);
  assert.doesNotMatch(runner, /getByRole\("textbox"\)/);
  assert.match(runner, /keyboard\.insertText/);
  assert.doesNotMatch(runner, /globalThis\.prompt|window\.prompt|page\.once\("dialog"|dialog\.accept/);
  assert.match(runner, /valid_publish_opens_shared_translation_prompt/);
  assert.match(runner, /web-official-editor-translation-prompt/);
  assert.match(runner, /valid_publish_attempt_uses_shared_postgrest_plan_and_fails_closed/);
  assert.match(runner, /fixture_publish_forbidden/);
  assert.match(runner, /statusCode === 403/);
  assert.match(runner, /official_editor_publish_fixture_not_denied/);
  assert.match(runner, /fixture_mutation_forbidden/);
  assert.doesNotMatch(runner, /SUPABASE_DB_URL|SERVICE_ROLE|21085800|\+240|68024260/);
});

test("Official Web media evidence proves real decoder failure and same-source recovery", () => {
  assert.match(officialMediaFrame, /OfficialPostMediaOpenTestTag = "official\.media\.open"/);
  assert.match(officialMediaFrame, /\.testTag\(OfficialPostMediaOpenTestTag\)/);
  assert.match(mediaRecovery, /QuataMediaPlaybackFailureTestTag = "media-playback\.failure"/);
  assert.match(mediaRecovery, /QuataMediaPlaybackRetryTestTag = "media-playback\.retry"/);
  assert.match(browserFeedMedia, /configureBrowserFeedVideoCrossOrigin\(this, videoUrl\)/);
  assert.match(browserFeedMedia, /globalThis\.crossOriginIsolated && source\.origin !== globalThis\.location\.origin/);
  assert.match(browserFeedMedia, /video\.crossOrigin = 'anonymous'/);
  assert.match(browserFeedMedia, /candidate\.hostname === '127\.0\.0\.1'/);
  assert.match(browserFeedMedia, /candidate\.protocol === 'https:'[\s\S]*?secureRemote \|\| localDevelopment/);
  assert.doesNotMatch(browserFeedMedia, /decoderAllowed/);
  assert.doesNotMatch(browserFeedMedia, /if \(!decoderAllowed\)[\s\S]*?isPlaying = true/);
  assert.match(runner, /clickProductTag\(page, "official\.media\.open"\)/);
  assert.match(runner, /waitForProductTag\(page, "media-playback\.failure"\)/);
  assert.match(runner, /server\.enableMedia\(\)/);
  assert.match(runner, /clickProductTag\(page, "media-playback\.retry"\)/);
  assert.match(runner, /video\.readyState >= 2 && !video\.paused && video\.currentTime > 0\.15/);
  assert.match(runner, /server\.mediaResponses\[0\] !== "invalid"/);
  assert.match(runner, /server\.mediaResponses\.slice\(1\)\.includes\("valid"\)/);
  assert.match(runner, /Buffer\.from\("not-a-valid-mp4", "utf8"\)/);
  assert.match(runner, /media_url: `\$\{origin\}\/storage\/v1\/object\/public\/official-media\/fixture-media\.mp4`/);
  assert.match(runner, /official_video_same_source_retry_reached_real_playback/);
});

test("Official editor Web evidence rejects every bearer on the public feed fixture", () => {
  for (const authorization of [
    "Bearer fixture.official.access.token",
    "Bearer wrong-token",
    "Bearer expired-token",
  ]) {
    assert.deepEqual(
      officialFeedPageFixtureResponse({ method: "GET", authorization, query: { p_limit: "50" } }),
      { status: 400, body: { error: "fixture_public_feed_bearer_forbidden" } },
    );
  }
  assert.deepEqual(
    officialFeedPageFixtureResponse({ method: "GET", query: { p_limit: "50" } }),
    { status: 200, body: [] },
  );
  const rows = [{ id: "fixture-video" }];
  assert.deepEqual(
    officialFeedPageFixtureResponse({ method: "GET", query: { p_limit: "50" }, rows }),
    { status: 200, body: rows },
  );
  assert.deepEqual(
    officialFeedPageFixtureResponse({ method: "GET", query: { p_limit: "50", p_before_id: "unexpected" } }),
    { status: 400, body: { error: "fixture_public_feed_initial_page_required" } },
  );
});

test("Official editor Web evidence ignores only its unavailable local Realtime fixture", () => {
  const origin = "http://127.0.0.1:45385";
  assert.equal(
    isExpectedFixtureRealtimeConsoleError(
      "WebSocket connection to 'ws://127.0.0.1:45385/realtime/v1/websocket?apikey=fixture-public-anon-key&vsn=2.0.0' failed: Error in connection establishment",
      origin,
    ),
    true,
  );
  for (const message of [
    "WebSocket connection to 'wss://production.invalid/realtime/v1/websocket?apikey=fixture-public-anon-key&vsn=2.0.0' failed: network",
    "WebSocket connection to 'ws://127.0.0.1:45385/realtime/v1/websocket?apikey=other&vsn=2.0.0' failed: network",
    "Uncaught TypeError: product fault",
  ]) {
    assert.equal(isExpectedFixtureRealtimeConsoleError(message, origin), false);
  }
  assert.match(runner, /isExpectedFixtureRealtimeConsoleError\(text, server\.origin\)/);
});

test("Official editor Web evidence runner and contract are callable from package scripts", () => {
  const fast = packageJson.scripts["test:ci-fast-contracts"];
  const wave2 = packageJson.scripts["test:web-wave2-contracts"];
  const evidence = packageJson.scripts["evidence:web-official-editor"];
  assert.match(fast, /scripts\/official-editor-web-evidence-contract\.test\.mjs/);
  assert.match(wave2, /scripts\/official-editor-web-evidence-contract\.test\.mjs/);
  assert.match(evidence, /scripts\/official-editor-web-evidence\.mjs/);
});
