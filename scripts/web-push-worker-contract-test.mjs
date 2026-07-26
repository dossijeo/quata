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
  indexedDB: { open() { throw new Error("not_needed_for_push_contract"); } },
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

await emit("notificationclick", { notification: { close() {}, data: notifications[0].options.data } });
assert(navigations[0] === "https://quata.test/#chat-sb%3A42?message=m%2F7", "conversation_id_must_normalize_to_chat_hash");

await emit("notificationclick", { notification: { close() {}, data: { thread_id: "123" } } });
assert(navigations[1] === "https://quata.test/#chat-sb%3A123", "legacy_thread_id_must_normalize_to_supabase_conversation");

await emit("notificationclick", { notification: { close() {}, data: {} } });
assert(navigations[2] === "https://quata.test/", "missing_chat_target_must_fail_closed_to_root");

console.log("Web Push worker contract passed: rendering and normalized chat deep links.");

function assert(condition, code) {
  if (!condition) throw new Error(code);
}
