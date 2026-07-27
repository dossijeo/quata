# Backlog de cierre de la migración multiplataforma

**Corte:** `main` `ea0322c159be61018a60604f6b9134bd4f290787`
**Fecha:** 2026-07-27  
**Alcance:** paridad funcional Web/iOS, seguridad compatible con Android, validación
real, distribución y cierre de deuda de migración.

Este documento sustituye como cola operativa a los estados históricos del tablero
anterior. Una tarea sólo se cierra cuando su criterio de aceptación está acreditado
en el SHA exacto integrado. Compilación, smoke, E2E y validación física son evidencias
distintas y no se sustituyen entre sí.

## Reglas de seguridad que no se pueden vulnerar

- No romper la versión Android publicada ni la navegación anónima por Feed.
- No desplegar RLS, DDL, funciones o grants con un `db push` genérico.
- Cada cambio remoto requiere snapshot, fingerprint, preflight, postcondiciones,
  rollback o forward compensatorio y autorización de despliegue.
- No introducir `service_role`, contraseñas, tokens, certificados ni claves privadas
  en clientes, logs, commits, capturas o artefactos.
- Las mutaciones permanecen `fail-closed` hasta superar pruebas con actor, outsider,
  anónimo y, cuando corresponda, administrador.
- Los fixtures E2E usan prefijo único, revocan sesiones y se eliminan con comprobación
  posterior de ausencia.

## Política obligatoria de agentes, ramas y limpieza

1. El orquestador asigna cada unidad a un subagente **GPT-5.6 Terra Medium**.
2. Cada cambio se realiza en un worktree y rama nuevos:
   `codex/next-<id>-<descripcion>`. El prefijo permite ejecutar la CI iOS existente.
3. Una rama sólo contiene una tarea o un lote inseparable y no se reutiliza.
4. El subagente implementador no cambia `main`, no fusiona y no borra ramas. Entrega:
   commit, archivos tocados, pruebas ejecutadas, límites, riesgos y evidencia.
5. Los commits son pequeños y convencionales:
   `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `ci(scope): ...` o
   `docs(scope): ...`. No se mezclan cambios oportunistas.
6. Un agente de validación independiente revisa el diff y ejecuta los gates. Para iOS
   registra Xcode, runtime, simulador, SHA, resultado y rutas de `xcresult`/capturas.
7. El orquestador integra sólo tras revisión y evidencia proporcional. Si hay conflicto,
   se corrige en la rama; no se parchea silenciosamente durante el merge.
8. Inmediatamente después de integrar y comprobar `main`: borrar worktree, rama local
   y rama remota. Confirmar que `main` queda limpio y que sólo existen ramas activas
   con propietario y tarea.
9. Las pruebas de simulador macOS se serializan mediante una cola del orquestador.
   En la VM de 4 vCPU sólo un agente puede compilar, arrancar simulador, ejecutar
   XCTest o tomar capturas a la vez. Los dos modelos se prueban secuencialmente.
10. Un fallo abre una tarea de corrección acotada. Nunca se marca una tarea verde por
    reutilizar la CI de otro SHA.

## Estados

- `PENDIENTE`: preparada para asignar.
- `BLOQUEADA`: depende de credenciales, contrato, despliegue o tarea anterior.
- `EN CURSO`: rama y agente identificados.
- `EN VALIDACIÓN`: implementación terminada, pendiente de gates.
- `INTEGRADA`: incluida en `main` y validada en su SHA.
- `CERRADA EXTERNA`: completada fuera del repositorio con evidencia enlazada.

## Ola 0 — Restablecer una base verificable

| ID | P | Estado | Tarea | Dependencias | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| OPS-IOS-001 | P0 | PENDIENTE | Preparar la VM macOS `192.168.1.109`: JDK 17 x86_64 con `JAVA_HOME`, XcodeGen y configuración de shell. Ejecutar `bash gradlew` para no ensuciar el modo 100644 del wrapper. | Acceso administrador ya disponible. | `java -version`, `bash gradlew --version`, `xcodegen --version`, `xcodebuild -version` y `xcrun simctl list` pasan; checkout limpio y ningún secreto versionado. Android SDK no bloquea esta tarea. |
| OPS-IOS-002 | P0 | BLOQUEADA | Adaptar comandos, scripts y README al Mac Intel: `iosX64` y Simulator x86_64 sin `ARCHS=arm64`, conservando arm64 para archive genérico de dispositivo. | OPS-IOS-001. | Framework/host/XCTest enlazan en Simulator x86_64; archive conserva slice iosArm64; se documenta la divergencia Xcode 26.6/iOS 26.5 local frente a Xcode 26.3/iOS 26.2 CI. |
| VAL-IOS-001 | P0 | BLOQUEADA | Ejecutar la línea base iOS completa sobre `main` actual. | OPS-IOS-002. | Compilación, XCFramework, host y XCTest verdes para Simulator Intel; archive unsigned verde para device arm64. Evidencia registra SHA, comandos, Xcode, runtime, UDID, arquitectura, logs y `xcresult` de cada lane por separado. |
| VAL-IOS-002 | P0 | BLOQUEADA | Smoke visual secuencial en iPhone 16 Pro/iOS 18.3 e iPhone 17 Pro/iOS 26.5. | VAL-IOS-001. | Boot/health-check, reset controlado de datos y capturas con UDID/runtime de Auth, Feed, menú y verticales; los estados Unsupported previstos se distinguen de pantalla vacía/crash/bloqueo. |
| DOC-001 | P0 | INTEGRADA | Reconciliar documentación obsoleta con `main` y el estado remoto real. | Ninguna. | En `ea0322c1`, tablero/evidencia registran la ola 2 como integrada y la rama histórica como inexistente; RLS-001 consta desplegada y hardened mediante `20260726171005`, sin afirmar que SB-07 remoto mutante completo haya pasado. |
| CI-001 | P0 | En revisión | Crear CI obligatoria Web/Wasm y Android para PR, con JDK/Node/Chrome versionados y timeouts explícitos. | Ninguna. | Ejecuta `:web:wasmJsBrowserTest` con Chrome instalado, `CHROME_BIN` exportado y launcher Karma CI `ChromeHeadlessNoSandbox`, y `wasmJsNodeTest` por módulo común con pruebas, además de `:web:wasmJsBrowserDistribution`, `web-browser-smoke.mjs --docmentis` y matriz Android/KMP. Los tests y `:app:assembleDebug` bloquean. `:app:lintDebug` y `:document-reader:lintDebug` son informativos mientras se elimina deuda heredada (54/312 y 20/418, respectivamente); sus informes, resumen y logs se publican explícitamente. |
| CI-002 | P0 | PENDIENTE | Exigir CI iOS por SHA mergeable para los paths KMP/iOS enumerados en el workflow y branch protection. | Configuración GitHub. | El required check con nombre estable bloquea el merge si el HEAD SHA afectado no está verde; conserva `xcresult`; PR fuera de los paths declarados no consume el gate. |

## Ola 1 — Frontera de identidad y seguridad

| ID | P | Estado | Tarea | Dependencias | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| AUTH-BOUNDARY-001 | P0 | PENDIENTE | Desplegar primero endpoints/RPC/Edge seguros en sombra y sustituir después en Android las lecturas/escrituras legacy de credenciales y recuperación. | Diseño de compatibilidad, auth-bridge de credenciales heredadas y versión mínima Android. | Flags servidor/cliente inicialmente cerrados; Android publicado sigue operando durante transición; nueva versión pasa login/registro/reset/sesión/lifecycle sin secretos ni PATCH anónimo; adopción medida y cutoff definido antes de revocar grants. |
| AUTH-BOUNDARY-002 | P0 | PENDIENTE | Unificar Web/iOS sobre la misma frontera segura y proyección pública mínima. | AUTH-BOUNDARY-001. | Web/iOS no consultan secretos ni identificadores internos; errores no filtran PII; pruebas con clave publicable y dos identidades verdes. |
| SEC-RLS-004A | P0 | BLOQUEADA | Retirar exposición pública de credenciales, recuperación, `pass_plain/hash` e IDs Auth mediante una proyección explícita compatible. | AUTH-BOUNDARY-001/002, selects exactos por cliente, adopción y gate APK antigua. | Cada `select=` Android/Web/iOS aprobado sigue decodificando; una clave anónima no lee campos privados; Feed/Official/Communities anónimos y auth multiplataforma pasan. |
| SEC-RLS-004B | P1 | BLOQUEADA | Minimizar teléfono y demás PII de la proyección pública. | SEC-RLS-004A y decisión producto/DTO/UI. | Ninguna ruta anónima necesita teléfono/códigos; pruebas de privacidad y compatibilidad verdes. |
| SEC-FOLLOWS-GUARD | P0 | BLOQUEADA | Versionar y desplegar primero el guard actor-bound de `community_profile_follows`; revocar RPC de recálculo a PUBLIC/anon/auth. | SEC-LEDGER-001 y snapshot/fingerprint. | Owner insert/delete; outsider/anónimo/inactivo denegados por PostgREST; toggle/untoggle Android API-37, realtime/cache y rollback condicionado verdes. |
| SEC-FOLLOWS-RECON | P0 | BLOQUEADA | Reconciliar los 74 perfiles y añadir mantenimiento seguro de contadores bajo advisory lock. | SEC-FOLLOWS-GUARD. | Contadores = aristas, concurrencia sin deadlock/lost update y fingerprints pre/post aprobados. |
| SEC-RLS-003 | P0 | BLOQUEADA | Cerrar escalada de identidad, rol y lifecycle en `community_profiles`. | AUTH-BOUNDARY-001/002 y SEC-FOLLOWS-RECON. | Preflight/fingerprints aprobados; UUID legacy se genera servidor; anónimo/outsider/inactivo denegados; admin inactivo no muta; lifecycle sólo service role; postflight/ledger y rollback de incidente documentados. |
| SEC-RLS-002 | P1 | PENDIENTE | Desplegar aisladamente la corrección de likes Official desde una única autoridad. | Backup fresco, dry-run allowlisted, snapshot y autorización nueva. | SHA forward/rollback, fingerprints y pre/postcondiciones ligados al corte; actor crea/elimina, spoof/cross-delete denegados; SB-09, lectura anónima y Android API-37 verdes. |
| SEC-MUTATIONS-001 | P1 | PENDIENTE | Programa de auditorías atómicas por dominio: Official; comments/reactions; follows; Composer+Storage; notifications. Cada lote usa rama y release propios. | Matriz de clientes Android/Web/iOS. | Por operación produce owner, contrato, DDL/RLS/RPC, migration+rollback/forward, autorización de wall/member/Storage, matriz PostgREST y decisión explícita enable/fail-closed. |
| SEC-LEDGER-001 | P1 | PENDIENTE | Clasificar los 29 markers históricos sin evidencia semántica suficiente, sin bloquear releases selectivos con anchors propios. | Inventario remoto read-only. | Cada marker queda reconciliado semánticamente o bloqueado explícitamente por archivo; se prohíbe replay/repair ciego y cada release selectivo usa pre/postcondición y forward compensatorio. |
| SEC-RLS-001-E2E | P1 | BLOQUEADA | Ejecutar el alcance comments de SB-07 remoto tras el forward ya desplegado; reactions continúa en SEC-MUTATIONS-001. | Fixture aislado, purga autorizada y ventana segura. | Own insert/delete, spoof insert y UPDATE bloqueados, outsider delete vacío/403 y matriz admin activo/inactivo; sesiones/datos ausentes. No reaplicar `171005`. |

## Ola 2 — Web funcional y verificable

| ID | P | Estado | Tarea | Dependencias | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| WEB-TEST-001 | P0 | PENDIENTE | Crear contrato AX/DOM y selectores estables Compose/Wasm para Playwright, usando el catálogo cross-platform de L10N-A11Y-001. | Decisión de accesibilidad. | Controles críticos tienen nombre localizado, role, enabled/selected/expanded cuando aplica, orden de foco/teclado y test AX automatizado. |
| WEB-E2E-001 | P0 | BLOQUEADA | E2E autenticado de UI real, incluyendo Chat de dos usuarios. | WEB-TEST-001, CI-001, bundle del SHA, dos cuentas y contrato de purga dura. | Login escrito por UI, navegación, mensaje A→B, reply, logout y cero residuos; bundle regenerado en el SHA exacto. |
| WEB-AUTH-001 | P0 | BLOQUEADA | Validar y activar registro Web con Turnstile, flags servidor/cliente, rate limit, idempotencia y cleanup lease. | SEC-RLS-003/004 y frontera Auth. | Registro real en staging/browser, challenge válido, errores uniformes, reintento seguro y purga; Android legacy intacto. |
| WEB-COMPOSER-001 | P0 | PENDIENTE | Contener y auditar el compositor actual, que ya hace POST y Storage con IDs suministrados por cliente. | SEC-MUTATIONS-001. | Suplantación y wall no autorizado denegados; texto/imagen/vídeo funcionan mediante contrato actor-bound; post y objetos E2E se purgan. |
| WEB-FEED-001 | P1 | BLOQUEADA | Implementar like, comentario, denuncia y borrado. | Seguridad/RLS de cada operación. | UI sólo se habilita tras E2E actor/outsider y rollback optimista; feed anónimo no cambia. |
| WEB-OFFICIAL-001 | P1 | BLOQUEADA | Implementar crear, borrar, like y comentario Official. | SEC-RLS-002 y SEC-MUTATIONS-001. | Operaciones autorizadas y spoof/cross-delete denegados; SB-09 y UI E2E verdes. |
| WEB-COMMUNITIES-001 | P1 | BLOQUEADA | Añadir contrato de comentario por `postId`, follows, roles y conversaciones de comunidad. | SEC-RLS-001-E2E, SEC-FOLLOWS-GUARD/RECON y lote Communities de SEC-MUTATIONS-001. | No se agrega comentarios de posts distintos; mutaciones y permisos pasan con dos usuarios; ranking no usa datos ficticios. |
| WEB-PROFILE-001 | P1 | PENDIENTE | Completar avatar, contactos→perfil Quata y Profile/SOS por UI. | Lote Profile/Storage de SEC-MUTATIONS-001. | Edición, avatar y contactos reales pasan; fallback local se etiqueta como no sincronizado; SB-06 UI deja cero residuos. |
| WEB-PUSH-001 | P1 | BLOQUEADA | Validar Web Push extremo a extremo. | Cuenta aislada, proveedor VAPID y navegador/dispositivo. | Alta, entrega, tap/deep-link, rotación, logout/unsubscribe y revocación pasan sin exponer tokens. |
| WEB-DOCS-001 | P1 | BLOQUEADA | Aprobar DocMentis para producción y validarlo con Storage autenticado. | Producto/legal, CSP/CORS y política de telemetría/licencia. | Licencia y tratamiento de telemetría/update-check/fuentes aprobados o deshabilitados; PDF/DOCX/PPTX/XLSX abren; red allowlisted sin URL/credenciales/PII; create/destroy y fallback seguro. |
| WEB-BUNDLE-001 | P1 | En revisión | Certificar y hacer bloqueante el presupuesto Wasm. | CI-001. | La rama de política fuerza captura desde checkout limpio/detached de `origin/main` o tag confiable y prohíbe aprobar baseline junto con payload/runtime/build/gate. Budget y baseline permanecen `proposed`/`candidate` hasta integrar CI-001; después requieren captura y PR dedicada con check exacto verde. |
| WEB-PERF-001 | P2 | PENDIENTE | Definir SLO y observabilidad reproducible sin PII. | CI-001 y runner Chrome/hardware fijo. | Cache cold, rutas y métricas p95 mount/heap definidas; al menos 5 muestras y tolerancia de variación. Empieza advisory y sólo se vuelve gate tras aprobar baseline. |
| WEB-SHARE-001 | P2 | PENDIENTE | Validar Share Target como PWA instalada. | Hosting de staging y browser compatible. | Texto/URL/archivos sobreviven cold start, se reclaman una sola vez y se limpia IndexedDB. |

## Ola 3 — iOS funcional, firmado y distribuible

| ID | P | Estado | Tarea | Dependencias | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| IOS-AUTH-001 | P0 | BLOQUEADA | Implementar registro iOS y validar login/reset/logout/refresh/cold start con backend real y cuentas aisladas. | AUTH-BOUNDARY-001/002, SEC-RLS-004A y OPS-IOS-002. | URL/publishable key se inyectan sin valores sin expandir; registro/sesiones pasan; logout limpia Keychain; offline/expirada fallan correctamente; no hay secretos privilegiados. |
| IOS-NAV-001 | P1 | BLOQUEADA | E2E de routing autenticado y deep links en simulador, sin exigir aún paridad funcional de cada vertical. | IOS-AUTH-001. | Menú, back/foreground y rutas Feed/Chat/Official/Notifications/Profile/SOS/Communities/Composer/Settings/What’s New abren su host o Unsupported explícito esperado y restauran estado. |
| IOS-CHAT-001 | P1 | BLOQUEADA | Completar Chat, adjuntos remotos y audio AVFoundation. | Contrato Chat/Storage acreditado por SB-04/05 y cuentas aisladas. | Simulador: texto/reply/adjuntos/retry/lifecycle entre dos usuarios. Dispositivo físico: micrófono, rutas de audio y background. En ambos se eliminan temporales y fixtures. |
| IOS-COMPOSER-001 | P1 | BLOQUEADA | Sustituir `iosComposerPublicationUnavailableRepository` por publicación real. | SEC-MUTATIONS-001 y Storage/RLS. | Texto, foto y vídeo publican y recargan; cancelación, límites, orientación y thumbnails funcionan; objetos se purgan en E2E. |
| IOS-PROFILE-001 | P1 | BLOQUEADA | Implementar mutaciones Profile/SOS, avatar y resolución de contactos. | SEC-RLS-003/004 y Storage. | Permisos denied/limited/cancel no corrompen sesión; edición, avatar y hasta cinco contactos pasan contra backend real. |
| IOS-OFFICIAL-001 | P1 | BLOQUEADA | Habilitar mutaciones Official operación a operación. | SEC-RLS-002. | Crear/like/comment/delete con actor funciona y outsider se deniega; E2E y UI verdes. |
| IOS-COMMUNITIES-001 | P1 | BLOQUEADA | Habilitar comentarios, follows, miembros y roles de Communities. | SEC-RLS-001-E2E, SEC-FOLLOWS-GUARD/RECON y lote Communities de SEC-MUTATIONS-001. | Matriz member/outsider/admin pasa y la UI deja de mostrar Unsupported sólo para operaciones acreditadas. |
| IOS-SIGN-001 | P0 | BLOQUEADA | Configurar App IDs `com.quata.ios`/`com.quata.ios.shareextension`, App Group `group.com.quata.ios.share`, Team, certificados, perfiles y Push. | Cuenta Apple Developer y roles administrativos. | Archive Release/IPA firmado; `codesign -d --entitlements` acredita perfiles de ambos targets, App Group y `aps-environment=production`. |
| IOS-TESTFLIGHT-001 | P0 | BLOQUEADA | Crear pipeline protegido de TestFlight. | IOS-SIGN-001, App Store Connect y API key segura. | Versión/build monotónicos, IPA procesada y visible a testers internos; logs/`xcresult` retenidos y rollback/retry documentados. |
| IOS-APNS-001 | P0 | BLOQUEADA | Registrar, rotar y revocar token APNs; validar proveedor y deep link. | IOS-SIGN-001, endpoint backend y dispositivo físico. | Push real llega a dispositivo firmado, tap abre destino y el token se trata como secreto operativo. |
| IOS-SHARE-001 | P0 | BLOQUEADA | Validar Share Extension/App Group en dispositivo. | IOS-SIGN-001 e IOS-CHAT-001. | Fotos/Files/URL/texto se encolan atómicamente, se reclaman una vez, se envían y limpian; límites 5/10 aplicados. |
| IOS-DEVICE-001 | P1 | BLOQUEADA | Ejecutar matriz física y visual de release. | IOS-TESTFLIGHT-001 y dispositivos/UDID. | iPhone pequeño/grande, iPad, iOS mínimo/actual, clean/upgrade, ES/EN, offline, permisos, Dynamic Type y VoiceOver documentados con capturas. |
| IOS-UITEST-001 | P1 | BLOQUEADA | Ampliar UI tests más allá de launch/relaunch. | DI por launch arguments y fixtures. | Auth/sesión, rutas, deep links, denegación/cancelación y cola Share tienen tests; APNs/App Group físicos permanecen separados y honestos. |
| IOS-RELEASE-001 | P2 | BLOQUEADA | Completar versionado, `xcconfig`, símbolos, privacidad y checklist App Review. | IOS-SIGN-001 y decisiones producto/legal. | Builds monotónicos; configuración/signing/runtime fuera de Git y fail-fast ante placeholders; dSYM disponible; Privacy Manifest y permisos/localización revisados para app y extensión. |

## Ola 4 — Paridad compartida y cierre técnico

| ID | P | Estado | Tarea | Dependencias | Criterio de aceptación |
| --- | --- | --- | --- | --- | --- |
| CHAT-PARITY-001 | P1 | BLOQUEADA | Añadir operaciones avanzadas comunes: participantes/admin, invitaciones, edición, borrado, favoritos, reenvío, retry, leave/report y SOS. | Contratos/RLS por operación. | Android no regresa; Web/iOS activan sólo capacidades E2E; ningún host inventa éxito local. |
| MEDIA-001 | P2 | PENDIENTE | Completar política común de vídeo/documentos, edición, exportación, thumbnails y formatos admitidos. | Decisiones de producto y límites de plataforma. | Matriz versionada por plataforma, fallos explícitos y fixtures reales; temporales/objetos se limpian. |
| L10N-A11Y-001 | P2 | PENDIENTE | Completar localización/mojibake y catálogo cross-platform de controles críticos; definir política Compose común. | Ninguna. | ES/EN/UTF-8 coherentes; nombre localizado, role, estado y orden de foco definidos; tests semánticos en design system. WEB-TEST-001 implementa sólo el puente AX/DOM. |
| JSIR-001 | P1 | PENDIENTE CI iOS | Retirar por lotes el JS IR que no es producto. | CI verde Web/Android/iOS por lote. | El lote final eliminó las declaraciones y source sets JS de los 12 módulos restantes, preservando Wasm. Web distribution/smoke y compilación Android pasan localmente; falta CI iOS sobre el SHA integrado. |
| WARNINGS-001 | P2 | PENDIENTE | Gobernar warnings por categoría y propietario. | CI-001. | Baseline con fecha/owner/límite para expect/actual beta, interop, deprecaciones y otras categorías; CI bloquea el crecimiento definido sin supresión global. |
| COVERAGE-001 | P1 | PENDIENTE | Definir matriz de tests y cobertura por módulo/target. | CI-001/002. | Runner y baseline/umbral por target compatible; JUnit y cobertura se reportan por separado; timeout es FAIL o infraestructura inconclusa con logs, nunca verde. |
| RELEASE-EVIDENCE-001 | P1 | PENDIENTE | Crear la fuente única viva de evidencia de release generada por CI; DOC-001 sólo sanea el corte histórico. | CI-001/002 y DOC-001. | Por plataforma registra SHA, compile/smoke/E2E/físico, entorno, fecha, artefacto y límites; README distingue Android publicado de Web/iOS parciales. |
| VAL-ANDROID-001 | P0 | BLOQUEADA | Gate proporcional de no regresión Android API-37 tras cada lote compartido. | Rama integrable validada. | Assemble/install/cold start, feed anónimo y rutas afectadas; PID vivo/crash buffer limpio sobre el SHA del lote. |
| FINAL-ANDROID-001 | P0 | BLOQUEADA | Gate global Android API-37. | Todas las olas. | Repite matriz completa de auth/feed anónimo/cinco verticales sobre SHA final y conserva evidencia. |
| FINAL-WEB-001 | P0 | BLOQUEADA | Gate de release Web. | CI-001, WEB-E2E-001, WEB-PUSH-001, WEB-DOCS-001, WEB-BUNDLE-001 y matriz explícita de mutaciones objetivo. | Cada dependencia y operación objetivo tiene evidencia por SHA; capacidades no objetivo permanecen fail-closed y documentadas. |
| FINAL-IOS-001 | P0 | BLOQUEADA | Gate de release iOS. | VAL-IOS-001/002, IOS-TESTFLIGHT-001, IOS-APNS-001, IOS-SHARE-001, IOS-DEVICE-001 y matriz funcional objetivo. | `xcresult` por arquitectura/runtime, TestFlight y evidencia física verdes; simulator nunca sustituye APNs/App Group/audio físico. |

## Bloqueos externos conocidos

| Bloqueo | Tareas afectadas | Acción para desbloquear |
| --- | --- | --- |
| Cuenta Apple Developer, Team, certificados y perfiles | IOS-SIGN-001 y posteriores | Confirmar App IDs, roles, certificados, provisioning y App Group. |
| App Store Connect/API key | IOS-TESTFLIGHT-001 | Crear app, permisos y secreto protegido de CI. |
| Proveedor APNs y dispositivo físico | IOS-APNS-001 | Configurar endpoint/provider y registrar dispositivo firmado. |
| Proveedor Web Push/cuenta aislada | WEB-PUSH-001 | Preparar staging, VAPID y navegador/dispositivo autorizado. |
| Aprobación legal/producto DocMentis | WEB-DOCS-001 | Resolver licencia, telemetría, updates, fuentes y CSP. |
| Ventanas de despliegue Supabase | Tareas SEC-* | Autorizar release serial con snapshot, gates y rollback. |
| Compatibilidad con APK Android antigua | SEC-RLS-003/004 | Medir adopción y fijar versión mínima/corte antes de revocar contratos legacy. |

## Orden inicial de despacho

1. En paralelo: OPS-IOS-001, DOC-001, CI-001 y diseño AUTH-BOUNDARY-001.
2. Tras preparar el Mac: OPS-IOS-002 → VAL-IOS-001 → VAL-IOS-002.
3. En paralelo con la frontera Auth: WEB-TEST-001, WEB-COMPOSER-001,
   SEC-MUTATIONS-001 y preparación de IOS-AUTH-001/IOS-COMPOSER-001 sin habilitar
   operaciones inseguras.
4. Cerrar guard/reconciliación de follows y frontera Auth antes de SEC-RLS-003/004A.
5. SEC-RLS-002 se libera aisladamente; nunca se mezcla con SEC-RLS-003/004.
6. Mutaciones Web/iOS se habilitan una por una tras el gate backend correspondiente.
7. Firma/TestFlight/APNs/Share se trabajan cuando estén disponibles las credenciales
   Apple; no bloquean el desarrollo en simulador.
8. Cada ola termina con VAL-ANDROID-001; FINAL-ANDROID-001 queda reservado al release global.
