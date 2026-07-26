# Matriz de evidencia para la auditoría final KMP

**Corte documental:** `94995a35` (`main`, 2026-07-26).
**Método de este lote:** revisión de fuentes, inventario, tablero y evidencias ya
referenciadas; no se ejecutaron Gradle, emulador, navegador, Supabase ni GitHub
Actions. Por ello no acredita compilaciones ni E2E nuevas.

Esta matriz es el punto de cierre de la migración, no un segundo tablero. El
estado operativo, responsables y entregas pequeñas viven en
[MULTIPLATFORM_MIGRATION_BOARD.md](MULTIPLATFORM_MIGRATION_BOARD.md); el detalle
por vertical y adaptador vive en
[MULTIPLATFORM_INVENTORY.md](MULTIPLATFORM_INVENTORY.md). Una evidencia sólo es
válida para el SHA que cita: un verde histórico no se propaga automáticamente a
un cambio posterior.

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
| Android no se rompe | MP-A10 cita `:app:compileDebugKotlin`, `:app:assembleDebug` y arranque API-37 sin crash para `ac9f6e0`. | Repetir assemble, instalación, arranque, `logcat -b crash` y `pidof` en el SHA final; resultados anteriores no cubren cambios posteriores. |
| `commonMain` no importa Android | El audit previo registró cero coincidencias de `^import android\\.` y la separación de contratos en `core/commonMain`. | Ejecutar la búsqueda/gate sobre el SHA final, incluyendo módulos añadidos; no basta con la revisión histórica. |
| Hosts de plataforma finos y lógica/UI compartida | `core`, `designsystem` y features exponen modelos, ViewModels y Compose común; `:app`, `web` e `iosApp` actúan como hosts. MP-A01 integra `QuataShared.framework`. | `:app` conserva el nombre histórico, no `androidApp/`; decidir ese renombre en un lote independiente si es requisito literal. Persisten adaptadores Android para media, sistema y navegación. |
| Clean Architecture por feature | El inventario separa dominio/estado/presentación y los contratos de plataforma; pantallas comunes se sitúan en los módulos feature/designsystem. | Auditar dependencias y source sets de cada feature final; quedan bordes Android con infraestructura y UI mezcladas que deben extraerse sólo cuando haya un slot/adaptador claro. |
| Feed, Chat, Communities, Profile/SOS, Official y Composer compartidos progresivamente | El inventario documenta ViewModels/estado/UI estructural común y slots para media, avatar, navegación o recursos. Communities/Official/Profile tienen shells iOS parciales. | No hay paridad funcional completa: media, mutaciones, realtime, permisos, contactos, previews de bitmap/vídeo y navegación nativa siguen por plataforma. Validar cada flujo de usuario, no sólo que el composable compile. |
| Design system común sin trasladar APIs Android a `commonMain` | Theme, controles, comentarios, emoji, ranking, paneles y Touch Flow constan como comunes; recursos/Coil/audio/cámara/ventana continúan adaptados. | Terminar sólo los componentes restantes que todavía viven en `:app`; preservar recursos, Context y lifecycle como adaptadores. |
| Adaptadores reales de plataforma | Android dispone de varios adaptadores reales; Web e iOS tienen contratos y una cobertura creciente de browser/UIKit. El inventario declara explícitamente `Unsupported` cuando no hay host. | Cámara, audio, Media3/MediaStore, documentos, contactos, WorkManager, SQLite/FastText/Vosk, EGL/Bitmap, push y Google Sign-In no tienen paridad completa en las tres plataformas. No convertir `Unsupported` en éxito ficticio. |
| Host Web Wasm real | `web/wasmJsMain` tiene `main`, `ComposeViewport`, routing y adaptadores browser; MP-A13 cita distribución Wasm y smoke Chrome con DocMentis en `eb41be9`. | La evidencia es de SHA anterior y smoke no autenticado. Rehacer distribución, bundle y smoke de navegador en el SHA final. Web **no está lista**: faltan E2E remoto, capacidades parciales y presupuesto aprobado. |
| Visor Web de documentos mediante DocMentis | El inventario declara carga perezosa de `@docmentis/udoc-viewer` para PDF/DOCX/PPTX/XLSX y fallback seguro para RTF/legacy; MP-A13 registra smoke DocMentis. | Falta prueba funcional con documentos propios, CORS y Storage autenticado. Licencia, telemetría, actualizaciones, fuentes y CSP de DocMentis requieren aprobación de producto/legal antes de despliegue. |
| Host iOS real y `iosMain` presente | `iosApp` UIKit, `:ios-shared` y CI reproducible están documentados; MP-A05 cita Kotlin/Native, framework, Xcode, XCTest y archive sin firma para `a6a11ba`. | iOS **no está lista**: no hay distribución firmada/dispositivo físico ni E2E configurado; permisos, push/APNs, media, documentos y rutas autenticadas restantes necesitan pruebas funcionales. |
| Pruebas iOS | CI contiene XCTest/UI de frontera y archive sin firma; no depende de afirmar que los adaptadores estén completos. | Ampliar XCTest/UI de host y adaptadores con simulador/archivos/permisos reales; ejecutar en SHA exacto de integración. |
| Backend y E2E con Supabase | SB-01..SB-08 describen runners, precondiciones, limpieza y límites. Los runners abortan antes de mutar si falta opt-in o contrato de purga. | SB-01..SB-07 siguen pendientes de entorno/CA/datos efímeros; SB-08 requiere credenciales push y dispositivo. No declarar backend real/E2E hasta registrar informe seguro, limpieza y revocación por cada escenario. |
| Bundle, warnings y rendimiento Web | MP-A07/MP-A13 conservan medición 35,29 MiB / 13,55 MiB gzip, análisis de DocMentis/Skiko y métricas locales de Chrome. | El budget está `proposed`, no es gate aprobado; faltan baseline certificado, runner controlado y resolución de avisos sin suprimirlos globalmente. |
| Inventario y documentación honestos | El inventario distingue KMP parcial de host real y el tablero etiqueta E2E/limitaciones. | MP-A14 permanece pendiente: reconciliar SHA, comandos, artefactos y alcance de smoke/E2E de toda evidencia antes de declarar finalización. |

## Matriz de plataformas: condición de salida

| Plataforma | Evidencia ya disponible | Qué falta para llamarla lista |
| --- | --- | --- |
| Android | Validaciones de compilación/assemble y arranque API-37 citadas para cortes concretos; host productivo existente. | Validación completa en el SHA final tras cada integración relevante; no se infiere de los resultados históricos. |
| Web / Wasm | Host real, bundle distribuible y smoke no autenticado con DocMentis documentados para `eb41be9`; varias lecturas/transacciones Web existen con límites explícitos. | Bundle + smoke en SHA final, baseline aprobado, navegador con sesión/configuración pública y SB-02..SB-07 según capacidades; confirmar documentos, push y acciones que hoy son parciales. **No lista.** |
| iOS | Framework umbrella, host UIKit y CI macOS con Kotlin/Native/Xcode/XCTest/archivo sin firma en SHAs citados. | CI exacta del SHA final, shell de verticales restantes, configuración pública/sesión, pruebas de permisos/archivos/media/push y E2E autorizado. **No lista.** |

## Secuencia obligatoria de auditoría final

1. Congelar el SHA candidato y actualizar la matriz sólo con su evidencia.
2. Ejecutar el gate de imports Android en `commonMain`, los módulos Wasm afectados y
   `:app:assembleDebug`.
3. Instalar Android en API-37, arrancar en frío, consultar crash buffer y PID.
4. Generar la distribución Wasm, medir bundle y pasar smoke real de navegador; sólo
   aprobar el presupuesto si el baseline corresponde a ese SHA.
5. Lanzar la CI macOS del mismo SHA y conservar enlaces a Kotlin/Native, framework,
   Xcode, XCTest y archive.
6. Ejecutar únicamente los SB autorizados con datos efímeros, registrar limpieza y
   revocación sin secretos, y etiquetar cada recorrido como smoke o E2E.
7. Reconciliar tablero e inventario con enlaces a los artefactos anteriores. Sólo
   entonces evaluar si cada fila puede pasar de Parcial/Bloqueado a Integrado.

Hasta completar esa secuencia, la conclusión de auditoría es: la migración tiene
arquitectura KMP real y progreso verificable, pero **no está terminada** y ni Web
ni iOS pueden declararse listas.
