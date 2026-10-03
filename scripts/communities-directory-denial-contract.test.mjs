import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [
  domain,
  state,
  viewModel,
  host,
  content,
  localization,
  viewModelTest,
  deniedUiTest,
  failureTest,
  android,
  web,
  ios,
] = await Promise.all([
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/domain/NeighborhoodRepository.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsUiState.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModel.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreenHost.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodListContent.kt"),
  read("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsLocalization.kt"),
  read("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodsViewModelTest.kt"),
  read("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/presentation/NeighborhoodDirectoryDeniedUiTest.kt"),
  read("feature/neighborhoods/src/commonTest/kotlin/com/quata/feature/neighborhoods/domain/NeighborhoodDirectoryFailureTest.kt"),
  read("app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt"),
  read("web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt"),
  read("feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt"),
]);

test("directory authorization failures have one portable meaning without broadening other failures", () => {
  assert.match(domain, /class NeighborhoodDirectoryAccessDeniedException/);
  assert.match(domain, /if \(statusCode == 401 \|\| statusCode == 403\) NeighborhoodDirectoryAccessDeniedException\(cause\) else cause/);
  assert.match(failureTest, /authorization statuses use the portable directory failure/);
  assert.match(failureTest, /non authorization failures preserve the transport cause/);
  assert.match(failureTest, /assertSame\(cause, neighborhoodDirectoryFailure\(500, cause\)\)/);
});

test("Android, Web and iOS normalize only their real directory transport rejection", () => {
  assert.match(android, /\.catch \{ error ->\s*val statusCode = \(error as\? SupabaseApiException\)\?\.statusCode\s*throw neighborhoodDirectoryFailure\(statusCode, error\)/s);
  assert.match(web, /catch \(error: WebPostgrestReadException\) \{\s*throw neighborhoodDirectoryFailure\(error\.failure\.statusCode, error\)/s);
  assert.match(ios, /catch \(error: IosNeighborhoodHttpException\) \{\s*throw neighborhoodDirectoryFailure\(error\.statusCode, error\)/s);
  assert.match(ios, /private class IosNeighborhoodHttpException\(val statusCode: Int\?\)/);
  assert.doesNotMatch(`${android}\n${web}\n${ios}`, /NeighborhoodDirectoryAccessDeniedException\(\)/);
});

test("the shared directory exposes a localized, deterministic retry state", () => {
  assert.match(state, /val directoryLoadFailed: Boolean = false/);
  assert.match(state, /val directoryAccessDenied: Boolean = false/);
  assert.match(content, /const val NeighborhoodDirectoryRetryTestTag = "neighborhood\.directory\.retry"/);
  assert.match(content, /if \(directoryAccessDenied\) strings\.directoryAccessDenied else error/);
  assert.match(content, /if \(directoryLoadFailed\)[\s\S]*?OutlinedButton\([\s\S]*?onClick = onRetry/);
  assert.match(host, /fun retryCommunities\(\) \{\s*stopObservingCommunities\(\)\s*startObservingCommunities\(\)\s*\}/s);
  assert.match(host, /onRetry = viewModel::retryCommunities/);
  for (const value of ["directoryAccessDenied", "retry"]) {
    assert.match(content, new RegExp(`val ${value}: String`));
  }
  for (const copy of [
    "No tienes permiso para consultar el directorio de comunidades.",
    "Vous n’avez pas l’autorisation de consulter l’annuaire des communautés.",
    "You do not have permission to view the communities directory.",
  ]) assert.match(localization, new RegExp(copy.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.match(deniedUiTest, /authorizationDenialIsVisibleAndRetriesExactlyOnce\(\) = runComposeUiTest/);
  assert.match(deniedUiTest, /onNodeWithText\("Directory access denied"\)\.assertIsDisplayed\(\)/);
  assert.match(deniedUiTest, /onNodeWithTag\(NeighborhoodDirectoryRetryTestTag\)\.assertIsDisplayed\(\)\.performClick\(\)/);
  assert.match(deniedUiTest, /assertEquals\(1, retries\)/);
});

test("one retry clears stale denial state before the recovered flow emits", () => {
  assert.match(viewModel, /isLoading = true,\s*error = null,\s*directoryLoadFailed = false,\s*directoryAccessDenied = false/s);
  assert.match(viewModel, /directoryAccessDenied = error is NeighborhoodDirectoryAccessDeniedException/);
  assert.match(viewModelTest, /directory authorization failure is explicit and one retry can recover/);
  assert.match(viewModelTest, /val retryRelease = CompletableDeferred<Unit>\(\)/);
  assert.match(viewModelTest, /assertTrue\(model\.uiState\.value\.isLoading\)[\s\S]*?assertFalse\(model\.uiState\.value\.directoryLoadFailed\)[\s\S]*?retryRelease\.complete\(Unit\)/);
});
