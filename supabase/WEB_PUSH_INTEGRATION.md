# Integracion de autenticacion web y Web Push

Esta especificacion describe el flujo exclusivo para clientes web de Quata. Los
clientes Android existentes no deben cambiar: siguen usando `action=login`, FCM,
`quata_register_push_token` y `quata_unregister_push_token`.

## Endpoints y claves publicas

- Supabase: `https://yrrlankpwmhluexshxnw.supabase.co`
- Login: `/functions/v1/quata-auth-bridge`
- Web Push: `/functions/v1/quata-web-push`
- API key publica: la clave publishable/anon configurada para el cliente.
- VAPID publica: puede obtenerse con `GET /functions/v1/quata-web-push`.

La clave privada VAPID y la service-role nunca deben incluirse en el cliente.

## 1. Identificador de la instancia web

Cada instalacion de navegador debe crear una vez un UUID y conservarlo en
almacenamiento local:

```ts
const CLIENT_INSTANCE_KEY = "quata_web_client_instance_id";

function webClientInstanceId(): string {
  const existing = localStorage.getItem(CLIENT_INSTANCE_KEY);
  if (existing) return existing;
  const created = crypto.randomUUID();
  localStorage.setItem(CLIENT_INSTANCE_KEY, created);
  return created;
}
```

Este identificador separa navegadores sin afectar a las sesiones Android. Un
nuevo login en la misma instancia rota su token web, pero no revoca otras
instancias web ni tokens FCM.

## 2. Login web

El cliente web debe usar `action: "web_login"`. No debe reutilizar
`action: "login"` para registrar Web Push.

```ts
const response = await fetch(
  `${SUPABASE_URL}/functions/v1/quata-auth-bridge`,
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: SUPABASE_PUBLISHABLE_KEY,
    },
    body: JSON.stringify({
      action: "web_login",
      country_code: "+240",
      phone_local: "680242607",
      password,
      client_instance_id: webClientInstanceId(),
    }),
  },
);

if (!response.ok) throw new Error("No se pudo iniciar sesion");
const login = await response.json();
```

La respuesta conserva los campos historicos y anade los exclusivos de web:

```json
{
  "profile": {},
  "session": {
    "access_token": "...",
    "refresh_token": "...",
    "expires_at": 0
  },
  "user": {},
  "client_type": "web",
  "web_session": {
    "id": "uuid",
    "token": "token-opaco"
  }
}
```

El cliente debe:

1. Entregar `session.access_token` y `session.refresh_token` a Supabase JS con
   `supabase.auth.setSession(...)`.
2. Persistir `web_session.token` junto a la sesion web.
3. Refrescar normalmente el access token con Supabase JS. El token opaco de la
   sesion web no cambia durante el refresh.

Un login web no modifica la password interna de Auth ni revoca refresh tokens
Android.

## 3. Service worker y suscripcion

Web Push requiere HTTPS, salvo `localhost`, permiso de notificaciones y un
service worker.

```ts
function base64UrlToUint8Array(value: string): Uint8Array {
  const padding = "=".repeat((4 - (value.length % 4)) % 4);
  const base64 = (value + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

const registration = await navigator.serviceWorker.register("/quata-sw.js");
const permission = await Notification.requestPermission();
if (permission !== "granted") throw new Error("Permiso de notificaciones denegado");

const keyResponse = await fetch(
  `${SUPABASE_URL}/functions/v1/quata-web-push`,
);
const { public_key: publicKey } = await keyResponse.json();

const subscription =
  (await registration.pushManager.getSubscription()) ??
  (await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToUint8Array(publicKey),
  }));
```

## 4. Registrar la suscripcion en Supabase

Todas las operaciones Web Push autenticadas requieren simultaneamente:

- `Authorization: Bearer <access_token vigente>`
- `x-quata-web-session: <web_session.token>`
- La API key publica en `apikey`

```ts
const response = await fetch(
  `${SUPABASE_URL}/functions/v1/quata-web-push`,
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: SUPABASE_PUBLISHABLE_KEY,
      Authorization: `Bearer ${accessToken}`,
      "x-quata-web-session": webSessionToken,
    },
    body: JSON.stringify({
      action: "subscribe",
      subscription: subscription.toJSON(),
    }),
  },
);

if (!response.ok) throw new Error("No se pudo registrar Web Push");
```

La operacion es idempotente. Si el navegador rota el endpoint o sus claves,
debe enviar de nuevo la suscripcion.

## 5. Recibir y mostrar la notificacion

El payload de chat contiene:

```json
{
  "type": "chat_message",
  "title": "Conversacion",
  "body": "Texto o fallback",
  "body_key": "chat_voice_note",
  "thread_id": "123",
  "conversation_id": "sb:123",
  "message_id": "456",
  "recipient_profile_id": "uuid"
}
```

`body_key` puede ser `chat_voice_note`, `chat_attachment`, `chat_message` o
estar vacio si `body` ya contiene texto. El service worker debe resolver esas
claves con los recursos localizados del idioma de la interfaz. Como un service
worker no puede leer `localStorage`, el cliente debe guardar el idioma actual
en IndexedDB o enviarlo al worker mediante `postMessage`.

Ejemplo minimo de `quata-sw.js`:

```js
self.addEventListener("push", (event) => {
  const payload = event.data?.json() ?? {};
  event.waitUntil((async () => {
    const body = await localizedNotificationBody(payload.body_key, payload.body);
    await self.registration.showNotification(payload.title || "Quata", {
      body,
      tag: `chat:${payload.message_id}`,
      data: payload,
      icon: "/icons/notification.png",
      badge: "/icons/notification-badge.png",
    });
  })());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const threadId = event.notification.data?.thread_id;
  const target = new URL(`/chats/${threadId}`, self.location.origin).href;
  event.waitUntil(clients.openWindow(target));
});
```

## 6. Baja de una suscripcion

Para desactivar solamente el endpoint actual:

```ts
await fetch(`${SUPABASE_URL}/functions/v1/quata-web-push`, {
  method: "POST",
  headers: authenticatedWebPushHeaders(),
  body: JSON.stringify({
    action: "unsubscribe",
    subscription: { endpoint: subscription.endpoint },
  }),
});
await subscription.unsubscribe();
```

## 7. Logout web

El orden recomendado es:

1. Llamar a `action: "logout"` mientras el access token sigue vigente.
2. Ejecutar `PushSubscription.unsubscribe()` en el navegador.
3. Cerrar la sesion Supabase solo localmente.
4. Borrar `web_session.token`, pero conservar el `client_instance_id`.

```ts
await fetch(`${SUPABASE_URL}/functions/v1/quata-web-push`, {
  method: "POST",
  headers: authenticatedWebPushHeaders(),
  body: JSON.stringify({ action: "logout" }),
});

await subscription?.unsubscribe();
await supabase.auth.signOut({ scope: "local" });
localStorage.removeItem("quata_web_session_token");
```

No debe usarse un logout global de Supabase: podria revocar otras sesiones del
mismo usuario, incluidas las de Android.

## Comportamiento multidispositivo

- Cada FCM Android activo recibe la notificacion.
- Cada suscripcion Web Push activa recibe la notificacion.
- Registrar un dispositivo nuevo no invalida ninguno anterior.
- El logout Android invalida solo el FCM enviado a
  `quata_unregister_push_token`.
- El logout web revoca solo su `web_client_session` y sus suscripciones.
- Firebase o el proveedor Web Push pueden marcar un endpoint como inexistente;
  el backend deja de reintentar esos endpoints permanentemente invalidos.

