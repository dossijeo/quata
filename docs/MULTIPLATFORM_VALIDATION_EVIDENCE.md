# Evidencia de validación multiplataforma

**Corte:** `c87e82af615a1778092ec3b5ecfc70d1ecd485ea` (2026-07-29).

Las categorías se mantienen separadas: una compilación acredita artefactos; un smoke acredita el recorrido descrito; E2E acredita backend real, identidad y limpieza. Ninguna fila extiende su alcance por inferencia.

## Web/Wasm

| Prueba | Resultado | Evidencia y límite |
| --- | --- | --- |
| Producción observada | GO | 510,69 s; limpieza del Job Object vacía. |
| Bundle Windows diagnóstico | GO dentro de margen | 37.164.745 bytes / 14.251.181 gzip, variación +274 / +91 contra el baseline Linux. No es una aprobación Windows. |
| Chrome frío | GO | Tres perfiles fríos; total 150 s. Rutas Auth, Feed, Chat, Official, Settings y Share Target herméticas. |
| DocMentis / Share Target | GO limitado | Smoke local de import/create/destroy y contrato local de Share; no acredita licencia, CSP, Storage autenticado ni instalación PWA real. |
| `wasmJsBrowserTest` | GO | 37/37. |
| E2E autenticado | HOLD | No existe una cuenta de prueba segura; no se creó cuenta ni se evitó Turnstile. |

La captura canónica Linux del run [`30410233909`](https://github.com/dossijeo/quata/actions/runs/30410233909) registró 14 assets, 37.164.471 bytes, 14.251.090 bytes gzip e inventario `adf71ab7455205f2a7baac38443c310742334bee91ecc745ef47e58937755827`. Ese artefacto fue aprobado por PR [#97](https://github.com/dossijeo/quata/pull/97). La reproducibilidad fue confirmada entre ejecuciones Linux; Windows difiere en WASM y JS por paths/contenido de host, de modo que se conserva sólo como observación local.

## Android API-37

| Prueba | Resultado | Límite |
| --- | --- | --- |
| Compilación e instalación `-r` | GO | Sobre `c87e82af`. |
| Arranque frío | GO | 4,924 s, proceso vivo y 0 crash/ANR observado. |
| Feed anónimo | GO | Verificado en Pixel y en AVD temporal, sin tocar la navegación anónima. |
| Matriz autenticada | HOLD | El AVD de referencia pasó a anónimo y el registro oficial está deshabilitado; no se usó un bypass ni se alteraron cuentas. |

## iOS

| Carril | Resultado | Límite |
| --- | --- | --- |
| GitHub Actions | GO limitado | Run [`30413800836`](https://github.com/dossijeo/quata/actions/runs/30413800836), job `90455727104`, SHA exacto `c87e82af`: 70/70, Kotlin/Native, framework/XCFramework, host Swift, XCTest, archive y artefactos. |
| Simulador Intel CPU raster | GO suplementario | Feed anónimo real en iOS 18.3 e iOS 26.5, respuestas HTTPS 200 y 0 crash. La estabilidad de relaunch iOS 26.5 queda HOLD: captura casi negra a 8 s y Feed a 28 s; medición en curso. |
| Chat/login visual real | HOLD | El contrato de rutas pasa, pero TCC/AX a través de SSH impide automatizar la interacción visual. No se afirma autenticación. |
| Signing/distribución | HOLD | Sin Team, perfiles, IPA, TestFlight, APNs, App Group firmado ni dispositivo físico. |

## Seguridad y límites de producción

No se desplegaron cambios de Supabase ni se modificaron RLS, DDL, funciones o grants durante PRs 93–97 o estas validaciones. Los hallazgos continúan abiertos en [RLS_FINDINGS.md](RLS_FINDINGS.md). Las mutaciones con contrato no acreditado continúan fail-closed.

## Conclusión del corte

Web es funcional para el recorrido anónimo verificado y tiene controles de bundle reproducibles; no está lista para declarar login/registro ni release autenticado. iOS compila, enlaza y muestra Feed anónimo en simulador, pero no está listo para distribución ni autenticación visual. La migración global sigue incompleta.
