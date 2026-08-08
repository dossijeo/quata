import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

async function kotlinAndSwiftFiles(dir) {
  const absolute = new URL(dir, root);
  const entries = await readdir(absolute, { withFileTypes: true });
  const files = await Promise.all(entries.map(async (entry) => {
    const child = join(dir, entry.name).replaceAll("\\", "/");
    if (entry.isDirectory()) return kotlinAndSwiftFiles(child);
    return /\.(kt|swift)$/.test(entry.name) ? [child] : [];
  }));
  return files.flat();
}

const [
  packageJson,
  inventory,
  verticalPlan,
  appNavGraph,
  androidHost,
  webHost,
  iosHost,
  chatScreenHost,
  conversationDetail,
  deepLinkFocus,
  selectedActions,
  groupManagement,
  viewModel,
] = await Promise.all([
  source("package.json"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("docs/CHAT_MULTIPLATFORM_VERTICAL_PLAN.md"),
  source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConversationDetailContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatMessageDeepLinkFocus.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatGroupManagementContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
]);

test("CHAT-COMMON-ROOT-001 is part of mandatory fast and Wave2 contracts", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/chat-common-root-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/chat-common-root-contract\.test\.mjs/);
});

test("Android, Wasm and iOS product routes mount ChatProductHostContent", () => {
  assert.match(appNavGraph, /import com\.quata\.feature\.chat\.presentation\.chat\.AndroidChatProductScreen/);
  assert.match(appNavGraph, /AndroidChatProductScreen\([\s\S]*?focusedMessageId = chatFocusedMessageId[\s\S]*?onFocusedMessageHandled = \{ chatFocusedMessageId = null \}/);
  assert.doesNotMatch(appNavGraph, /com\.quata\.feature\.chat\.presentation\.chat\.ChatScreen\b/);

  assert.match(androidHost, /fun AndroidChatProductScreen\(/);
  assert.match(androidHost, /ChatProductHostContent\(/);
  assert.match(androidHost, /conversationModel = viewModel\.commonModel/);

  assert.match(webHost, /fun WebChatHost\(/);
  assert.match(webHost, /ChatProductHostContent\(/);
  assert.match(webHost, /conversationList = \{ listModifier ->[\s\S]*?ConversationsScreenHost\(/);
  assert.match(webHost, /onOpenFavorites = \{ onOpenConversation\(AppDestinations\.FavoriteMessagesConversationId\) \}/);

  assert.match(iosHost, /fun QuataChatViewController\(dependencies: IosChatHostDependencies\)/);
  assert.match(iosHost, /ChatProductHostContent\(/);
  assert.match(iosHost, /conversationList = \{ listModifier ->[\s\S]*?ConversationsScreenHost\(/);
  assert.match(iosHost, /onOpenFavorites = \{ dependencies\.onOpenConversation\(AppDestinations\.FavoriteMessagesConversationId\) \}/);
});

test("platform product sources do not route through the legacy browser-style wrapper", async () => {
  const files = (
    await Promise.all([
      kotlinAndSwiftFiles("app/src/main/java"),
      kotlinAndSwiftFiles("web/src/wasmJsMain"),
      kotlinAndSwiftFiles("feature/chat/src/iosMain"),
      kotlinAndSwiftFiles("iosApp/iosApp"),
    ])
  ).flat();
  const offenders = [];
  for (const file of files) {
    const text = await source(file);
    if (/ChatBrowserHostContent\(/.test(text)) offenders.push(file);
  }
  assert.deepEqual(offenders, [], "legacy ChatBrowserHostContent must not be a platform product route");
});

test("common chat root owns read states, retry, history paging and one-shot focused message handling", () => {
  assert.match(chatScreenHost, /ChatProductScaffold\(/);
  assert.match(chatScreenHost, /ChatReadFailureContent\(/);
  assert.match(chatScreenHost, /model\.retryMessageLoading\(\)/);
  assert.match(chatScreenHost, /model\.loadOlderMessages\(\)/);
  assert.match(chatScreenHost, /focusedMessageId = focusedMessage\?\.id/);
  assert.match(chatScreenHost, /deepLinkRequest = ChatMessageDeepLinkRequest\.NoTarget[\s\S]*?onFocusedMessageHandled\(\)/);

  assert.match(conversationDetail, /item\(key = "chat-initial-loading"\)/);
  assert.match(conversationDetail, /item\(key = "chat-history-loading"\)/);
  assert.match(conversationDetail, /listState\.scrollToItem\(index\)/);
  assert.match(conversationDetail, /visibleItemsInfo\.any \{ item -> item\.key == focusedMessage\.composeKey\(\) \}/);
  assert.match(conversationDetail, /private const val FocusedMessageHighlightMillis = 8_000L/);
  assert.match(conversationDetail, /delay\(FocusedMessageHighlightMillis\)[\s\S]*?onFocusedMessageHandled\(\)/);
  assert.match(conversationDetail, /firstVisible <= 2 && !isLoadingOlderMessages\)[\s\S]*?onLoadOlderMessages\(\)/);
  assert.match(conversationDetail, /testTag = if \(isSelected\) "chat\.message\.\$\{message\.id\}\.selected" else "chat\.message\.\$\{message\.id\}"/);
  assert.match(conversationDetail, /if \(isSelected\) \{[\s\S]*?Box\([\s\S]*?testTag = "chat\.message\.\$\{message\.id\}\.selected"/);
  assert.match(conversationDetail, /stateDescription = if \(isSelected\) "selected" else "not selected"/);

  assert.match(deepLinkFocus, /hasMoreHistory -> ChatMessageDeepLinkRequest\.LoadingOlder/);
  assert.match(deepLinkFocus, /else -> ChatMessageDeepLinkRequest\.Unavailable/);
  assert.match(deepLinkFocus, /retryChatMessageDeepLinkRequest/);
});

test("common chat action chrome owns mute and tombstone action guards", () => {
  assert.match(groupManagement, /testTag = "chat\.menu\.options"/);
  assert.match(groupManagement, /ChatUiEvent\.ConversationMutedChanged\(conversation\?\.isMuted != true\)/);
  assert.match(groupManagement, /conversation\?\.isMuted == true\) strings\.reactivateNotifications else strings\.muteConversation/);

  for (const tag of ["copy", "reply", "forward", "edit", "report", "favorite", "delete"]) {
    assert.match(selectedActions, new RegExp(`testTag = "chat\\.action\\.${tag}"`));
  }
  assert.match(selectedActions, /if \(!message\.isDeleted\) \{[\s\S]*?chat\.action\.copy[\s\S]*?chat\.action\.reply[\s\S]*?chat\.action\.forward/);
  assert.match(selectedActions, /if \(!message\.isDeleted\) CompactIconButton\([\s\S]*?testTag = "chat\.action\.favorite"/);

  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ !it\.isLocalEcho && !it\.isDeleted \}/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ !it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}/);
});

test("SCR-CHAT inventory reflects the real common-root state without declaring final GO", () => {
  const scrChat = inventory.split(/\r?\n/).find((line) => line.startsWith("| `SCR-CHAT` |"));
  const chatFavorites = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-FAVORITES` |"));
  assert.ok(scrChat, "SCR-CHAT row must exist");
  assert.ok(chatFavorites, "CHAT-FAVORITES row must exist");
  assert.match(scrChat, /\*\*COMÚN con límites\.\*\*/);
  assert.match(scrChat, /`ChatProductHostContent`\/`ChatScreenHost` se consume en Android, Wasm e iOS/);
  assert.doesNotMatch(scrChat, /FALLBACK|PARCIAL/);
  assert.match(scrChat, /no declarar GO/);
  assert.match(chatFavorites, /FavoriteMessagesConversationId/);
  assert.match(chatFavorites, /Android, Wasm e iOS/);
  assert.match(inventory, /\| `CHAT-MESSAGES` \|[\s\S]*?Raíz común conectada para lectura/);
  assert.match(inventory, /\| `CHAT-FOCUSED-MESSAGE` \|[\s\S]*?contrato común de foco/);
  assert.doesNotMatch(inventory, /Web\/iOS aún conservan `ChatBrowserHostContent`/);

  assert.match(verticalPlan, /Android, Wasm e iOS consumen ya `ChatProductHostContent`\/`ChatScreenHost`/);
  assert.match(verticalPlan, /`SCR-CHAT` permanece \*\*COMÚN con límites\*\*/);
});
