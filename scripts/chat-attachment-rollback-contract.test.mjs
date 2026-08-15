import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(path, "utf8");

const [
  commonRepository,
  commonRepositoryTest,
  webRepository,
  webUploader,
  iosTransport,
  androidRepository,
  androidRemote,
  androidApi,
  androidHttp,
  androidUiTest,
  iosUiTest,
  iosRuntime,
  webRunner,
  androidRunner,
  iosRunner,
] = await Promise.all([
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/data/PostgrestChatRepository.kt"),
  source("feature/chat/src/commonTest/kotlin/com/quata/feature/chat/data/PostgrestChatRepositoryTest.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatRepository.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatAttachmentUploader.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/data/IosPostgrestChatTransport.kt"),
  source("app/src/main/java/com/quata/feature/chat/data/ChatRepositoryImpl.kt"),
  source("app/src/main/java/com/quata/feature/chat/data/ChatRemoteDataSource.kt"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt"),
  source("app/src/main/java/com/quata/data/supabase/SupabaseHttpClient.kt"),
  source("app/src/androidTest/java/com/quata/feature/chat/presentation/chat/ChatActionsNotificationsInstrumentedTest.kt"),
  source("iosApp/iosAppUITests/QuataIosAuthenticatedChatActionsNotificationsUITests.swift"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/IosChatRuntimeBootstrap.kt"),
  source("scripts/chat-actions-notifications-web-evidence.mjs"),
  source("scripts/chat-actions-notifications-android-evidence.mjs"),
  source("scripts/chat-actions-notifications-ios-evidence.mjs"),
]);

test("common chat repository rolls back uploaded storage only when registration fails", () => {
  assert.match(commonRepository, /suspend fun deleteUploadedAttachment\(uploaded: UploadedChatAttachment\): Boolean = false/);
  assert.match(commonRepository, /val uploaded = attachmentUploader\.upload\(profileId, file\)/);
  assert.match(commonRepository, /transport\.post\("quata_chat_register_attachment", body\)\.successOrThrow\(\)/);
  assert.match(commonRepository, /catch \(error: Throwable\) \{\s*runCatching \{ attachmentUploader\.deleteUploadedAttachment\(uploaded\) \}\s*throw error\s*\}/s);
  assert.match(commonRepositoryTest, /registerFailureAfterAttachmentUploadDeletesTheOrphanStorageObject/);
  assert.match(commonRepositoryTest, /retryAfterSendFailureReusesRegisteredAttachment/);
  assert.match(commonRepositoryTest, /assertEquals\(emptyList\(\), deletedStoragePaths\)/);
});

test("web chat storage adapter can delete the uploaded object with authenticated Supabase Storage request", () => {
  assert.match(webRepository, /object : ChatAttachmentUploader/);
  assert.match(webRepository, /override suspend fun deleteUploadedAttachment\(uploaded: UploadedChatAttachment\): Boolean =\s*attachmentUploader\.delete\(uploaded\.storagePath\)/s);
  assert.match(webUploader, /suspend fun delete\(storagePath: String\): Boolean/);
  assert.match(webUploader, /"\$baseUrl\/storage\/v1\/object\/chat-attachments"/);
  assert.match(webUploader, /method: 'DELETE'/);
  assert.match(webUploader, /JSON\.stringify\(\{ prefixes: \[storagePath\] \}\)/);
  assert.match(webUploader, /Authorization: `Bearer/);
});

test("iOS chat storage adapter exposes matching authenticated cleanup", () => {
  assert.match(iosTransport, /override suspend fun deleteUploadedAttachment\(uploaded: UploadedChatAttachment\): Boolean/);
  assert.match(iosTransport, /setHTTPMethod\("DELETE"\)/);
  assert.match(iosTransport, /JsonObject\(mapOf\("prefixes" to JsonArray\(listOf\(JsonPrimitive\(cleanPath\)\)\)\)\)/);
  assert.match(iosTransport, /"\$\{configuration\.storageBaseUrl\(\)\}\/object\/\$ChatAttachmentsBucket"/);
  assert.match(iosTransport, /AttachmentCleanupMillis = 30_000L/);
});

test("Android legacy chat path deletes uploaded storage if register RPC or response parsing fails", () => {
  assert.match(androidRemote, /suspend fun deleteChatAttachmentObject\(storagePath: String\)/);
  assert.match(androidApi, /suspend fun deleteChatAttachmentObject\(storagePath: String\)/);
  assert.match(androidHttp, /suspend fun deleteObject\(path: String, bucket: String = config\.storageBucket\)/);
  assert.match(androidHttp, /removePrefix\("\$bucket\/"\)/);
  assert.match(androidHttp, /\?: cleanPath/);
  assert.match(androidHttp, /execute\("DELETE", "\$\{config\.storageUrl\}\/object\/\$bucket", body, useContentProfile = false\)/);
  assert.match(androidRepository, /return try \{\s*if \(shouldFailAttachmentRegistrationForEvidence\(\)\) \{\s*error\("chat_attachment_register_e2e_failure"\)\s*\}\s*val registered = remote\.registerChatAttachment/s);
  assert.match(androidRepository, /attemptOrQueuePendingOutgoing\(session, pending\)/);
  assert.match(androidRepository, /removePendingOutgoing\(session\.userId, outgoing\)/);
  assert.doesNotMatch(androidRepository, /CHAT_EVIDENCE_NAME_KEY/);
  assert.match(androidRepository, /registered\.obj\.long\("id"\)\s*\?: registered\.obj\.obj\("file"\)\?\.long\("id"\)\s*\?: error\("No se pudo registrar el adjunto"\)/s);
  assert.match(androidRepository, /catch \(error: Throwable\) \{\s*upload\.key\?\.takeIf \{ it\.isNotBlank\(\) \}\?\.let \{ storagePath ->\s*runCatching \{ remote\.deleteChatAttachmentObject\(storagePath\) \}/s);
});

test("visual evidence runners exercise register-failure rollback on every platform", () => {
  for (const runner of [webRunner, androidRunner, iosRunner]) {
    assert.match(runner, /"register-failure"/);
    assert.match(runner, /assertNoAttachmentPickerResidue/);
    assert.match(runner, /storageResidueCount:\s*0/);
    assert.match(runner, /register_failure_rolled_back_storage/);
  }

  assert.match(webRunner, /page\.route\("\*\*\/rest\/v1\/rpc\/quata_chat_register_attachment"/);
  assert.match(webRunner, /outcome === "success" \|\| outcome === "register-failure"/);
  assert.match(webRunner, /while \(!registerFailureInjected && Date\.now\(\) < registerDeadline\)/);
  assert.match(webRunner, /isExpectedAttachmentRegisterFailureFault/);
  assert.match(webRunner, /nonBlockingBrowserRuntimeFaults/);
  assert.match(webRunner, /chat_attachment_register_e2e_failure/);
  assert.match(webRunner, /web-chat-attachment-picker-register-failure-\$\{source\}/);

  assert.match(androidUiTest, /outcome != "success" && outcome != "register-failure"/);
  assert.match(androidUiTest, /if \(outcome == "register-failure"\)/);
  assert.match(androidUiTest, /forceNativeSend = outcome == "register-failure"/);
  assert.match(androidUiTest, /val sentByNative = forceNativeSend && clickComposerSendNative\(\)/);
  assert.doesNotMatch(androidUiTest, /if \(forceNativeSend && clickComposerSendNative\(\)\) return/);
  assert.match(androidUiTest, /ChatAttachmentErrorTestTag/);
  assert.match(androidUiTest, /android-chat-attachment-picker-register-failure-\$source/);
  assert.match(androidRepository, /I_ACCEPT_ANDROID_CHAT_ATTACHMENT_PICKER_FIXTURE/);
  assert.match(androidRepository, /chat_attachment_register_e2e_failure/);

  assert.match(iosUiTest, /pickerOutcome != "success" && pickerOutcome != "register-failure"/);
  assert.match(iosUiTest, /if pickerOutcome == "register-failure"/);
  assert.match(iosUiTest, /chat\.attachment\.error/);
  assert.match(iosUiTest, /ios-chat-attachment-picker-register-failure-\\\(pickerSource\)/);
  assert.doesNotMatch(iosRunner, /mktemp \/tmp\/quata-ios-chat-actions-credentials\.XXXXXX\.json/);
  assert.match(iosRuntime, /I_ACCEPT_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE/);
  assert.match(iosRuntime, /chat_attachment_register_e2e_failure/);
});
