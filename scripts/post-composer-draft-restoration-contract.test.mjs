import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("the durable envelope is versioned actor-bound and excludes raw media bytes", async () => {
  const store = await source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/PostComposerDraftStore.kt");
  assert.match(store, /const val StateKey = "post-composer\.draft\.state\.v1"/);
  assert.match(store, /preferences as\? AtomicPreferenceStore/);
  assert.match(store, /updateStringAtomically\(StateKey\)/);
  assert.match(store, /current\.actorProfileId != normalized[\s\S]*?encodedDraft = null/);
  assert.match(store, /revision = current\.revision \+ 1/);
  assert.match(store, /current\.matches\(lease\) && current\.revision == restoredRevision/);
  assert.match(store, /!it\.startsWith\("data:", ignoreCase = true\)/);
  assert.match(store, /decoded == null[\s\S]*?current\.copy\(revision = current\.revision \+ 1, encodedDraft = null\)[\s\S]*?return null/);
  assert.match(store, /private val mutationLock = Mutex\(\)/);
  assert.match(store, /suspend fun save\(lease: PostComposerDraftActorLease[\s\S]*?!current\.matches\(lease\)[\s\S]*?current\.copy/);
  assert.match(store, /suspend fun clear\(lease: PostComposerDraftActorLease\)[\s\S]*?!current\.matches\(lease\)[\s\S]*?generation = current\.generation \+ 1[\s\S]*?PostComposerDraftActorLease\(lease\.actorProfileId, next\.generation\)/);
  assert.doesNotMatch(store, /accessToken|refreshToken|bearerToken/);
});

test("the common root restores before persistence and clears publish discard and reset", async () => {
  const root = await source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt");
  assert.match(root, /durableDraftReady = false[\s\S]*?shouldResetDraftForActorTransition[\s\S]*?CreatePostUiEvent\.ClearDraft[\s\S]*?baselineMutationRevision = viewModel\.draftMutationRevision\(\)[\s\S]*?if \(actor == null\)[\s\S]*?return@LaunchedEffect[\s\S]*?afterObservation\(actor\)[\s\S]*?store\.activateActor\(actor\)[\s\S]*?initialStep == null && !resetForActorChange[\s\S]*?store\.restore\(lease\.actorProfileId, durableMediaReferenceAvailable\)[\s\S]*?store\.isCurrent\(restoration\)[\s\S]*?viewModel\.draftMutationRevision\(\) == baselineMutationRevision[\s\S]*?durableDraftReady = true/);
  assert.match(root, /previousActorProfileId == null && nextActorProfileId != null && hasAuthenticationContinuation/);
  assert.match(root, /val appliedRestoration = if[\s\S]*?durablePersistedSnapshot = appliedRestoration \?: baseline[\s\S]*?shouldPersistPostComposerDraft\(durableDraftReady, durableSnapshot, durablePersistedSnapshot\)[\s\S]*?store\.save\(lease, durableSnapshot\)/);
  assert.match(root, /suspend fun completeDraftClear[\s\S]*?attemptPostComposerDraftClear\(request\.actorProfileId, request\.actorLease, store::clear\)[\s\S]*?!isPostComposerDraftClearRequestCurrent[\s\S]*?request\.actorLease[\s\S]*?currentDraftActorLease[\s\S]*?PostComposerDraftClearAttempt\.Failed[\s\S]*?pendingDraftClearRequest = request[\s\S]*?durablePersistedSnapshot = null/);
  assert.match(root, /fun requestDraftClear[\s\S]*?val request = PostComposerDraftClearRequest[\s\S]*?actorProfileId = currentDraftActorProfileId[\s\S]*?actorLease = currentDraftActorLease[\s\S]*?scope\.launch \{ completeDraftClear\(request\) \}/);
  assert.match(root, /attemptPostComposerDraftClear[\s\S]*?catch \(cancelled: CancellationException\)[\s\S]*?throw cancelled[\s\S]*?catch \(_: Throwable\)[\s\S]*?PostComposerDraftClearAttempt\.Failed/);
  assert.match(root, /PostComposerDraftClearAction\.Reset[\s\S]*?lastResetToken = action\.token[\s\S]*?PostComposerDraftClearAction\.PublishSuccess[\s\S]*?onPostCreated[\s\S]*?PostComposerDraftClearAction\.Discard[\s\S]*?dispatchCreatePostBack/);
  assert.match(root, /errorMessage = if \(pendingDraftClearRequest != null\) copy\.draftDiscardFailed[\s\S]*?onRetry = pendingDraftClearRequest/);
});

test("Android Web and iOS inject the same store and validate platform media references", async () => {
  const [android, androidRoot, web, webRoot, ios, iosRoot] = await Promise.all([
    source("app/src/main/java/com/quata/feature/postcomposer/presentation/CreatePostScreen.kt"),
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);
  for (const host of [android, web, ios]) {
    assert.match(host, /PostComposerDraftStore/);
    assert.match(host, /durableMediaReferenceAvailable/);
    assert.match(host, /draftActorProfileId/);
  }
  assert.match(android, /openFileDescriptor\(uri, "r"\)/);
  assert.match(web, /webComposerDraftMediaReferenceAvailable/);
  assert.match(ios, /NSFileManager\.defaultManager::fileExistsAtPath/);
  assert.match(androidRoot, /durableDraftStore = postComposerDraftStore/);
  assert.match(androidRoot, /PostComposerDraftStore\(AndroidPreferenceStore\(appContext, commitWrites = true\)\)/);
  assert.doesNotMatch(androidRoot, /createPostResetToken \+= 1/);
  assert.match(webRoot, /durableDraftStore = postComposerDraftStore/);
  assert.match(iosRoot, /private lazy var postComposerDraftStore = PostComposerDraftStore[\s\S]*?durableDraftStore: self\?\.postComposerDraftStore/);
});

test("Android process-restart evidence scrolls to discard and verifies durable removal", async () => {
  const testSource = await source("app/src/androidTest/java/com/quata/feature/postcomposer/presentation/CreatePostPostflightInstrumentedTest.kt");
  assert.match(testSource, /onNodeWithTag\("composer-back"[\s\S]*?\.performScrollTo\(\)[\s\S]*?\.performClick\(\)/);
  assert.match(testSource, /waitForPersistedDraftCleared\(initialSession\?\.userId\.orEmpty\(\)\)/);
  assert.match(testSource, /restored_draft_persistent_record_cleared_after_discard/);
  assert.match(testSource, /store\.restore\(actorProfileId\) \{ false \} == null/);
  const runner = await source("scripts/create-post-postflight-android-evidence.mjs");
  const preflightClear = runner.indexOf('await run(adb, ["shell", "pm", "clear", "com.quata"]);');
  const credentialWrite = runner.indexOf("await adbRunAsWrite(");
  assert.ok(preflightClear >= 0 && preflightClear < credentialWrite);
  assert.match(runner, /android_app_data_cleared_before_evidence/);
});

test("session transitions retire the previous actor draft on every platform", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);
  assert.match(android, /LaunchedEffect\(currentUserId, postComposerDraftStore\)[\s\S]*?activateActor\(currentUserId\)/);
  assert.match(web, /LaunchedEffect\(currentUserId, isSessionResolved, postComposerDraftStore\)[\s\S]*?if \(isSessionResolved\) postComposerDraftStore\.activateActor\(currentUserId\)/);
  assert.match(ios, /onLoggedOut:[\s\S]*?retireIosPostComposerDraft/);
});

test("the executable common tests cover isolation corruption cleanup and unavailable media", async () => {
  const tests = await source("feature/postcomposer/src/commonTest/kotlin/com/quata/feature/postcomposer/presentation/PostComposerDraftStoreTest.kt");
  for (const name of [
    "restoresOnlyTheMatchingActorAndClearsThePreviousActorOnChange",
    "malformedOrUnknownPayloadFailsClosedAndIsRemoved",
    "unavailableMediaIsDroppedWhileSafeFieldsRemain",
    "rawMediaBytesAndInvalidActorsAreNeverPersisted",
    "clearRemovesTheDraftAndActiveActorMarker",
    "actorChangeSerializesWithInFlightSaveAndRejectsStaleWrites",
    "delayedRestoreLeaseIsInvalidAfterActorChange",
    "independentStoresShareTheActorFenceAndRejectAStaleTabWrite",
    "actorSwitchResetsExistingContentButPreservesTheLoginContinuation",
    "unresolvedActorDoesNotBecomeALogoutOrBlockLaterRestoration",
    "failedAtomicClearKeepsTheDraftAndReturnsNoReplacementLease",
    "delayedMediaRepairCannotOverwriteANewerDraftFromAnotherStore",
    "rejectedRestoreDoesNotTriggerAnInitialEmptyAutosaveOverTheNewerDraft",
    "clearAttemptTurnsStorageFailuresIntoRetryableResultsAndPreservesCancellation",
    "clearCompletionIsRejectedAfterTheAuthenticatedActorChanges",
    "staleClearLeaseCannotDeleteANewDraftAfterActorCyclesBack",
  ]) assert.match(tests, new RegExp(`fun ${name}\\(`));
});
