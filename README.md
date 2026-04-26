# Qüata Android — V3 Fused Base

Proyecto base Android para **Qüata**, fusionando:

- arquitectura MVVM con `ViewModel`, `UiState` y `UiEvent`
- `domain/` con interfaces de repositorio y casos de uso
- repositorios reales preparados para WordPress y Supabase
- modo mock por defecto para poder abrir y navegar sin credenciales
- diseño oscuro con acentos naranja inspirado en el mockup
- navegación inferior tipo Instagram
- login email/password + botón Google preparado
- feed, publicación, chat, notificaciones y perfil
- helpers de cámara, sesión, preferencias, red, notificaciones y datos mock centralizados

## Abrir en Android Studio

1. Descomprime el ZIP.
2. Abre la carpeta raíz `quata_v3_fused` con Android Studio.
3. Espera a que sincronice Gradle.
4. Ejecuta el módulo `app`.

## Modo mock y modo real

Por defecto el proyecto usa datos mock:

```kotlin
buildConfigField("boolean", "USE_MOCK_BACKEND", "true")
```

Cuando quieras probar llamadas reales:

1. Cambia a `false` en `app/build.gradle.kts`.
2. Rellena los placeholders en `core/config/AppConfig.kt`.
3. Configura WordPress JWT Auth / endpoint de registro.
4. Configura Supabase REST, tablas y policies.
5. Añade Firebase si quieres push real.

## Configuración principal

Archivo:

```text
app/src/main/java/com/quata/core/config/AppConfig.kt
```

Ahí tienes:

- `WORDPRESS_BASE_URL`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `FEED_SOURCE`
- nombres de tablas Supabase

## Nota

El proyecto está diseñado para compilar como base funcional. Las integraciones reales pueden requerir adaptar endpoints, tablas, campos, permisos y plugins concretos de WordPress/Supabase.
