# Plataformas y capacidades

## Android

Android es el producto publicado y la referencia temporal de compatibilidad. Usa Jetpack Compose y conserva servicios específicos como CameraX, Media3, FCM, WorkManager, ubicación y almacenamiento local.

La migración también debe hacer que Android monte la raíz común. No es suficiente añadir la raíz para Web/iOS y mantener una pantalla Android distinta.

## Web/Wasm

Web se entrega como Kotlin/Wasm con Compose Multiplatform. El documento HTML contiene el contenedor de montaje, metadatos públicos de despliegue y scripts del bundle.

Responsabilidades propias del host Web:

- Sesión y almacenamiento del navegador.
- Fetch, Web Push y service worker.
- Selectores y APIs multimedia del navegador.
- Deep links mediante rutas hash.
- Configuración pública inyectada en el artefacto.

No deben existir formularios o pantallas HTML que sustituyan a Compose.

## iOS

iOS usa un host Swift/UIKit y un framework Kotlin/Native generado desde `ios-shared`. Los controladores deben montar las raíces Compose exportadas.

Responsabilidades propias del host iOS:

- UIKit y lifecycle.
- Keychain y sesión renovable.
- Photos/File pickers, cámara y media.
- Quick Look y servicios del sistema.
- APNs y firma.
- Navegación nativa necesaria para alojar la navegación común.

Una build de simulador o un archive sin firma no acredita distribución en dispositivo ni TestFlight.

## Estados de capacidad

La matriz `capabilities/platform-capability-matrix.json` utiliza:

| Estado | Significado |
|---|---|
| `implemented` | Todas las operaciones declaradas poseen implementación ejecutable. |
| `read-only` | Sólo está disponible el flujo de lectura. |
| `contract-only` | Existe contrato, pero la ejecución de producción permanece cerrada. |
| `blocked` | El adaptador falla de forma explícita para esa capacidad. |
| `external` | Depende de firma, sistema operativo o servicio de entrega externo. |

Esta matriz es un inventario técnico, no un GO visual. Sus hashes impiden cambiar una implementación sin revisar también la declaración de capacidad.

## Regla de interpretación

Una capacidad se considera parte del producto multiplataforma sólo cuando coinciden:

1. Raíz común montada.
2. Adaptadores reales.
3. Navegación y autenticación correctas.
4. Backend real para reads y mutaciones.
5. Compilación y pruebas de las tres plataformas.
6. Evidencia visual/funcional del mismo SHA.
