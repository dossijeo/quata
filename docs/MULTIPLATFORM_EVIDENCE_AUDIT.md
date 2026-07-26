# Matriz de evidencia para la auditoría final KMP

**Corte documental integrado:** `9c26d315636e5d54d8b423d3fe1a63d2ef7387f4`
(`origin/main`, 2026-07-26).
**Método de esta corrección:** se reconciliaron las evidencias de gates ya
realizados contra el historial integrado. No se ejecutaron Gradle, emulador,
navegador, Supabase ni GitHub Actions en este lote documental; no acredita
compilaciones ni E2E nuevas.

Esta matriz es el punto de cierre de la migración, no un segundo tablero. El
estado operativo, responsables y entregas pequeñas viven en
[MULTIPLATFORM_MIGRATION_BOARD.md](MULTIPLATFORM_MIGRATION_BOARD.md); el detalle
por vertical y adaptador vive en
[MULTIPLATFORM_INVENTORY.md](MULTIPLATFORM_INVENTORY.md). Una evidencia sólo es
válida para el SHA que cita: un verde histórico no se propaga automáticamente a
un cambio posterior.

## Anclas de validación vigentes

| Superficie | SHA / ejecución | Evidencia acreditada | Alcance que no acredita |
| --- | --- | --- | --- |
| Android y Web/Wasm | `acde140ccf2debd22b98d9bd1ce2ab008f4a1211` | Gates Android/Web ya registrados para ese corte, incluida distribución Wasm y el informe de bundle que originó el baseline propuesto. Los commits posteriores hasta `9c26d315` sólo incorporan smoke iOS y documentación. | No convierte el smoke Web en E2E autenticado ni aprueba el presupuesto; tampoco sustituye una repetición si un futuro lote altera Android o Web. |
| iOS | `f352044643c230c722486e2eb0bebdd93a30a4fc`, [CI #30187628542](https://github.com/dossijeo/quata/actions/runs/30187628542) | Ejecutó con éxito Kotlin/Native de todos los targets, enlace/ensamblado de `QuataShared` XCFramework, host Swift, simulador, XCTest/UI de frontera y archive genérico sin firma. | No acredita firma, distribución, dispositivo físico, permisos, APNs ni E2E contra Supabase. |
| Documentación final | `9c26d315636e5d54d8b423d3fe1a63d2ef7387f4` | Esta matriz y su trazabilidad de límites están integradas en `origin/main`. | No es una ejecución de plataforma. |

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
| Android no se rompe | La ancla Android/Web `acde140c` conserva los gates Android ya realizados; los cambios entre ese SHA y `9c26d315` no modifican código Android. MP-A10 además registra compile/assemble y arranque API-37 sin crash para su corte. | Repetir assemble, instalación, arranque, `logcat -b crash` y `pidof` sólo cuando un lote posterior toque Android o sus dependencias transitivas; no atribuir el gate de `acde140c` a cambios futuros. |
| `commonMain` no importa Android | El audit previo registró cero coincidencias de `^import android\\.`; el gate versionado permanece en el historial y los cambios posteriores hasta `9c26d315` no añaden código común funcional. | Ejecutar el gate en cualquier lote que modifique `commonMain` o source sets; la evidencia actual no exonera cambios nuevos. |
| Hosts de plataforma finos y lógica/UI compartida | `core`, `designsystem` y features exponen modelos, ViewModels y Compose común; `:app`, `web` e `iosApp` actúan como hosts. MP-A01 integra `QuataShared.framework`. | `:app` conserva el nombre histórico, no `androidApp/`; decidir ese renombre en un lote independiente si es requisito literal. Persisten adaptadores Android para media, sistema y navegación. |
| Clean Architecture por feature | El inventario separa dominio/estado/presentación y los contratos de plataforma; pantallas comunes se sitúan en los módulos feature/designsystem. | Auditar dependencias y source sets de cada feature final; quedan bordes Android con infraestructura y UI mezcladas que deben extraerse sólo cuando haya un slot/adaptador claro. |
| Feed, Chat, Communities, Profile/SOS, Official y Composer compartidos progresivamente | El inventario documenta ViewModels/estado/UI estructural común y slots para media, avatar, navegación o recursos. Communities/Official/Profile tienen shells iOS parciales. | No hay paridad funcional completa: media, mutaciones, realtime, permisos, contactos, previews de bitmap/vídeo y navegación nativa siguen por plataforma. Validar cada flujo de usuario, no sólo que el composable compile. |
| Design system común sin trasladar APIs Android a `commonMain` | Theme, controles, comentarios, emoji, ranking, paneles y Touch Flow constan como comunes; recursos/Coil/audio/cámara/ventana continúan adaptados. | Terminar sólo los componentes restantes que todavía viven en `:app`; preservar recursos, Context y lifecycle como adaptadores. |
| Adaptadores reales de plataforma | Android dispone de varios adaptadores reales; Web e iOS tienen contratos y una cobertura creciente de browser/UIKit. El inventario declara explícitamente `Unsupported` cuando no hay host. | Cámara, audio, Media3/MediaStore, documentos, contactos, WorkManager, SQLite/FastText/Vosk, EGL/Bitmap, push y Google Sign-In no tienen paridad completa en las tres plataformas. No convertir `Unsupported` en éxito ficticio. |
| Host Web Wasm real | `web/wasmJsMain` tiene `main`, `ComposeViewport`, routing y adaptadores browser. La ancla `acde140c` acredita el gate Web/Wasm y el informe de bundle; `9c26d315` no modifica esa superficie. | El smoke sigue siendo no autenticado y el budget sigue `proposed`: repetir distribución/bundle/smoke sólo si cambia Web/Wasm y no declarar Web lista hasta E2E remoto y capacidades pendientes. |
| Visor Web de documentos mediante DocMentis | El inventario declara carga perezosa de `@docmentis/udoc-viewer` para PDF/DOCX/PPTX/XLSX y fallback seguro para RTF/legacy; MP-A13 registra smoke DocMentis. | Falta prueba funcional con documentos propios, CORS y Storage autenticado. Licencia, telemetría, actualizaciones, fuentes y CSP de DocMentis requieren aprobación de producto/legal antes de despliegue. |
| Host iOS real y `iosMain` presente | `iosApp` UIKit, `:ios-shared` y CI reproducible están documentados. La CI exacta `#30187628542` para `f3520446` valida Kotlin/Native, framework/XCFramework, host Swift, simulador, XCTest/UI y archive sin firma; `9c26d315` sólo añade documentación después de ese gate. | iOS **no está lista**: no hay distribución firmada/dispositivo físico ni E2E configurado; permisos, push/APNs, media, documentos y rutas autenticadas restantes necesitan pruebas funcionales. |
| Pruebas iOS | La CI `#30187628542` ya cubre la frontera Swift/Kotlin y el smoke de host Compose en simulador para el SHA citado. | Ampliar XCTest/UI de permisos, archivos y adaptadores; reejecutar CI cuando cambien los targets, framework, host Swift o pruebas iOS, no por esta corrección documental. |
| Backend y E2E con Supabase | SB-01 se ejecutó el 2026-07-26 con pooler de producción, CA explícita de Supabase y TLS `verify-full`: catálogo/RPC/bucket completos, sin DDL, DML, RPC ni datos de negocio. El workflow manual de GitHub Actions `#30194306847` también pasó sobre `cdb1ff42`, consumiendo secretos de repositorio y sin publicar el informe. SB-02 ejecutó contra producción el bridge Web, refresh, doble login/logout y revocación global; su perfil y usuario Auth efímeros fueron purgados y comprobados. SB-03 verificó Feed y Official autenticados/públicos y deep links con filas aisladas que se purgaron. SB-04..SB-08 mantienen runners, precondiciones, limpieza y límites fail-closed. | SB-04..SB-07 requieren datos efímeros y limpieza/revocación verificable; SB-08 requiere credenciales push y dispositivo. No declarar backend real/E2E completo hasta registrar esas evidencias. |
| Bundle, warnings y rendimiento Web | MP-A07/MP-A13 conservan medición 35,29 MiB / 13,55 MiB gzip, análisis de DocMentis/Skiko y métricas locales de Chrome. | El budget está `proposed`, no es gate aprobado; faltan baseline certificado, runner controlado y resolución de avisos sin suprimirlos globalmente. |
| Inventario y documentación honestos | El inventario distingue KMP parcial de host real y el tablero etiqueta E2E/limitaciones. | MP-A14 permanece pendiente: reconciliar SHA, comandos, artefactos y alcance de smoke/E2E de toda evidencia antes de declarar finalización. |

## Matriz de plataformas: condición de salida

| Plataforma | Evidencia ya disponible | Qué falta para llamarla lista |
| --- | --- | --- |
| Android | Gates Android acreditados en el corte `acde140c`; no hay código Android posterior hasta el corte documental `9c26d315`. | Revalidar ante cambio Android/dependencia transitiva; después completar los flujos funcionales que sigan delegados a adaptadores. |
| Web / Wasm | Gate Web/Wasm y baseline propuesto acreditados en `acde140c`; host real y smoke no autenticado con DocMentis. | Aprobar baseline certificado, ejecutar navegador con sesión/configuración pública y SB-02..SB-07 según capacidades; confirmar documentos, push y acciones parciales. **No lista.** |
| iOS | CI exacta `#30187628542` verde para `f3520446`, ancestro directo del corte documental, con Kotlin/Native/Xcode/XCTest/archive sin firma. | Shell de verticales restantes, configuración pública/sesión, permisos/archivos/media/push y E2E autorizado. **No lista.** |

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
