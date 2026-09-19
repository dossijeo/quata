#!/usr/bin/env node
/**
 * Deterministic contract test for the credential-free Web Push service worker boundary.
 *
 * This does not claim delivery from a Push provider: it proves that an already-delivered
 * payload is rendered and that a notification tap is normalized to the shared chat route.
 * Provider delivery and browser subscription lifecycle stay in SB-08 E2E.
 */
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const workerPath = new URL("../web/src/wasmJsMain/resources/quata-sw.js", import.meta.url);
const source = await readFile(workerPath, "utf8");
const listeners = new Map();
const notifications = [];
const navigations = [];
let storedLocale = "en";

const self = {
  location: { origin: "https://quata.test" },
  addEventListener(type, listener) { listeners.set(type, listener); },
  registration: {
    async showNotification(title, options) { notifications.push({ title, options }); },
  },
};
const clients = {
  async matchAll() { return []; },
  async openWindow(target) { navigations.push(target); return { focus: async () => undefined }; },
};
const context = vm.createContext({
  self,
  clients,
  URL,
  Map,
  Promise,
  indexedDB: { open() {
    const request = {};
    queueMicrotask(() => {
      request.result = { transaction() { return { objectStore() { return { get() {
        const value = {};
        queueMicrotask(() => { value.result = storedLocale; value.onsuccess(); });
        return value;
      } }; } }; } };
      request.onsuccess();
    });
    return request;
  } },
  File: class File {},
  Response,
  console,
});
vm.runInContext(source, context, { filename: workerPath.pathname });

async function emit(type, value) {
  const pending = [];
  listeners.get(type)?.({ ...value, waitUntil(work) { pending.push(Promise.resolve(work)); } });
  await Promise.all(pending);
}

await emit("push", {
  data: { json: () => ({ title: "Conversation", body: "Hello", conversation_id: "sb:42", message_id: "m/7" }) },
});
assert(notifications.length === 1, "push_must_show_one_notification");
assert(notifications[0].title === "Conversation", "push_must_preserve_title");
assert(notifications[0].options.body === "Hello", "push_must_preserve_body");
assert(notifications[0].options.actions?.length === 1, "chat_notification_must_expose_one_action");
assert(notifications[0].options.actions[0].action === "reply", "chat_notification_action_must_be_reply");
assert(notifications[0].options.actions[0].title === "Reply", "chat_notification_reply_action_must_use_current_locale");

await emit("notificationclick", { action: "reply", notification: { close() {}, data: notifications[0].options.data } });
assert(navigations[0] === "https://quata.test/#chat-sb%3A42?message=m%2F7", "conversation_id_must_normalize_to_chat_hash");

await emit("notificationclick", { notification: { close() {}, data: { thread_id: "123" } } });
assert(navigations[1] === "https://quata.test/#chat-sb%3A123", "legacy_thread_id_must_normalize_to_supabase_conversation");

await emit("notificationclick", { notification: { close() {}, data: {} } });
assert(navigations[2] === "https://quata.test/", "missing_chat_target_must_fail_closed_to_root");

// Assert rendered worker notifications against Android's product strings, not
// a second copy of the worker table. Regional locale tags use the same language.
for (const [locale, resource] of [["en-US", "values"], ["es-ES", "values-es"], ["fr-FR", "values-fr"], ["fr-CA", "values-fr"]]) {
  storedLocale = locale;
  const xml = await readFile(new URL(`../app/src/main/res/${resource}/strings.xml`, import.meta.url), "utf8");
  for (const [key, name] of [["chat_voice_note", "notification_voice_note"], ["chat_attachment", "notification_attachment"], ["chat_message", "notification_new_message"]]) {
    const expected = new RegExp(`<string name="${name}">([^<]+)</string>`).exec(xml)?.[1];
    assert(Boolean(expected), "android_notification_reference_missing");
    await emit("push", { data: { json: () => ({ title: "Conversation", body_key: key, body: "provider fallback", conversation_id: "sb:42" }) } });
    assert(notifications.at(-1).options.body === expected, `notification_body_parity_${locale}_${key}`);
    const expectedReply = locale.startsWith("es") ? "Responder" : locale.startsWith("fr") ? "Répondre" : "Reply";
    assert(notifications.at(-1).options.actions[0].title === expectedReply, `notification_reply_action_parity_${locale}`);
  }
}
storedLocale = "unsupported";
await emit("push", { data: { json: () => ({ body_key: "chat_voice_note", body: "provider fallback" }) } });
assert(notifications.at(-1).options.body === "provider fallback", "unsupported_locale_preserves_provider_fallback");
await emit("push", { data: { json: () => ({ body_key: "chat_message" }) } });
assert(notifications.at(-1).options.body === "New message", "missing_fallback_uses_english");
assert(notifications.at(-1).options.actions === undefined, "notification_without_chat_target_must_not_expose_reply");

console.log("Web Push worker contract passed: rendering and normalized chat deep links.");

function assert(condition, code) {
  if (!condition) throw new Error(code);
}
