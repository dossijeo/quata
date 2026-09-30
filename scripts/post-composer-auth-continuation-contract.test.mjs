import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("POST-PUBLISH preserves one exact draft and resumes one authenticated submit", async () => {
  const [coordinator, root, android, androidNavigation, web, ios, swift] = await Promise.all([
    source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/PostComposerAuthenticationContinuation.kt"),
    source("feature/postcomposer/src/commonMain/kotlin/com/quata/feature/postcomposer/presentation/CreatePostRoot.kt"),
    source("app/src/main/java/com/quata/feature/postcomposer/presentation/CreatePostScreen.kt"),
    source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
    source("web/src/wasmJsMain/kotlin/com/quata/web/WebPostComposerHost.kt"),
    source("feature/postcomposer/src/iosMain/kotlin/com/quata/feature/postcomposer/presentation/IosComposerHost.kt"),
    source("iosApp/iosApp/QuataIosApp.swift"),
  ]);

  for (const field of ["step", "text", "textPatternId", "imageUri", "videoUri", "locationLabel", "latitude", "longitude", "locationOrigin", "selectedDestinationWallId"]) {
    assert.match(coordinator, new RegExp(`val ${field}:`), `snapshot must retain ${field}`);
  }
  assert.match(coordinator, /if \(current\.requestId != requestId\) return null/);
  assert.match(coordinator, /fun cancelAuthentication\(\)[\s\S]*?_pending\.value = null/);
  assert.match(root, /authenticationRequiredSubmitType/);
  assert.match(root, /coordinator\.request\(viewModel\.snapshot\(step\), type\)/);

  assert.match(android, /pendingAuthenticationContinuation/);
  assert.doesNotMatch(android, /onDispose \{[^}]*clearOwnedMedia\(\)/s);
  assert.match(androidNavigation, /postComposerAuthenticationSurfaceVisited/);
  assert.match(androidNavigation, /currentRoute in authenticationRoutes -> postComposerAuthenticationSurfaceVisited = true/);
  assert.match(androidNavigation, /postComposerAuthenticationSurfaceVisited && !isAuthenticated && !isAuthRequiredPromptOpen/);
  assert.match(web, /authenticationContinuationCoordinator\?\.claim\(pending\.requestId\)/);
  assert.match(ios, /authenticationContinuationCoordinator\?\.claim\(pending\.requestId\)/);
  assert.doesNotMatch(ios, /onAuthRequired = dependencies\.onClose/);
  assert.match(swift, /case \.feed, \.official, \.communities, \.notifications, \.composer,/);
  assert.match(swift, /case \.composer:\s*guard let controller = composerFactory\?\(\) else \{ return \}\s*showRouteController\(controller, route: \.composer\)/);
});
