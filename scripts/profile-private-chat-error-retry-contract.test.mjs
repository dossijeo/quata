import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("profile private chat remote failure retries the same action across platforms", () => {
  const androidRepository = read("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt");
  const androidChatRepository = read("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt");
  const androidFault = read("app/src/main/java/com/quata/feature/neighborhoods/data/ProfilePrivateChatEvidenceFaults.kt");
  const androidTest = read("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt");
  const androidRunner = read("scripts/chat-actions-notifications-android-evidence.mjs");
  const webRepository = read("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt");
  const webRunner = read("scripts/chat-actions-notifications-web-evidence.mjs");
  const iosRepository = read("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt");
  const sharedChatRepository = read("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt");
  const iosTest = read("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift");
  const iosRunner = read("scripts/chat-actions-notifications-ios-evidence.mjs");
  const iosWrapper = read("scripts/run-ios-chat-actions-notifications-ui-test.sh");
  const viewModelTest = read("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt");

  assert.match(androidFault, /AtomicBoolean[\s\S]*compareAndSet\(true, false\)/);
  assert.match(androidRepository, /currentSession\(\)[\s\S]*cachedPrivateConversationId\(userId\)[\s\S]*ProfilePrivateChatEvidenceFaults\.consumeFailure\(\)[\s\S]*openGroupConversation/);
  assert.doesNotMatch(androidChatRepository.match(/override suspend fun cachedPrivateConversationId[\s\S]*?override suspend fun cachedCommunityConversationId/)?.[0] ?? "", /getOrCreatePrivateThread|refreshAll/);
  assert.match(androidTest, /profile-private-chat-error-retry[\s\S]*requestFailureOnce\(\)[\s\S]*public-profile\.error\.[\s\S]*assertIsEnabled\(\)[\s\S]*performClick\(\)[\s\S]*waitForMarker/);
  assert.match(androidRepository, /requiresRemoteOpen\(\)[\s\S]*cachedPrivateConversationId[\s\S]*consumeFailure\(\)[\s\S]*openGroupConversation[\s\S]*markRemoteOpenSucceeded\(\)/);
  assert.match(androidRunner, /--profile-private-chat-error-retry-only/);
  assert.match(androidRunner, /snapshotTemporaryPrivateConversation[\s\S]*matchingPrivateThreadCount/);

  assert.match(webRepository, /authenticatedUserId\(\)[\s\S]*openWebPrivateConversation[\s\S]*cachedConversationId[\s\S]*webProfilePrivateChatEvidenceFailureRequested\(\)[\s\S]*openPrivateConversation/);
  assert.match(webRunner, /--profile-private-chat-error-retry-only/);
  assert.match(webRunner, /__QUATA_PROFILE_PRIVATE_CHAT_FORCE_FAILURE__ = true[\s\S]*__QUATA_PROFILE_PRIVATE_CHAT_FORCE_REMOTE__ = true[\s\S]*profile_private_chat_error_retry_error_missing[\s\S]*profile_private_chat_error_retry_same_action_missing[\s\S]*clickSameAction[\s\S]*matchingPrivateThreadCount/);
  assert.match(webRepository, /webProfilePrivateChatEvidenceRemoteOpenRequired\(\)[\s\S]*cachedPrivateConversationId[\s\S]*webProfilePrivateChatEvidenceFailureRequested\(\)[\s\S]*openPrivateConversation[\s\S]*webProfilePrivateChatEvidenceRemoteOpenCompleted\(\)/);

  assert.match(iosRepository, /authenticatedSession\(\)[\s\S]*iosProfilePrivateChatEvidenceFailureRequested\(\)[\s\S]*cachedPrivateConversationId\(userId\)[\s\S]*openPrivateConversation/);
  assert.doesNotMatch(sharedChatRepository.match(/override suspend fun cachedPrivateConversationId[\s\S]*?override suspend fun cachedCommunityConversationId/)?.[0] ?? "", /openPrivateConversation/);
  assert.match(iosTest, /privateChatMode == "error-retry"[\s\S]*QUATA_IOS_PROFILE_PRIVATE_CHAT_FORCE_FAILURE[\s\S]*public-profile\.error\.[\s\S]*retry\.isEnabled[\s\S]*retry\.coordinate/);
  assert.match(iosRepository, /profilePrivateChatEvidenceRemoteOpenCompleted[\s\S]*evidenceRemoteOpen[\s\S]*cachedPrivateConversationId[\s\S]*profilePrivateChatEvidenceFailureConsumed[\s\S]*openPrivateConversation/);
  assert.match(iosRunner, /--profile-private-chat-error-retry-only/);
  assert.match(iosRunner, /PROFILE_PRIVATE_CHAT_UI_E2E=.*error-retry/);
  assert.match(iosRunner, /snapshotTemporaryPrivateConversation[\s\S]*matchingPrivateThreadCount/);
  assert.match(iosWrapper, /testProfilePrivateChatFromChatUsesSharedPublicProfileAction/);
  assert.match(iosWrapper, /PROFILE_PRIVATE_CHAT_UI_E2E" == "1" \|\| "\$QUATA_IOS_CHAT_PROFILE_PRIVATE_CHAT_UI_E2E" == "error-retry"/);

  assert.match(viewModelTest, /private chat failure clears loading and the same action can retry[\s\S]*openPrivateChatCalls[\s\S]*private_chat_failed[\s\S]*sb:private-2/);
});
