import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const repository = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebNeighborhoodsRepository.kt", import.meta.url),
  "utf8",
);
const wasmTest = await readFile(
  new URL("../web/src/wasmJsTest/kotlin/com/quata/web/WebRepositoryReadAuthModeTest.kt", import.meta.url),
  "utf8",
);

test("WEB-PUBLIC-PROFILE-AUTH-001 keeps directory and public profiles anonymous-readable", () => {
  assert.match(
    repository,
    /WebNeighborhoodsReadOperation\.Directory,\s*WebNeighborhoodsReadOperation\.UserProfile\s*->\s*WebPostgrestAuthMode\.Public/,
  );
  assert.match(
    repository,
    /WebNeighborhoodsReadOperation\.CurrentUserAdmin\s*->\s*WebPostgrestAuthMode\.SessionRequired/,
  );
});

test("WEB-PUBLIC-PROFILE-AUTH-001 keeps the executable Wasm regression aligned", () => {
  assert.match(
    wasmTest,
    /assertEquals\(WebPostgrestAuthMode\.Public,\s*webNeighborhoodsReadAuthMode\(WebNeighborhoodsReadOperation\.UserProfile\)\)/,
  );
  assert.doesNotMatch(
    wasmTest,
    /assertEquals\(WebPostgrestAuthMode\.SessionRequired,\s*webNeighborhoodsReadAuthMode\(WebNeighborhoodsReadOperation\.UserProfile\)\)/,
  );
});
