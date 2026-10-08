import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("the durable envelope is versioned actor-bound and excludes raw media bytes", async () => {
  const store = await source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/PostComposerDraftStore.kt");
  assert.match(store, /const val DraftKeyPrefix = "post-composer\.draft\.v1\."/);
  assert.match(store, /previous != null && previous != normalized[\s\S]*?preferences\.remove\(draftKey\(previous\)\)/);
  assert.match(store, /!it\.startsWith\("data:", ignoreCase = true\)/);
  assert.match(store, /decoded == null[\s\S]*?preferences\.remove\(key\)[\s\S]*?return null/);
  assert.doesNotMatch(store, /accessToken|refreshToken|bearerToken/);
});

test("the common root restores before persistence and clears publish discard and reset", async () => {
  const root = await source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt");
  assert.match(root, /durableDraftReady = false[\s\S]*?store\.restore\(it, durableMediaReferenceAvailable\)[\s\S]*?durableDraftReady = true/);
  assert.match(root, /if \(durableDraftReady\) store\.save\(actor, durableSnapshot\)/);
  assert.match(root, /LaunchedEffect\(resetToken\)[\s\S]*?durableDraftStore\?\.clear\(draftActorProfileId\)/);
  assert.match(root, /if \(state\.successMessage != null\)[\s\S]*?durableDraftStore\?\.clear\(draftActorProfileId\)/);
  assert.match(root, /ComposerBackButtonContent[\s\S]*?durableDraftStore\?\.clear\(draftActorProfileId\)/);
});

test("Android Web and iOS inject the same store and validate platform media references", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/feature/postcomposer/presentation/CreatePostScreen.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt"),
    source("feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt"),
  ]);
  for (const host of [android, web, ios]) {
    assert.match(host, /PostComposerDraftStore/);
    assert.match(host, /durableMediaReferenceAvailable/);
    assert.match(host, /draftActorProfileId/);
  }
  assert.match(android, /openFileDescriptor\(uri, "r"\)/);
  assert.match(web, /webComposerDraftMediaReferenceAvailable/);
  assert.match(ios, /NSFileManager\.defaultManager::fileExistsAtPath/);
});

test("session transitions retire the previous actor draft on every platform", async () => {
  const [android, web, ios] = await Promise.all([
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);
  assert.match(android, /LaunchedEffect\(currentUserId, postComposerDraftStore\)[\s\S]*?activateActor\(currentUserId\)/);
  assert.match(web, /LaunchedEffect\(currentUserId, postComposerDraftStore\)[\s\S]*?activateActor\(currentUserId\)/);
  assert.match(ios, /onLoggedOut:[\s\S]*?clearIosPostComposerDraft/);
});

test("the executable common tests cover isolation corruption cleanup and unavailable media", async () => {
  const tests = await source("feature/postcomposer/src/commonTest/kotlin/com/quata/feature/postcomposer/presentation/PostComposerDraftStoreTest.kt");
  for (const name of [
    "restoresOnlyTheMatchingActorAndClearsThePreviousActorOnChange",
    "malformedOrUnknownPayloadFailsClosedAndIsRemoved",
    "unavailableMediaIsDroppedWhileSafeFieldsRemain",
    "rawMediaBytesAndInvalidActorsAreNeverPersisted",
    "clearRemovesTheDraftAndActiveActorMarker",
  ]) assert.match(tests, new RegExp(`fun ${name}\\(`));
});
