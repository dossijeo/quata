# Desarrollo y builds

## Requisitos generales

- Git.
- JDK 17.
- Android SDK con `compileSdk 36`.
- PowerShell en Windows para los scripts `.ps1`.
- Node/Chrome gestionados por Gradle para tests Wasm.
- macOS y Xcode para hosts y simuladores iOS.

No se deben compartir caches Gradle mutables entre builds simultáneos que puedan corromperse o interferir.

## Android

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

La instalación local depende de que `adb` esté disponible en `PATH` o se invoque desde el Android SDK.

## Web/Wasm

Distribución de producción:

```powershell
.\gradlew.bat :web:wasmJsBrowserDistribution
```

Salida habitual:

```text
web/build/dist/wasmJs/productionExecutable/
```

El artefacto necesita inyección de configuración pública de despliegue. Nunca se inyectan secretos. `http://localhost:4174/` está reservado para la distribución de la última `main`; los candidatos usan otros puertos.

## iOS

Las tareas Kotlin/Native pueden compilar targets `iosX64`, `iosArm64` e `iosSimulatorArm64`. El host Swift se genera/compila con los scripts y workflows documentados.

Referencias:

- [CI iOS](https://github.com/dossijeo/quata/blob/main/docs/IOS_CI.md)
- [Archive sin firma](https://github.com/dossijeo/quata/blob/main/docs/IOS_UNSIGNED_ARCHIVE.md)
- [Firma y release](https://github.com/dossijeo/quata/blob/main/docs/IOS_SIGNING_RELEASE.md)

## Configuración

Los valores públicos se suministran mediante propiedades Gradle, variables de entorno o metadatos del artefacto según plataforma. Los ficheros locales con credenciales deben permanecer fuera de Git y con permisos restringidos.

## Concurrencia

El pipeline admite como máximo un build Android, uno Wasm y uno iOS simultáneos. Una tarea que compile varias plataformas ocupa todas las lanes afectadas. Antes de lanzar otra tarea se revisan Java/Gradle, puertos, ADB y simuladores.
