# Evidencia verificable de validacion multiplataforma

**Corte de este registro:** `d0815a55` (`origin/main`, 2026-07-25).

Este documento reconcilia evidencia ya obtenida; no ejecuta compilaciones ni
convierte documentacion de intencion en un resultado nuevo. Un SHA aparece como
integrado solo cuando es ancestro de `d0815a55`. Los enlaces de Actions apuntan
al run y al `headSha` exactos, no a una ejecucion posterior de `main`.

## Lectura de los tipos de prueba

| Tipo | Que acredita | Que no acredita |
| --- | --- | --- |
| Compilacion | Que las fuentes y el artefacto indicado compilan/enlazan para ese SHA. | Que el producto se pueda usar contra servicios reales. |
| Smoke | Que un host real arranca y realiza el recorrido local limitado descrito. | Login, permisos, datos remotos, RLS o una experiencia completa. |
| E2E | Que un recorrido usa backend/configuracion reales y limpia sus datos. | No existe evidencia verde de Web/iOS autenticado en este corte. |

## iOS: CI macOS por SHA exacto

Todos los runs siguientes terminaron con `success`. El workflow `iOS compile`
compila Kotlin/Native, enlaza el framework compartido, genera/compila el host
Swift y ejecuta XCTest. Desde `a6a11ba`, tambien crea un archive generico sin
firma; eso no es un IPA firmado ni una prueba en dispositivo fisico.

| SHA integrado | Integracion acreditada | Run exacto | Alcance adicional |
| --- | --- | --- | --- |
| `63fd8bba6f724979fdce1de18dd34433ef4db3cb` | Framework umbrella `QuataShared` | [#30160529772](https://github.com/dossijeo/quata/actions/runs/30160529772) | Compilacion, enlace y smoke XCTest del host. |
| `2dbd3098ad754bfe8e48b11a940ce47edfbcd040` | Transporte escalar Feed comun | [#30165185499](https://github.com/dossijeo/quata/actions/runs/30165185499) | Compilacion/enlace/host/XCTest; no RLS ni PostgREST E2E. |
| `4e188825c37c70ffbe5b81373d47a1ecac967f1f` | Shell Profile/SOS | [#30166431406](https://github.com/dossijeo/quata/actions/runs/30166431406) | Smoke de factory/ruta; no permisos ni ContactsUI real. |
| `c8cf7dfc118c66b0498229d1864f3fcf8eb3876f` | Catalogo localizado de capacidades | [#30168079601](https://github.com/dossijeo/quata/actions/runs/30168079601) | Compilacion/enlace/host/XCTest. |
| `ba4e3d693c7c10bf59c5fb120db5422c0e640a4c` | Watchdog y diagnostico XCTest | [#30170360900](https://github.com/dossijeo/quata/actions/runs/30170360900) | Smoke XCTest con watchdog; no funcionalidad adicional de producto. |
| `3d496f800d8c4cdb80cd30c6709292d091a9c964` | Shell Communities | [#30170903885](https://github.com/dossijeo/quata/actions/runs/30170903885) | Host/ruta autenticada estructural; no mutaciones, media, roles ni E2E. |
| `a6a11baacc2b5412e80040e54f9a90b165c4ee80` | Archive iOS sin firma | [#30171716978](https://github.com/dossijeo/quata/actions/runs/30171716978) | Ademas del smoke XCTest, archive generico y comprobacion de `QuataShared.framework`. |
| `e9e09c5a9c9deaf662ce52a78137a4d6fe6170ce` | Transporte escalar Official comun | [#30172609978](https://github.com/dossijeo/quata/actions/runs/30172609978) | Compilacion/enlace/host/XCTest; no lectura remota E2E. |

La definicion concreta de esos pasos, artefactos y limites se mantiene en
[IOS_CI.md](IOS_CI.md) y [IOS_UNSIGNED_ARCHIVE.md](IOS_UNSIGNED_ARCHIVE.md).
Los candidatos posteriores a este corte requieren su propia CI exacta antes de
integrarse; un verde de cualquiera de las filas no se hereda automaticamente.

## Web: Wasm, distribucion y smoke local

| SHA integrado | Comando/resultado registrado | Tipo y limites |
| --- | --- | --- |
| `a0d77ab9262d0732905aa577b82921777bd64486` | `:web:wasmJsBrowserDistribution`, `node scripts/web-browser-smoke.mjs` y `:app:assembleDebug` terminaron correctamente en el lote documentado. | Bundle Wasm de produccion y smoke Chrome de rutas no autenticadas. No es login, Supabase, RLS ni E2E remoto. |
| `eb41be9663f616cf6c030bd802137a5f31e421ad` | El bundle Wasm y `web-browser-smoke.mjs --docmentis` pasaron localmente; se registro inventario 35.29 MiB (13.55 MiB gzip) y metricas locales. | Smoke de importacion/create/destroy de DocMentis y observabilidad local; no licencia/telemetria/CSP de produccion, Storage autenticado ni documentos remotos. |
| `d712542b` (bundle baseline `acde140c`) | `WEB-AUTH-BROWSER-01` obtuvo una sesión real por `quata-auth-bridge`, inyectó sólo configuración pública en una copia temporal del bundle, restauró `WebAuthStorage` en Chrome, montó `#feed` y leyó el perfil autenticado desde el navegador; logout Web y revocación global pasaron. La cuenta aislada se purgó y se comprobó ausente. | E2E parcial de restauración, no automatización del formulario Compose. El runner no modifica Kotlin y se ejecutó contra el bundle ya validado `acde140c`; el bundle del SHA integrado debe volver a generarse antes de elevarlo a gate final. |

La fuente de esos resultados locales es
[WASM_WEB_VALIDATION.md](WASM_WEB_VALIDATION.md),
[WASM_BUNDLE_OBSERVABILITY.md](WASM_BUNDLE_OBSERVABILITY.md) y
[WEB_BROWSER_SMOKE.md](WEB_BROWSER_SMOKE.md). No hay un artefacto CI remoto
asociado a esos comandos en este corte; por tanto se presentan como evidencia
local registrada y no como un check de GitHub Actions.

## Android: compilacion y arranque API-37

| SHA integrado | Evidencia registrada | Tipo y limites |
| --- | --- | --- |
| `10f58f931e3f54c6103e8ba5d2de36c88de391a5` | `:app:compileDebugKotlin` y `:app:assembleDebug` pasaron. Tras reiniciar API-37, instalacion, arranque frio (`3.04 s`), `pidof` vivo y `logcat -b crash` vacio fueron observados para la reduccion de wrappers Feed. | Compilacion y smoke en emulador; no cubre todos los flujos ni atribuye el ANR previo a la migracion. |

El detalle y la delimitacion del incidente anterior estan en
[ANDROID_STARTUP_DIAGNOSTICS.md](ANDROID_STARTUP_DIAGNOSTICS.md). El resultado
de arranque posterior esta resumido en
[MULTIPLATFORM_MIGRATION_BOARD.md](MULTIPLATFORM_MIGRATION_BOARD.md); no se
conserva en el repositorio un logcat bruto reutilizable, por lo que debe
repetirse en el SHA final para la auditoria de cierre.

## E2E: estado honesto y gates faltantes

No se ha acreditado un E2E completo de Web autenticado ni de iOS contra Supabase
en este corte. `WEB-AUTH-BROWSER-01` acredita restauración de sesión Web y una
lectura autenticada en Chrome, pero no la entrada del formulario Compose ni el
resto de verticales. Los runners disponibles no sustituyen evidencia ejecutada:

| Gate pendiente | Evidencia necesaria para cerrarlo |
| --- | --- |
| Web autenticado completo | Regenerar bundle para el SHA final y automatizar el formulario Compose; después ampliar `WEB-AUTH-BROWSER-01` a rutas autenticadas/mutaciones autorizadas y conservar limpieza verificable. Ver [WEB_AUTHENTICATED_BROWSER_E2E.md](WEB_AUTHENTICATED_BROWSER_E2E.md), [SUPABASE_E2E_SB02.md](SUPABASE_E2E_SB02.md) y [SUPABASE_E2E_SB03.md](SUPABASE_E2E_SB03.md). |
| iOS funcional | XCTest/UI de rutas autenticadas y adaptadores reales (permisos, contactos, archivos/media) en simulador o dispositivo configurado. El XCTest actual solo acredita el smoke de frontera del host. |
| Push | Registro/entrega y limpieza real de Web Push/APNs; service worker o bridge por si solos no acreditan entrega. |
| Android cierre | `assembleDebug`, instalacion, arranque API-37, crash buffer y PID sobre el SHA final, especialmente despues de cada lote Android. |

Por estas ausencias, Web e iOS permanecen **parciales**. Esta tabla debe
actualizarse por lote con el SHA, enlace de run o salida local conservada, el
tipo de prueba y sus limites; nunca sustituyendo un E2E por una compilacion o
un smoke.
