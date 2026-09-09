# Inventario maestro de pantallas y flujos — migración Compose Multiplatform

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
>
> **Reconciliado por auditoría el 7 de septiembre de 2026.** Corte exacto de `main`:
> `efd6a805d3b8f744e4d8b2153a0b1a087e88eb53`. La auditoría fue estática/documental: no reejecutó E2E, no abrió los
> `.xcresult` físicos y no certificó la identidad del APK Android publicado.

Este documento sigue siendo la fuente de verdad del **alcance de producto** de la migración. La
reconciliación separa cuatro dimensiones que no deben confundirse:

1. **Requisito**: qué comportamiento pertenece realmente al producto de referencia.
2. **Implementación**: qué existe en código común o en un adaptador nativo.
3. **Integración**: qué PR/commit está ya en `main`.
4. **Evidencia**: qué escenario concreto fue acreditado y sobre qué SHA.

Un merge demuestra integración; una atestación declara evidencia; la lectura de código demuestra
implementación estática. Ninguno de esos hechos equivale por sí solo a una ejecución nueva sobre el
`main` actual.

## Referencia Android y trazabilidad

- Snapshot Android histórico reproducible usado por la auditoría:
  `bd8a73b03b1139024f9e0447b0d452f156267578`.
- Referencia publicada identificada el 8 de septiembre de 2026 por confirmación del propietario:
  `quata-release-v32.aab`, SHA-256 `bf6aadc60e18b05d4f4203c8356a9e9a8b4c917262cc0a1d28518b8c6baf70ff`.
  Procedencia y contrato binario: [referencia publicada v32](./ANDROID_PUBLISHED_REFERENCE_V32.md).
  No está demostrado el commit exacto de compilación ni el hash de un APK servido por Google Play.
- Cualquier “no existe en Android” debe delimitar el artefacto/snapshot y recorrido realmente
  auditados. La identificación del AAB no convierte las pruebas previas con APK debug en pruebas
  ejecutadas sobre el Android publicado.
- Deben registrarse por requisito, cuando estén disponibles:
  `androidReferenceCommit`, `androidReferenceVersionCode`, `androidReferenceAabSha256`, `androidReferenceApkSha256`,
  `productSha`, `mergePr/mergeSha`, `evidenceSha`, `evidenceKind` y `evidenceValidity`.

La versión detallada previa a esta reconciliación queda preservada por Git en el corte auditado
[`efd6a805`](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md).
Se conserva como historial de evidencia y no debe usarse como estado operativo vigente.

## Estados de lectura

- **GO**: cierre acreditado para el alcance exacto descrito. Un GO focal no cierra padres ni hermanos.
- **COMÚN con límites**: implementación común existente con casos o evidencias concretas pendientes.
- **PARCIAL**: implementación compartida incompleta o frontera todavía no reconciliada.
- **IMPLEMENTADO / evidencia pendiente**: el código común existe; falta acreditar integración/retorno/E2E.
- **UNVERIFIED**: requisito auténtico o cobertura propuesta todavía sin cierre operativo.
- **LEGACY_UNRESOLVED**: discrepancia histórica que requiere fijar referencia y decidir producto.
- **NO APLICA**: superficie deliberadamente técnica/interna.

## A–D. Inventario reconciliado de las 75 unidades

Las 75 unidades originales se mantienen. La columna “Estado tras auditoría” es el estado operativo
vigente de este documento; “Corrección/alcance” y “Límite preservado” impiden convertir una observación
documental en una orden de implementación o en un GO demasiado amplio.

### A. Principales
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `SCR-AUTH-LOGIN` | COMÚN con límites; separar acceso, expiración, logout y retorno/restauración de acción. | Separar acceso, expiración, logout y restauración de acción. | No se revalidaron sesión ni cada ruta de retorno en plataformas. | S28,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/auth/presentation/login/LoginScreen.kt) |
| `SCR-AUTH-REGISTER` | COMÚN con límites; acceso legal #241 integrado el 13/08/2026; E2E completo de registro/legal no recertificado. | Actualizar integración de legales #241; conservar las pruebas remotas pendientes. | Registro completo y legal no se cierran por compartir componentes. | S24,S11,S01 · [fuente](https://github.com/dossijeo/quata/pull/241) |
| `SCR-AUTH-RECOVERY` | GO en `main` #215; conservar el GO y añadir trazabilidad explícita del productor del secreto (`ACCOUNT-RECOVERY-SECRET`). | Vincular recuperación al alta/actualización real del secreto desde Cuenta. | No inferir disponibilidad actual del endpoint desplegado ni repetir SQL como prueba del productor. | S01,S14,S10 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `SCR-FEED` | COMÚN con límites; Live/Ranking y medición de layout existen en host común; quedan integración/evidencia y subflujos no focales. | Actualizar Live, layout y referencias a subflujos emoji; separar acciones Feed. | Sin GO visual/operativo global ni prueba nueva de todos los adaptadores. | S15,S16,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt) |
| `SCR-OFFICIAL` | COMÚN con límites; preservar apertura exacta/autor y delegar comentarios/media a sus IDs. | Conservar apertura exacta/autor y delegar comentarios/media a sus IDs. | La conexión del NavGraph no demuestra todos los estados del repositorio ni del editor. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-OFFICIAL-EDITOR` | GO en `main` #217; “adjuntos” significa imagen/vídeo en este editor; rich text focal no cierra toda la toolbar. | Sustituir «adjuntos» genérico por imagen/vídeo; no añadir PDFs/Office. | No cerrar todas las herramientas rich text por el GO focal de publicar. | S01,S04 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `SCR-COMMUNITIES` | COMÚN con límites; directorio, miembros, perfil y apertura de Chat son subrutas reales; faltan error/retorno/postflight. | Preservar directorio, miembros, perfil y apertura de chat como subrutas. | Éxito/error/retorno y back Android a miembros no se certifican aquí. | S06,S07,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `OVR-PUBLIC-PROFILE` | COMÚN con límites; biografía no es requisito de paridad sustentado; documentos proceden de adjuntos de Chat compartidos con el visitante. | Derivar estado de PROF-* y documentar adjuntos compartidos de Chat. | No convertir perfil en repositorio público de archivos ni exigir bio. | S06,S07,S12,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `SCR-CONVERSATIONS` | COMÚN con límites; `CONV-PROFILE` se reconcilia con `PROF-ENTRY`; inbox/invites/paginación/realtime conservan validación propia. | Consolidar CONV-PROFILE con PROF-ENTRY, sin crear otro visor. | No se cerraron invitaciones, búsqueda, paginación ni realtime de inbox. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-CHAT` | **COMÚN con límites.** `ChatProductHostContent`/`ChatScreenHost` se consume en Android, Wasm e iOS; conservar `CHAT-*`; ciclo de vida/entrega y push del sistema quedan separados; no declarar GO global. | Conservar CHAT-*; añadir acuses/ciclo de vida y separar push del listado. | No se revalidaron redes, permisos físicos, media ni operaciones de grupo. | S11,S17,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-NOTIFICATIONS` | GO en `main` #224 sólo para la lista interna; push del sistema y respuesta directa son flujos separados. | Añadir flujo push del sistema y respuesta directa desde notificación. | No retirar GO del listado ni extenderlo a FCM/APNs/Web Push. | S17,S18,S19,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/notifications/QuataFirebaseMessagingService.kt) |
| `SCR-ACCOUNT` | COMÚN con límites; avatar focal ya cerrado; añadir `ACCOUNT-DETAILS` y `ACCOUNT-RECOVERY-SECRET`; contraseña queda `LEGACY_UNRESOLVED`. | Reconciliar ACCOUNT-AVATAR, legales y añadir datos/recuperación. | Separar nuevo password no soportado de flujo histórico cuya escritura no está probada. | S13,S08,S14,S20,S24,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt) |
| `SCR-SOS` | COMÚN para configuración/contactos; emisión SOS se inventaría en `FLOW-SOS-DISPATCH`; agenda OS no se presume requisito de paridad. | Separar envío/recuperación de ubicación y agenda OS como alcance no trazado. | Mantener límites de persistencia/consentimiento de importados hasta decisión de producto. | S11,S08,S13,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-CREATE-POST` | COMÚN con límites reducidos; no reabrir subflujos `POST-*` ya cerrados por pendientes cruzados del padre. | Distinguir pruebas cruzadas de POST-* ya documentadas como completas. | No se verificó nuevamente cada codec/export/permutación OS. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-WHATS-NEW` | GO en `main` #222; fuera de la cola de primera migración, sólo regresión de visto/versiones. | Eliminar de cola de primera migración; mantener regresión de visto/versiones. | No hay reejecución del marcado de visto ni de catálogo. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SCR-RELEASE-HISTORY` | GO integrado por PR #240 el 12/08/2026; integración no equivale a nueva certificación E2E del `main` actual. | Registrar #240 integrada el 12/08 y distinguir SHA de producto/head/merge. | Integración no acredita automáticamente nueva evidencia E2E del main actual. | S23,S11,S01 · [fuente](https://github.com/dossijeo/quata/pull/240) |

### B. Perfil público
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `PROF-ENTRY` | GO focal; requisito único para entradas al perfil desde Feed/Official/Comunidades/Conversaciones/Chat. | Usar esta fila como dependencia única de entradas al perfil. | Las evidencias de cinco orígenes se han leído como atestación, no reejecutado. | S06,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-HEADER` | GO focal para nombre/barrio/avatar/roles/KPI; biografía retirada como bloqueo de paridad salvo decisión de producto citada. | Retirar bio del pendiente Android o clasificarla como mejora aprobable. | Nombre/barrio/avatar/roles/KPI sí tienen procedencia. | S05,S06,S12,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/domain/NeighborhoodModels.kt) |
| `PROF-FOLLOW` | GO focal; mantener follow/unfollow, contador y auth como mismo contrato; errores/rollback siguen fuera. | Mantener seguir/dejar de seguir, contador y auth como mismo contrato. | Rollback y backend negativo no se consideran cerrados. | S06,S07,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-FOLLOW-LISTS` | GO focal; listas auténticas. Validar listas largas; no exigir paginación nueva de backend sin procedencia. | Distinguir probar listas largas de exigir paginación nueva de backend. | El recorrido histórico consultado usa listas completas; no acredita inexistencia de límites en todo backend. | S06,S07,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-CONTENT` | GO focal; documentos son adjuntos privados compartidos desde Chat, no publicaciones documentales públicas. | Especificar que los attachments proceden de Chat compartido con el visitante. | La galería de posts no produce PDFs/Office. | S06,S07,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-MEDIA-DETAIL` | GO focal de media; visor documental/descarga/compartir es contrato distinto. | No reabrir media por la existencia de límites del lector documental. | No atribuir al visor de media la validación de descarga/documentos largos. | S06,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-PRIVATE-CHAT` | GO focal; conservar sesión, peer y conversación única; carreras/error remoto no recertificados. | Conservar sesión, identificación del peer y apertura de conversación única. | No se prueban carreras, error ni duplicación remota en esta auditoría. | S07,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt) |
| `PROF-ROLES` | GO focal; preservar admin/official, exclusión de perfil propio y guardas; permisos negativos pendientes. | Preservar admin/official, exclusión del perfil propio y guardas del repositorio. | Permisos negativos y errores fuera de camino feliz permanecen pendientes. | S06,S07,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `PROF-SAFETY` | GO focal; preservar reportar/bloquear, confirmación, auth y efecto de cierre; recuperación de fallo pendiente. | Mantener reportar/bloquear, confirmación, auth y efecto de cierre. | No se certifican permisos ni recuperación de fallo por plataformas. | S06,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |

### B. Conversaciones
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `CONV-INBOX` | COMÚN con límites; lista/paginación/realtime requiere postflight; repositorio actual no se trazó por completo en la auditoría. | Conservar postflight de lista/paginación/realtime. | No se trazó completamente su repositorio actual. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CONV-SEARCH-FAVORITES` | COMÚN con límites; separar búsqueda/favoritos de inbox de mensajes favoritos de Chat. | Separar búsqueda/favoritos de inbox de mensajes favoritos. | Vacío/error y persistencia de búsqueda no revalidados. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CONV-NEW` | COMÚN con límites; mantener creación, resolución de miembros y unicidad; mutaciones remotas no recertificadas. | Mantener creación, resolución de miembros y unicidad. | No se certificaron mutaciones remotas. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CONV-INVITES` | COMÚN con límites; aceptar/rechazar es caso real independiente; no se ejecutaron mutaciones en la auditoría. | Mantener aceptar/rechazar invitación como caso real independiente. | No se realizó ninguna aceptación/rechazo ni cleanup. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CONV-PROFILE` | Deriva de `PROF-ENTRY`; conservar sólo retorno profundo/errores/escenarios no incluidos en su evidencia focal. | Referenciar PROF-ENTRY y conservar solo escenarios no incluidos en su atestación. | No elevar GO por encontrar un callback: falta validar vigencia de la evidencia exacta. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |

### B. Chat
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `CHAT-MESSAGES` | COMÚN con límites; #226 (`702aad06`) cerró un foco previo de mensajes, pero acuse de entrega, reconexión y lectura requieren trazabilidad separada antes de exigir cierre global. | Añadir acuse de entrega y reconexión; trazar la lectura antes de exigir su cierre. | Paginación profunda, errores de red y permisos de borrado se mantienen. | S11,S17,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `CHAT-COMPOSER` | COMÚN con límites; teclado/respuesta/edición preservados; emoji/adjuntos enlazados; escritura local no prueba presencia remota. | Mantener teclado/respuesta/edición; enlazar emoji y adjuntos. | No interpretar estado de escritura local como presencia remota certificada. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-MESSAGE-ACTIONS` | COMÚN con límites; conservar selección/confirmaciones; permisos negativos y rollback forzado siguen abiertos. | Mantener selección/confirmaciones comunes; no inventar nuevas acciones. | Permisos negativos y rollback forzado permanecen. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-FAVORITES` | GO en `main` #219; `FavoriteMessagesConversationId` mantiene apertura al origen y alta/baja/estado vacío en Android, Wasm e iOS; evidencia no reejecutada. | Mantener apertura al origen y baja/estado vacío como alcance. | No se reejecutó la evidencia de tres plataformas. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `CHAT-NOTIFICATIONS` | COMÚN para mute/unmute; no equivale a registro/entrega/reply del push del sistema. | Separar mute/estado de inbox de registro/entrega/reply OS. | Propagación inbox y errores de mute siguen pendientes. | S18,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/notifications/NotificationFactory.kt) |
| `CHAT-FOCUSED-MESSAGE` | GO en `main` #219 para contrato común de foco; recepción del deep link/cold start/auth se valida en `FLOW-DEEP-LINKS`. | Enlazar a FLOW-DEEP-LINKS propuesto sin reabrir el scroll focal ya validado. | Cold start y sesión expirada no quedan cubiertos por un mensaje enfocado. | S11,S18,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `CHAT-ATTACHMENTS` | COMÚN con límites; Web/Wasm, Android e iOS mantienen picker/upload/playback/documento/download/share separados; evidencia 8af482e8 sigue declarada, no reejecutada. Report Web `build-reports/web/chat-actions-notifications-evidence.json`; Android `build-reports/android/chat-actions-notifications-evidence.json`; iOS `build-reports/ios/chat-attachments-audio-evidence.json`; fixtures comunes en `scripts/e2e-fixtures/chat-attachments.mjs`; selección nativa document/gallery/camera acreditada por `chat-attachment-picker-evidence-8892cea2-{document,gallery,camera}.json` y `docs/candidate-attestations/chat-attachment-picker.json`; limpieza con residuo físico cero en las evidencias declaradas. | Mantener separación de picker/upload/playback/documento/download/share. | No se verificaron nuevamente ejecución nativa ni permisos físicos; 8af482e8 y 8892cea2 son evidencias declaradas, no reejecutadas por esta auditoría. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-AUDIO` | COMÚN con límites; Web/Wasm, Android e iOS conservan invalidez de estados optimistas; 8af482e8 declaró `audioPlaybackObserved.state=playing`, reproducción consecutiva y grabación por compositor compartido con reportes `build-reports/web/chat-actions-notifications-evidence.json`, `build-reports/android/chat-actions-notifications-evidence.json` y `build-reports/ios/chat-attachments-audio-evidence.json`; permisos/grabación física/red no recertificados aquí. | No aceptar Play solicitado como Playing ni simular grabación como permiso real; mantener reproducción y grabación separadas de permisos OS/red. | Estados nativos, grabación física y red no reejecutados por esta auditoría. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-FORWARD` | GO focal; conservar picker común y reenvío real frente a fixtures; errores/cleanup no reejecutados. | Conservar picker común y reenvío real frente a fixtures. | Errores forzados y limpieza no reejecutados. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-GROUP` | COMÚN con límites; invitaciones, roles, bloqueos, salida y borrado separados; negativas/mutaciones no reejecutadas. | Separar invitaciones, roles, bloqueos, salida y borrado sin duplicar infraestructura. | No se trazaron todas las guardas negativas ni se realizaron mutaciones. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `CHAT-LOCATION-SOS` | COMÚN para recepción/representación y mapa; emisión se separa en `FLOW-SOS-DISPATCH`. | Conservar render/mapa y añadir FLOW-SOS-DISPATCH. | No declarar envío completo por mostrar una tarjeta SOS. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `CHAT-TRANSLATION` | GO focal de Chat; no equivale a captura/backdrop global de todos los orígenes. | No confundir overlay de Chat con captura/backdrop global de todos los orígenes. | No se revalidó traducción remota ni equivalencia visual. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `CHAT-PROFILE` | GO focal; usar perfil global y preservar retorno a la conversación; errores/navegación profunda siguen fuera. | Preservar retorno a conversación; no duplicar pantalla de perfil. | Errores y repetición/navegación profunda no certificados. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |

### B. Publicación
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `POST-TEXT-DESTINATION` | COMÚN con límites focales preservados; no reimplementar por fila padre abierta. | Conservar selección real wall_id y validación; no reimplementar por fila padre abierta. | Permisos y combinaciones con media siguen separados. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `POST-PICKER-CAMERA` | COMÚN con límites focales preservados; permisos OS físicos exhaustivos no recertificados. | Conservar resultados/cancelación/permiso y vídeo largo documentados. | No se verificaron todos los permisos OS físicos. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `POST-IMAGE-EDITOR` | Cierre focal preservado; crop/zoom/pan/rotación y diferencia post/avatar; export no reejecutado. | Preservar crop/zoom/pan/rotación y diferencia post/avatar. | No se ejecutó export ni comparativa visual. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `POST-VIDEO-EDITOR` | Cierre focal preservado; `CaptionDocument`/export real; codec/captions/blur no reejecutados. | Preservar contrato CaptionDocument y export real; no generar captions ficticias. | No se ejecutó codec/captions/blur en plataformas. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `POST-LOCATION` | COMÚN con límites focales preservados; no retirar permisos por inferencia; origen detallado del selector no trazado. | No retirar permisos por inferencia: Android inyecta locationService/permissionService. | Origen detallado del selector no trazado; no clasificado como requisito falso. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `POST-PUBLISH` | Cierre focal preservado; pendientes cruzados no reabren publicar/rollback; limpieza/error remoto no reejecutados. | No confundir casos cruzados pendientes con volver a implementar publicar/rollback. | No se reejecutó limpieza física ni error remoto. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |

### B. Cuenta/SOS
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `ACCOUNT-AVATAR` | GO focal 090d4fb3; retirar “avatar Web real” como carencia genérica; sólo revalidar ante diff relevante. | Eliminar «avatar Web real» como carencia genérica; conservar regresión exacta. | El contrato de evidencia no sustituye los tres informes ni su vigencia en main. | S20,S13,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/ACCOUNT_AVATAR_EVIDENCE.md) |
| `ACCOUNT-SETTINGS` | Dependencia integrada por #295 el 24/08/2026; operaciones destructivas/push real siguen excluidas. | Referenciar #295 y separar operaciones de cuenta/push excluidas. | No hay GO nuevo de desactivar/borrar/logout fallido. | S25,S22,S01 · [fuente](https://github.com/dossijeo/quata/pull/295) |
| `ACCOUNT-DEACTIVATE` | COMÚN; evidencia pendiente. Acción real histórica: confirmación con contraseña, auth repository y retorno. | Mantener confirmación con contraseña, authRepository y retorno como contrato. | Operación destructiva no ejecutada; no eliminar como requisito inventado. | S11,S08,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `ACCOUNT-DATA-DELETE` | COMÚN; evidencia pendiente. Acción real histórica `deleteAccountData`; operación destructiva no ejecutada. | Preservar deleteAccountData y errores en el inventario. | Operación destructiva no ejecutada ni cierre nuevo. | S11,S08,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `SOS-CONTACTS` | Paridad Qüata: contactos de la red y límite 5/5. Importación de agenda OS = alcance separado cuya aprobación no quedó localizada. | Conservar contactos Qüata y 5/5; exigir procedencia/aprobación para importación OS. | No borrar código de agenda ni certificar importados sin decisión de producto. | S08,S10,S13,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt) |

### C. Transversales
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `SCR-SETTINGS` | GO focal integrado por #295 el 24/08/2026; no extiende el GO a push real ni operaciones destructivas de cuenta. | Registrar #295 integrada el 24/08; preservar limitaciones de push/cuenta. | No se reejecutó evidencia; no cerrar todo el lector documental. | S25,S22,S01 · [fuente](https://github.com/dossijeo/quata/pull/295) |
| `OVR-POST-DETAIL` | COMÚN con límites; mantener foco/retorno y revisar cruce Live desde detalle enfocado. | Distinguir detalle exacto de Feed/Official y cruce con Live. | Hipótesis de Live desde detalle enfocada pendiente de prueba. | S15,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt) |
| `OVR-COMMENTS` | COMÚN con límites reducidos; deduplicar estados/rollback/visual ya cubiertos por `FLOW-EMOJI`; no promover comentarios completos a GO. | Reconciliar Feed/Official con selector 5363ed22 y cierres FLOW-EMOJI. | No extender cierres focales a todos los comentarios/orígenes. | S21,S15,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/candidate-attestations/flow-emoji-selector-states.json) |
| `OVR-MEDIA` | PARCIAL reducido; separar imagen/vídeo de documentos; reproducción de media no cierra descarga/compartir documental. | Separar imágenes/vídeos de documentos y referenciar cierres por origen. | Descarga/compartir documental no se cierra por reproducir media. | S06,S07,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `OVR-LIVE-RANKING` | IMPLEMENTADO en host común; falta comprobación de integración/evidencia/retorno. No crear otra implementación. | Cambiar a implementado en host común; auditar integración/evidencia y retorno. | No hay GO E2E; entrada desde detalle enfocado necesita prueba. | S15,S16,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt) |
| `FLOW-TRANSLATOR` | PARCIAL/COMÚN con límites; FastText/trigger preservados; captura/retorno global y comparativa por plataforma siguen abiertos. | Conservar FastText/trigger y distinguir captura/retorno global. | No se ejecutó traducción ni se cerró backdrop por plataformas. | S11,S15,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `OVR-ABOUT` | GO focal integrado por #308 el 31/08/2026; no reimplementar About/legales; lector no legal conserva límites. | Registrar #308 integrada el 31/08; no reimplementar About/legales. | Los límites de lector no legal siguen abiertos. | S26,S01 · [fuente](https://github.com/dossijeo/quata/pull/308) |
| `OVR-UGC-TERMS` | COMÚN con límites; aceptación local/sync/logout preservados; persistencia remota nativa no recertificada. | Preservar aceptación local, sync y logout; no igualar prueba local a remota. | La persistencia Android/iOS no se certificó con backend real aquí. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-LEGAL-DOCUMENTS` | GO focal integrado: #241 (13/08) y #295 (24/08); no cierra lector documental global ni permisos/descarga. | Registrar #241/#295 y separar Chat/perfil/documentos del catálogo legal. | Ni permisos OS ni descarga documental global quedan cerrados. | S24,S25,S22,S01 · [fuente](https://github.com/dossijeo/quata/pull/241) |
| `OVR-AUTH-REQUIRED` | COMÚN con límites; distinguir retorno a pantalla de restauración de la acción original; validar cualquier mejora frente al baseline. | Distinguir retorno a pantalla de restauración de la acción original. | La referencia histórica vuelve al Feed tras login; validar cambio aprobado frente al operating model. | S11,S02,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-EXTERNAL-SHARE` | GO focal candidato; revisión/CI/merge no certificados de nuevo en esta auditoría; separar incoming, selección/envío y outgoing. | Separar incoming OS share, selección/envío y outgoing share. | No se comprobaron share sheets físicos, límites por parser ni merge de esta candidata. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-COMMUNITY-CHAT` | COMÚN con límites; productor real localizado; auth/error/retorno y host actual requieren postflight. | Conservar resolución de wall/caché, auth y error/retorno. | No se ejecutó creación/apertura real ni se certificó el host actual. | S07,S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt) |
| `FLOW-DOCUMENT-VIEWER` | PARCIAL reducido; productor de documentos de perfil = adjuntos compartidos de Chat; Feed/Official excluidos; descarga/compartir y lectores nativos siguen abiertos. | Nombrar productor compartido de Chat y privacidad; mantener exclusión Feed/Official. | No probar perfil fabricando documentos en posts ni cerrar descarga/compartir. | S07,S06,S04,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt) |
| `FLOW-SHELL-NAV` | COMÚN con límites; añadir `FLOW-DEEP-LINKS` y `FLOW-CONNECTIVITY-PRESENCE` para no esconder lifecycle/enlaces bajo shell. | Añadir deep links y reconexión/presencia como contratos propios. | No se certifican cold start, sesión expirada ni todos los backstacks. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-EMOJI` | COMÚN con límites reducidos; reconciliar cierres focales (incl. 5363ed22) con `OVR-COMMENTS`; rutas no focales siguen abiertas. | Referenciar subcasos cubiertos sin reabrirlos por OVR-COMMENTS. | La atestación no valida rutas ajenas ni toda la interfaz actual. | S21,S15,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/candidate-attestations/flow-emoji-selector-states.json) |
| `FLOW-RICH-TEXT` | GO focal sólo para entrada/persistencia de body del editor oficial; no cierra toolbar/formato/enlaces globales. | Relacionar con SCR-OFFICIAL-EDITOR sin extender a formato/enlaces/toolbar. | No se trazó cada acción del editor original ni actual. | S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/docs/SCREEN_MIGRATION_INVENTORY_V2.md) |
| `FLOW-SPLASH-STARTUP` | COMÚN reducido; separar splash, Novedades, restauración de sesión y deep link; cold/warm start no reejecutado. | Separar splash, WhatsNew, restauración de sesión y deep link. | No se ejecutó arranque frío/caliente de ningún binario. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-IOS-LAYOUT` | Medición de layout de Feed ya implementada en común; quedan montaje iOS real, overrides, rotación, safe areas y teclado global. | Cambiar pendiente de implementación por comprobación de host y evidencia de rotación. | No se certificaron iPhone/iPad, safe areas ni teclado global. | S15,S01 · [fuente](https://github.com/dossijeo/quata/blob/efd6a805d3b8f744e4d8b2153a0b1a087e88eb53/feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt) |

### D. Internas/nativas
| ID | Estado tras auditoría | Corrección / alcance vigente | Límite preservado | Fuentes |
|---|---|---|---|---|
| `INT-NEIGHBORHOOD-USERS` | Parte de `SCR-COMMUNITIES`; mantener como subruta privada, no pantalla de producto independiente. | Mantener subruta de miembros dentro del directorio común. | No crear otra pantalla independiente por ser función privada. | S06,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/neighborhoods/presentation/NeighborhoodsScreen.kt) |
| `INT-RICH-TEXT-QA` | NO APLICA producto; ruta técnica de QA no crea requisito público. | Mantener QA fuera de requisitos públicos salvo aprobación explícita. | La ruta técnica existe, pero no crea un requisito de producto. | S11,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `INT-PLATFORM-PERMISSIONS` | Adaptadores nativos; catálogo debe vincularse a flujos reales y separar agenda añadida, push y SOS. | Vincular cada borde a flujos reales; separar agenda añadida y push/SOS. | No exigir sensores/APIs inexistentes en todas las plataformas ni usar no-op como éxito. | S11,S17,S01 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |

## E. Cobertura añadida o explicitada por la auditoría

Estas nueve entradas **no equivalen a nueve implementaciones ausentes**. Se añaden para que la
cobertura sea verificable y para que un fixture, un callback o una pantalla receptora no sustituyan
al productor real del flujo. `ACCOUNT-PASSWORD-LEGACY` es investigación/decisión, no autorización para
implementar un cambio de contraseña nuevo.
| ID | Estado de requisito | Origen | Contrato / aceptación | No inferir | Fuentes |
|---|---|---|---|---|---|
| `ACCOUNT-DETAILS` | GO focal · Android/Web/iOS exact-SHA | Cuenta → Mis datos | Nombre, barrio, teléfono local con prefijo conservado, carga, guardado, fallo de guardado contractual y recarga desde la superficie común `ProfileScreenHost`. Aceptación ejecutada sobre Product/Evidence SHA `bc34d1ebbb7f858526b77329ef4ab6f0d0e168b6`: modificar datos de perfiles autorizados desde UI/host real, verificar persistencia remota y lectura tras recarga, restaurar snapshot exacto y cubrir fallo de guardado sin falso éxito mediante contrato común. Manifest: `docs/candidate-attestations/account-details-parity.json`. | No cierra `ACCOUNT-RECOVERY-SECRET`, `ACCOUNT-PASSWORD-LEGACY`, avatar, SOS, logout/borrado de cuenta, cambio de prefijo, validación exhaustiva de entrada inválida ni otros subflujos de Cuenta/Perfil. Web/Wasm usa bridge localhost-only con opt-in porque Compose/Wasm no expone nodos DOM estables para el formulario canvas; no se ejecuta sobre una cuenta real sin opt-in y resuelve primero el gate UGC real cuando bloquea la shell autenticada. | S08,S13,S14 · [fuente](https://github.com/dossijeo/quata/blob/bc34d1ebbb7f858526b77329ef4ab6f0d0e168b6/feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt) |
| `ACCOUNT-RECOVERY-SECRET` | Aceptación local Android/Web/iOS verificada · certificación/integración pendientes | Cuenta → pregunta/respuesta → recuperación | Product/Evidence Android `1394c8471ff26674c402f97bd4efa6b02b44975d`; Web `88be97f9f79aac13a07aa995b3bf27f4535eb6fc`; iOS Product `8823a188462d30c72d77e540e70f4cf26fb131df`, caller/evidencia `c11cfc7eda86c1b1e090908494a943110cd683c9`. Productor real, lectura sin respuesta, recuperación autorizada y restitución acreditados localmente con revisión independiente. Fixtures eliminados. Evidencia y límites en `docs/recovery-secret/salvage-and-runner-design.md`. | Provisional: NO es GO integrado. Android reabre Cuenta por la entrada de evidencia; Web abre Login explícitamente tras reset; iOS usa `auth-recovery-real`, sin certificar entrada normal desde Login ni localización completa. No promueve ACCOUNT-DETAILS, padres ni vecinos. No aceptar SQL de preparación como productor ni asumir disponibilidad actual del endpoint; escritura desactivada tras los ensayos. | S08,S14,S01 · [fuente Android](https://github.com/dossijeo/quata/blob/1394c8471ff26674c402f97bd4efa6b02b44975d/app/src/main/java/com/quata/feature/auth/presentation/recovery/ForgotPasswordScreen.kt) |
| `ACCOUNT-PASSWORD-LEGACY` | LEGACY_UNRESOLVED | Cuenta → campo histórico de contraseña | Resolver discrepancia de UI y determinar si existía una escritura funcional. Aceptación: Fijar APK/commit y seguir UI → evento → repositorio → backend; registrar decisión de producto. | Es una investigación/decisión, no autorización para inventar un cambio de contraseña que el baseline leído no ejecuta. | S08,S10,S13,S14 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt) |
| `FLOW-SOS-DISPATCH` | VERIFIED_ANDROID · aceptación pendiente | Botón global SOS | Configuración reciente, permisos, emisión, rate limit/error, ausencia de ubicación y recuperación posterior. Aceptación: Entorno autorizado sin alertas a terceros: verificar destinatarios, unicidad, mensaje real y actualización posterior; cleanup. | No usar una tarjeta recibida o fixture como prueba de envío. No suponer sensores/background equivalentes en todos los OS. | S11 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-PUSH-LIFECYCLE` | VERIFIED_ANDROID · aceptación pendiente | FCM / canales equivalentes | Registro/rotación de token, sesión/destinatario, background/foreground, mensaje objetivo y baja. Aceptación: Token de prueba ligado a usuario, recepción real, enlace exacto, no duplicación visible y cambio/logout sin fuga entre cuentas. | No se deduce de la lista interna ni de un toggle. Capacidades APNs/Web Push no se certificaron aquí. | S17,S18 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/notifications/QuataFirebaseMessagingService.kt) |
| `FLOW-NOTIFICATION-REPLY` | VERIFIED_ANDROID · aceptación pendiente | Acción Reply del sistema | Respuesta desde notificación con sesión, envío, error/reintento y resultado. Aceptación: Usar acción nativa disponible, verificar el mensaje exacto y estados de notificación enviados/error; restaurar recursos. | Definir equivalencia por plataforma: no construir controles o APIs que el OS no ofrece. | S18,S19 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/notifications/NotificationFactory.kt) |
| `FLOW-DEEP-LINKS` | VERIFIED_ANDROID · aceptación pendiente | Enlace externo / notificación | Feed, Official y Chat/messageId; arranque frío/caliente, auth y consumo único. Aceptación: Abrir cada tipo con app cerrada y abierta, comprobar destino exacto, salida y enlace inexistente sin crash. | No sustituye pruebas de foco; no presupone retomar automáticamente acciones históricamente no restauradas. | S11,S18 · [fuente](https://github.com/dossijeo/quata/blob/bd8a73b03b1139024f9e0447b0d452f156267578/app/src/main/java/com/quata/core/navigation/AppNavGraph.kt) |
| `FLOW-CONNECTIVITY-PRESENCE` | GO focal · Android/Web/iOS certificado e integrado en #320 | Lifecycle y conectividad de shell | Interrupción/reposición de red, retorno a foreground, refresco sin duplicados y acuse `delivered` acreditados sobre Product/Evidence SHA `fb97462d5db02fc9ee2072e0a3d49b8d5db91331`. Certificación final Web/Android, iOS y CodeQL completada; merge `009af1b1790d73a8f9cc2ace9bf9e2de7bb85b64`. Manifest: `docs/candidate-attestations/connectivity-presence.json` (atestación local histórica). | Android conserva el límite nativo de refresco visual del chat abierto tras reconexión: `delivered` se recupera con la red y la vista al volver a foreground; Web/iOS acreditan refresco automático de red. No cierra cola offline durable, ciclo completo de lectura, sesiones expiradas ni otras rutas. Identidad del APK Android publicado no acreditada. No promociona FLOW-SHELL-NAV. | S11,S17,S15 · [PR integrada y checks finales](https://github.com/dossijeo/quata/pull/320) |
| `PROF-SHARED-CHAT-DOCUMENTS` | GO focal · Android/Web/iOS certificado e integrado en #321 | Chat compartido → perfil del interlocutor | Product/Evidence SHA `6d701eebfe2e3dde9075142900c6c50765e34004`: adjuntar PDF desde Chat real, abrirlo desde el perfil del peer en lector real y volver al mismo perfil/Chat; visitante Web sin adjuntos, error iOS genérico controlado y limpieza propia verificada. Revisión independiente y certificación final Web/Android, iOS y CodeQL aprobadas; merge `0bb46fa7c5af0e60be93fbddf8eef12c1b50be51`. Manifest: `docs/candidate-attestations/prof-shared-chat-documents.json`. | Web necesita una recarga al cambiar de actor mediante el bridge de Auth existente; Android/Web requieren limpieza explícita de sesiones Auth propias retenidas tras logout. Restricción de visitante acreditada en UI, no endurecimiento/confidencialidad backend. No cierra Auth, documentos públicos de perfil ni publicaciones Feed/Official. | S07,S06 · [fuente](https://github.com/dossijeo/quata/blob/6d701eebfe2e3dde9075142900c6c50765e34004/feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/presentation/IosNeighborhoodsHost.kt) |

## F. Integraciones reconciliadas

| Alcance | Hecho de integración | Consecuencia documental |
|---|---|---|
| `SCR-RELEASE-HISTORY` | PR #240 integrada el **12/08/2026**; merge `1bd76f9157da1b55ff04da4bf0d0dab640a3608e`. | Retirar “merge pendiente”; conservar revisión de vigencia de evidencia. |
| Registro / legales | PR #241 integrada el **13/08/2026**; merge `02d64ddfe5277e3150acacecd448aa335823c37a`. | No tratar #241 como candidata abierta. |
| `SCR-SETTINGS` / legales | PR #295 integrada el **24/08/2026**; merge `429510c93908f5d55d77fd8b4da66e8821244f1d`. | El GO focal legal/settings está integrado; push y ciclo destructivo siguen fuera. |
| `OVR-ABOUT` | PR #308 integrada el **31/08/2026**; merge `bdc03263170b4265325dd70af7f1bd83a01d8c50`. | Retirar “elevar tras merge”; no cerrar el lector documental global. |
| `FLOW-DOCUMENT-VIEWER` | PR #317 integrada el **07/09/2026** en el corte auditado. | Feed/Official no son productores de adjuntos documentales; perfil usa adjuntos compartidos de Chat. |

La integración no constituye por sí sola una nueva ejecución E2E.

## G. Reglas de dependencia y alcance

- `PROF-ENTRY` es el requisito único de entrada al perfil global. `CONV-PROFILE` y `CHAT-PROFILE`
  lo consumen; no deben crear otro visor/perfil.
- `PROF-HEADER` no exige biografía mientras no exista una decisión de producto citada y un productor
  real. Nombre, barrio, avatar, roles y KPI sí tienen procedencia.
- Los documentos visibles desde perfil proceden de **adjuntos compartidos de Chat entre visitante y
  perfil visitado**. No convierten Feed/Official ni todos los perfiles en repositorios públicos de
  documentos.
- `SCR-NOTIFICATIONS` conserva su GO para la lista interna. Registro/recepción de push y respuesta
  desde la notificación se prueban en `FLOW-PUSH-LIFECYCLE` y `FLOW-NOTIFICATION-REPLY`.
- `SOS-CONTACTS` cubre la red de usuarios Qüata. La agenda del teléfono es un alcance separado cuya
  aprobación no quedó localizada en las fuentes auditadas; no se borra código ni se presume requisito.
- Mostrar una tarjeta SOS recibida no demuestra emisión. La emisión vive en `FLOW-SOS-DISPATCH`.
- Foco dentro de Feed/Official/Chat no demuestra recepción de enlaces. Cold/warm start, auth,
  resolución y consumo único viven en `FLOW-DEEP-LINKS`.
- “Realtime” no cierra por sí solo reconexión, foreground/background, observadores, presencia ni
  `delivered`; estos casos se hacen observables en `FLOW-CONNECTIVITY-PRESENCE`.
- Un padre abierto no reabre hijos con cierre focal. Un GO focal tampoco cierra toda una familia de
  herramientas o todos los orígenes.

## H. Cola operativa derivada del inventario

1. Fijar la referencia Android publicada: commit, `versionCode`/`versionName`, SHA-256 del APK y origen
   autorizado. Hasta entonces no convertir ausencia en el snapshot en requisito falso universal.
2. Cerrar trazabilidad y aceptación de `ACCOUNT-DETAILS`, `ACCOUNT-RECOVERY-SECRET`,
   `FLOW-SOS-DISPATCH`, `FLOW-PUSH-LIFECYCLE`, `FLOW-NOTIFICATION-REPLY`, `FLOW-DEEP-LINKS`,
   `FLOW-CONNECTIVITY-PRESENCE` y `PROF-SHARED-CHAT-DOCUMENTS`.
3. Resolver `ACCOUNT-PASSWORD-LEGACY` contra la referencia publicada antes de recuperar el campo o
   crear una función nueva.
4. Revalidar sólo cuando el diff lo justifique los cierres focales ya existentes: avatar, Release
   History, Settings/legales, About, Live/layout común y estados focales de emoji/comentarios.
5. Mantener fuera de la cola de primera migración `SCR-WHATS-NEW` y cualquier otra unidad ya integrada
   cuyo único pendiente sea regresión o vigencia de evidencia.
6. No ejecutar mutaciones destructivas, SOS real, cambios de rol/cuenta o pruebas sobre datos reales
   sin entorno autorizado y restauración explícita.

## I. Plantilla obligatoria por requisito/candidata

```text
id
requirementStatus: VERIFIED_ANDROID | UNVERIFIED | APPROVED_CHANGE | LEGACY_UNRESOLVED
androidReferenceCommit
androidReferenceVersionCode
androidReferenceApkSha256
androidEntryPoint
androidProducer
androidSourcePathAndLines
scopeIncluded
scopeExcluded
implementationStatus
productSha
mergePr / mergeSha
evidenceScope
evidenceSha
evidenceKind: STATIC | UNIT | HERMETIC_UI | REAL_BACKEND | DEVICE
evidenceValidity
remainingAcceptanceCriteria
```

La evidencia demuestra el comportamiento de un requisito ya identificado; no debe crear por sí sola
un requisito de producto.

## J. Procedencia de esta reconciliación

Auditoría: `auditoria_quata_inventario.md`, `auditoria_quata_inventario.xlsx` y
`auditoria_quata_datos.json`, corte **07/09/2026**, `main`
`efd6a805d3b8f744e4d8b2153a0b1a087e88eb53`.

La auditoría revisó documentalmente las 75 unidades y produjo 20 observaciones. No compiló aplicaciones,
no ejecutó tests ni mutó backend. Los detalles de contraste, fuentes y límites permanecen en los
entregables de auditoría y en el historial archivado del inventario.
