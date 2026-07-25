# Auditoría de evidencia de migración KMP

**Base auditada:** `e950f045ab9d985883aa7c44dcc00489a989ab65` (`main` al reconciliar este documento).
**Método:** inspección de fuentes y configuración solamente; no se ejecutaron Gradle, emulador ni CI en este lote. Por tanto, este documento no convierte compilaciones previas en evidencia nueva.

## Requisitos con evidencia integrada

| Requisito | Evidencia verificable |
| --- | --- |
| Módulos KMP y separación de host | `settings.gradle.kts` incluye `:core`, `:designsystem`, `:web` y las once features. Sus `build.gradle.kts` declaran `commonMain`, Wasm e iOS; `:app` conserva `MainActivity` y `AppNavGraph` como host Android. |
| Sin Android en código compartido | Búsqueda `^import android\\.` bajo todos los `*/src/commonMain/**/*.kt`: **0** coincidencias. Las referencias textuales a `Context` de contratos explicitan que no se debe retener uno. |
| UI, estados y ViewModels compartidos | Existen piezas comunes comprobables, entre otras `FeedReelVideoPlaybackHostContent`, `ChatMessageBubbleContent`/slots y los contratos de `DocumentOpenService` y plataforma en `core/commonMain`. |
| Host Web real y fino | `web/src/wasmJsMain/.../Main.kt` contiene `main()`, `ComposeViewport`, composición de repositorios/adaptadores browser y routing; `web` incluye `index.html`, manifest y service worker. |
| Host iOS real y compilable por CI | `iosApp/iosApp/QuataIosApp.swift` es un `AppDelegate` UIKit que compone Auth/Feed exportados; `.github/workflows/ios-build.yml` compila 14 targets Kotlin/Native, enlaza framework, genera Xcode y ejecuta XCTest. |
| Límites de plataforma explícitos | `PlatformResult.Unsupported` aparece en bordes iOS sin host activo; Web declara explícitamente mutaciones aún no implementadas en Feed, Official, Chat y Communities. No hay evidencia de resultados falsos. |

## Pendiente interno (implementable sin cambiar el contrato externo)

| Área | Evidencia actual | Criterio para cerrarla |
| --- | --- | --- |
| Composition roots iOS | El launcher instala Auth/Feed y bootstrap Chat autenticado; otros hosts exportados siguen requiriendo composición y recorridos configurados. `IosPlatformServices` necesita cobertura funcional de cámara, archivos, audio, contactos y ubicación. | Conectar cada host iOS autenticado que falte a repositorios y contratos concretos; cubrir rutas reales, no pantallas Swift de sustitución. |
| Cobertura funcional iOS | CI prueba compilación, enlace, host Swift y XCTest de frontera; no prueba permisos, selección de contactos, Quick Look, audio o navegación autenticada. | XCTest/UI y pruebas de adaptadores con simulador, permisos y archivos locales reales. |
| Capacidades de media/documentos | Los contratos y algunos adaptadores existen, pero siguen Android la edición/exportación, renderers integrados, MediaStore/Media3 y varias rutas de caché/URI. | Extraer sólo lógica portable restante y conectar adaptadores reales desde hosts consumidores. |
| Estructura objetivo del host Android | El proyecto conserva el módulo `:app`, no `androidApp/`. Es compatible con la validación solicitada (`:app`), pero no coincide literalmente con el árbol objetivo. | Decidir una migración de nombre/host separada y de bajo riesgo; no renombrar masivamente durante la extracción de features. |
| Inventario operativo | `MULTIPLATFORM_MIGRATION_BOARD.md` declara foto de `main` `79a8458`, aunque esta auditoría parte de `8da5bad`. También `iosApp/README.md` conserva descripciones anteriores al launcher Auth/Feed presente. | Actualizar puntualmente tablero e inventario cuando se integre cada lote; no usar esos textos desfasados como prueba de estado actual. |

## Bloqueado por configuración, backend o toolchain externos

| Bloqueo | Evidencia | Lo necesario |
| --- | --- | --- |
| E2E Web autenticado | La distribución Wasm y el smoke no autenticado ya están verificados con Kotlin `2.2.21`/Compose `1.10.0`; no prueban Supabase ni sesión. | Configuración pública, usuario/datos efímeros y recorrido browser autenticado con limpieza verificable. |
| Runtime y recorrido Auth/Feed iOS | `QuataIosApp.swift` sólo crea bootstrap con `QUATA_SUPABASE_URL` y `QUATA_SUPABASE_PUBLISHABLE_KEY`; sin ellas no instala Auth/Feed configurado. | Build settings públicos reales y una sesión de prueba autorizada, sin secretos de servicio. |
| Mutaciones/realtime Web | `WebFeedRepository`, `WebOfficialRepository`, `WebChatRepository` y `WebNeighborhoodsRepository` contienen rutas `*_not_implemented`; el tablero exige RLS/RPC/identidad verificables. | Contratos backend, RLS y endpoints aprobados; implementar los transportes browser contra esos contratos. |
| Push entregado | Existen service worker Web y bridge APNs iOS, pero faltan credenciales/registro y prueba de entrega real. | Configuración Web Push/APNs autorizada y pruebas extremo a extremo. |

## Validaciones aún necesarias para una auditoría final

1. Repetir la búsqueda de imports Android en `commonMain` sobre el commit final.
2. Compilar los módulos Wasm relevantes y regenerar distribución/smoke cuando un lote toque el host; no presentar ese smoke no autenticado como E2E remoto.
3. Ejecutar CI macOS completa y conservar el resultado de Kotlin/Native, framework, Xcode y XCTest.
4. En Windows: `:app:assembleDebug`, instalación en API-37, arranque, `logcat -b crash` y `pidof`.
5. Ejecutar flujos configurados Web/iOS sólo después de disponer de runtime, permisos y contratos backend reales. Hasta entonces Web e iOS permanecen **parciales**, no listos para declarar la migración terminada.
