# Tablero operativo de migración multiplataforma

## Foto de control — 2026-07-29

**HEAD acreditado:** `main` `185960769011f03d644130012fe8051ad536e9bf`.
El proyecto sigue incompleto: la navegación pública Web/Android está validada y
iOS cuenta con una matriz pública real de simulador, pero ninguna de esas
evidencias habilita mutaciones, distribución ni un rollout de backend.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Producción observada, cinco perfiles Chrome fríos, rutas profundas/recarga, diseño adaptable, foco y árbol de accesibilidad ligados a nodos. | Login y demás recorridos autenticados no se declaran listos; la ejecución con cuenta aislada está en validación y permanece HOLD hasta que termine con limpieza verificable. |
| Presupuesto Wasm | Integrado | Watchdog sin ventanas visibles, baseline Linux aprobado y captura canónica reproducible. | Windows sigue siendo diagnóstico: el artefacto Wasm/JS depende del host. El presupuesto es un gate técnico, no un SLO de producto. |
| Android | GO limitado | Build, `install -r`, arranque frío y Feed anónimo API-37 con 0 crash/ANR tras cold boot. | Falta matriz autenticada controlada; no se modifica Android publicado ni el Feed anónimo. |
| iOS CI | GO limitado | Run [`30425431607`](https://github.com/dossijeo/quata/actions/runs/30425431607), job `90490809295`, verde sobre `ba6a72a`: Kotlin/Native, XCFramework, host Swift, XCTest, archive sin firma y contratos de matriz pública. | El run no es una IPA firmada, TestFlight, APNs ni dispositivo físico. |
| iOS simulador | GO funcional suplementario | Feed público HTTPS 200 en iOS 18.3 e iOS 26.5; matriz endurecida serial, con OCR/captura, PID/logs limitados y limpieza. | CPU-raster Intel es evidencia funcional, no SLA. La autenticación visual está en validación/HOLD hasta resultado final. |
| Capacidades y rutas | Integrado | Manifest fail-closed por plataforma y contratos de factorías/rutas iOS integrados por PRs #101 y #103. | Una ruta o contrato no prueba por sí solo el backend, permisos ni E2E. |
| RLS/DB | Sin cambios | Esta ola no cambió RLS, DDL, funciones, grants ni datos de Supabase. | Hallazgos existentes siguen abiertos; no se endurecen políticas mientras convivan clientes publicados. |

## Integraciones recientes

| PR | Merge | Resultado |
| --- | --- | --- |
| [#98](https://github.com/dossijeo/quata/pull/98) | `801dcbad` | Evidencia final inicial del corte `c87`. |
| [#99](https://github.com/dossijeo/quata/pull/99) | `f38503b9` | Arnés de cinco muestras de rendimiento Web; observación, no SLO. |
| [#100](https://github.com/dossijeo/quata/pull/100) | `be56cacf` | UX pública Web: responsive, accesibilidad, deep links y recarga. |
| [#101](https://github.com/dossijeo/quata/pull/101) | `54a2f07e` | Matriz de capacidades fail-closed. |
| [#102](https://github.com/dossijeo/quata/pull/102) | `32f1bb65` | Matriz pública endurecida para simuladores iOS. |
| [#103](https://github.com/dossijeo/quata/pull/103) | `9344b5fa` | Contratos de rutas y factorías iOS instaladas. |
| [#104](https://github.com/dossijeo/quata/pull/104) | `18596076` | Requisitos de producción APNs y hallazgo del dispatcher. |

## Próxima cola

1. Cerrar la validación autenticada iOS con cuenta aislada: login, refresh, logout y relanzamiento; clasificar el resultado y limpiar la sesión. No publicar credenciales en evidencia.
2. Configurar firma Apple en un carril separado: Team, identificador, perfiles, App Group y archive/IPA firmado. El archive actual entregado es deliberadamente **sin firma**.
3. Completar APNs en backend y un dispositivo físico firmado según [IOS_APNS_PRODUCTION_REQUIREMENTS.md](IOS_APNS_PRODUCTION_REQUIREMENTS.md); el dispatcher actual aún no tiene un canal APNs verificado.
4. Ejecutar E2E Web/Android autenticado sólo con datos y limpieza autorizados.
5. Mantener RLS-001..005 en releases compatibles independientes; no cambiar políticas en este backlog.

## Decisiones vigentes

- Kotlin `2.2.21` y Compose `1.10.0` permanecen fijados. Kotlin `2.3.20`/Compose `1.11.0` se rechazó porque Compose 1.11 no publica `iosX64` para el carril Intel y no acredita el Skiko CPU-raster.
- CI conserva la prueba Metal estricta. CPU-raster Intel es un carril suplementario, no una relajación del contrato.
- `migrationComplete`, `webReady` e `iosReady` siguen siendo `false` hasta terminar los gates externos de autenticación, firma, APNs/dispositivo y backend.
