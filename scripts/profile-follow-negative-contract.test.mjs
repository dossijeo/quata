import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  viewModelTest,
  uiState,
  profileHost,
  profilePrimaryActions,
  androidRepository,
  androidFault,
  androidUi,
  androidRunner,
  iosRepository,
  iosUi,
  iosRunner,
  iosWrapper,
  webRepository,
  webHost,
  webRunner,
  localFacade,
  packageJson,
] = await Promise.all([
  read("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsUiState.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePrimaryActions.kt"),
  read("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt"),
  read("app/src/main/java/com/quata/feature/neighborhoods/data/ProfileFollowEvidenceFaults.kt"),
  read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  read("scripts/chat-actions-notifications-android-evidence.mjs"),
  read("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt"),
  read("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  read("scripts/chat-actions-notifications-ios-evidence.mjs"),
  read("scripts/run-ios-chat-actions-notifications-ui-test.sh"),
  read("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt"),
  read("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsHost.kt"),
  read("scripts/chat-actions-notifications-web-evidence.mjs"),
  read("scripts/profile-follow-local-evidence-facade.mjs"),
  read("package.json"),
]);

test("shared profile follow state rolls back and exposes a stable error anchor", () => {
  assert.match(viewModelTest, /follow is optimistic and rolls back on backend failure/);
  assert.match(viewModelTest, /assertFalse\(model\.uiState\.value\.selectedProfile\?\.user\?\.isFollowing == true\)/);
  assert.match(profileHost, /PublicProfileErrorTestTagPrefix = "public-profile\.error\."/);
});

test("shared retry preserves the failed follow intent and stays bound to its profile", () => {
  assert.match(uiState, /data class FailedProfileFollowAction/);
  assert.match(uiState, /val desiredState: Boolean/);
  assert.match(viewModelTest, /unfollow retry preserves the exact desired state/);
  assert.match(viewModelTest, /follow retry does not mutate a newer visible profile/);
  assert.match(viewModelTest, /follow retry reconciliation cannot continue after profile navigation/);
  assert.match(viewModelTest, /follow retry clears when backend already reached the desired state/);
  assert.match(profileHost, /PublicProfileFollowRetryTestTagPrefix = "public-profile\.follow\.retry\."/);
  assert.match(profileHost, /failedProfileFollowAction\?\.userId == profile\.user\.id/);
  assert.match(profileHost, /onRetryFollowUser\(profile\.user\.id\)/);
  assert.match(profilePrimaryActions, /contentDescription = PublicProfileFollowActionTestTagPrefix \+ userId/);
});

test("platform repositories force the same opt-in pre-mutation failure", () => {
  assert.match(androidFault, /AtomicBoolean/);
  assert.match(androidRepository, /BuildConfig\.DEBUG && ProfileFollowEvidenceFaults\.consumeFailure\(\)/);
  assert.match(iosRepository, /QUATA_IOS_PROFILE_FOLLOW_FORCE_FAILURE/);
  assert.match(webRepository, /__QUATA_PROFILE_FOLLOW_FORCE_FAILURE__/);
  for (const source of [androidRepository, iosRepository, webRepository]) {
    assert.match(source, /profile_follow_e2e_forced_failure/);
  }
});

test("Android, iOS and Web gates assert rollback then visible retry convergence", () => {
  assert.match(androidUi, /runProfileFollowNegativeStage/);
  assert.match(androidUi, /Failed follow must restore the original follower count/);
  assert.match(androidRunner, /--profile-follow-negative-only/);
  assert.match(androidUi, /public-profile\.follow\.retry\./);
  assert.match(androidUi, /android-chat-profile-follow-negative-retry-succeeded/);
  assert.match(androidRunner, /pollProfileFollowEdge\(state\.a\.profileId, state\.b\.profileId, true\)/);

  assert.match(iosUi, /followMode == "negative"/);
  assert.match(iosUi, /Failed follow must restore the original follower count/);
  assert.match(iosRunner, /--profile-follow-negative-only/);
  assert.match(iosRepository, /profileFollowEvidenceFailureConsumed/);
  assert.match(iosUi, /public-profile\.follow\.retry\./);
  assert.match(iosUi, /ios-chat-profile-follow-negative-retry-succeeded/);
  assert.match(iosRunner, /profile_follow_failure_rolled_back_then_visible_retry_converged_and_backend_edge_was_created/);
  assert.match(iosWrapper, /QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E.*negative/);

  assert.match(webHost, /data-quata-profile-follow-failed/);
  assert.match(webRunner, /toggleFollowFailureFromOpenProfile/);
  assert.match(webRunner, /profile_follow_negative_ui_rollback_mismatch/);
  assert.match(webRunner, /web-chat-profile-follow-negative-retry-succeeded/);
  assert.match(webRunner, /MutationObserver/);
  assert.match(webRunner, /profile_follow_negative_optimistic_transition_missing/);
  assert.match(webRunner, /profile_follow_retry_loading_transition_missing/);
  assert.match(webRunner, /public-profile\.follow\.\$\{peerProfile\.profileId\}/);
  assert.match(webRunner, /public-profile\.follow\.retry\.\$\{peerProfile\.profileId\}/);
  assert.match(webRunner, /item\.label\.split\(","\).*includes\(tag\)/);
  assert.match(webRunner, /clickProfileAnchorOrText/);
  assert.match(webRunner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_LOCAL_FACADE_AUTHORIZATION/);
  assert.match(webRunner, /MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_LOCAL_FACADE/);
  assert.match(webRunner, /QUATA_CHAT_ACTIONS_NOTIFICATIONS_WEB_PORT/);
  assert.match(webRunner, /server\.listen\(port, "127\.0\.0\.1", ok\)/);
  assert.match(webRunner, /isNonBlockingAuthorizedLocalFacadeRealtimeFault/);
  assert.match(webRunner, /Unexpected response code: 404/);
  assert.match(webRunner, /pollProfileFollowEdge\(peerProfile\.actorProfileId, peerProfile\.profileId, true\)/);
  assert.match(localFacade, /profile\.pass_hash === sha256\(password\)/);
  assert.match(localFacade, /on conflict \(profile_id, client_instance_id\) do nothing/);
  assert.match(localFacade, /delete from public\.web_client_sessions where id = \$1 returning id/);
  assert.match(localFacade, /receipt\.cleanupVerified = removed === createdWebSessionIds\.size/);
  assert.doesNotMatch(localFacade, /"access-control-allow-origin": "\*"/);
  assert.match(localFacade, /config\.allowedOrigins\.length !== 1/);
  assert.match(localFacade, /local_facade_origin_must_be_exact_loopback_origin/);
  assert.match(localFacade, /allowedOrigins\.has\(requestOrigin\)/);
  assert.match(localFacade, /x-quata-facade-control/);
  assert.match(localFacade, /timingSafeEqual\(expectedHash, suppliedHash\)/);
});

test("the focused contract runs in both fast contract suites", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/profile-follow-negative-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/profile-follow-negative-contract\.test\.mjs/);
});
