# Wiki técnica de Qüata

Esta wiki explica el producto, su arquitectura y el proceso de migración de Qüata a Kotlin y Compose Multiplatform.

Qüata nació como aplicación Android. Android continúa siendo la referencia funcional y visual publicada mientras las mismas experiencias se conectan a Web/Wasm e iOS mediante raíces Compose compartidas.

## Qué es fuente de verdad

La autoridad documental se interpreta en este orden:

1. Código integrado en `main` y contratos ejecutables.
2. [Modelo operativo de migración](https://github.com/dossijeo/quata/blob/main/docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).
3. Matriz de capacidades e inventario de pantallas versionados.
4. Evidencia GO/NO-GO del commit exacto.
5. Esta wiki como explicación navegable de los anteriores.
6. Documentos fechados, planes y snapshots como material histórico.

Una rama o PR no describe el producto integrado. Una compilación verde tampoco acredita por sí sola funcionalidad, backend o paridad visual.

## Estado general

- **Android:** referencia publicada y contrato de compatibilidad.
- **Web:** aplicación Kotlin/Wasm y Compose real; la migración se acepta pantalla por pantalla.
- **iOS:** host Swift/UIKit que monta Compose Multiplatform; todavía requiere cierre funcional, firma y distribución.
- **Backend:** Supabase es el backend principal; WordPress conserva servicios de media y Firebase/Web Push/APNs cubren canales de notificación según plataforma.

Consulta [Estado del proyecto](10-Estado-del-proyecto.md) para distinguir lo integrado de los candidatos activos.

## Lectura recomendada

1. [Producto y alcance](01-Producto-y-alcance.md)
2. [Arquitectura multiplataforma](02-Arquitectura-multiplataforma.md)
3. [Plataformas y capacidades](03-Plataformas-y-capacidades.md)
4. [Backend y datos](04-Backend-y-datos.md)
5. [Navegación y autenticación](05-Navegacion-y-autenticacion.md)
6. [Migración multiplataforma](06-Migracion-multiplataforma.md)
7. [Desarrollo y builds](07-Desarrollo-y-builds.md)
8. [Pruebas, CI y evidencia](08-Pruebas-CI-y-evidencia.md)
9. [Seguridad y releases](09-Seguridad-y-releases.md)

## Cómo mantener esta wiki

Las páginas fuente viven en `docs/wiki` dentro del repositorio principal. Los cambios deben revisarse con el código mediante PR y sincronizarse después con la GitHub Wiki. No se deben editar dos versiones divergentes de la misma página.
