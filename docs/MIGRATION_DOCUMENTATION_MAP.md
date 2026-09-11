# Mapa de conservación documental

Este mapa sirve para revisar la reorganización; no añade controles por iteración.
El [modelo breve](MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md) y sus siete runbooks
forman una única fuente de verdad. El esquema orienta; el detalle conserva las condiciones.

## Procedencia

- Base: `9afe514cea667ee3abbf9f9e726d8ac5c32dac77`; blob del modelo `44ae98047c947b917480be7fb2a1a09c167809af`.
- Texto trasladado: blob `c1013bfca630f4340ab7ef0b2a037ad9c58cf8c4`, observado en `8a80c45b7ac473a97608ef282f854890a73d0e4a` (#327).
- El único delta entre ambas fuentes son las 21 líneas de autorización permanente de promoción concedida por el propietario el 11 de septiembre de 2026. No se importan cambios funcionales de esa PR.
- Los adjuntos disponibles son el esquema y el README; los runbooks se reconstruyen desde ese blob Git, sin atribuir a esta entrega las verificaciones del paquete no adjuntado.
- Los bloques del detalle conservan el texto original salvo dos enlaces relativos y dos referencias espaciales convertidas en enlaces. Los títulos y anclas permiten consultar cada tema; no cambian su alcance.

## Destino del texto original

| Sección original | Destino íntegro |
|---|---|
| 1. Objetivo y referencia de producto | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#1-objetivo-y-referencia-de-producto) |
| 2. Contrato de navegación y autenticación | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#2-contrato-de-navegación-y-autenticación) |
| Superficies públicas | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#superficies-públicas) |
| Superficies y acciones privadas | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#superficies-y-acciones-privadas) |
| Contrato Web específico | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#contrato-web-específico) |
| 9. Criterios que nunca justifican un atajo | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#9-criterios-que-nunca-justifican-un-atajo) |
| 10. Cierre de la migración | [Producto y navegación](runbooks/migration/PRODUCT_AND_NAVIGATION.md#10-cierre-de-la-migración) |
| 3. Seguridad y compatibilidad con producción | [Seguridad y operaciones remotas](runbooks/migration/SECURITY_AND_REMOTE.md#3-seguridad-y-compatibilidad-con-producción) |
| 4. Flujo por pantalla | [Flujo de candidata e integración](runbooks/migration/WORKFLOW.md#4-flujo-por-pantalla) |
| Two-lane migration pipeline + native auto-merge | [Flujo de candidata e integración](runbooks/migration/WORKFLOW.md#two-lane-migration-pipeline--native-auto-merge) |
| 5. Evidencia y gates | [Evidencia y certificación](runbooks/migration/EVIDENCE_AND_CI.md#5-evidencia-y-gates) |
| Preflight local, candidato y certificación remota | [Evidencia y certificación](runbooks/migration/EVIDENCE_AND_CI.md#preflight-local-candidato-y-certificación-remota) |
| Product/Evidence SHA y attestation documental | [Evidencia y certificación](runbooks/migration/EVIDENCE_AND_CI.md#productevidence-sha-y-attestation-documental) |
| Identidad obligatoria del candidato integrado | [Evidencia y certificación](runbooks/migration/EVIDENCE_AND_CI.md#identidad-obligatoria-del-candidato-integrado) |
| Inventario = estado operativo actual | [Ramas y cierre operativo](runbooks/migration/BRANCHES_AND_CLOSEOUT.md#inventario--estado-operativo-actual) |
| 6. Ramas, commits y PR | [Ramas y cierre operativo](runbooks/migration/BRANCHES_AND_CLOSEOUT.md#6-ramas-commits-y-pr) |
| 7. Presupuesto de ejecución y procesos | [Entorno y presupuesto de ejecución](runbooks/migration/EXECUTION_ENVIRONMENT.md#7-presupuesto-de-ejecución-y-procesos) |
| Informes durante procesos largos | [Entorno y presupuesto de ejecución](runbooks/migration/EXECUTION_ENVIRONMENT.md#informes-durante-procesos-largos) |
| 8. Runtimes estables | [Entorno y presupuesto de ejecución](runbooks/migration/EXECUTION_ENVIRONMENT.md#8-runtimes-estables) |
| Mac Hyper-V sin Metal: renderer raster CPU | [Entorno y presupuesto de ejecución](runbooks/migration/EXECUTION_ENVIRONMENT.md#mac-hyper-v-sin-metal-renderer-raster-cpu) |
| Testing por capas: preferencia para nuevas unidades y suites con churn real | [Estrategia de testing](runbooks/migration/TESTING_STRATEGY.md#testing-por-capas-preferencia-para-nuevas-unidades-y-suites-con-churn-real) |

La introducción y la precedencia permanecen en el modelo principal. Sus 22 anclas originales se conservan mediante títulos o anclas explícitas.

## Distinciones conservadas

- Ready/promoción y certificación final: la autorización permanente permite promover tras revisión, preflight y evidencia válidos; la certificación final determina el merge. La frase original del paso 8 se lee junto a esa autorización posterior, conservada íntegra en WORKFLOW.
- Head e integración: el head aislado es diagnóstico; el GO integrado exige el merge sintético y sus padres exactos. La reutilización de evidencia sigue requiriendo la regla formal y la revisión del diff.
- Handoff y cierre: liberar trabajo activo durante certificación no cierra el inventario; el cierre documental posterior sigue siendo obligatorio para la unidad funcional.
- `docs_only` y attestation: la clasificación barata no amplía la allowlist de reutilización. Los nuevos runbooks y este mapa no se añaden a ella; esta PR no acredita producto ni cambia evidencias.
- El contrato del recorder sigue encontrando en el modelo breve la grabación de macro visual y `missing_stable_anchor`; el procedimiento íntegro se consulta en EVIDENCE_AND_CI.
- Los nombres de agentes, rutas y referencias de simuladores trasladados son reglas/contexto de la fuente, no un descubrimiento nuevo del entorno. No se inventa un comando ni se amplía autorización para un helper del host.

Inventario, producto, CI, autorización remota independiente y evidencias permanecen fuera de esta refactorización.
