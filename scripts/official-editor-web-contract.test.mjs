import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const webOfficialHost = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt", import.meta.url),
  "utf8",
);

test("Web Official surface exposes the shared editor action for official users", () => {
  assert.match(webOfficialHost, /fun WebOfficialHost\(/);
  assert.match(webOfficialHost, /onCreateOfficialPost: \(\) -> Unit/);
  assert.match(webOfficialHost, /canCreateOfficialPost = true/);
  assert.doesNotMatch(webOfficialHost, /canCreateOfficialPost = false/);
});

test("Official publish eligibility remains owned by commonMain state", async () => {
  const commonHost = await readFile(
    new URL("../feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialFeedScreenHost.kt", import.meta.url),
    "utf8",
  );
  assert.match(commonHost, /val canPublish = state\.currentUser\?\.isOfficial == true && slots\.canCreateOfficialPost/);
  assert.doesNotMatch(webOfficialHost, /rememberWebOfficialCreatePermission/);
});
