import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const main = await readFile(
  new URL("../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt", import.meta.url),
  "utf8",
);

test("Web navigation persistence follows the current Compose navigation state", () => {
  assert.match(
    main,
    /val navigationState = navigation\.state\s+LaunchedEffect\(navigationState, runtimeConfiguration\.isBackendConfigured\)[\s\S]*?putString\("web\.navigation\.route", navigationState\.route\)[\s\S]*?navigationState\.chatConversationId/,
  );
});
