# Tablero operativo de migración multiplataforma

Este tablero es la fuente persistente para despacho. El inventario mantiene la
arquitectura; este archivo mantiene estado, evidencia y dependencias.

## Reglas

- Consultar este tablero antes de crear una tarea. No reasignar una fila **Ya compartido**.
- Cada entrega vive en worktree/branch aislado y sólo es **Integrado** tras merge en `main`.
- **En revisión** no es completado: debe contener PR y validación vigente.
- Actualizar la fila al abrir PR, corregir un fallo, fusionar o descubrir un bloqueo externo.
- Tras merge verde, eliminar la rama remota y su worktree; nunca reutilizar una rama para otra feature.

## Leyenda

| Estado | Significado |
| --- | --- |
| Integrado | En `main` con evidencia de validación proporcional al lote. |
| En revisión | PR abierta; no cuenta como terminado. |
| Pendiente | Unidad válida todavía no asignada. |
| Bloqueado externo | Falta backend, credencial o contrato verificable. |
| Ya compartido | Auditado; extraer de nuevo duplicaría UI. |

## Foto de control — 2026-07-26

La ola 1 está integrada en `main` mediante PR #46, merge
`587789ff03df0c1b83baa2b6ca74babc4e4d3499`. La ola 2 permanece como candidato
`9cc84dc2a77935ae2b84a7159e435c1ca6f8f220`: sus gates locales Web/Android
están registrados, pero la CI iOS
[#30210875187](https://github.com/dossijeo/quata/actions/runs/30210875187)
sigue pendiente. Compilación, smoke y E2E se contabilizan por separado.

| Área/lote | Estado | Evidencia y siguiente acción |
| --- | --- | --- |
| CI iOS base y XCTest | Integrado | Workflow macOS compila los 14 targets Kotlin/Native, enlaza el framework y construye el host Swift; #25 añadió el XCTest de enlace. |
| Alcance CI iOS | Integrado | El workflow evita el trigger amplio de `codex/**`: valida PRs y las ramas efímeras `codex/next-*`, además de `main`/`master`. |
| Tests comunes Feed | Integrado con límite conocido | #8 cubre `RemoteFeedReadRepository` en `commonTest`. Si reaparece el fallo interno conocido de Compose JS en `wasmJsTest`, el gate de ese lote es `:feature:feed:compileKotlinWasmJs`; no se convierte ese test en un falso PASS. Es distinto del ICE de producción, ya resuelto. |
| Runner smoke Web | Integrado y verificado arquitectónicamente | El lote `a0d77ab` actualizó Kotlin/Compose a `2.2.21`/`1.10.0`; `:web:wasmJsBrowserDistribution` generó el bundle y `scripts/web-browser-smoke.mjs` pasó rutas no autenticadas. `WEB-AUTH-BROWSER-01` suma restauración autenticada real en Chrome con configuración pública temporal, lectura PostgREST desde el navegador y revocación de sesión; no suplanta la automatización del formulario Compose. Ver [WEB_AUTHENTICATED_BROWSER_E2E.md](WEB_AUTHENTICATED_BROWSER_E2E.md). |
| Servicios Web base | Integrado | Cámara MediaDevices, audio Chat, pausa de polling, documentos, caché IndexedDB, thumbnails de vídeo, recuperación Auth y lifecycle de cuenta (#5, #17, #20, #21, #23, #24). |
 | Servicios iOS base | Integrado | AVFoundation/deep links (#6), autorización real de cámara con `AVCaptureDevice`, permiso de micrófono con `AVAudioSession`, grabación/reproducción/caché de audio AVFoundation, MIME de galería y UTI Office (#14/#16), APNs bridge (#19), Quick Look (#30), Keychain con renovación de sesión (#13), transporte Auth nativo, ContactsUI, thumbnails Quick Look de documentos y thumbnails AVFoundation de vídeo locales. XCTest cubre la admisión determinista de thumbnail de vídeo (URL local, remoto/malformado, ancho, tiempo y fallback de archivo ausente) sin afirmar decodificación de un fixture. Fotos y Archivos siguen explícitamente no disponibles en `PermissionService`; el micrófono sólo autoriza y la grabación permanece en su host de audio. |
| Profile/SOS y UI compartida | Integrado | SOS/Profile (#9) y las acciones inyectables del host SOS iOS se integraron en `main`; Composer preview (#11), Chat title bar (#12), Feed media Web (#27), lista/burbuja Chat, detalle/comentarios Official y overlay fullscreen del Design System están en `commonMain` mediante slots. |
| Catálogo Auth común | Integrado | `AuthCatalog` centraliza prefijos, preguntas secretas y copy inglés/español/francés para Android, Web e iOS; las antiguas matrices Android se eliminaron y `commonTest` cubre el catálogo. |
| Playback Feed común | Integrado | `FeedReelVideoPlaybackHostContent` extrae la estructura visual y el estado de reproducción del reel a `commonMain`; Android conserva el reproductor/media y las APIs nativas como slots. |
| Hosts Web | Parcial, host real | `Main.kt` enruta Auth, Feed, Chat, Official, Notifications, Profile, Settings, Communities, Composer, WhatsNew/About y External Share. En ola 2 pasaron distribución Wasm y smoke de rutas. El preflight Chat autenticó dos cuentas y abrió hilo, pero el E2E UI quedó bloqueado porque el canvas Compose/shadow DOM no expone el textbox al DOM/AX de Chrome; no acredita envío/reply/logout y dejó cero residuos. |
| Límite de runtime Auth Web | Integrado; E2E parcial | Sin configuración pública, Auth y Profile fallan de forma explícita: alta Web `fail-closed` y Profile conserva sólo borrador local no sincronizado; con sesión/configuración Profile selecciona el gateway remoto. Login/restauración PostgREST sí tienen evidencia anterior; alta pública segura y automatización del formulario Compose siguen sin E2E. |
| Bootstrap y launcher Feed/Auth iOS | Integrado, parcial funcional | `IosFeedRuntimeBootstrap` restaura una sesión Keychain y, con `QUATA_SUPABASE_URL` y clave publicable configuradas, crea dependencias PostgREST de sólo lectura sin datos Swift de ejemplo. Si no existe sesión, el composition root instala el host Auth común con el transporte iOS y, tras login, vuelve a instalar Feed. |
| Hosts iOS y smoke tests | Parcial | Los hosts están cableados, pero ola 2 sólo puede declararse candidata hasta que termine #30210875187. APNs acredita plumbing, no entrega. External Share acredita archive sin firma, no App Group firmado ni dispositivo físico. El Mac virtual con Xcode 16 es incompatible con las bibliotecas Kotlin/Native generadas para Xcode 26, por lo que no sustituye la CI. |

## PRs activas — no integradas

| PR | Lote | Estado actual | Dependencia o siguiente acción |
| --- | --- | --- | --- |
| #3 `codex/security-hardening` | Endurecimiento de seguridad existente | En revisión (externa) | PR draft ajena al flujo de migración; no reutilizar ni borrar sin autorización explícita. |

## Lotes en desarrollo — sin PR

No hay lotes de migración activos sin PR en la foto de control. Los cinco lotes iOS
de Auth transport, launcher autenticado, ContactsUI, thumbnails Quick Look y smoke
tests ya están integrados en `main`; las próximas tareas deben abrir una rama nueva
en vez de reutilizar sus ramas efímeras.

## Dependencias de integración activas

| Grupo | Orden seguro |
| --- | --- |
| Feed y composición iOS | El transporte de lectura, `IosFeedRuntimeBootstrap`, transporte Auth y launcher autenticado están integrados. El bootstrap restaura Keychain y el launcher presenta Auth o Feed según la sesión; la siguiente evidencia necesaria es un recorrido configurado extremo a extremo, sin datos Swift de ejemplo. |
| Share | El Share Target Web y la UI External Share común están integrados; revisar su interacción con el host y almacenamiento del navegador al validar flujos reales. |
| Plataforma Web | Composer, Communities y WhatsNew/About ya enrutan desde `Main.kt`. Bundle Wasm de producción y smoke no autenticado verificados; coordinar rutas nuevas para evitar solapes y mantener las pruebas autenticadas separadas. |
| Hosts iOS | Los exports por feature, Auth/Feed launcher, ContactsUI y thumbnails existen. Las dependencias se siguen componiendo desde el launcher y se deben probar con permisos/configuración reales; no sustituirlas por datos de ejemplo Swift. |

## Auditoría de features

| Feature | Estado | Límite real restante |
| --- | --- | --- |
| Feed | Parcial | Dominio/estado, tarjeta de metadata, overlay de reel, controles de vídeo y host común de playback ya son comunes con slots de media/acciones/navegación. El bootstrap y launcher Auth/Feed iOS están integrados; faltan mutaciones backend verificables y validación extremo a extremo con runtime configurado. |
| Chat | Parcial | Estados, ViewModels y UI estructural son comunes; Web tiene lectura/polling y mutaciones aprobadas. El intento E2E del candidato `1d604ab3` llegó a login/hilo, pero quedó bloqueado en `open_chat_a` por falta de textbox DOM/AX. No acredita envío/reply/logout UI; la purga comprobó cero residuos. |
| Profile/SOS | Parcial integrado | Web usa gateway remoto sólo con sesión y configuración pública; en caso contrario muestra borrador local no sincronizado. Compilación Wasm ola 2 pasó, pero el E2E remoto sigue condicionado por RLS/permisos. iOS conserva los límites de ContactsUI/avatar/mutaciones. |
| Communities | Parcial | Listados, miembros, KPI, comentarios, emoji, ranking, paneles y hosts Web/iOS integrados. En Web el panel de comentarios se mantiene correctamente de solo lectura: `WebNeighborhoodsHost` recibe sólo el texto (`onSubmitComment: (String) -> Unit`) y agrega los comentarios de todos los posts del perfil, por lo que no puede identificar el `postId` que exige `community_comments`. Además, `WebPostgrestClient` expone sólo `GET` y `WebFeedRepository.addComment` declara explícitamente `web_feed_mutation_not_implemented`. Habilitar el botón actual inventaría persistencia o publicaría contra un post arbitrario. Hace falta un contrato común por post y un transporte browser/RLS revisado; mientras tanto el host debe conservar `commentsEnabled = false`. Persisten también media, URI, audio y navegación específica de plataforma. |
| Official | Parcial | Dominio, listado/detalle, entrada de comentarios, editor y host iOS integrados. Quedan media/avatar/HTML/URI/navegación y mutaciones backend. |
| Post Composer | En revisión iOS | Formularios, preview textual, controles de vídeo, previews estructurales y ruta/host Web e iOS integrados. La vertical iOS en revisión usa el selector UIKit de galería y captura de foto ya inyectados, conserva el retorno al Feed y expone `ios_composer_publication_not_implemented` hasta tener mutaciones/RLS/E2E; no afirma cámara de vídeo, preview/editor bitmap/vídeo, exportación ni publicación. |
| Settings/Auth/WhatsNew/Notifications | Parcial | El catálogo Auth es común, el transporte/launcher Auth iOS están integrados y los hosts iOS inyectables de Settings/Auth/WhatsNew y Notifications existen; Web enruta Auth, Settings, Notifications y WhatsNew/About. Faltan configuración real de runtime y credenciales/pruebas de push. |
| External Share | Parcial | Política/payload común, Android, host iOS inyectable y Share Target Web están integrados. El archive iOS sin firma sólo valida estructura: falta App Group/provisioning real y prueba en dispositivo físico. |

## Adaptadores y hosts

| Capacidad | Android | Web | iOS | Límite/siguiente acción |
| --- | --- | --- | --- | --- |
| Cámara/galería | Real | MediaDevices y metadata de navegador integrados | Cámara, PHPicker/UTI y selector de documentos existen como adaptadores | Completar edición/exportación y conectar los adaptadores desde hosts que los necesiten. |
| Audio | Real | Grabador/reproductor de navegador disponibles; Chat recibe reproductor | Grabación, reproducción y caché AVFoundation integradas | Falta cobertura de URI/caché inyectada y pruebas de host reales. |
| Vídeo | Media3/Android | Thumbnails de navegador integrados | Thumbnails AVFoundation de archivos locales integradas | Faltan edición/exportación y pruebas de medios reales. |
| Documentos | Real | Reader/open seguro y visor DocMentis 0.7.9 integrado para PDF/DOCX/PPTX/XLSX; RTF y formatos Office legacy conservan descarga con URL normalizada (sin `javascript:`, `data:` ni Blob de otro origen). DocMentis sin licencia requiere verificación online, telemetría, comprobación de actualización y puede pedir Google Fonts; desplegar sólo con revisión legal/CSP | Quick Look, tipos Office y thumbnails locales integrados | Faltan renderer/thumbnails multiplataforma completos y pruebas de archivos reales. |
| Sesión | Existente | localStorage/lifecycle integrado | Keychain renovable, transporte Auth y launcher Feed/Auth integrados | Requiere configuración runtime y recorrido extremo a extremo real. |
| Notificaciones | FCM/canales por auditar | Web Push y service worker integrados según Supabase; el inbox común puede mostrar que falta runtime o que la entrega sigue sin verificar | Bridge APNs/permiso/deep links como adaptadores; el inbox común recibe estado/copy/acción de permiso inyectable | Requiere credenciales, registro desde el host y pruebas de entrega reales; ningún notice declara push habilitado. |
| Navegación host | MainActivity/AppNavGraph | Launcher enruta los hosts migrados, incluido Composer, Communities, WhatsNew/About y External Share | Composition root UIKit instala Feed restaurado o Auth si no hay sesión | Completar pruebas autenticadas iOS y pruebas de rutas Web, sin duplicar UI. |

## Backlog de la evaluación 2026-07-25

Fuente: evaluación no rastreada `docs/MULTIPLATFORM_MIGRATION_ASSESSMENT_2026-07-25.md`,
auditada en `bd8a73b` y reconciliada el 2026-07-25 contra `main` `5e22835`. No
reabre DocMentis ni la política Android de apertura de documentos ya integradas.
Cada fila distingue que un target compile de que la capacidad llegue al usuario.

| ID | Prioridad | Unidad y alcance | Dependencias / bloqueo | Estado | Criterio de salida y evidencia requerida |
| --- | --- | --- | --- | --- | --- |
| MP-A01 | P0 | Crear `:ios-shared` como framework umbrella y devolver Feed a sus capas inferiores. | MP-A05 mantiene enlace/XCTest. | Integrado | `94efb7b`: `QuataShared.framework` es el framework único; Feed ya no exporta features hermanas. CI iOS #30160529772 verde (Kotlin, link, Xcode y XCTest/UI), además de Android/Wasm. |
| MP-A02 | P0 | Matriz versionada de capacidades por feature y plataforma: `compila`, `exportada`, `compuesta`, `navegable`, `backend_real`, `e2e`; UI Web etiqueta origen local/no disponible sin ocultar rutas cableadas. | Basarse en evidencia, no inferir backend por compilación. | Integrado | `a40ae20`: `FeatureCapabilityManifest` v1 y registry Web distinguen Real/Local/Unsupported. E2E continúa falso hasta SB-01..SB-07. |
| MP-A03 | P1 | Sustituir `WebProfileRepository` local por gateway remoto; preferencias sólo como borrador offline explícito y nunca como confirmación remota. | SB-06 requiere entorno/RLS y datos efímeros; no pooler, service-role ni cambios de esquema. | Integrado arquitectónicamente; E2E bloqueado externo | `09df13f`: lectura/patch remotos requieren sesión y confirman filas; el error RLS queda visible. Sigue pendiente SB-06 con snapshot/restauración/limpieza. |
| MP-A04 | P1 | Shell/navegación iOS por verticales: Notifications/Official, después Profile/SOS; luego Communities, Composer, Settings, WhatsNew y External Share sin datos Swift de ejemplo. | Runtime/sesión pública para repositorios reales; push/dispositivo bloqueado por credenciales (SB-08). | En revisión | `259b5ea` integra Notifications/Official; `4e18882` integra Profile/SOS. `3d496f8` integra Communities como ruta interna autenticada con lectura PostgREST/URLSession y repositorio Chat real; #30170903885 es CI exacta verde (Kotlin, framework, Swift y XCTest) usando watchdog de simulador. La rama Composer en revisión compone una ruta interna autenticada con galería/cámara UIKit existentes y error explícito de publicación, sin deep link público, post ficticio, media/vídeo/exportación o E2E declarados. |
| MP-A05 | P0 | CI iOS reproducible por SHA: asociar Kotlin/link/Xcode/XCTest al commit exacto, seleccionar simulador y no reutilizar verde de otro SHA. Integrar sólo tras revisar ramas retenidas `codex/next-ios-document-host-tests` y `codex/next-ios-video-thumbnail-host-tests`; evaluar en paralelo `codex/next-ios-ci-concurrency-policy`. | GitHub Actions/macOS externo. Ramas retenidas no cuentan integradas mientras su CI no sea verde sobre SHA rebasado. | Parcial integrada | `a6a11ba` añadió el carril posterior a XCTest para archive genérico de dispositivo sin firma. CI exacta `#30171716978` verde para ese SHA: Kotlin/Native, enlace, XCFramework, Xcode, XCTest y archive; conserva artefactos descargables. No acredita IPA, provisioning, firma, distribución ni dispositivo físico. Las ramas de documentos/vídeo retenidas siguen pendientes de su propia CI exacta. |
| MP-A06 | P1 | Localización común y reparación de mojibake: inventariar cadenas, adoptar recursos Compose o contrato común incremental y corregir archivos con `Ãƒ` real, preservando UTF-8. | MP-A02 debe mostrar etiquetas localizadas. Sin conversión masiva ni cambio de copy funcional sin revisión. | Parcial integrada | `c8cf7df` limita el primer catálogo común a los avisos de capacidad: ES/EN por etiqueta de idioma, fallback español y pruebas comunes. `:core:wasmJsTest`, compilación Web/Android, distribución+smoke Web y CI iOS exacta `#30168079601` verdes. No altera otros textos ni corrige archivos por la codificación de consola; el inventario/corrección del resto sigue pendiente. |
| MP-A07 | P1 | Reducir warnings Wasm/Compose y medir bundle: encapsular `ExperimentalWasmJsInterop`, corregir deprecaciones, presupuesto decreciente y análisis de Skiko/icons/visor/features. Superficie: `wasmJsMain`, Gradle y `scripts/`. | No ocultar warnings ni retirar DocMentis. Gates: `wasmJsBrowserDistribution` y smoke real. | En revisión | Fase de observabilidad: `:web:wasmJsBrowserDistribution --no-daemon --console=plain --stacktrace` terminó localmente en 5m41s y produjo `productionExecutable`; hitos Kotlin optimize/webpack separados en el log. Informe reproducible: 35.29 MiB (13.55 MiB gzip), DocMentis 18.77 MiB emitidos, Skiko 8.24 MiB y Quata 5.58 MiB. `wasm-bundle-baseline.json` aporta ahora inventario/hash exacto de un artefacto local como candidato `proposed`; `--budget` informa sus regresiones sin bloquear hasta certificarse el SHA y aprobarse. Aún faltan opt-ins/deprecaciones y smoke real sobre el SHA integrado. |
| MP-A08 | P2 | Decidir `js(IR)` frente a Wasm: producto soportado con host/pruebas/publicación propios, o retirada gradual de targets/adaptadores JS sin afectar Wasm. Superficie: Gradle, `jsMain`, CI e inventario. | MP-A07 aporta coste; decisión de producto si JS se conserva. | Retirada gradual iniciada | Ola 2 retiró únicamente `js(IR)` de `:feature:whatsnew`; quedan 12 módulos. Bundle/smoke Wasm y Android están verdes localmente; la CI iOS exacta #30210875187 permanece pendiente, por lo que MP-A08 no está cerrado. |
| MP-A09 | P2 | Convention plugins y métricas: extraer configuración KMP/Compose repetida a `build-logic`; automatizar source sets, imports Android en `commonMain`, `not_implemented`, features conectadas y Android desplazado. | MP-A01 y MP-A08 estabilizan grafo/targets. No contar lector Office vendorizado como código propio migrable. | Parcial integrada | Piloto incremental sin drift; informe por SHA vinculado al tablero; métrica distingue extraído de adoptado/eliminado y no declara completitud por líneas. `scripts/multiplatform-metrics.ps1` emite JSON/Markdown de solo lectura, excluye lector vendorizado/build/dependencias y no reescribe inventario ni tablero; falta adoptar más módulos al convention plugin y acordar baseline/gate. |

### Nota de avance MP-A09: piloto de convencion

La rama `codex/next-kmp-convention-plugin-scaffold` incorpora `build-logic` y
aplica `quata.kmp-compose-feature` solamente a `:feature:settings`. La
convencion conserva JS IR, Wasm y los tres targets iOS; el modulo conserva su
target Android, source sets y dependencias. Antes de ampliar el rollout requiere
comparacion de targets/dependencias y gates Android, Wasm e iOS.

## Cola reconciliada de la auditoría 2026-07-25

Las tareas MP-A01, MP-A02 y la parte arquitectónica de MP-A03 ya se cerraron
después del SHA auditado; no deben volver a despacharse. Esta cola convierte los
huecos que la auditoría identifica, pero que no estaban suficientemente acotados,
en entregas pequeñas y ordenadas. Cada una actualiza la matriz de capacidades y
el inventario sólo con evidencia del SHA validado.

| ID | Prioridad | Entrega acotada | Dependencia | Estado | Salida verificable |
| --- | --- | --- | --- | --- | --- |
| MP-A10 | P1 | Reducir duplicación Android en una única vertical ya adoptada (Feed o Profile): inventariar UI/estado común realmente consumido por `:app`, retirar sólo la implementación desplazada y añadir regresión. | MP-A09 métricas; compile/assemble y emulador API-37. | Integrada | `ac9f6e0`: Feed retiró cuatro wrappers privados sin llamadas (`ReelScrims`, `ReelTopChips`, `ReelRoundChip`, `ReelChip`) que sólo redirigían a componentes comunes. Media3, Coil, recursos, navegación y Lifecycle permanecen Android. `:app:compileDebugKotlin` y `:app:assembleDebug` pasaron; tras reiniciar API-37, instalación/arranque frío tardó 3,04 s, mantuvo PID y el buffer crash quedó vacío. El ANR previo se reprodujo también en baseline bajo presión de CPU y no se atribuye a esta migración. |
| MP-A11 | P1 | Convergencia de transporte en una vertical remota (Feed u Official): mover wire models, mapping, errores, paginación y caso de uso a `commonMain`; mantener HTTP/fetch/URLSession y secretos en adaptadores. | SB-01 confirma contratos; no cambios RLS/DDL. | Parcial integrada | `2dbd309`: el mapeo escalar Feed a DTO es común en `FEED_TRANSPORT_CONVERGENCE.md`, preservando los errores de respuesta incompleta propios de Web/iOS. `e9e09c5` aplica el mismo límite a Official: el vocabulario y mapeo escalar PostgREST viven en `OfficialRemoteProtocol`, mientras JSON/Foundation, fetch/URLSession, endpoints y errores HTTP/RLS permanecen en los adaptadores Web/iOS. Sus puertas locales Official/Web/Android y la CI iOS exacta #30172609978 pasaron (Kotlin, framework/XCFramework, Swift, XCTest y archive sin firma). Los `select`, mutaciones y errores HTTP/RLS no equivalentes esperan SB-01/SB-03; E2E sigue pendiente de entorno. |
| MP-A12 | P1 | Cerrar la vertical iOS siguiente a Official/Notifications: Profile/SOS, con ruta autenticada, back/deep link y estados de error desde el shell; sin datos Swift de ejemplo. | MP-A04 Official/Notifications integrado; permisos/contactos reales se prueban aparte. | Integrado, límites explícitos | `4e18882`: Profile/SOS se exporta por `QuataShared`, reutiliza Keychain y el gateway PostgREST de lectura, y la factory queda diferida hasta haber sesión/configuración. CI iOS exacta #30166431406 verde (Kotlin, framework, Xcode, XCTest/UI); XCTest cubre cola/factory de ruta. No existe deep link público Profile/SOS; las mutaciones, RLS/E2E, avatar y resolución agenda→perfil continúan fuera del alcance verificado. |
| MP-A13 | P1 | Cerrar la fase 2 Web Wasm: aplicar presupuesto revisable de bundle, medir arranque/memoria en navegador y resolver deprecaciones restantes sin suprimir warnings globalmente. | MP-A07 fase 1 integrada; no retirar DocMentis por tamaño sin decisión de producto. | Parcial, evidencia local | En `eb41be9`, `:web:wasmJsBrowserDistribution --no-daemon --console=plain` y el smoke Chrome real con DocMentis pasaron. El inventario emitido fue 35,29 MiB (13,55 MiB gzip; DocMentis 18,77 MiB, Skiko 8,24 MiB, app 5,58 MiB). El informe local del mismo SHA mide Auth frío 1.026 ms y heap JS 7,94 MiB en Chrome 150; no es SLO móvil. El candidato versionado actual mide 35,23 MiB/13,54 MiB y conserva DocMentis 18,77 MiB; el gate compara en modo informativo mientras `wasm-bundle-budget.json` sea `proposed`, y bloqueará sólo crecimiento acordado al aprobar baseline de SHA certificado. Siguen pendientes runner controlado, baseline aprobado y los avisos Gradle de resolución durante configuración. |
| MP-A14 | P1 | Reconciliar documentación operativa con checks: generar/actualizar evidencia de SHA para iOS, Wasm y hosts, y marcar explícitamente qué es smoke frente a E2E. | MP-A05 y cada CI exacta. | Reconciliado; cierre condicionado | `docs/mp-a14-final-evidence.json` separa ola 1 integrada (`587789ff`, PR #46), ola 2 candidata (`9cc84dc2`), compilación, smoke y E2E. Web/Android local están documentados; #30210875187 sigue pendiente. No se declara migración completa. |

### Validación Android de arranque (API-37)

El ANR observado antes de integrar MP-A10 se reprodujo también sobre el baseline
cuando el emulador estaba bajo presión de CPU; no hubo evidencia que lo atribuyera
a los wrappers Feed. Tras reiniciar API-37, la validación exacta de `ac9f6e0`
instaló el APK, inició `com.quata/.MainActivity` en frío en 3,04 s, conservó el
PID tras diez segundos y dejó vacío el buffer `crash`. Esta evidencia valida el
corte; no convierte la observación previa de rendimiento del emulador en un
problema funcional ni elimina WorkManager o DI.

Para la ola 2 se ejecutó A/B sobre el mismo API-37: ola 1 tardó 25,392 s y
ola 2 21,159 s, ambas sin crash ni ANR. Se clasifica
`environment_both_slow`; no es una mejora de rendimiento acreditada. El APK
ola 2 mide 79.029.367 bytes, SHA-256
`3317E295A9BB14F38AF845EB52B30D225F2B21A62A439DA5AC90754A836B2979`.

## Unidades pendientes verificables

| Prioridad | Unidad | Estado | Archivos o superficie de control | Criterio de salida |
| --- | --- | --- | --- | --- |
| P0 | E2E configurado Auth/Feed iOS | Pendiente de entorno | `iosApp/iosApp/QuataIosApp.swift`, `feature/feed/src/iosMain/**/IosFeedRuntimeBootstrap.kt` | Login/restauración → Feed contra URL y clave publicable de runtime, con cuenta efímera y sin datos Swift de ejemplo. |
| P0 | Pruebas funcionales de hosts iOS | Pendiente de entorno macOS | `iosApp/iosAppTests/`, `IosProfileSosHost.kt`, adaptadores ContactsUI/Quick Look/AVFoundation | XCTest/UI con permisos, selección de contacto y archivo local reales; el smoke de arranque actual no basta. |
| P0 | E2E navegador autenticado | Parcial verificado | `web/src/wasmJsMain/**/Main.kt`, `scripts/run-web-authenticated-browser-e2e.ps1` | `WEB-AUTH-BROWSER-01` usó una cuenta aislada creada/purgada externamente: bridge de login, restauración exacta de `WebAuthStorage`, `#feed`, lectura autenticada desde Chrome, logout Web y revocación global pasaron. Falta automatizar el formulario Compose y ampliar rutas/mutaciones. |
| P1 | Mutaciones y tiempo real Web | Bloqueado externo | `WebFeedRepository.kt`, `WebOfficialRepository.kt`, `WebChatRepository.kt` | Contratos RPC/RLS/identidad aprobados y pruebas con dos usuarios; no implementar un transporte manual inseguro. |
| P1 | Comentarios Communities Web por post | Bloqueado externo | `WebNeighborhoodsHost.kt`, `WebPostgrestClient.kt`, contrato `community_comments` | Acción común que reciba `postId`, POST RLS revisado y E2E crear/listar/borrar; conservar `commentsEnabled = false` hasta entonces. |
| P1 | Media/documentos multiplataforma restantes | Pendiente | `core` contratos de media/documentos y hosts consumidores Android/Web/iOS | Edición/exportación, renderer/miniaturas y pruebas de archivos reales; no recrear adaptadores AVFoundation ya existentes. |
| P1 | Visor Office integrado Web con DocMentis | Integrado; validación funcional pendiente | `web/`, interop Wasm/JS y host de documentos Web | `@docmentis/udoc-viewer` 0.7.9 se carga perezosamente para PDF/DOCX/PPTX/XLSX y conserva descarga segura para RTF/legacy/error. La ruta de verificación incluye bundle Wasm de producción, `wasmJsTest` y smoke browser de importación dinámica/create/destroy; aún hace falta adjuntar evidencia de su ejecución sobre el commit integrado. Sigue pendiente E2E con documento propio, CORS y Storage autenticado; no se declara Web E2E autenticado. Antes de despliegue, aprobar runtime Wasm/licencia, permiso online por apertura, telemetría, actualización y fuentes externas. |
| P1 | Push entregado | Bloqueado externo | `supabase/WEB_PUSH_INTEGRATION.md`, service worker Web, bridge APNs/FCM | Credenciales, suscripción/dispositivo y entrega con deep link; revocación comprobada y sin tokens en repo. |
| P2 | Auditoría final | Pendiente | Inventario, `commonMain`, Gradle/CI y emulador API-37 | Imports Android, JS/Wasm, CI iOS, ensamblado/instalación/arranque Android y evidencia requisito por requisito. |

## Política de ramas

1. Programación: worktree `codex/*`, sin Gradle redundante por agente.
2. Integración: instantánea congelada; correcciones sólo en la misma rama.
3. Publicación: PR a `main`; CI iOS se dispara en PR y también en `main`/ramas `codex/next-*` según el workflow vigente.
4. Merge: sólo después de checks verdes proporcionales al lote; no hay excepción activa para el ICE Wasm de producción, ya resuelto.
5. Limpieza: tras merge verde, eliminar branch remota y worktree; una rama nunca se reutiliza para otra feature.

## Carril de validación Supabase

La validación contra Supabase es un trabajo independiente de la extracción de
código: puede ejecutarse en paralelo y no autoriza cambios de esquema,
migraciones, tablas, funciones ni `db push`. Usa exclusivamente usuarios y datos
efímeros identificables, una transacción de limpieza y una comprobación final de
que no quedan filas creadas por el lote. No registrar secretos, URLs de conexión
ni tokens en este repositorio, logs o informes.

| Área de prueba | Estado | Alcance y evidencia requerida |
| --- | --- | --- |
| Conectividad y catálogo de contratos | Pendiente | Consulta de solo lectura para confirmar tablas, RPC y políticas que consumen los repositorios KMP; registrar nombres de contrato y resultado, nunca credenciales. |
| Auth y restauración de sesión | Pendiente | Crear usuario de prueba, login/logout/restauración desde los adaptadores Web/iOS cuando el runtime esté configurado y borrar el usuario/datos dependientes. |
| Feed y Official de lectura | Runner preparado; pendiente de entorno | `scripts/run-supabase-e2e-sb03.ps1` valida lectura PostgREST autenticada/pública y forma de deep link sobre filas efímeras ya aprobadas. No existe un endpoint Web seguro para crear/borrar estos posts: el runner registra esa precondición en vez de simularla. |
| Chat y adjuntos | Pendiente | Dos usuarios efímeros, conversación, mensaje y adjunto permitido; comprobar transporte KMP, descarga sin cabeceras privilegiadas y limpieza de objeto/filas. |
| Profile/SOS | Pendiente | Perfil y hasta cinco contactos SOS efímeros, prueba de patch/normalización y restauración del estado previo o borrado completo. |
| Communities/comentarios | Pendiente | Validar el contrato por `postId`, RLS y mutación antes de habilitar Web; crear y eliminar comentario de prueba en la misma transacción lógica. |
| Notificaciones/push | Bloqueado externo | Las credenciales de APNs/Web Push/FCM y el dispositivo registrado son necesarios; mientras falten, sólo validar el repositorio y deep links con datos efímeros. |

Al cerrar una fila se debe anotar: commit validado, fecha, adaptador/plataforma,
casos ejecutados, identificadores efímeros ya eliminados y resultado de la
verificación de limpieza. Un fallo abre una unidad de corrección separada; no se
oculta como una prueba satisfactoria.

### Cola E2E delegable

Cada lote se ejecuta en una rama o commit ya compilado y usa un prefijo único
`e2e-<fecha>-<lote>` para usuario, post, mensaje, objeto de Storage y cualquier
otro dato creado. Un agente de pruebas no cambia DDL, migraciones, funciones,
políticas ni configuración remota; su único permiso mutante es crear y borrar
los datos de su propio prefijo. Antes de cerrar el lote debe consultar que no
quedan filas ni objetos con ese prefijo.

| ID | Prerrequisito | Recorrido E2E concreto | Estado | Evidencia de cierre |
| --- | --- | --- | --- | --- |
| SB-01 | URL de pooler y exactamente una CA PEM explícita y confiable presentes en el entorno del proceso | Ejecutar `scripts/run-supabase-e2e-sb01.ps1`: catálogo PostgreSQL/Storage y RPC de adaptadores KMP, en transacción `READ ONLY`, sin datos de negocio ni secretos | **Integrado/verificado (2026-07-26):** informe local seguro con `missing: []` para relaciones, RPC y bucket; PostgreSQL 17.6; TLS `verify-full` con CA de Supabase. No ejecutó DDL, DML, RPC ni consultas de datos. El runner conserva fail-closed y acepta opcionalmente `-DbUrlFile` para no exponer la URL en la consola. El workflow manual `#30194306847` sobre `cdb1ff42` pasó en 19 s usando secretos de GitHub y eliminó el informe al terminar. | Informe local sin secretos, listas `missing` vacías, commit y fecha; guía en `docs/SUPABASE_E2E_SB01.md` |
| SB-02 | Cuenta efímera ya aprovisionada por el flujo autorizado; URL y clave publicable en entorno | Ejecutar `scripts/run-supabase-e2e-sb02.ps1 -AllowExistingTestUser`: login Web bridge, forma de persistencia en memoria, refresh, logout Web, login posterior y revocación global final como limpieza | **Integrado/verificado (2026-07-26):** cuenta aislada creada únicamente para el lote, login Web/refresh/logout/login posterior y revocación global pasaron; el informe declara `sessions_revoked`. La cuenta de prueba y su usuario Auth se purgaron tras la ejecución con comprobación de ausencia. | Informe seguro, `sessions_revoked` y borrado de la cuenta anotado por quien la aprovisionó; guía en `docs/SUPABASE_E2E_SB02.md`. `-CreateUser` rechaza el lote porque Web aún no expone un alta pública segura |
| SB-03 | SB-02 verde, dos filas aisladas ya aprovisionadas y contrato de visibilidad explícito | Ejecutar `scripts/run-supabase-e2e-sb03.ps1 -AllowExistingTestData`: Feed/Official autenticado, lectura publishable visible o denegada por RLS según contrato y forma de deep link | **Integrado/verificado (2026-07-26):** Feed y Official leyeron sus filas efímeras como usuario autenticado y con clave publishable sin bearer; ambas políticas devolvieron `visible` y los fragmentos `#post-…`/`#official-…` fueron válidos. Se revocaron sesiones y se purgaron/comprobaron ausentes perfil, wall y posts del lote. | Informe seguro, sesiones revocadas y limpieza de las filas anotada por quien las aprovisionó; guía en `docs/SUPABASE_E2E_SB03.md`. No crea ni borra posts porque falta endpoint Web seguro revisado. |
| SB-04 | Dos usuarios efímeros preaprovisionados, SB-02 verde y purga externa autorizada de ambas cuentas | Ejecutar `scripts/run-supabase-e2e-sb04.ps1 -AllowExistingTestData -AllowChatMutation`: hilo privado, inbox/detail, mensaje/reply, read y mute/unmute con JWT de cada cuenta | **Integrado/verificado (2026-07-26):** dos identidades aisladas completaron hilo privado, inbox/detalle, mensaje/reply, read y mute/unmute con JWT público. El runner efectuó cleanup lógico y revocó sesiones; después se ejecutó la purga dura autorizada, eliminando hilos por creador, usuarios Auth y perfiles, con ausencia comprobada. | Informe sin secretos; borrado lógico solicitado, sesiones revocadas y purga externa de cuentas/datos Chat confirmada. Guía: `docs/SUPABASE_E2E_SB04.md`. Sin contrato de purga exacto aborta antes de red/mutación. |
| SB-05 | SB-04, dos cuentas aisladas exclusivas y purga autorizada por ciclo de vida de cuenta | Ejecutar `scripts/run-supabase-e2e-sb05.ps1 -AllowExistingTestData -AllowChatAttachmentMutation`: Blob no sensible, subida `chat-attachments`, registro `file_ids`, lectura/descarga por el segundo JWT y borrado del objeto | **Integrado/verificado (2026-07-26):** blob de texto inocuo subido, registrado y enlazado al mensaje; metadata/descarga de par y contrato de lectura pública pasaron. El runner borró el objeto y negó la descarga posterior; la purga dura autorizada eliminó los dos perfiles y filas `chat_attachments`, verificadas ausentes. | Informe seguro confirma borrado de objeto y denegación posterior; la purga autorizada debe eliminar filas `chat_attachments` y anotar su verificación posterior. Guía: `docs/SUPABASE_E2E_SB05.md`. Sin doble opt-in aborta antes de red/mutación. |
| SB-06 | Perfil aislado sin contactos SOS previos y entre uno y cinco perfiles candidatos efímeros visibles | Ejecutar `scripts/run-supabase-e2e-sb06.ps1 -AllowProfileMutation -AllowSosContactMutation`: proyección `ProfileRemoteGateway`, patch/lectura de `display_name`, normalización KMP y posiciones SOS | **Integrado/verificado (2026-07-26):** proyección Profile, patch/readback/restauración exacta de `display_name`, dos candidatos SOS y normalización de posiciones pasaron. El conjunto creado quedó vacío/verificado, sesión revocada y perfil/candidatos efímeros purgados. | `display_name` previo restaurado exactamente, conjunto SOS creado borrado/verificado vacío y sesión revocada; guía: `docs/SUPABASE_E2E_SB06.md`. Sin snapshot, conjunto SOS inicial vacío y acknowledgements exactos aborta antes de red/mutación. |
| SB-07 | Dos usuarios aislados, comunidad/post preaprovisionados y purga externa autorizada | Ejecutar `scripts/run-supabase-e2e-sb07.ps1 -AllowExistingTestData -AllowCommunityMutation`: validar post/membresía, crear/listar/borrar comentario y reacción emoji, y comprobar que el segundo JWT no borra el comentario del actor | **Bloqueado por seguridad (2026-07-26):** el outsider borró el comentario del actor; el runner produjo `rls_violation`. El fixture aislado se purgó y verificó sin perfiles/wall/post residuales. No se cambia RLS mientras la web publicada dependa de las políticas actuales; requiere corrección coordinada antes de reintentar. | Evidencia de fallo seguro y purga. No se declara ranking remoto: no existe una ruta persistente consumida por `SupabaseCommunityApi`. |
| SB-08 | Credenciales push y cliente/dispositivo real | Registrar y revocar suscripción; entregar notificación y comprobar deep link de Chat normalizado | Bloqueado externo (diagnóstico 2026-07-26) | El endpoint VAPID público y CORS están desplegados; el contrato del worker acredita la normalización de tap sin red. Faltan cuenta aislada/configuración pública de cliente, proveedor Push operativo y, para APNs, firma/entitlement/proveedor/dispositivo. Ver `SUPABASE_E2E_SB08.md`. |
| SB-09 | Dos usuarios Official aislados, post efímero y purga autorizada | Crear like propio, intentar suplantación/borrado ajeno y verificar purga | **Bloqueado por seguridad (2026-07-26):** RLS-002; el JWT de A pudo insertar con `profile_id` de B. Las mutaciones Official permanecen `fail-closed`; no se cambió RLS y se verificó cero residuos. | Repetir sólo tras una corrección coordinada compatible con la Web publicada. Ver `docs/RLS_FINDINGS.md`. |

### Reconciliación de evidencia E2E (2026-07-26)

Las entradas históricas SB-01 a SB-06 de la tabla anterior quedan sustituidas
por esta evidencia ejecutada y con purga comprobada: SB-01 catálogo TLS y CI
manual #30194306847; SB-02 sesión Web; SB-03 Feed/Official y deep links;
SB-04 Chat de dos usuarios; SB-05 adjuntos/Storage; SB-06 Profile/SOS. Cada
lote usó identidades/filas efímeras, revocó sesiones y confirmó la eliminación
de sus propios datos. SB-07 y SB-09 no están verdes: RLS-001 y RLS-002 están
en `docs/RLS_FINDINGS.md`; ambas superficies mutantes permanecen bloqueadas
sin modificar las políticas existentes.

La asignación respeta dependencias: SB-01 abre SB-02; SB-02 abre SB-03 y
SB-04; SB-04 abre SB-05. SB-06 y SB-07 pueden ejecutarse en paralelo una vez
confirmado el catálogo. Ningún resultado de compilación sustituye estas pruebas
de integración real.
