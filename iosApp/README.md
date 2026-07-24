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

En macOS, genera el proyecto con XcodeGen (`xcodegen generate`) desde esta carpeta y construye
primero el framework `QuataFeed` para el simulador iOS:

```sh
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
```

Windows no puede enlazar los targets nativos de iOS; la compilación Wasm y Android continúa
siendo la verificación disponible en este entorno.
