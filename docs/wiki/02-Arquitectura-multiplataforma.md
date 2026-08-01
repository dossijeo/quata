# Arquitectura multiplataforma

## Objetivo

Android, Web e iOS deben ejecutar la misma pantalla de producto definida en Kotlin y Compose dentro de `commonMain`.

```text
commonMain
├─ raíz Compose de producto
├─ estado y ViewModel
├─ reglas y validación
├─ eventos visibles
├─ navegación de producto
└─ contratos de repositorio y plataforma
   ├─ Android adapters
   ├─ Web/Wasm adapters
   └─ iOS adapters
```

## Qué debe ser común

- Composición completa de la pantalla.
- Estado y transiciones de estado.
- Reglas de negocio y validación.
- Carga, vacío, error, reintento y progreso.
- Decisiones de autenticación.
- Eventos que ve o activa el usuario.
- Navegación entre superficies del producto.
- Contratos de repositorio.
- Catálogos de texto cuando sea viable.

Compartir únicamente modelos, fragmentos visuales o un ViewModel no cumple el objetivo.

## Qué puede ser específico

- Permisos del sistema.
- Cámara, galería y selector de ficheros.
- Grabación y reproducción multimedia.
- Edición/exportación que dependa de codecs de plataforma.
- Notificaciones y registro de tokens.
- Keychain, preferencias del navegador o almacenamiento Android.
- Lifecycle y contenedores UIKit/Activity/browser.
- Transporte HTTP cuando las librerías disponibles difieran.
- Renderizadores nativos de documentos.

Estos adaptadores ofrecen capacidades a la raíz común; no poseen una segunda versión de la pantalla.

## Hosts

- `app/` monta las raíces comunes desde Android y conserva servicios Android existentes.
- `web/` arranca Compose/Wasm dentro de `#quata-root` y proporciona APIs del navegador.
- `ios-shared/` exporta el framework Kotlin/Native.
- `iosApp/` contiene el host Swift/UIKit y adapta servicios Apple.

El `index.html` Web debe ser un host mínimo. Los controles de producto viven en el canvas Compose, no en un formulario HTML paralelo.

## Datos y efectos

Los repositorios comunes expresan operaciones reales. Cada plataforma puede implementar el transporte de manera distinta, pero debe respetar el mismo contrato observable:

- Los reads devuelven datos remotos o errores honestos.
- Las mutaciones sólo finalizan con éxito tras la persistencia remota requerida.
- Los caches aceleran o permiten lectura offline, pero no sustituyen una mutación remota.
- Cancelación y timeout se propagan sin convertir errores en éxito.

## Dependencias principales

- Kotlin Multiplatform.
- Compose Multiplatform.
- Coroutines y Flow.
- kotlinx.serialization.
- Supabase/PostgREST/RPC/Realtime.
- Adaptadores Android, browser y Foundation/UIKit.

Para decisiones o excepciones puntuales se debe crear una decisión arquitectónica fechada, no modificar silenciosamente el contrato general.
