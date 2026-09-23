# Auditoría de cierre de la migración — 23 de septiembre de 2026

Este recibo aplica la lista de cierre del
[modelo operativo](MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md#10-cierre-de-la-migración). No añade
reglas, no renueva evidencia por un cambio documental y no amplía ningún GO más allá de sus límites.

## Resultado

- El inventario operativo conserva 75 filas y 75 identificadores únicos.
- No quedan filas `PARCIAL`, `UNVERIFIED`, `LEGACY_UNRESOLVED` o
  `IMPLEMENTADO / evidencia pendiente` en el inventario vigente.
- La [ronda funcional final del propietario](MIGRATION_OWNER_FUNCTIONAL_ROUND.md) registra PASS en
  Android, Web/Wasm e iOS Simulator, con limpieza y límites explícitos.
- Web conserva `web_login`, Web Push, activación `notificationclick`, ruta Chat exacta y el segmento
  autenticado de Reply. iOS conserva firma local, configuración, interacción de notificaciones y
  Reply en Simulator. APNs real, dispositivo físico y distribución pagada permanecen como límites
  externos aceptados, no como éxitos inferidos.
- Los cierres focales preservan Android publicado, no introducen fallbacks/no-op ni atribuyen backend
  ficticio. Las candidatas funcionales integradas conservan sus gates finales y evidencia exacta.

## Reconciliaciones finales

### FLOW-TRANSLATOR

`QuataTranslatorOverlaySource` define exactamente `Chat` y `Comments`. Los consumidores de producto
son Chat y comentarios de Feed, Official y perfil público. Los cuatro orígenes tienen recorrido
positivo acreditado en Android, Web/Wasm e iOS; por eso la composición pasa de `PARCIAL` a GO focal.
Permanecen fuera offline, fallos exhaustivos del proveedor y comparación visual exhaustiva. En el
postflight de perfil el error/retry es una rama del runner; no se presenta como fallo real observado.

### FLOW-IOS-LAYOUT

La evidencia integrada cubre rotación iPhone/iPad, offline→online, las 12 variantes del router,
seis tamaños de contenedor entre 320×1024 y 1024×768 y el borde de teclado translúcido de comentarios.
El Simulator no ofrece una API soportada para operar Split View o Stage Manager. Sus gestos y ciclo
de escena quedan como límite externo; el relayout interno sí fue ejercitado con tamaños
representativos sobre el router productivo. Los gutters de safe area permanecen como evidencia
visual; overlays/overrides y teclado global siguen pendientes. #300 sólo acredita el borde de
teclado de comentarios.

## Trabajo draft que no forma parte del cierre

- [#399](https://github.com/dossijeo/quata/pull/399) prepara una denegación RLS más estricta para
  roles. Permanece draft porque el cliente Android publicado todavía depende de la política actual.
  El modelo operativo prohíbe romper ese cliente para cerrar deuda RLS.
- [#412](https://github.com/dossijeo/quata/pull/412) prepara reconciliación de contadores y guardas de
  actor. Permanece draft porque no existe un restore point administrado verificable. Su drill lógico
  no sustituye esa salvaguarda y no autoriza el despliegue.

Ambas PR son endurecimiento posterior y conservan sus bloqueos fail-closed. No reabren el alcance
focal ya acreditado de `PROF-ROLES` o `PROF-FOLLOW`.

## Comprobaciones documentales

- Conteo estructural del tramo A–D del inventario: 75 filas, 75 IDs únicos y cero estados abiertos
  de las cuatro categorías anteriores.
- Búsqueda de consumidores del traductor: dos fuentes registradas y cuatro superficies de producto,
  todas enlazadas a evidencia vigente.
- Contratos documentales, `git diff --check` y revisión independiente se ejecutan sobre el head exacto
  de esta PR antes de promoverla.
- #431 quedó integrada mediante `f0c900790cd190cacd743101f5109b965e7fd5cf`; su árbol coincide con
  el head certificado `2af4727efb4e94009f95892ae228b0a006aff1c0`. Este cierre se publica después
  de esa integración como PR exclusivamente documental.
