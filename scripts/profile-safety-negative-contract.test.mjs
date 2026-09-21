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
const androidRunner = await read("./chat-actions-notifications-android-evidence.mjs");
const androidUi = await read("../app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
const webRunner = await read("./chat-actions-notifications-web-evidence.mjs");
const iosRunner = await read("./chat-actions-notifications-ios-evidence.mjs");
const iosWrapper = await read("./run-ios-chat-actions-notifications-ui-test.sh");
const iosUi = await read("../iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
const packageJson = JSON.parse(await read("../package.json"));

test("PROF-SAFETY failure preserves the optimistic rollback state machine and exposes bounded evidence anchors", () => {
  assert.match(viewModel, /selectedProfile = before\.copy\(isBlockedByCurrentUser = blocked\)/);
  assert.match(viewModel, /selectedProfile = before,[\s\S]*profileSafetyUpdatingUserId = null,[\s\S]*error = error\.message/);
  assert.match(viewModelTest, /profile block is optimistic and restores the exact profile on backend failure/);
  assert.match(commonHost, /PublicProfileModerationLoadingTestTagPrefix = "public-profile\.safety\.loading\."/);
  assert.match(commonHost, /PublicProfileErrorTestTagPrefix = "public-profile\.error\."/);
  assert.match(moderationActions, /PublicProfileModerationLoadingTestTagPrefix \+ userId/);
});

test("PROF-SAFETY fault hooks are debug or localhost scoped and fail before remote mutation", () => {
  assert.match(androidRepository, /BuildConfig\.DEBUG && ProfileSafetyEvidenceFaults\.consumeBlockFailure\(\)[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*sessionManager\.currentSession\(\)/);
  assert.match(androidFault, /AtomicBoolean/);
  assert.match(iosRepository, /iosProfileSafetyBlockEvidenceFailureRequested\(\)[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*authenticatedSession\(\)/);
  assert.match(webRepository, /webProfileSafetyBlockEvidenceFailureRequested\(\)[\s\S]*error\("profile_safety_block_e2e_forced_failure"\)[\s\S]*authenticatedUserId\(\)/);
  assert.match(webRepository, /\['localhost', '127\.0\.0\.1'\]\.includes/);
});

test("PROF-SAFETY focal runners prove optimistic state, error, rollback and backend absence", () => {
  for (const runner of [androidRunner, webRunner, iosRunner]) {
    assert.match(runner, /profile-safety-negative-only/);
    assert.match(runner, /expectedBlocked: false/);
    assert.match(runner, /profile_safety_failed_block_optimistic_state_error/);
    assert.match(runner, /cleanupProfileRolesSafetyFixture/);
  }
  for (const ui of [androidUi, iosUi]) {
    assert.match(ui, /public-profile\.safety\.loading\./);
    assert.match(ui, /public-profile\.safety\.unblock\./);
    assert.match(ui, /public-profile\.error\./);
    assert.match(ui, /public-profile\.safety\.block\./);
  }
  assert.match(webRunner, /__QUATA_PROFILE_SAFETY_BLOCK_FORCE_FAILURE__/);
  assert.match(iosWrapper, /QUATA_IOS_PROFILE_SAFETY_BLOCK_FORCE_FAILURE/);
});

test("PROF-SAFETY contract is included in both fast suites", () => {
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /profile-safety-negative-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /profile-safety-negative-contract\.test\.mjs/);
});
