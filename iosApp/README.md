# Host iOS

`iosApp` es el borde UIKit de Quata para iOS. No contiene pantallas Swift de
sustitución ni repositorios de ejemplo: el estado, ViewModels y UI de Auth y
Feed proceden de los módulos Compose/Kotlin compartidos exportados por
`QuataFeed.framework`.

## Estado actual

`AppDelegate` crea una raíz UIKit que instala una de estas superficies Compose:

- Feed cuando `IosFeedRuntimeBootstrap` restaura una sesión válida de Keychain.
- Auth cuando existen la configuración pública de runtime y no hay una sesión
  restaurable; tras el login, el host vuelve a instalar Feed.
- Un estado explícito de configuración/sesión pendiente cuando falta runtime.

El host retiene `IosPlatformServiceComposition` y conecta los adaptadores iOS
reales que ya están disponibles en el borde de plataforma (portapapeles,
preferencias, compartir, selector de documentos/galería, cámara, ubicación y
permisos). Cada feature debe recibir únicamente los contratos que consume. Las
rutas autenticadas de Chat y el resto de hosts siguen pendientes de composición;
la acción de conversaciones muestra un aviso explícito en vez de simular una
navegación inexistente.

La aplicación **no está terminada para iOS**. Faltan recorridos autenticados de
extremo a extremo, navegación completa, pruebas funcionales de permisos y
adaptadores, y composición de las demás features. El estado detallado y los
límites verificables están en [el tablero de migración](../docs/MULTIPLATFORM_MIGRATION_BOARD.md)
y [la auditoría de evidencia](../docs/MULTIPLATFORM_EVIDENCE_AUDIT.md).

## Configuración pública de runtime

Para mostrar Auth o restaurar/cargar Feed, el bundle necesita estos *build
settings* públicos:

```text
QUATA_SUPABASE_URL
QUATA_SUPABASE_PUBLISHABLE_KEY
```

`project.yml` los transfiere al `Info.plist` generado. Configúralos en el
esquema, en la configuración de CI o desde el entorno de tu integración; el
launcher rechaza valores vacíos o literales `$(...)` sin expandir. Nunca añadas
una service-role key, token de usuario, certificado ni VAPID privada al
repositorio, al bundle o a este archivo.

Sin esos valores, el host conserva una pantalla Compose honesta de configuración
pendiente. No fabrica datos, URL ni sesiones Swift para aparentar funcionalidad.

## Construcción local en macOS

Se requiere Xcode 16.3, JDK 17 y XcodeGen. Este flujo local enlaza el
framework que consume el host; la comprobación reproducible de todos los
módulos Kotlin/Native se ejecuta en la CI macOS descrita más abajo. Desde la
raíz del repositorio:

```bash
./gradlew :feature:feed:linkDebugFrameworkIosSimulatorArm64
brew install xcodegen
cd iosApp
xcodegen generate
cd ..
xcodebuild \
  -project iosApp/QuataIos.xcodeproj \
  -scheme QuataIos \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO \
  build
```

El esquema `QuataIos` incluye `QuataIosTests` y `QuataIosUITests`. Las pruebas
actuales cubren la frontera Swift/Kotlin y el arranque seguro del host; no
constituyen una validación funcional completa de Auth, Feed, permisos o media.

## CI macOS

El workflow [ios-build.yml](../.github/workflows/ios-build.yml) compila los
targets Kotlin/Native, enlaza `QuataFeed.framework`, genera el proyecto Xcode,
construye el host Swift y ejecuta XCTest en simulador. Desde PowerShell, con la
rama publicada y `gh auth login` hecho:

```powershell
.\scripts\run-ios-ci.ps1 -Ref NOMBRE_DE_RAMA
```

El script espera el resultado y descarga los informes en
`build-reports/ios/<run-id>`. Para comandos equivalentes, artefactos, logs y
restricciones de la toolchain, consulta [la guía de CI iOS](../docs/IOS_CI.md).

Windows no puede enlazar ni ejecutar los targets nativos de iOS; usa esa CI
macOS para la verificación iOS y conserva la compilación Android/Wasm como gates
locales correspondientes.
