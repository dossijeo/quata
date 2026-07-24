# Host iOS mínimo

`feature:feed/iosMain` expone `QuataFeedViewController(dependencies:)`. Al recibir un
`FeedRepository` iOS real, presenta `FeedBrowserHostContent`, cuyo ViewModel, estado y UI son
código Compose de `commonMain`; el adaptador iOS se limita a crear un `UIViewController`.

El launcher SwiftUI no fabrica un repositorio de ejemplo ni reutiliza el repositorio Android. Por
ahora muestra de forma explícita el estado de migración, hasta que exista un cliente autenticado
iOS que implemente `FeedRepository`. Conectar ese repositorio consiste en crear
`IosFeedHostDependencies(repository: …)` y pasarlo a `QuataFeedViewController`; no existe un
`FeedRootView` Swift separado.

La pantalla de migración no es una vista Swift de sustitución: presenta directamente el
`UIViewController` Compose exportado por `QuataFeed.framework`. Su vista raíz tiene el
identificador de accesibilidad `quata-ios-migration-status`, para que un UI test del host pueda
verificar esa frontera Swift/Kotlin sin requerir un repositorio ni sesión de ejemplo.

Los adaptadores iOS de portapapeles (`IosClipboardService`) y preferencias (`IosPreferenceStore`)
ya existen. `IosPlatformServices` también agrupa compartir y selector de archivos, que necesitan
un presenter/document picker UIKit activo antes de poder declararse disponibles.

Para archivos, `IosFilePickerService` recibe un `IosViewControllerProvider` y conecta los
adaptadores reales `IosDocumentPickerHost` e `IosPhotoPickerHost`: el primero importa documentos
con `UIDocumentPickerViewController` y el segundo abre la galería con `PHPickerViewController`.
Ambos conservan sus delegates y devuelven `PlatformFile` con URL, nombre y MIME; PhotosUI copia
la representación temporal al sandbox antes de devolverla. `IosImagePickerCameraHost` presenta
`UIImagePickerController` para cámara, conserva su delegate, devuelve cancelación/error reales y
copia JPEG al temporal; `IosPlatformServices(presenterProvider: …)` lo conecta automáticamente,
y `project.yml` declara `NSCameraUsageDescription`. El launcher no construye estos servicios todavía
porque ninguna de sus pantallas de estado consume `PlatformServices`; se deben adjuntar al crear el
host autenticado que vaya a usar una feature con archivos o cámara.

El mismo `IosViewControllerProvider` activa `IosUIKitSharePresenter` al construir
`IosPlatformServices`: `ShareService` presenta el `UIActivityViewController` real en el
controlador activo y devuelve `Unsupported` si el host no puede presentar. Sin provider, los
servicios conservan el comportamiento seguro de no disponibilidad.

Para taps APNs, el delegate UIKit debe pasar `UNNotificationResponse` a
`IosNotificationDeepLinkAdapter.handleApnsTap(userInfo:)` y adjuntar un
`IosNotificationDeepLinkHost` cuando exista navegación Chat. El adaptador normaliza IDs de
`NSString`/`NSNumber` y payloads anidados `data`/`quata`/`payload`, y reutiliza el parser común
de deep links. El launcher actual no adjunta ese host porque aún no contiene navegación Chat.

`IosCoreLocationHost` es el adaptador real de ubicación: se inyecta como `locationHost` al
construir `IosPlatformServices`, solicita únicamente permiso *When In Use* y usa
`CLLocationManager.requestLocation()` para devolver una coordenada real. También enruta
`PlatformPermission.Location` mediante `IosCompositePermissionService`, sin sustituir el
permiso de notificaciones. El target iOS debe declarar
`NSLocationWhenInUseUsageDescription`; sin host inyectado, ubicación continúa explícitamente no
disponible.

## Límite actual de inyección del launcher

`iosApp/project.yml` enlaza únicamente `QuataFeed.framework`. Éste reexporta `:core` para que
Swift pueda acceder a `IosPlatformServices` y sus contratos desde el mismo framework, sin
embebir un segundo framework Core. Eso sólo habilita la composición: `QuataFeed` todavía no
consume `IosPlatformServices` hasta recibir un `FeedRepository` real. Por ello el launcher no
debe crear un `IosViewControllerProvider` todavía: compartir, selector, audio y APNs quedarían
construidos sin consumidor. La primera fase que añada repositorio autenticado/navegación debe
construir `IosPlatformServices(presenterProvider: …)` desde el controlador Compose activo y
entregarlo a una feature que lo use; así los adaptadores reales se conectarán a una superficie
efectiva.

## Bloqueo verificable para una composición Swift real

La única entrada de Feed que puede mostrar contenido real es
`QuataFeedViewController(dependencies:)`; sus dependencias exigen una implementación completa de
`FeedRepository`. No basta con cargar una lista inicial: `FeedReadRepository` exige observación
continua, carga/recarga/paginación, perfil actual y autor y detalle de post. Las mutaciones de
like, reporte, comentario y borrado forman `FeedMutationRepository`; `FeedRepository` compone
ambos. `iosReadOnlyFeedHostDependencies` envuelve un backend de lectura con
`ReadOnlyFeedRepository`, que devuelve explícitamente `Unsupported` para mutaciones. Esto permite
que un backend iOS real y revisado habilite primero el navegador compartido, pero no sustituye su
implementación: en el árbol actual sólo existen `FeedRepositoryImpl` de Android (depende de
`Context`, sesión, Supabase, WordPress y caché Android) y `WebFeedRepository` en `wasmJsMain`.
No hay implementación bajo `feature/feed/src/iosMain` ni un cliente de sesión/HTTP iOS que pueda
satisfacer siquiera el contrato de lectura.

La ruta mínima ya está definida sin secretos: `RemoteFeedReadRepository` en `commonMain` recibe
un `FeedReadTransport`. El adaptador iOS debe implementar ese transporte con URLSession, la URL
pública de despliegue y el token de la sesión del usuario inyectados por su launcher; devuelve DTOs
`FeedRemote*` y el repositorio común hace el mapeo, perfilado y polling. No hay URL, clave ni
fallback de datos en ese contrato, y las mutaciones siguen bloqueadas por `ReadOnlyFeedRepository`.

La especificación de lectura sí está fijada por el host Web: `GET {base}/rest/v1` sobre
`community_posts`, `community_comments`, `community_post_likes` y `community_profiles`, con
cabeceras `apikey`, `Authorization: Bearer <access-token>` y `Accept: application/json`; los
campos y filtros PostgREST están definidos por `WebFeedRepository`. El bloqueo restante no es el
endpoint: iOS aún no tiene un flujo que inyecte de forma segura la URL pública, la publishable key
y un access token renovable. Hasta que exista ese proveedor de sesión, un `URLSession` real sólo
devolvería errores de configuración/autorización y no debe montarse en el launcher.

Además, `IosFeedHostDependencies` no recibe `PlatformServices`; por tanto construir
`IosPlatformServices` desde Swift ahora no puede llegar a Feed. El siguiente cambio con consumidor
real debe introducir un repositorio iOS autenticado y, únicamente si esa feature necesita servicios
del sistema, ampliar su composition root para recibir los contratos concretos que use. Hasta ese
momento el estado de migración evita una UI aparentemente funcional con datos o adaptadores falsos.

En macOS, genera el proyecto con XcodeGen (`xcodegen generate`) desde esta carpeta y construye
primero el framework `QuataFeed` para el simulador iOS:

```sh
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
```

Windows no puede enlazar los targets nativos de iOS; la compilación Wasm y Android continúa
siendo la verificación disponible en este entorno.

## Prueba de frontera Swift/Kotlin

XcodeGen crea `QuataIosUITests`. Su única prueba lanza la aplicación real y espera el identificador
`quata-ios-migration-status` de la vista raíz del `UIViewController` Compose exportado por
`QuataFeed.framework`; no usa repositorio, sesión ni contenido de ejemplo. En macOS, después de
generar el proyecto y enlazar el framework, se puede ejecutar con un simulador arrancado:

```sh
xcodebuild -project QuataIos.xcodeproj -scheme QuataIos \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' test
```
