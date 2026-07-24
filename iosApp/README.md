# Host iOS mínimo

El host SwiftUI sólo presenta `QuataFeedViewController`, creado en `:feature:feed/iosMain` con Compose Multiplatform y UI de `commonMain`.

Los adaptadores iOS de portapapeles (`IosClipboardService`) y preferencias (`IosPreferenceStore`) ya existen en `:core/iosMain`, pero este launcher no los puede conectar todavía: `QuataFeedViewController()` no recibe dependencias y la pantalla de estado no consume esos contratos. La siguiente ampliación debe introducir una API de composición/inyección en el módulo compartido y enlazar el framework que la exporte; no se añade un contenedor Swift sin consumidores.

`ShareService` requiere que el host presente `UIActivityViewController`; `LocationService` necesita un `CLLocationManager` delegado y la clave `NSLocationWhenInUseUsageDescription`; y `PermissionService` debe coordinar los delegados de cámara, micrófono, fotos, contactos, ubicación y notificaciones, además de sus claves de `Info.plist` y permisos. Ninguno puede implementarse correctamente en el launcher actual sin esa API de inyección y configuración nativa, por lo que siguen explícitamente no disponibles en iOS.

En macOS, genera el proyecto con XcodeGen (`xcodegen generate`) desde esta carpeta y construye primero el framework `QuataFeed` para el simulador iOS:

```sh
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
```

Windows no puede enlazar los targets nativos de iOS; la compilación Wasm y Android continúa siendo la verificación disponible en este entorno.
