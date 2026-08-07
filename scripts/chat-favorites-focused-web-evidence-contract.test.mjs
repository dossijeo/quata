import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

const [runner, packageJson] = await Promise.all([
  source("scripts/chat-favorites-focused-web-evidence.mjs"),
  source("package.json"),
]);
const [androidRunner, androidTest, appBuild] = await Promise.all([
  source("scripts/chat-favorites-focused-android-evidence.mjs"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatFavoritesFocusedDeepLinkInstrumentedTest.kt"),
  source("app/build.gradle.kts"),
]);
const [iosRunner, iosTest] = await Promise.all([
  source("scripts/run-ios-chat-favorites-focused-ui-test.sh"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatFavoritesFocusedUITests.swift"),
]);

test("CHAT-FAVORITES-FOCUSED-WEB-001 is included in fast and Wave2 contracts", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(
    scripts["test:ci-fast-contracts"],
    /scripts\/chat-favorites-focused-web-evidence-contract\.test\.mjs/,
  );
  assert.match(
    scripts["test:web-wave2-contracts"],
    /scripts\/chat-favorites-focused-web-evidence-contract\.test\.mjs/,
  );
});

test("runner never hardcodes authorized test credentials", () => {
  assert.doesNotMatch(runner, /68024260[78]/);
  assert.doesNotMatch(runner, /21085800/);
  assert.doesNotMatch(androidRunner, /68024260[78]/);
  assert.doesNotMatch(androidRunner, /21085800/);
  assert.doesNotMatch(androidTest, /68024260[78]/);
  assert.doesNotMatch(androidTest, /21085800/);
  assert.doesNotMatch(iosRunner, /68024260[78]/);
  assert.doesNotMatch(iosRunner, /21085800/);
  assert.doesNotMatch(iosTest, /68024260[78]/);
  assert.doesNotMatch(iosTest, /21085800/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_A_PHONE/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_A_PASSWORD/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_B_PHONE/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_B_PASSWORD/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_A_PHONE/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_A_PASSWORD/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_B_PHONE/);
  assert.match(androidRunner, /QUATA_CHAT_EVIDENCE_B_PASSWORD/);
});

test("runner validates the full UUID profile shape returned by web_login", () => {
  assert.match(
    runner,
    /const uuid = \/\^\[0-9a-f\]\{8\}-\[0-9a-f\]\{4\}-\[1-8\]\[0-9a-f\]\{3\}-\[89ab\]\[0-9a-f\]\{3\}-\[0-9a-f\]\{12\}\$\/i;/,
  );
});

test("runner uses the common favorite conversation route and focused deep link", () => {
  assert.match(runner, /const favoriteConversationId = "__favorite_messages__"/);
  assert.match(runner, /chatFragment\(favoriteConversationId\)/);
  assert.match(runner, /chatFragment\(`sb:\$\{state\.thread\}`, String\(state\.message\)\)/);
  assert.match(runner, /data-quata-shell-route/);
  assert.match(runner, /focusedPage = await openAuthenticatedChatPage\([\s\S]*?faults,\s*0,\s*\)/);
});

test("runner validates backend mutation, navigation evidence and reversible cleanup", () => {
  assert.doesNotMatch(runner, /quata_chat_get_or_create_private_thread/);
  assert.match(runner, /quata_chat_start_thread/);
  assert.match(runner, /p_unique_key/);
  assert.match(runner, /qadata-chat-fav-focus/);
  assert.match(runner, /quata_chat_send_message/);
  assert.match(runner, /quata_chat_set_favorite[\s\S]*p_favorite: true/);
  assert.match(runner, /quata_chat_get_favorites/);
  assert.match(runner, /web-favorites-list/);
  assert.match(runner, /web-favorites-open-source/);
  assert.match(runner, /web-focused-message/);
  assert.match(runner, /quata_chat_set_favorite[\s\S]*p_favorite: false/);
  assert.match(runner, /quata_chat_delete_messages/);
  assert.match(runner, /quata_chat_delete_thread/);
  assert.match(runner, /quata_chat_get_thread/);
  assert.match(runner, /quata_chat_get_inbox/);
  assert.match(runner, /cleanup_verified_favorite_absent/);
  assert.match(runner, /cleanup_verified_message_absent_for_a/);
  assert.match(runner, /cleanup_verified_message_absent_for_b/);
  assert.match(runner, /cleanup_verified_thread_absent_for_a/);
  assert.match(runner, /cleanup_verified_thread_absent_for_b/);
  assert.match(runner, /hardDeleteTemporaryThread/);
  assert.match(runner, /QUATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP_AUTHORIZATION/);
  assert.match(runner, /MANAGER_APPROVED_QADATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP/);
  assert.match(runner, /missing_hard_cleanup_authorization/);
  assert.match(runner, /uniqueKey\.startsWith\("qadata-chat-fav-focus-"\)/);
  assert.match(runner, /parsedConnection\.searchParams\.delete\("sslmode"\)/);
  assert.match(runner, /rejectUnauthorized: true/);
  assert.match(runner, /delete from public\.chat_threads where id = \$1 and unique_key = \$2 returning id/);
  assert.match(runner, /cleanup_verified_physical_residue_absent/);
  assert.match(runner, /cleanup\.actions\.push\(\.\.\.await logicalCleanup\(config, state\)\)/);
  assert.match(runner, /state\.hardCleanup = await hardDeleteTemporaryThread\(state\.thread, state\.uniqueKey\)/);
});

test("runner records the exact git candidate identity in the evidence report", () => {
  assert.match(runner, /import \{ spawn \} from "node:child_process"/);
  assert.match(runner, /async function gitMetadata\(\)/);
  assert.match(runner, /"rev-parse", "HEAD"/);
  assert.match(runner, /"status", "--porcelain"/);
  assert.match(runner, /git: await gitMetadata\(\)/);
  assert.match(androidRunner, /git: await gitMetadata\(\)/);
});

test("runner supports authorized adjacent-profile evidence without exposing credentials in commands", () => {
  assert.match(runner, /QUATA_CHAT_FAVORITES_FOCUSED_USE_ADJACENT_AUTHORIZED_PROFILE/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_SSH_HOST/);
  assert.match(runner, /QUATA_CHAT_EVIDENCE_SSH_CREDENTIALS_FILE/);
  assert.match(runner, /runSilent\("ssh", \[host, `cat \$\{file\}`\]\)/);
  assert.match(runner, /resolveAdjacentRecipientProfile/);
  assert.match(runner, /where phone_key = any\(\$1::text\[\]\)/);
  assert.match(runner, /verifyRecipientParticipant/);
  assert.match(runner, /where thread_id = \$1 and profile_id = \$2/);
  assert.match(runner, /adjacent_recipient_participant_verified/);
  assert.match(runner, /state\.b\?\.accessToken/);
  assert.match(runner, /hardDeleteTemporaryThread/);
});

test("runner redacts sensitive runtime state from report", () => {
  assert.match(runner, /markerSha256: sha256\(marker\)/);
  assert.match(runner, /uniqueKeySha256: sha256\(uniqueKey\)/);
  assert.doesNotMatch(runner, /report\.[a-zA-Z0-9_.]*\s*=\s*.*connectionString/);
  assert.doesNotMatch(runner, /report\.[a-zA-Z0-9_.]*\s*=\s*.*accessToken/);
  assert.doesNotMatch(runner, /report\.[a-zA-Z0-9_.]*\s*=\s*.*refreshToken/);
  assert.doesNotMatch(runner, /report\.[a-zA-Z0-9_.]*\s*=\s*.*password/);
  assert.match(runner, /mode: 0o600/);
});

test("Android evidence runner uses real deep links, temp credentials and reversible cleanup", () => {
  assert.match(appBuild, /androidTestImplementation\("androidx\.test\.uiautomator:uiautomator:2\.3\.0"\)/);
  assert.match(androidTest, /ChatFavoritesFocusedDeepLinkInstrumentedTest/);
  assert.match(androidTest, /quataChatEvidenceCredentialsFile/);
  assert.match(androidTest, /targetContext\.filesDir/);
  assert.match(androidTest, /app-internal:/);
  assert.match(androidTest, /ActivityScenario\.launch<MainActivity>\(chatIntent\(safeFavoritesUrl\)\)/);
  assert.match(androidTest, /ActivityScenario\.launch<MainActivity>\(chatIntent\(safeFocusedUrl\)\)/);
  assert.match(androidTest, /device\.findObject\(By\.textContains\(safeMarkerProbe\)\)\?\.click\(\)/);
  assert.match(androidTest, /android-focused-message/);
  assert.match(androidRunner, /qadata-chat-fav-focus-android-/);
  assert.match(androidRunner, /quata_chat_start_thread/);
  assert.match(androidRunner, /quata_chat_send_message/);
  assert.match(androidRunner, /quata_chat_set_favorite/);
  assert.match(androidRunner, /deviceCredentialsPath/);
  assert.match(androidRunner, /app-internal:chat-favorites-focused-credentials\.json/);
  assert.match(androidRunner, /files\/chat-favorites-focused-evidence/);
  assert.match(androidRunner, /run-as", "com\.quata"/);
  assert.match(androidRunner, /adbRunAsCat/);
  assert.match(androidRunner, /android-favorites-list\.png/);
  assert.match(androidRunner, /android-favorites-open-source\.png/);
  assert.match(androidRunner, /android-focused-message\.png/);
  assert.match(androidRunner, /:app:assembleDebug/);
  assert.match(androidRunner, /:app:assembleDebugAndroidTest/);
  assert.match(androidRunner, /"adb", \["install", "-r", "app\/build\/outputs\/apk\/debug\/app-debug\.apk"\]/);
  assert.match(androidRunner, /"am", "instrument"/);
  assert.match(androidRunner, /android_instrumentation_not_ok/);
  assert.match(androidRunner, /android_instrumentation_semantic_failure/);
  assert.match(androidRunner, /quataChatEvidenceCredentialsFile/);
  assert.match(androidRunner, /MANAGER_APPROVED_QADATA_CHAT_FAVORITES_FOCUSED_HARD_CLEANUP/);
  assert.match(androidRunner, /delete from public\.chat_threads where id = \$1 and unique_key = \$2 returning id/);
  assert.match(androidRunner, /cleanup_verified_physical_residue_absent/);
});

test("iOS evidence runner uses real custom-scheme deep links and the shared seeded-session lane", () => {
  assert.match(iosTest, /QUATA_IOS_CHAT_FAVORITES_FOCUSED_UI_E2E/);
  assert.match(iosTest, /quata:\/\/egquata\.com\/#chat-__favorite_messages__/);
  assert.match(iosTest, /encodedFragment\(conversationId\)/);
  assert.match(iosTest, /encodedQuery\(messageId\)/);
  assert.match(iosTest, /matching\(identifier: "chat\.message\./);
  assert.match(iosTest, /ios-favorites-list/);
  assert.match(iosTest, /ios-favorites-open-source/);
  assert.match(iosTest, /ios-focused-message/);

  assert.match(iosRunner, /QUATA_IOS_AUTH_E2E_FILE/);
  assert.match(iosRunner, /QuataIosAuthenticatedSessionSeederTests\/testSeedAuthenticatedSessionForVisualGates/);
  assert.match(iosRunner, /QuataIosAuthenticatedChatFavoritesFocusedUITests\/testFavoriteRouteOpensSourceAndFocusedDeepLinkHighlightsMessage/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_E2E_CONVERSATION_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_E2E_MESSAGE_ID/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_E2E_MARKER_PROBE/);
  assert.match(iosRunner, /CHAT_FAVORITES_FOCUSED_IOS_UI_GATE_PASSED/);
});
