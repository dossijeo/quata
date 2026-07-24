# Host iOS mínimo

`feature:feed/iosMain` expone `QuataFeedViewController(dependencies:)`. Al recibir un
`FeedRepository` iOS real, presenta `FeedBrowserHostContent`, cuyo ViewModel, estado y UI son
código Compose de `commonMain`; el adaptador iOS se limita a crear un `UIViewController`.

El launcher SwiftUI no fabrica un repositorio de ejemplo ni reutiliza el repositorio Android. Por
ahora muestra de forma explícita el estado de migración, hasta que exista un cliente autenticado
iOS que implemente `FeedRepository`. Conectar ese repositorio consiste en crear
`IosFeedHostDependencies(repository: …)` y pasarlo al `FeedRootView` documentado en el entry
point Swift.

Los adaptadores iOS de portapapeles (`IosClipboardService`) y preferencias (`IosPreferenceStore`)
ya existen. `IosPlatformServices` también agrupa compartir y selector de archivos, que necesitan
un presenter/document picker UIKit activo antes de poder declararse disponibles.

Para el selector de documentos, `IosFilePickerService.attachDocumentPicker` recibe un
`IosViewControllerProvider` y conecta el adaptador real `IosDocumentPickerHost`. Éste presenta
`UIDocumentPickerViewController` en modo import, conserva su delegate hasta el callback y
devuelve `PlatformFile` con URL, nombre y MIME conocido. El launcher no lo conecta todavía
porque ninguna de sus pantallas de estado consume `PlatformServices`; se debe adjuntar al crear
el host autenticado que vaya a usar una feature con archivos.

El mismo `IosViewControllerProvider` activa `IosUIKitSharePresenter` al construir
`IosPlatformServices`: `ShareService` presenta el `UIActivityViewController` real en el
controlador activo y devuelve `Unsupported` si el host no puede presentar. Sin provider, los
servicios conservan el comportamiento seguro de no disponibilidad.

Para taps APNs, el delegate UIKit debe pasar `UNNotificationResponse` a
`IosNotificationDeepLinkAdapter.handleApnsTap(userInfo:)` y adjuntar un
`IosNotificationDeepLinkHost` cuando exista navegación Chat. El adaptador normaliza IDs de
`NSString`/`NSNumber` y payloads anidados `data`/`quata`/`payload`, y reutiliza el parser común
de deep links. El launcher actual no adjunta ese host porque aún no contiene navegación Chat.

En macOS, genera el proyecto con XcodeGen (`xcodegen generate`) desde esta carpeta y construye
primero el framework `QuataFeed` para el simulador iOS:

```sh
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
```

Windows no puede enlazar los targets nativos de iOS; la compilación Wasm y Android continúa
siendo la verificación disponible en este entorno.
