import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  hostTest,
  viewModelTest,
  androidFault,
  androidRepository,
  androidUi,
  androidRunner,
  iosRepository,
  iosUi,
  iosRunner,
  iosWrapper,
  webRepository,
  webRunner,
  actorBoundary,
  actorBoundaryRollback,
  androidHttpClient,
  selectiveReleaseExecutor,
  packageJson,
] = await Promise.all([
  source("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreenHostTest.kt"),
  source("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt"),
  source("app/src/main/java/com/quata/feature/neighborhoods/data/CommunityChatEvidenceFaults.kt"),
  source("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
  source("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("supabase/migrations/20260927094500_chat_actor_auth_boundary.sql"),
  source("supabase/rollbacks/20260927094500_chat_actor_auth_boundary.rollback.sql"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt"),
  source("scripts/selective-db-release-executor.mjs"),
  source("package.json"),
]);

test("the common host fails closed without identity or a cached thread/active wall", () => {
  assert.match(hostTest, /directory remains public while private community actions need an identity/);
  assert.match(hostTest, /assertFalse\(canPerformNeighborhoodPrivateAction\(null\)\)/);
  assert.match(hostTest, /community chat opens only when cached conversation or wall exists/);
  assert.match(hostTest, /assertFalse\(canOpenCommunityChat\(community\("Bata", conversationId = null, wallId = null\)\)\)/);
});

test("the shared model exposes a scoped error and retries the exact community once", () => {
  assert.match(viewModelTest, /community chat failure retries the same community and navigates once/);
  assert.match(viewModelTest, /assertEquals\(listOf\("Bata", "Bata"\), repository\.communityChatCalls\)/);
  assert.match(viewModelTest, /assertEquals\(listOf\("sb:community-1"\), opened\)/);
});

test("each platform has an opt-in one-shot pre-RPC fault and a same-anchor recovery gate", () => {
  assert.match(androidFault, /AtomicBoolean/);
  assert.match(androidRepository, /BuildConfig\.DEBUG && CommunityChatEvidenceFaults\.consumeFailure\(\)/);
  assert.match(androidUi, /community-chat-negative/);
  assert.match(androidUi, /community chat recoverable error/);
  assert.match(androidRunner, /--community-chat-negative-only/);

  assert.match(iosRepository, /QUATA_IOS_COMMUNITY_CHAT_FORCE_FAILURE/);
  assert.match(iosUi, /testCommunityChatFailureRetriesSameCommunityAnchor/);
  assert.match(iosUi, /recoverable community chat error/);
  assert.match(iosRunner, /--community-chat-negative-only/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_COMMUNITY_CHAT_NEGATIVE_UI_E2E/);

  assert.match(webRepository, /\['localhost', '127\.0\.0\.1'\][\s\S]*__QUATA_COMMUNITY_CHAT_FORCE_FAILURE__/);
  assert.match(webRunner, /--community-chat-negative-only/);
  assert.match(webRunner, /community_chat_forced_failure_visible_without_navigation_web/);
  assert.match(webRunner, /community_chat_retry_same_anchor_opened_real_chat_web/);
});

test("the focal backend proof rejects anonymous, actor spoof and missing-wall opens without residue", () => {
  assert.match(webRunner, /verifyCommunityChatBackendNegatives/);
  assert.match(webRunner, /const anonymousStatus = await attempt\(null,[\s\S]*"anonymous"\)/);
  assert.match(webRunner, /const actorSpoofStatus = await attempt\(actor\.accessToken,[\s\S]*"actor_spoof"\)/);
  assert.match(webRunner, /const missingWallStatus = await attempt\(actor\.accessToken,[\s\S]*"missing_wall"\)/);
  assert.match(webRunner, /missingWallResidueCount: residue/);
});

test("anonymous Chat remains available only to the exact published Android v32 signature", () => {
  assert.match(actorBoundary, /quata_legacy_android_v32_compatibility/);
  assert.match(actorBoundary, /context\.method = 'POST'/);
  assert.match(actorBoundary, /context\.path in \([\s\S]*'\/rpc\/quata_chat_open_community_thread'/);
  assert.match(actorBoundary, /user-agent[\s\S]*okhttp\/4\.12\.0/);
  assert.match(actorBoundary, /x-quata-client-generation'[\s\S]*is null/);
  assert.match(actorBoundary, /origin'[\s\S]*is null/);
  assert.match(actorBoundary, /referer'[\s\S]*is null/);
  assert.match(actorBoundary, /authenticated chat actor is required/);
  assert.match(androidHttpClient, /x-quata-client-generation", "android-auth-boundary-v1"/);
  assert.match(actorBoundaryRollback, /drop function if exists public\.quata_legacy_android_v32_chat_request_allowed/);
});

test("the selective release pins the exact actor boundary and verifies both client generations", () => {
  const boundarySha256 = createHash("sha256").update(actorBoundary).digest("hex");
  assert.match(selectiveReleaseExecutor, new RegExp(`20260927094500[^\\n]+${boundarySha256}`));
  assert.match(selectiveReleaseExecutor, /selective_release_chat_actor_boundary_legacy_v32_failed/);
  assert.match(selectiveReleaseExecutor, /selective_release_chat_actor_boundary_modern_anonymous_not_rejected/);
  assert.match(selectiveReleaseExecutor, /selective_release_chat_actor_boundary_acl_failed/);
  assert.match(selectiveReleaseExecutor, /x-quata-client-generation/);
});

test("the focused contract runs in both fast contract suites", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/community-chat-negative-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/community-chat-negative-contract\.test\.mjs/);
});
