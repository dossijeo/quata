# Host iOS

`iosApp` es el borde UIKit de Quata para iOS. No contiene pantallas Swift de
sustitución ni repositorios de ejemplo: el estado, ViewModels y UI de Auth y
Feed proceden de los módulos Compose/Kotlin compartidos exportados por
`QuataShared.xcframework`.

## Frontera de framework

`iosApp` embebe exclusivamente `QuataShared.xcframework`, producido por
`:ios-shared`. El módulo paraguas exporta las superficies Kotlin que el host
usa hoy (`:core`, Auth, Feed, Chat y Notifications) y no contiene pantallas,
navegación ni repositorios. Los nombres de las fachadas Kotlin, como
`QuataFeedViewControllerKt`, se conservan para evitar una ruptura de la API
Swift; lo que cambia es sólo el módulo Swift importado (`QuataShared`).

`feature:feed` ya no fabrica ni exporta el framework de la aplicación y no
depende de features hermanas. Las futuras features iOS se añaden al paraguas
únicamente cuando una ruta UIKit real requiera su API pública.

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

## Enlaces directos iOS

El host registra el esquema personalizado estable `quata`. El formato que iOS
entrega al `AppDelegate` es `quata://egquata.com/#post-<id>`; los fragmentos
`official-<id>` y `chat-<conversation>?message=<id>` se resuelven por el mismo
parser Kotlin compartido. Por ejemplo, en un simulador arrancado:

```bash
xcrun simctl openurl booted 'quata://egquata.com/#post-<id>'
```

Los enlaces `https://egquata.com/#...` siguen siendo el formato web/compartido.
No son Universal Links en iOS: este target no declara Associated Domains ni
pretende que el sistema entregue URLs HTTPS a la app.

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

El registro iOS está desactivado por defecto (`QUATA_IOS_REGISTRATION_ENABLED=false`).
Su activación requiere que el entorno de firma inyecte, además, estos valores
públicos y efímeros; cualquier valor ausente, vacío o sin expandir mantiene el
registro inaccesible:

```text
QUATA_IOS_REGISTRATION_API_KEY
QUATA_IOS_REGISTRATION_CLIENT_INSTANCE_ID
QUATA_IOS_REGISTRATION_CHALLENGE_TOKEN
```

No se almacenan secretos de servicio ni tokens de usuario en el repositorio o
en los valores predeterminados del proyecto.

## Construcción local en macOS

Se requiere Xcode 16.3, JDK 17 y XcodeGen. Este flujo local enlaza el
framework que consume el host; la comprobación reproducible de todos los
módulos Kotlin/Native se ejecuta en la CI macOS descrita más abajo. Desde la
raíz del repositorio:

```bash
./gradlew :ios-shared:assembleQuataSharedDebugXCFramework
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
actuales cubren la frontera Swift/Kotlin, el arranque seguro del host y las
políticas deterministas del adaptador de documentos (MIME→UTI para PDF/RTF/Office
y rechazo de referencias no locales antes de Quick Look); no constituyen una
validación funcional completa de Auth, Feed, permisos o media.

## CI macOS

El workflow [ios-build.yml](../.github/workflows/ios-build.yml) compila los
targets Kotlin/Native, ensambla `QuataShared.xcframework`, genera el proyecto Xcode,
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

### Mac Intel: bootstrap y build de simulador x86_64

Un Mac Intel usa la slice `iosX64`, no la slice `iosSimulatorArm64` de la CI
Apple Silicon. El bootstrap instala Temurin 17.0.20+8 x64 como `JAVA_HOME`,
JetBrains Runtime 21.0.10 x64 para el daemon fijado por Gradle y XcodeGen
2.44.1 bajo el usuario actual, sin `sudo` ni cambios de perfil. Versiones,
URLs, SHA-256 y el commit del tag XcodeGen quedan fijados en el script; los
binarios y el archivo de entorno se reemplazan de forma atómica.

```bash
bash scripts/bootstrap-ios-intel-mac.sh
source ~/.config/quata/ios-intel.env
bash scripts/build-ios-intel-simulator.sh
```

El build llama al wrapper como `bash ./gradlew`, compila/enlaza `iosX64`, crea
un XCFramework local de una sola slice y construye el host con `ARCHS=x86_64`.
El archive generico sigue usando la tarea canonica que reconstruye su
XCFramework completo con `iosArm64`; ambos carriles permanecen separados. El
build Intel no arranca simulador ni ejecuta pruebas XCTest/UI.

## Archive genérico sin firma

En macOS, `bash scripts/archive-ios-unsigned.sh` valida por separado que el
host y la slice `iosArm64` de `QuataShared.xcframework` forman un
`.xcarchive` para `generic/platform=iOS`. No ejecuta XCTest, no genera un IPA
y no firma ni distribuye una aplicación. Consulta
[`IOS_UNSIGNED_ARCHIVE.md`](../docs/IOS_UNSIGNED_ARCHIVE.md) para los límites
de firma, provisioning y dispositivo físico que siguen pendientes.
