import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = async (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

const [destinations, policy, android, web, ios, inventory] = await Promise.all([
  source("core/src/commonMain/kotlin/com/quata/core/navigation/AppDestinations.kt"),
  source("core/src/commonMain/kotlin/com/quata/core/navigation/ShellNavigationPolicy.kt"),
  source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt"),
  source("iosApp/iosApp/QuataIosApp.swift"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
]);

const declaredDestinations = [...destinations.matchAll(/data object (\w+) : AppDestinations\("([^"]+)"\)/g)]
  .map(([, name, route]) => ({ name, route }));

const expectedDestinations = [
  ["Login", "login"],
  ["Register", "register"],
  ["ForgotPassword", "forgot_password"],
  ["Feed", "feed"],
  ["Neighborhoods", "neighborhoods"],
  ["Official", "official"],
  ["OfficialPostEditor", "official/editor"],
  ["RichTextEditorQa", "debug/rich_text_editor_qa"],
  ["CreatePost", "create_post"],
  ["Conversations", "conversations"],
  ["Chat", "chat/{conversationId}"],
  ["Notifications", "notifications"],
  ["WhatsNew", "whats_new"],
  ["About", "about"],
  ["ReleaseHistory", "release_history"],
  ["Profile", "profile"],
  ["UserProfile", "user_profile/{userId}"],
].map(([name, route]) => ({ name, route }));

test("the finite common destination catalog is explicit and complete", () => {
  assert.deepEqual(declaredDestinations, expectedDestinations);
  assert.match(policy, /else -> QuataShellRouteAccess\.Private/);
  assert.match(policy, /route\.substringBefore\('\/'\)/);
});

test("Android registers every common destination or its owning nested host", () => {
  const navHostDestinations = [
    "Login", "Register", "ForgotPassword", "Feed", "Neighborhoods", "Official",
    "OfficialPostEditor", "RichTextEditorQa", "CreatePost", "Conversations", "Chat",
    "Notifications", "ReleaseHistory", "Profile",
  ];
  navHostDestinations.forEach((name) => assert.match(android, new RegExp(`composable\\([\\s\\S]{0,80}AppDestinations\\.${name}\\.route`), name));
  assert.match(android, /StartupDestination\.WhatsNew[\s\S]*WhatsNewScreen/);
  assert.match(android, /onLogoClick = \{ isAboutDialogOpen = true \}/);
  assert.match(android, /key = "global_user_profile"/);
});

test("Web maps every product route family and keeps exact nested identifiers", () => {
  [
    "auth", "feed", "communities", "official", "official-editor", "post", "chat",
    "notifications", "profile", "composer", "settings", "whats-new", "about", "release-history",
  ].forEach((fragment) => assert.ok(web.includes(`\"${fragment}\"`), fragment));
  assert.match(web, /chatConversationId: String\?/);
  assert.match(web, /chatMessageId: String\?/);
  assert.match(web, /officialPostId: String\?/);
  assert.match(web, /postId: String\?/);
  assert.match(web, /quataChatUrl\(conversationId, messageId\)/);
});

test("iOS has a closed PendingRoute catalog with symmetric persistence", () => {
  const routeBlock = ios.match(/enum PendingRoute: Equatable \{([\s\S]*?)\n\s*var appDestinationRoute:/)?.[1];
  assert.ok(routeBlock, "PendingRoute declaration missing");
  const cases = [...routeBlock.matchAll(/case (\w+)/g)].map((match) => match[1]);
  assert.deepEqual(cases, [
    "feed", "chat", "official", "officialEditor", "notifications", "profileSos",
    "communities", "composer", "settings", "whatsNew", "about", "releaseHistory",
  ]);
  ["official-editor", "notifications", "composer", "settings", "whats-new", "about", "release-history"]
    .forEach((route) => {
      assert.match(ios, new RegExp(`return \"${route}\"`), `persist ${route}`);
      assert.match(ios, new RegExp(`case \"${route}\": return`), `restore ${route}`);
    });
  assert.match(ios, /persistedChatRoutePrefix = "chat-v1:"/);
  assert.match(ios, /persistedFeedPostRoutePrefix = "feed-v1:"/);
  assert.match(ios, /persistedOfficialPostRoutePrefix = "official-v1:"/);
});

test("the inventory names the finite catalog instead of an arbitrary product backstack", () => {
  const row = inventory.split("\n").find((line) => line.startsWith("| `FLOW-SHELL-NAV` |"));
  assert.ok(row, "FLOW-SHELL-NAV row missing");
  assert.match(row, /catálogo finito de destinos productivos/);
  assert.doesNotMatch(row, /backstacks secundarios arbitrarios no enumerados/);
});
