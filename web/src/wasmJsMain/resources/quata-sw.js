/* Web Push worker for Quata. It contains no credentials or VAPID material. */
const LOCALE_DB = "quata-web";
const LOCALE_STORE = "settings";
const LOCALE_KEY = "notification-locale";
const INCOMING_SHARE_STORE = "incoming-shares";
const SHARE_TARGET_PATH = "/share-target";
// Mirrors WebIncomingShareTargetContract. The worker cannot import the Kotlin/Wasm module, so
// this boundary keeps the byte/file limits explicit while the launcher tests the same contract.
const MAX_SHARED_FILES = 8;
const MAX_SHARED_FILE_BYTES = 25 * 1024 * 1024;
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

// Web Share Target requests arrive at the worker, not at the Kotlin/Wasm launcher. Persist their
// FormData (including Blob files) first, then redirect to a stable hash route the launcher reads.
self.addEventListener("fetch", (event) => {
  const requestUrl = new URL(event.request.url);
  if (event.request.method === "POST" && requestUrl.origin === self.location.origin && requestUrl.pathname === SHARE_TARGET_PATH) {
    event.respondWith(receiveIncomingShare(event.request));
  }
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

async function receiveIncomingShare(request) {
  try {
    const form = await request.formData();
    const text = [form.get("title"), form.get("text"), form.get("url")]
      .filter((value) => typeof value === "string" && value.trim())
      .join("\n")
      .trim();
    const files = form.getAll("files").filter((value) => typeof File !== "undefined" && value instanceof File);
    if ((!text && files.length === 0) || files.length > MAX_SHARED_FILES || files.some((file) => file.size > MAX_SHARED_FILE_BYTES)) {
      return Response.redirect(new URL("/#share-target-error", self.location.origin), 303);
    }
    await writeIncomingShare({
      id: `share-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      text,
      createdAt: Date.now(),
      attachments: files.map((file) => ({
        blob: file,
        name: file.name || "attachment",
        mimeType: file.type || null,
      })),
    });
    await notifyOpenClients({ type: "quata:incoming-share" });
    return Response.redirect(new URL("/#share-target", self.location.origin), 303);
  } catch (_) {
    return Response.redirect(new URL("/#share-target-error", self.location.origin), 303);
  }
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
    const request = indexedDB.open(LOCALE_DB, 2);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(LOCALE_STORE)) database.createObjectStore(LOCALE_STORE);
      if (!database.objectStoreNames.contains(INCOMING_SHARE_STORE)) database.createObjectStore(INCOMING_SHARE_STORE, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function writeIncomingShare(payload) {
  const database = await openLocaleDatabase();
  await new Promise((resolve, reject) => {
    const transaction = database.transaction(INCOMING_SHARE_STORE, "readwrite");
    transaction.objectStore(INCOMING_SHARE_STORE).put(payload);
    transaction.oncomplete = resolve;
    transaction.onerror = reject;
    transaction.onabort = reject;
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
