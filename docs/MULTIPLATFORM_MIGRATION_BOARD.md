# Tablero operativo de migración multiplataforma

> Fuente de verdad del método de trabajo: [`MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md`](./MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
> Este tablero es una fotografía de progreso y no puede redefinir los gates, la arquitectura ni el
> presupuesto de ejecución establecidos allí.

## Foto de control — 2026-08-02

**HEAD integrado:** `main` `5d2a52d1` (PR #156), posterior a #154. El proyecto sigue incompleto:
las raíces comunes de Crear publicación y Cuenta/Perfil/SOS están integradas, pero integración de
raíz no equivale a GO visual o funcional final en Web/iOS.

| Área | Estado | Qué acredita | Límite vigente |
| --- | --- | --- | --- |
| Web/Wasm | GO limitado | Shell público, rutas principales y varias raíces Compose comunes están integrados; #154 incorpora `CreatePostRoot` y #156 `ProfileScreenHost`. | Faltan postflights autenticados por flujo y paridad visual exacta. Avatar Web se acredita por contratos, no por una mutación E2E real guardada y limpiada. |
| Presupuesto Wasm | Integrado | Watchdog sin ventanas visibles, baseline Linux aprobado y captura canónica reproducible. | Windows sigue siendo diagnóstico: el artefacto Wasm/JS depende del host. El presupuesto es un gate técnico, no un SLO de producto. |
| Android | GO limitado | Build, `install -r`, arranque frío y Feed anónimo API-37 con 0 crash/ANR tras cold boot. | Falta matriz autenticada controlada; no se modifica Android publicado ni el Feed anónimo. |
| iOS CI | GO limitado | CI y contratos Swift/Kotlin siguen siendo gates obligatorios; #156 restauró el acceso Swift/Kotlin requerido por el host. | No prueba IPA/TestFlight/APNs/dispositivo físico ni postflight visual exacto de Cuenta/Perfil/SOS. |
| iOS simulador | GO funcional suplementario | Feed público y la lane CPU-raster Intel son utilizables para validar Compose en Hyper-V. | Ejecutar el postflight exacto de #156: perfil público, Cuenta/SOS autenticado, recuperación de error de sesión y comparación Android↔iOS. CPU-raster no es SLA ni reemplaza CI ARM. |
| Crear publicación (#154) | COMÚN con límites | `CreatePostRoot` común está integrado en Android, Wasm e iOS. | La evidencia de #154 no debe presentarse como GO visual/funcional final: validar publicación, adaptadores de medios y paridad autenticada sin modificar RLS. |
| Cuenta/Perfil/SOS (#156) | COMÚN con límites | `ProfileScreenHost` común está integrado en Android, Wasm e iOS; editor de avatar Web contractual. | Falta postflight visual/funcional iOS y mutación E2E Web de avatar con archivo/cuenta temporal y limpieza, o declaración explícita de capacidad pendiente. |
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
| [#106](https://github.com/dossijeo/quata/pull/106) | `d8652326` | UI de logout autenticado iOS; CI iOS verde. |
| [#154](https://github.com/dossijeo/quata/pull/154) | `68d1fab7` | `CreatePostRoot` integrado como raíz común. No atribuye GO visual ni publicación E2E acreditada. |
| [#156](https://github.com/dossijeo/quata/pull/156) | `5d2a52d1` | `ProfileScreenHost` común para Cuenta/Perfil/SOS y editor de avatar Web contractual. Postflight iOS y mutación E2E avatar pendientes. |

## Registro de candidato #156 y mejora de preflight

La candidata congelada de #156 requirió **tres rondas finales de certificación**. Dos defectos se
escaparon del preflight local y se registran para no maquillarlos como incidencias normales de CI:

| Defecto escapado del preflight local | Corrección integrada | Gate preventivo |
| --- | --- | --- |
| La factoría Kotlin requerida por Swift no estaba disponible al construir el host iOS. | Se restauró la factoría/puente Swift-Kotlin. | Añadido: compilar Kotlin/Native y construir el host Swift localmente antes de publicar. |
| El gateway de perfil impedía la lectura pública sin sesión. | Se corrigió el fallback público del gateway. | Pendiente de consolidar como gate focal: arrancar el perfil público iOS con sesión ausente/expirada y acreditar lectura y recuperación antes de publicar. |

Las rondas anteriores al congelado sólo fueron diagnósticas; no se reutilizan como evidencia final.
La evidencia final exige base, head y merge sintético exactos según el modelo operativo.

## Próxima cola

1. Ejecutar y guardar el postflight exacto de #156: Android↔Wasm↔iOS para Cuenta/Perfil/SOS, perfil público sin sesión, recuperación de sesión y estados de error. Acreditar o dejar pendiente la subida real de avatar Web con datos temporales y limpieza.
2. Ejecutar el postflight de #154: Crear publicación común en Web/iOS con sesión real, adaptadores de medios y comparación visual; no convertir sus contratos en un GO no probado.
3. Aplicar el modelo de integración secuencial/ejecución paralela: una candidata final, preflight local completo, merge sintético y CI como certificación. Preparar localmente la siguiente unidad mientras CI está activo, sin publicar candidatos adicionales.
4. Publicar después de #156 una PR aislada de pipeline: cancelación por PR, nunca para `main` ni `workflow_dispatch`, y separar lane rápida de lane completa de candidato.
5. Configurar firma Apple y completar APNs/dispositivo físico en carriles independientes. Mantener RLS-001..005 documentados; no cambiar políticas en este backlog.

## Decisiones vigentes

- Kotlin `2.2.21` y Compose `1.10.0` permanecen fijados. Kotlin `2.3.20`/Compose `1.11.0` se rechazó porque Compose 1.11 no publica `iosX64` para el carril Intel y no acredita el Skiko CPU-raster.
- CI conserva la prueba Metal estricta. CPU-raster Intel es un carril suplementario, no una relajación del contrato.
- `migrationComplete`, `webReady` e `iosReady` siguen siendo `false` hasta terminar los gates externos de autenticación, firma, APNs/dispositivo y backend.
