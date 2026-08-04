# Matriz de evidencia para la auditoría final KMP

> **DOCUMENTO HISTÓRICO (MP-A14).** Esta matriz conserva la procedencia de evidencias del corte
> `ea0322c1`/`d8652326`; no describe el estado consolidado actual de `main`, no define la cola y no
> autoriza cambios de backend. Para alcance vigente usar
> [`SCREEN_MIGRATION_INVENTORY_V2.md`](./SCREEN_MIGRATION_INVENTORY_V2.md); para método y gates usar
> [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md), y
> para la foto operativa usar [`MULTIPLATFORM_MIGRATION_BOARD.md`](./MULTIPLATFORM_MIGRATION_BOARD.md).
> Todas las afirmaciones RLS de este archivo son históricas y deben revalidarse contra el backend
> actual antes de cualquier diagnóstico; durante la migración no se endurecen ni despliegan políticas
> que puedan romper Android publicado o la Web antigua.

> **Actualización posterior a MP-A14 (2026-07-29):** el corte operativo es
> `d8652326f61d93f33bb860d64565ad74e3e80ed5`. PRs #99–#106 aportan evidencia
> posterior a la reconciliación histórica de abajo: Web público y Android siguen
> verdes; iOS tiene matriz pública integrada, rutas/factorías y CI
> [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607)
> verde sobre `ba6a72a`; #106 añadió logout autenticado con CI
> [`30429034347`](https://github.com/dossijeo/quata/actions/runs/30429034347)
> verde. Ninguno acredita autenticación visual final, firma, APNs,
> dispositivo físico, RLS ni migración completa.

**Corte integrado:** `main` `ea0322c159be61018a60604f6b9134bd4f290787`.
La ola 1 permanece acreditada por
`587789ff03df0c1b83baa2b6ca74babc4e4d3499`
([PR #46](https://github.com/dossijeo/quata/pull/46), 2026-07-26) y la ola 2 por
`9cc84dc2a77935ae2b84a7159e435c1ca6f8f220`
([PR #47](https://github.com/dossijeo/quata/pull/47)); ambas son ancestros de
este corte. La rama histórica `codex/integration-wave2` ya no existe.
**Método de esta corrección:** se reconciliaron las evidencias de gates ya
realizados contra el historial integrado. No se ejecutaron Gradle, emulador,
navegador, Supabase ni GitHub Actions en este lote documental; no acredita
compilaciones ni E2E nuevas.

Esta matriz es el punto de reconciliación MP-A14, no una declaración de que la
migración esté completa ni un segundo tablero. El
estado operativo, responsables y entregas pequeñas viven en
[MULTIPLATFORM_MIGRATION_BOARD.md](MULTIPLATFORM_MIGRATION_BOARD.md); el detalle
por vertical y adaptador vive en
[MULTIPLATFORM_INVENTORY.md](MULTIPLATFORM_INVENTORY.md). Una evidencia sólo es
válida para el SHA que cita: un verde histórico no se propaga automáticamente a
un cambio posterior.

## Anclas de validación vigentes

| Superficie | SHA / ejecución | Evidencia acreditada | Alcance que no acredita |
| --- | --- | --- | --- |
| Ola 1 integrada | `587789ff03df0c1b83baa2b6ca74babc4e4d3499`, [PR #46](https://github.com/dossijeo/quata/pull/46) | Merge confirmado en `main`; conserva la evidencia histórica por SHA de sus lotes. | No acredita automáticamente cambios posteriores. |
| Android ola 2 integrada | `9cc84dc2`, [PR #47](https://github.com/dossijeo/quata/pull/47) | `assembleDebug` verde; APK 79.029.367 bytes/SHA-256 registrado. A/B API-37 contra ola 1 recorrió cinco áreas, con crash buffer limpio, sin ANR: 25,392 s ola 1 y 21,159 s ola 2. | Smoke comparativo, no benchmark; ambos hosts lentos se clasificaron `environment_both_slow`. |
| Web/Wasm ola 2 | `9cc84dc2` | Tests/compilaciones acotados verdes, distribución Wasm verde y smoke DocMentis de seis rutas en 29 s, sin fixtures remotos. | Dos `compileTest` agotaron timeout sin diagnóstico; el smoke no es E2E autenticado. Chat UI sigue bloqueado por AX aunque el preflight remoto y la purga pasaron. |
| iOS ola 2 | `9cc84dc2`, [CI #30210875187](https://github.com/dossijeo/quata/actions/runs/30210875187) | **Verde**, completada 2026-07-26 16:59:46Z: Kotlin/Native, enlace/XCFramework, host Swift + Share Extension, simulador/XCTest, archive sin firma y artefacto. | No acredita firma, dispositivo físico, App Group operativo, entrega APNs ni E2E autenticado. |
| Logout iOS integrado | `d8652326`, [PR #106](https://github.com/dossijeo/quata/pull/106), [CI #30429034347](https://github.com/dossijeo/quata/actions/runs/30429034347) | **Verde** para la UI de logout y sus contratos Swift/Kotlin. Un carril redactado confirmó sesión y perfil de backend. | No acredita login, refresh ni logout visual: falta configuración remota `0600` e aislamiento Keychain/test host. PR #107 se cerró sin merge por fixture que desconectó Compose. |
| Evidencia mecanizable | [`mp-a14-final-evidence.json`](mp-a14-final-evidence.json) | SHAs, gates locales, hash del APK, run iOS y límites funcionales del corte. | Es documentación; no ejecuta ni transforma un smoke en E2E. |

## Convenciones de evidencia

| Marca | Significado |
| --- | --- |
| **Integrado** | Código y/o documentación de alcance están presentes en `main`; puede conservar una validación de SHA anterior que debe reejecutarse si cambia la superficie. |
| **Validado en SHA citado** | Hay comando, CI o prueba de dispositivo/navegador documentada para ese SHA; no equivale a E2E ni a paridad de producto. |
| **Parcial** | La frontera o UI existe, pero quedan rutas, adaptadores o datos reales sin verificar. |
| **Bloqueado externo** | Falta configuración, credencial, entorno o autorización; no se interpreta como funcionalidad completada. |

## Requisitos no negociables y evidencia actual

| Requisito | Evidencia actual | Límite / acción obligatoria antes del cierre |
| --- | --- | --- |
| Migración progresiva, sin reescritura masiva ni mover varias features a la vez | El inventario registra cortes verticales y el tablero separa MP-A01..MP-A14; MP-A10 documenta una retirada limitada de wrappers Feed. | Revisar cada lote contra el diff y conservar la secuencia de validación; no hay una prueba automática que demuestre por sí sola que todo cambio futuro es incremental. |
| Android no se rompe | En `9cc84dc2`, `assembleDebug` pasó y el A/B API-37 contra `587789ff` no mostró diferencial de ANR/crash; cinco áreas arrancaron y el buffer crash quedó limpio. | Es smoke en un entorno lento, no rendimiento certificado ni cobertura funcional completa. |
| `commonMain` no importa Android | `scripts/multiplatform-metrics.ps1` registró cero imports Android en `commonMain` para `9cc84dc2`. | Ejecutar el gate en cualquier lote posterior que modifique `commonMain` o source sets. |
| Hosts de plataforma finos y lógica/UI compartida | `core`, `designsystem` y features exponen modelos, ViewModels y Compose común; `:app`, `web` e `iosApp` actúan como hosts. MP-A01 integra `QuataShared.framework`. | `:app` conserva el nombre histórico, no `androidApp/`; decidir ese renombre en un lote independiente si es requisito literal. Persisten adaptadores Android para media, sistema y navegación. |
| Clean Architecture por feature | El inventario separa dominio/estado/presentación y los contratos de plataforma; pantallas comunes se sitúan en los módulos feature/designsystem. | Auditar dependencias y source sets de cada feature final; quedan bordes Android con infraestructura y UI mezcladas que deben extraerse sólo cuando haya un slot/adaptador claro. |
| Feed, Chat, Communities, Profile/SOS, Official y Composer compartidos progresivamente | El inventario documenta ViewModels/estado/UI estructural común y slots para media, avatar, navegación o recursos. Communities/Official/Profile tienen shells iOS parciales. | No hay paridad funcional completa: media, mutaciones, realtime, permisos, contactos, previews de bitmap/vídeo y navegación nativa siguen por plataforma. Validar cada flujo de usuario, no sólo que el composable compile. |
| Design system común sin trasladar APIs Android a `commonMain` | Theme, controles, comentarios, emoji, ranking, paneles y Touch Flow constan como comunes; recursos/Coil/audio/cámara/ventana continúan adaptados. | Terminar sólo los componentes restantes que todavía viven en `:app`; preservar recursos, Context y lifecycle como adaptadores. |
| Adaptadores reales de plataforma | Android dispone de varios adaptadores reales; Web e iOS tienen contratos y una cobertura creciente de browser/UIKit. El inventario declara explícitamente `Unsupported` cuando no hay host. | Cámara, audio, Media3/MediaStore, documentos, contactos, WorkManager, SQLite/FastText/Vosk, EGL/Bitmap, push y Google Sign-In no tienen paridad completa en las tres plataformas. No convertir `Unsupported` en éxito ficticio. |
| Host Web Wasm real | `9cc84dc2` pasó `:web:wasmJsTest`, distribución y smoke DocMentis de Auth/Feed/Chat/Official/Settings/Share Target. | El smoke sigue siendo no autenticado y el budget sigue `proposed`. Chat UI autenticado no alcanzó envío/reply/logout por el bloqueo AX; 0 residuos no convierte el recorrido en éxito. |
| Visor Web de documentos mediante DocMentis | El inventario declara carga perezosa de `@docmentis/udoc-viewer` para PDF/DOCX/PPTX/XLSX y fallback seguro para RTF/legacy; MP-A13 registra smoke DocMentis. | Falta prueba funcional con documentos propios, CORS y Storage autenticado. Licencia, telemetría, actualizaciones, fuentes y CSP de DocMentis requieren aprobación de producto/legal antes de despliegue. |
| Host iOS real y `iosMain` presente | `iosApp` UIKit y `:ios-shared` existen; `#30210875187` pasó sobre `9cc84dc2`, incluidos host Swift, Share Extension y archive sin firma. | iOS **no está lista** aunque compile: faltan firma/dispositivo, App Group físico, entrega APNs, permisos y E2E funcional. El Mac virtual con Xcode 16 es incompatible con las platform libraries actuales de Kotlin/Native/Xcode 26. |
| Pruebas iOS | `#30210875187` pasó Kotlin/Native, framework/XCFramework, host Swift, simulador/XCTest, archive genérico sin firma y publicación de artefacto. | No sustituye App Group firmado, APNs entregado ni recorrido real de External Share. |
| Backend y E2E con Supabase | SB-01..SB-06 conservan evidencia y limpieza. El forward RLS-001 `20260726171005` está desplegado y hardened; su postflight compuesto no equivale al SB-07 remoto mutante completo. SB-09 confirmó RLS-002 (suplantación de `profile_id` en like Official). | Communities/Official Web permanecen fail-closed. Falta ejecutar el alcance mutante completo de SB-07 en remoto con fixtures/purga autorizados y cerrar SB-09 antes de habilitar mutaciones. RLS-002/003/004/005 permanecen abiertos. |
| Bundle, warnings y rendimiento Web | MP-A07/MP-A13 conservan medición 35,29 MiB / 13,55 MiB gzip, análisis de DocMentis/Skiko y métricas locales de Chrome. | El budget está `proposed`, no es gate aprobado; faltan baseline certificado, runner controlado y resolución de avisos sin suprimirlos globalmente. |
| Inventario y documentación honestos | MP-A14 reconcilia olas 1 y 2 integradas, gates locales, CI iOS verde y límites en Markdown/JSON. | Reconciliación documental completada; la migración no se declara completa. |

## Matriz de plataformas: condición de salida

| Plataforma | Evidencia ya disponible | Qué falta para llamarla lista |
| --- | --- | --- |
| Android | `9cc84dc2`: assemble y smoke A/B API-37 sin diferencial de crash/ANR. | Completar flujos funcionales; la lentitud compartida del entorno no es un benchmark aprobado. |
| Web / Wasm | `9cc84dc2`: compilación/tests acotados, distribución y smoke local verdes. | Resolver AX de Compose Chat, alta Web fail-closed, RLS-001/RLS-002, push y E2E UI. **No lista.** |
| iOS | `#30210875187` exacta y verde para `9cc84dc2`. | Firma/dispositivo, App Group/External Share físico, APNs entregado, permisos/media y E2E autorizado. **No lista.** |

## Secuencia obligatoria de auditoría final

1. Congelar el SHA candidato y actualizar la matriz sólo con su evidencia. Para el
   corte documental actual, las anclas Android/Web `acde140c` e iOS `f3520446` ya
   cubren sus respectivas superficies sin cambios posteriores de código.
2. Si el candidato cambia `commonMain`, Android o Web/Wasm, ejecutar únicamente los
   gates afectados: imports Android, módulos Wasm, `:app:assembleDebug`, instalación
   API-37, crash buffer/PID y distribución/smoke Web según corresponda.
3. Aprobar el presupuesto sólo cuando el baseline sea regenerado desde la distribución
   del SHA que vaya a integrar; `proposed` sigue siendo observación, no bloqueo.
4. Si cambia Kotlin/Native, framework, host Swift o pruebas iOS, lanzar CI macOS y
   conservar enlace a Kotlin/Native, framework, Xcode, XCTest y archive.
5. Ejecutar únicamente los SB autorizados con datos efímeros, registrar limpieza y
   revocación sin secretos, y etiquetar cada recorrido como smoke o E2E.
6. Reconciliar tablero e inventario con enlaces a los artefactos anteriores. Sólo
   entonces evaluar si cada fila puede pasar de Parcial/Bloqueado a Integrado.

Hasta completar esa secuencia, la conclusión de auditoría es: la migración tiene
arquitectura KMP real y progreso verificable, pero **no está terminada** y ni Web
ni iOS pueden declararse listas.
