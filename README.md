# Qüata

Qüata es una aplicación social y comunitaria escrita en Kotlin. La aplicación Android publicada es actualmente la referencia funcional y visual; el proyecto está migrando sus flujos a **Kotlin y Compose Multiplatform** para reutilizar el mismo producto en Android, Web/Wasm e iOS.

> [!IMPORTANT]
> La migración multiplataforma está en curso. Que un módulo compile para varias plataformas no significa que su pantalla, navegación o backend estén terminados. El estado aceptado se determina por SHA y por los criterios GO/NO-GO del modelo operativo.

## Producto

Qüata reúne:

- Feed público y muro Oficial.
- Comunidades, perfiles y relaciones sociales.
- Conversaciones privadas, grupales, comunitarias y SOS.
- Publicación de texto, imagen y vídeo.
- Comentarios, reacciones, moderación y notificaciones.
- Traducción Fang y traducción oficial ES/EN/FR.
- Lectura y previsualización de documentos.
- Navegación pública con autenticación sólo para acciones privadas.

La versión Android de referencia es `1.0.4` (`versionCode 32`). Los ports Web e iOS no deben reescribir estas experiencias con pantallas paralelas: deben montar las mismas raíces Compose de `commonMain` y limitar el código específico a servicios de plataforma.

## Estado multiplataforma

Estado resumido, no sustitutivo de la evidencia exacta:

| Superficie | Situación |
|---|---|
| Android | Producto publicado y referencia de compatibilidad. |
| Web/Wasm | Aplicación Compose/Wasm real. Feed y Oficial tienen integración acreditada; el resto continúa por pantalla y por gate. |
| iOS | Host Swift + Compose Multiplatform en desarrollo. Hay flujos públicos y adaptadores reales, pero la aplicación completa todavía no está lista para distribución. |

Consulta la [Wiki de Qüata](https://github.com/dossijeo/quata/wiki) y, para el estado por pantalla, el [inventario versionado](docs/SCREEN_MIGRATION_INVENTORY_V2.md).

## Arquitectura

```text
                 ┌────────────────────────────┐
                 │ commonMain                 │
                 │ UI Compose, estado, reglas │
                 │ navegación y contratos     │
                 └─────────────┬──────────────┘
             ┌─────────────────┼─────────────────┐
             │                 │                 │
       Android host       Web/Wasm host       iOS host
       servicios OS       APIs browser        Swift/UIKit + OS
             │                 │                 │
             └─────────────────┼─────────────────┘
                               │
                  Supabase · WordPress · Push
```

Principios obligatorios:

1. Android publicado es la referencia durante la migración.
2. Las tres plataformas montan la misma raíz común de producto.
3. Estado, reglas, eventos visibles y navegación de producto viven en común.
4. Cámara, pickers, ficheros, reproducción, permisos, push y lifecycle son adaptadores.
5. No se aceptan callbacks vacíos, repositorios de disponibilidad ficticia ni éxitos locales presentados como remotos.
6. Cada GO corresponde a un commit exacto con CI, backend y comparación visual/funcional.

La definición completa está en el [modelo operativo](docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md).

## Módulos principales

```text
app/                 Host y producto Android
core/                Contratos y servicios compartidos
designsystem/        Tema y componentes Compose comunes
feature/             Funcionalidades KMP por dominio
  auth/              Login, registro y recuperación
  feed/              Feed
  official/          Muro Oficial
  chat/              Conversaciones, mensajes y SOS
  neighborhoods/     Comunidades y perfiles públicos
  profile/           Cuenta y configuración SOS
  postcomposer/      Publicación de contenido
  notifications/     Avisos internos
  whatsnew/          Novedades e historial
web/                 Host Kotlin/Wasm
ios-shared/          Framework exportado al host iOS
iosApp/              Aplicación Swift/UIKit
supabase/            Migraciones, RPC y Edge Functions
docs/                Fuente técnica canónica
```

## Configuración y secretos

Los clientes usan únicamente configuración pública. No se deben almacenar en Git claves `service_role`, tokens, contraseñas, certificados, claves APNs/FCM ni credenciales de prueba.

Variables públicas habituales:

- `QUATA_SUPABASE_URL`
- `QUATA_SUPABASE_PUBLISHABLE_KEY`
- `QUATA_WORDPRESS_BASE_URL`

Los secretos de Edge Functions, firma y proveedores se configuran en sus gestores correspondientes. Consulta la Wiki antes de preparar un entorno o un release.

## Compilación rápida

Requisitos locales de Android/Web: JDK 17 y Android SDK con `compileSdk 36`.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat :web:wasmJsBrowserDistribution
```

La compilación iOS requiere macOS/Xcode y los carriles documentados en [docs/IOS_CI.md](docs/IOS_CI.md).

## Documentación

- [Wiki de Qüata](https://github.com/dossijeo/quata/wiki): guía técnica navegable en español.
- [Modelo operativo de migración](docs/MULTIPLATFORM_MIGRATION_OPERATING_MODEL.md): norma vinculante del pipeline.
- [Inventario de pantallas](docs/SCREEN_MIGRATION_INVENTORY_V2.md): estado por superficie.
- [Matriz de capacidades](capabilities/platform-capability-matrix.json): disponibilidad ejecutable por plataforma.
- [Seguridad de base de datos](docs/DATABASE_RELEASE_SAFETY.md): restricciones para cambios Supabase.
- [CI Web/Android](docs/CI_WEB_ANDROID.md) e [iOS](docs/IOS_CI.md): gates reproducibles.

Los documentos fechados, snapshots, evaluaciones y planes de corrección son evidencia histórica. No prevalecen sobre el código de `main`, el modelo operativo ni un gate exacto posterior.
