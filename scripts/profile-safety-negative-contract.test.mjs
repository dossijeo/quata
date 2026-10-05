import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");
const commonHost = await read("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt");
const moderationActions = await read("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfileModerationActions.kt");
const viewModel = await read("../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModel.kt");
const viewModelTest = await read("../feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt");
const androidRepository = await read("../app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt");
const androidFault = await read("../app/src/main/java/com/quata/feature/neighborhoods/data/ProfileSafetyEvidenceFaults.kt");
const iosRepository = await read("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt");
const webRepository = await read("../web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt");
const androidHost = await read("../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt");
const webHost = await read("../web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsHost.kt");
const iosHost = await read("../feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosNeighborhoodsHost.kt");
const androidRunner = await read("./chat-actions-notifications-android-evidence.mjs");
const androidUi = await read("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
const webRunner = await read("./chat-actions-notifications-web-evidence.mjs");
const iosRunner = await read("./chat-actions-notifications-ios-evidence.mjs");
const iosWrapper = await read("./run-ios-chat-actions-notifications-ui-test.sh");
const iosUi = await read("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
const packageJson = JSON.parse(await read("../package.json"));

test("PROF-SAFETY failure preserves the optimistic rollback state machine and exposes bounded evidence anchors", () => {
  assert.match(viewModel, /selectedProfile = before\.copy\(isBlockedByCurrentUser = blocked\)/);
  assert.match(viewModel, /selectedProfile = currentState\.selectedProfile\?\.let \{ current ->[\s\S]*if \(current\.user\.id == userId\) \{[\s\S]*current\.copy\(isBlockedByCurrentUser = before\.isBlockedByCurrentUser\)[\s\S]*profileSafetyUpdatingUserId = null,[\s\S]*error = error\.message/);
  assert.match(viewModelTest, /profile block is optimistic and restores the exact profile on backend failure/);
  assert.match(viewModelTest, /profile block failure stays scoped and resumes retry on its target profile/);
  assert.match(viewModelTest, /assertEquals\(null, model\.uiState\.value\.error\)[\s\S]*model\.closeUserProfile\(\)[\s\S]*assertEquals\("denied", model\.uiState\.value\.error\)[\s\S]*model\.retryProfileSafety\(\)/);
  assert.match(viewModelTest, /profile block success preserves a newer profile error after navigation/);
  assert.match(viewModelTest, /assertEquals\("b offline", model\.uiState\.value\.error\)[\s\S]*blockResult\.complete\(Result\.success\(true\)\)[\s\S]*assertEquals\("b offline", model\.uiState\.value\.error\)/);
  assert.match(commonHost, /PublicProfileModerationLoadingTestTagPrefix = "public-profile\.safety\.loading\."/);
  assert.match(commonHost, /PublicProfileErrorTestTagPrefix = "public-profile\.error\."/);
  assert.match(moderationActions, /PublicProfileModerationLoadingTestTagPrefix \+ userId/);
});

test("PROF-SAFETY retry preserves the exact failed action across all three hosts", () => {
  assert.match(viewModel, /FailedProfileSafetyAction\([\s\S]*action = ProfileModerationAction\.Report/);
  assert.match(viewModel, /action = if \(blocked\) ProfileModerationAction\.Block else ProfileModerationAction\.Unblock/);
  assert.match(viewModel, /fun retryProfileSafety\(\)[\s\S]*selectedProfile\?\.user\?\.id != failed\.userId[\s\S]*ProfileModerationAction\.Report -> reportProfile\(failed\.userId\)[\s\S]*ProfileModerationAction\.Block -> setProfileBlocked\(failed\.userId, true\)[\s\S]*ProfileModerationAction\.Unblock -> setProfileBlocked\(failed\.userId, false\)/);
  assert.match(viewModel, /failedProfileSafetyAction[\s\S]*takeIf \{ it\.userId == userId \}[\s\S]*errorMessage/);
  assert.match(viewModel, /error = if \(currentState\.selectedProfile\?\.user\?\.id == userId\) \{[\s\S]*message[\s\S]*\} else \{[\s\S]*currentState\.error/);
  assert.equal((viewModel.match(/error = if \(currentState\.selectedProfile\?\.user\?\.id == userId\)/g) ?? []).length, 4);
  assert.match(commonHost, /PublicProfileModerationRetryTestTagPrefix = "public-profile\.safety\.retry\."/);
  assert.match(commonHost, /failedProfileSafetyAction\?\.takeIf[\s\S]*it\.userId == profile\.user\.id && it\.errorMessage == message[\s\S]*TextButton\([\s\S]*onClick = onRetryProfileSafety/);
  for (const host of [androidHost, webHost, iosHost]) {
    assert.match(host, /failedProfileSafetyAction/);
    assert.match(host, /retryProfileSafety/);
  }
  assert.match(viewModelTest, /failed profile report retries the exact action and clears its retry state on success/);
  assert.match(viewModelTest, /failed profile block retries its desired state after rollback/);
  assert.match(viewModelTest, /failed profile unblock retries its desired state after rollback/);
  assert.match(viewModelTest, /profile safety retry cannot mutate a newer visible profile/);
});

test("PROF-SAFETY fault hooks are debug or localhost scoped, one-shot and fail before remote mutation", () => {
  assert.match(androidRepository, /BuildConfig\.DEBUG && ProfileSafetyEvidenceFaults\.consumeBlockFailure\(\)[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*sessionManager\.currentSession\(\)/);
  assert.match(androidFault, /AtomicBoolean/);
  assert.match(iosRepository, /iosProfileSafetyBlockEvidenceFailureRequested\(\) && !profileSafetyBlockEvidenceFailureConsumed[\s\S]*profileSafetyBlockEvidenceFailureConsumed = true[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*authenticatedSession\(\)/);
  assert.match(webRepository, /webProfileSafetyBlockEvidenceFailureRequested\(\)[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*authenticatedUserId\(\)/);
  assert.match(webRepository, /__QUATA_PROFILE_SAFETY_BLOCK_FORCE_FAILURE__ !== true\) return false;[\s\S]*__QUATA_PROFILE_SAFETY_BLOCK_FORCE_FAILURE__ = false/);
  assert.match(webRepository, /\['localhost', '127\.0\.0\.1'\]\.includes/);
});

test("PROF-SAFETY focal runners prove optimistic state, error, rollback and same-control retry", () => {
  for (const runner of [androidRunner, webRunner, iosRunner]) {
    assert.match(runner, /profile-safety-negative-only/);
    assert.match(runner, /expectedBlocked: true/);
    assert.match(runner, /profile_safety_failed_block_optimistic_state_error_exact_rollback_and_same_control_retry_verified/);
    assert.match(runner, /cleanupProfileRolesSafetyFixture/);
  }
  for (const ui of [androidUi, iosUi]) {
    assert.match(ui, /public-profile\.safety\.loading\./);
    assert.match(ui, /public-profile\.safety\.unblock\./);
    assert.match(ui, /public-profile\.safety\.retry\.block\./);
    assert.match(ui, /public-profile\.error\./);
    assert.match(ui, /public-profile\.safety\.block\./);
  }
  assert.match(webRunner, /__QUATA_PROFILE_SAFETY_BLOCK_FORCE_FAILURE__/);
  assert.match(androidRunner, /!profileFollowNegativeOnly && !profileSafetyNegativeOnly/);
  assert.match(androidRunner, /native_loopback_auth_rest_facade_accepted_for_profile_safety_retry/);
  assert.match(androidRunner, /android_debug_package_precompiled_before_profile_safety_retry_instrumentation/);
  assert.match(androidRunner, /profile_safety_retry_block_persisted_verified_by_db/);
  assert.match(iosRunner, /!profileFollowNegativeOnly && !profileSafetyNegativeOnly/);
  assert.match(iosRunner, /native_loopback_auth_rest_facade_accepted_for_ios_profile_safety_retry/);
  assert.match(iosWrapper, /QUATA_IOS_PROFILE_SAFETY_BLOCK_FORCE_FAILURE/);
  assert.match(iosUi, /if profileSafetyNegative \|\| verifiesNonAdminPermissions \|\| verifiesRoleErrorRetry \{[\s\S]*app\.wait\(for: \.runningForeground/);
  assert.match(iosUi, /\} else \{[\s\S]*feed\.waitForExistence\(timeout: 20\)[\s\S]*The seeded normal launch must restore Feed/);
});

test("PROF-SAFETY Android runner treats its focal completion sentinel as success", () => {
  assert.match(
    androidRunner,
    /throw new Error\([\s\S]*profile_safety_negative_only_completed[\s\S]*catch \(error\) \{[\s\S]*error\?\.message === "profile_safety_negative_only_completed"/,
  );
});

test("PROF-SAFETY contract is included in both fast suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /profile-safety-negative-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /profile-safety-negative-contract\.test\.mjs/);
});
