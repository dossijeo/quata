# Contrato AX/DOM Web (WEB-TEST-001)

## Alcance del primer slice

Quata Web usa Compose Multiplatform sobre Kotlin/Wasm. `ComposeViewport` pinta la
aplicación dentro de un canvas; por tanto los controles Compose no son, hoy, inputs
o botones HTML que Playwright pueda localizar por `getByRole()` o `getByLabel()`.
El canvas se monta además bajo el árbol que administra Compose. No se debe concluir
que una etiqueta visual o un `OutlinedTextField` es automatizable por DOM.

Este slice fija dos contratos complementarios y sin cambiar flujos de producto:

1. **Semántica Compose (AX futuro / tests Compose):** los controles críticos tienen
   `testTag` estable. Es el origen de verdad para la intención de accesibilidad y
   para pruebas Compose cuando el runtime exponga esa semántica al navegador.
2. **DOM de observación Playwright:** `quata-test-contract` tiene un shadow root
   abierto con marcadores `data-testid`, todos `aria-hidden` y sin handlers. Indica
   que el runtime arrancó y qué superficie/ruta Compose está activa. No duplica
   controles ni degrada a lectores de pantalla.

El script `web-test-contract.js` usa sólo estado de observación y nunca contiene
secretos, tokens, datos de Supabase ni lógica de negocio.

## Selectores versionados (v1)

Los selectores DOM se consultan atravesando el shadow root del host:

```js
const contract = page.locator('quata-test-contract').locator('[data-testid="web-test-contract"]');
```

Playwright atraviesa shadow roots abiertos con sus locators CSS; una alternativa
portable es `page.locator('quata-test-contract').evaluate(host => host.shadowRoot.querySelector(...))`.
Los nombres de contrato son:

| Propósito | Compose `testTag` | Marcador DOM `data-testid` |
| --- | --- | --- |
| Teléfono login | `auth.phone` | `auth-phone-input` |
| Contraseña login | `auth.password` | `auth-password-input` |
| Enviar login | `auth.submit` | `auth-submit` |
| Recuperación | `auth.forgot-password` | `auth-forgot-password` |
| Registro | `auth.register` | `auth-register` |
| Actualizar chats | `chat.refresh` | `chat-refresh` |
| Nuevo chat | `chat.new-conversation` | `chat-new-conversation` |
| Campo mensaje | `chat.message` | `chat-message-input` |
| Enviar mensaje | `chat.send` | `chat-send` |
| Volver a chats | `chat.back` | `chat-back` |

El host expone `data-contract-version="1"`; aumentar la versión ante cambios
incompatibles. `web-test-contract` publica `data-surface` (`auth` o
`authenticated`) y `data-route` (la ruta/hash solicitada: `login`, `feed`, `chat`,
etc.). El smoke sin sesión real puede informar una ruta protegida con superficie
`auth`; no lo interpreta como acceso autenticado a ese host.

## Qué acredita Playwright ahora

Playwright puede comprobar que la distribución se carga, que no hay excepciones,
que Compose actualiza la superficie/ruta y que los marcadores v1 existen. Esto hace
robustos los waits de arranque y routing del smoke, sin depender de píxeles ni de
copy localizado.

Sigue bloqueado el E2E real de teclear, clicar Enviar, seleccionar mensaje, responder
y cerrar sesión *a través de la UI Compose*. Los marcadores DOM no son proxies de
interacción y los tests no deben hacerles click, `fill` ni modificar su estado. Ese
bloqueo sólo se levanta si Compose/Wasm materializa su árbol semántico como DOM
interactivo (o si se aprueba una integración HTML accesible que mantenga una única
fuente de interacción).

Referencias de plataforma: [ComposeViewport renderiza sobre canvas](https://kotlinlang.org/docs/multiplatform/compose-css-styles.html)
y [estado Web/Wasm de Compose](https://kotlinlang.org/docs/multiplatform/faq.html).
