/* Web Push worker for Quata. It contains no credentials or VAPID material. */
const LOCALE_DB = "quata-web";
const LOCALE_STORE = "settings";
const LOCALE_KEY = "notification-locale";
const notificationBodies = {
  en: { chat_voice_note: "Voice note", chat_attachment: "Attachment", chat_message: "New message" },
  es: { chat_voice_note: "Nota de voz", chat_attachment: "Adjunto", chat_message: "Nuevo mensaje" },
};

self.addEventListener("message", (event) => {
  const data = event.data;
  if (data?.type === "quata:set-notification-locale" && typeof data.locale === "string") {
    event.waitUntil(writeLocale(data.locale));
  }
});

self.addEventListener("push", (event) => {
  const payload = readPushPayload(event);
  event.waitUntil((async () => {
    const body = await localizedNotificationBody(payload.body_key, payload.body);
    await self.registration.showNotification(payload.title || "Quata", {
      body,
      tag: payload.message_id ? `chat:${payload.message_id}` : undefined,
      data: payload,
    });
  })());
});

// The worker cannot read the web-session token from localStorage. An open launcher can, so it
// performs the authenticated idempotent subscribe operation after receiving this signal. A later
// launcher startup also performs that reconciliation when no window was open at rotation time.
self.addEventListener("pushsubscriptionchange", (event) => {
  event.waitUntil(notifyOpenClients({ type: "quata:push-subscription-change" }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = chatNotificationTarget(event.notification.data);
  event.waitUntil(openOrFocusQuataWindow(target));
});

function readPushPayload(event) {
  try { return event.data?.json() ?? {}; } catch (_) { return {}; }
}

function chatNotificationTarget(payload) {
  // Android maps legacy thread-only payloads to the shared Supabase conversation id too.
  const conversationId = payload?.conversation_id ||
    (payload?.thread_id ? `sb:${payload.thread_id}` : null);
  if (!conversationId) return new URL("/", self.location.origin).href;
  const message = payload?.message_id ? `?message=${encodeURIComponent(payload.message_id)}` : "";
  return new URL(`/#chat-${encodeURIComponent(conversationId)}${message}`, self.location.origin).href;
}

async function openOrFocusQuataWindow(target) {
  const windows = await clients.matchAll({ type: "window", includeUncontrolled: true });
  const existing = windows.find((client) => new URL(client.url).origin === self.location.origin);
  if (existing) {
    const navigated = typeof existing.navigate === "function" ? await existing.navigate(target) : existing;
    return (navigated || existing).focus();
  }
  return clients.openWindow(target);
}

async function notifyOpenClients(message) {
  const windows = await clients.matchAll({ type: "window", includeUncontrolled: true });
  windows.forEach((client) => client.postMessage(message));
}

async function localizedNotificationBody(bodyKey, fallback) {
  if (!bodyKey) return fallback || "";
  const locale = (await readLocale()).split("-")[0].toLowerCase();
  return notificationBodies[locale]?.[bodyKey] || fallback || notificationBodies.en[bodyKey] || "";
}

function openLocaleDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(LOCALE_DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(LOCALE_STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function readLocale() {
  try {
    const database = await openLocaleDatabase();
    return await new Promise((resolve, reject) => {
      const request = database.transaction(LOCALE_STORE, "readonly").objectStore(LOCALE_STORE).get(LOCALE_KEY);
      request.onsuccess = () => resolve(request.result || "en");
      request.onerror = () => reject(request.error);
    });
  } catch (_) { return "en"; }
}

async function writeLocale(locale) {
  const database = await openLocaleDatabase();
  await new Promise((resolve, reject) => {
    const transaction = database.transaction(LOCALE_STORE, "readwrite");
    transaction.objectStore(LOCALE_STORE).put(locale, LOCALE_KEY);
    transaction.oncomplete = resolve;
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
