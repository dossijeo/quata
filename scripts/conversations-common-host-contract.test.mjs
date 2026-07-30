import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const androidScreen = new URL(
  "../app/src/main/java/com/quata/feature/chat/presentation/conversations/ConversationsScreen.kt",
  import.meta.url,
);
const browserHost = new URL(
  "../feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt",
  import.meta.url,
);

test("Android mounts the common conversations root", async () => {
  const source = await readFile(androidScreen, "utf8");
  assert.match(source, /ConversationsScreenHost\s*\(/);
});

test("browser and iOS list path cannot regress to the legacy fallback", async () => {
  const source = await readFile(browserHost, "utf8");
  assert.match(source, /if \(conversationId == null\)[\s\S]*ConversationsScreenHost\s*\(/);
  assert.doesNotMatch(source, /ChatBrowserConversationList|ChatConversationCreationContent/);
});
