# Tablero operativo de migración multiplataforma

## Foto de control — 2026-07-29

**HEAD acreditado:** `main` `c87e82af615a1778092ec3b5ecfc70d1ecd485ea`. El proyecto no se declara terminado: Web e iOS tienen carriles funcionales limitados y continúan bloqueados los recorridos autenticados reales y la distribución iOS.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Producción Wasm, rutas herméticas, 3 Chrome fríos, DocMentis y Share Target. | No hay cuenta E2E segura ni automatización de login UI real. |
| Presupuesto Wasm | Integrado | Gate fail-closed, baseline aprobado Linux y captura canónica reproducible. | Windows es diagnóstico: el artefacto es host-dependiente. |
| Android | GO limitado | Build, `install -r`, arranque frío 4,924 s, Feed anónimo en Pixel y AVD temporal, 0 crash/ANR. | Matriz autenticada no ejecutada: el AVD de referencia quedó anónimo y el registro oficial está deshabilitado. |
| iOS CI | GO limitado | Run exacto `30413800836`, job `90455727104`, 70/70; Kotlin/Native, XCFramework, host, XCTest y archive sin firma. | No firma, IPA, TestFlight, APNs ni dispositivo físico. |
| iOS simulador Intel | GO limitado suplementario | CPU raster y Feed anónimo HTTPS 200 en iOS 18.3 e iOS 26.5, 0 crash. | Chat/login sólo contrato; no hay E2E visual por TCC/AX SSH. El relaunch iOS 26.5 sigue en HOLD de medición: casi negro a 8 s y Feed a 28 s. |
| RLS/DB | Sin cambios | PRs 93–97 y estas validaciones no cambiaron RLS, DDL, funciones ni grants. | Hallazgos existentes siguen abiertos y no se cierran aquí. |

## Integraciones recientes

| PR | Merge | Resultado |
| --- | --- | --- |
| [#93](https://github.com/dossijeo/quata/pull/93) | `7c260fa` | Watchdog Wasm estable, sin ventanas visibles y con limpieza fail-closed. |
| [#94](https://github.com/dossijeo/quata/pull/94) | `40dc604` | Gate de aprobación de baseline con allowlist estricta. |
| [#96](https://github.com/dossijeo/quata/pull/96) | `30682427` | Workflow de captura canónica Linux; run `30410233909`. |
| [#97](https://github.com/dossijeo/quata/pull/97) | `c87e82af` | Baseline Linux aprobado e integrado. |

## Próxima cola

1. Habilitar cuenta aislada y contrato seguro para E2E Web/Android/iOS, sin cambiar las políticas actuales ni automatizar un bypass de Turnstile.
2. Medir y resolver, si procede, la estabilidad de relaunch iOS 26.5.
3. Desbloquear TCC/AX de la VM y repetir la matriz visual autenticada iOS.
4. Preparar signing y prueba física iOS como carril separado de CI sin firma.
5. Definir SLO Web reproducible y completar la revisión de producción DocMentis.
6. Tratar RLS-001..005 en releases compatibles independientes; las mutaciones afectadas permanecen fail-closed.

## Decisiones técnicas vigentes

- Permanecen Kotlin `2.2.21` y Compose `1.10.0`. El experimento Kotlin `2.3.20`/Compose `1.11.0` queda rechazado porque Compose 1.11 no publica dependencias `iosX64`; tampoco se acreditó compatibilidad con el Skiko CPU-raster usado en la VM Intel.
- La rama que relajaba Metal no se integra. CI conserva el test Metal estricto; el raster CPU es un carril local suplementario, no una rebaja del contrato.
- La captura de tamaño canónica es Linux. En Windows el mismo conjunto de fuentes produce WASM/JS dependiente de host, por lo que no puede aprobar un baseline.
