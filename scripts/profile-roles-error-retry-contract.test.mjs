import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("profile role mutation failure and retry stay focal and cross-platform", () => {
  const androidRepository = read("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt");
  const androidFault = read("app/src/main/java/com/quata/feature/neighborhoods/data/ProfileRolesEvidenceFaults.kt");
  const androidTest = read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  const androidRunner = read("scripts/chat-actions-notifications-android-evidence.mjs");
  const webRepository = read("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt");
  const webRunner = read("scripts/chat-actions-notifications-web-evidence.mjs");
  const iosRepository = read("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt");
  const iosTest = read("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
  const iosRunner = read("scripts/chat-actions-notifications-ios-evidence.mjs");

  assert.match(androidFault, /AtomicBoolean/);
  assert.match(androidFault, /compareAndSet\(true, false\)/);
  assert.match(androidRepository, /currentProfile\?\.is_admin == true[\s\S]*ProfileRolesEvidenceFaults\.consumeFailure\(\)[\s\S]*updateProfileRoles/);
  assert.match(androidTest, /profile-roles-error-retry[\s\S]*runProfileRolesErrorRetryStage/);
  assert.match(androidTest, /ToggleableState\.Off[\s\S]*ProfileRolesEvidenceFaults\.requestFailureOnce\(\)[\s\S]*public-profile\.error\.[\s\S]*ToggleableState\.On/);
  assert.match(androidRunner, /--profile-roles-error-retry-only/);
  assert.match(androidRunner, /expected: \{ isAdmin: false, isOfficial: true \}/);

  assert.match(webRepository, /isCurrentUserAdmin\(\)[\s\S]*webProfileRolesEvidenceFailureRequested\(\)[\s\S]*client\.patch/);
  assert.match(webRepository, /__QUATA_PROFILE_ROLES_FORCE_FAILURE__[\s\S]*= false/);
  assert.match(webRunner, /--profile-roles-error-retry-only/);
  assert.match(webRunner, /__QUATA_PROFILE_ROLES_FORCE_FAILURE__ = true[\s\S]*profile_roles_error_missing[\s\S]*isOfficial: false[\s\S]*profile_roles_retry_not_clickable[\s\S]*isOfficial: true/);

  assert.match(iosRepository, /isCurrentUserAdmin\(\)[\s\S]*iosProfileRolesEvidenceFailureRequested\(\)[\s\S]*profileRolesEvidenceFailureConsumed[\s\S]*feedTransport\.mutate/);
  assert.match(iosTest, /rolesSafetyMode == "error-retry"/);
  assert.match(iosTest, /QUATA_IOS_PROFILE_ROLES_FORCE_FAILURE[\s\S]*public-profile\.error\.[\s\S]*official\.isEnabled[\s\S]*value == %@/);
  assert.match(iosRunner, /--profile-roles-error-retry-only/);
  assert.match(iosRunner, /QUATA_IOS_CHAT_PROFILE_ROLES_SAFETY_UI_E2E=.*error-retry/);
  assert.match(iosRunner, /QUATA_IOS_PROFILE_ROLES_FORCE_FAILURE/);
});
